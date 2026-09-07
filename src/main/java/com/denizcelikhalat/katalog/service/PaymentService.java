package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.MeasurementMode;
import com.denizcelikhalat.katalog.model.Order;
import com.denizcelikhalat.katalog.model.OrderItem;
import com.denizcelikhalat.katalog.model.PaymentStatus;
import com.denizcelikhalat.katalog.model.PriceCurrency;
import com.denizcelikhalat.katalog.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kartla ödeme akışının orkestrasyonu: sipariş -> (gerekirse döviz kuru hesapla) -> iyzico
 * Checkout Form başlat -> callback'te sonucu SUNUCU TARAFINDA doğrula -> Order.paymentStatus
 * güncelle.
 *
 * Kart bilgisi (numara/CVV) bu serviste veya herhangi bir yerde bizim tarafımızda TUTULMAZ;
 * yalnızca iyzico'nun ürettiği token/paymentPageUrl ile çalışılır.
 *
 * TRY DÖNÜŞÜMÜ VE GÜVENLİK KURALLARI (bu sınıfın tek sorumluluğu):
 * - Ürünler EUR/USD/TRY karışık olabilir; iyzico'ya HER ZAMAN TRY gönderilir.
 * - Güncel kur ExchangeRateService'ten alınır (TCMB). Kur GÜVENİLİR şekilde alınamazsa
 *   (TCMB erişilemedi + cache yok/bayat + fallback kapalı) ödeme KESİNLİKLE BAŞLATILMAZ.
 * - Bir sipariş için ödeme tutarı bir kez hesaplanınca (Order.paymentAmount dolunca) DONAR:
 *   kullanıcı "Ödemeye Devam Et" ile tekrar denerse kur değişse bile AYNI tutar kullanılır.
 * - TRY'ye çevrilmiş tutar belirlenen eşiği (manual-approval-threshold-try) aşarsa, otomatik
 *   ödeme başlatılmaz; manuel onay/iletişim istenir.
 *
 * AŞAMA 6 — ÖDEME TUTARINA KARGO DAHİL EDİLMESİ:
 * Ödeme tutarı artık [ürün toplamı (TRY'ye çevrilmiş)] + [Order.shippingCost SNAPSHOT'ı]
 * şeklinde hesaplanır: product total -> TRY conversion -> + Order.shippingCost -> manual
 * approval kontrolü -> iyzico. Order.shippingCost, sipariş oluşturulurken (OrderService.
 * placeOrder) ShippingService/ShippingCostService tarafından BİR KEZ hesaplanıp Order'a
 * yazılmış SNAPSHOT değerdir ve zaten TRY cinsindendir; burada ASLA yeniden hesaplanmaz
 * (ShippingService/ShippingCostService bu sınıftan hiç çağrılmaz), yalnızca okunur.
 * shippingCost null ise (kargo hesaplanamadı/manuel inceleme) 0 TL kabul edilir; ödeme akışı
 * bundan etkilenmez/patlamaz. Order.totalAmount (ürün toplamı) BU HESAPTAN ETKİLENMEZ —
 * yalnızca Order.paymentAmount (iyzico'ya giden gerçek tahsilat tutarı) kargo dahil olur.
 */
@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final IyzicoClient iyzicoClient;
    private final ExchangeRateService exchangeRateService;

    @Value("${app.payment.callback-url:http://localhost:8080/payment/callback}")
    private String callbackUrl;

    @Value("${app.payment.exchange.manual-approval-threshold-try:50000}")
    private BigDecimal manualApprovalThresholdTry;

    public PaymentService(OrderRepository orderRepository,
                          OrderService orderService,
                          IyzicoClient iyzicoClient,
                          ExchangeRateService exchangeRateService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.iyzicoClient = iyzicoClient;
        this.exchangeRateService = exchangeRateService;
    }

    public static class InitiateOutcome {
        public boolean success;
        public String redirectUrl;   // başarılıysa: kullanıcının yönlendirileceği iyzico sayfası
        public String errorMessage;  // başarısızsa: kullanıcıya/loga gösterilecek sebep
    }

    /**
     * Bir siparişi ödeme akışına sokar. Sipariş zaten PENDING durumda oluşturulmuş olmalı
     * (OrderService.placeOrder). CUSTOM (teklif) ürünler sepete hiç girmediği için burada
     * yalnızca defansif bir kontrol olarak tekrar reddedilir.
     *
     * Kur alınamazsa VEYA tutar manuel onay eşiğini aşarsa: paymentStatus PENDING kalır, sepet
     * SİLİNMEZ (markOrderAsPaid hiç çağrılmaz), mail GÖNDERİLMEZ, iyzico'ya YÖNLENDİRME yapılmaz.
     */
    @Transactional
    public InitiateOutcome initiatePayment(Long orderId, String buyerIp) {
        InitiateOutcome outcome = new InitiateOutcome();

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            outcome.success = false;
            outcome.errorMessage = "Sipariş bulunamadı.";
            return outcome;
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            outcome.success = false;
            outcome.errorMessage = "Bu sipariş için ödeme zaten tamamlanmış.";
            return outcome;
        }

        // ---- CUSTOM (teklif) ürün güvenlik kontrolü ----
        // Ürün detay sayfasında CUSTOM ürünler için Sepete Ekle formu zaten gösterilmiyor,
        // dolayısıyla normal şartlarda sepete/siparişe giremezler. Yine de savunma amaçlı
        // burada da reddedilir; ödeme akışına asla girmemeleri garanti edilir.
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null
                        && item.getProduct().getMeasurementMode() == MeasurementMode.CUSTOM) {
                    outcome.success = false;
                    outcome.errorMessage = "Teklife bağlı (CUSTOM) ürünler kartla ödeme akışına giremez.";
                    return outcome;
                }
            }
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            outcome.success = false;
            outcome.errorMessage = "Sipariş boş, ödeme başlatılamaz.";
            return outcome;
        }

        // ---- Ödeme tutarını belirle: dondurulmuş mu, yoksa yeni mi hesaplanacak? ----
        ChargeOutcome charge = buildCharge(order);
        if (!charge.available) {
            // Kur güvenilir şekilde alınamadı -> ASLA fallback'e sessizce düşülmez, ödeme başlatılmaz.
            logger.warn("Ödeme başlatılamadı (döviz kuru alınamadı) - orderId={}", order.getId());
            outcome.success = false;
            outcome.errorMessage = charge.errorMessage;
            return outcome;
        }

        // ---- Büyük sipariş güvenliği: manuel onay eşiği ----
        if (manualApprovalThresholdTry != null
                && charge.amount.compareTo(manualApprovalThresholdTry) > 0) {
            logger.warn("Ödeme başlatılamadı (manuel onay eşiği aşıldı) - orderId={}, tutar={}, esikTry={}",
                    order.getId(), charge.amount, manualApprovalThresholdTry);
            outcome.success = false;
            outcome.errorMessage = "Bu tutardaki siparişler için ödeme öncesi fiyat onayı gerekmektedir. "
                    + "Lütfen bizimle iletişime geçin.";
            return outcome;
        }

        // ---- iyzico Checkout Form başlat ----
        String conversationId = "ORDER-" + order.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);

        IyzicoClient.InitializeResult result = iyzicoClient.initializeCheckoutForm(
                order, conversationId, buyerIp, callbackUrl, charge.chargeRequest);

        if (!result.success || result.paymentPageUrl == null) {
            // GÜVENLİ LOG: yalnızca iyzico'nun teşhis alanları (status/errorCode/errorGroup/
            // conversationId/errorMessage) loglanır. API key/secret/Authorization header/
            // checkout form token/kart bilgisi/kullanıcı şifresi BURADA ASLA loglanmaz.
            logger.error("iyzico ödeme başlatma başarısız - orderId={}, status={}, errorCode={}, "
                            + "errorGroup={}, conversationId={}, errorMessage={}",
                    order.getId(), result.status, result.errorCode, result.errorGroup,
                    (result.conversationId != null ? result.conversationId : conversationId),
                    result.errorMessage);

            outcome.success = false;
            outcome.errorMessage = (result.errorMessage != null)
                    ? result.errorMessage : "Ödeme sayfası oluşturulamadı.";
            return outcome;
        }

        order.setPaymentProvider("IYZICO");
        order.setPaymentConversationId(conversationId);
        order.setPaymentToken(result.token);
        order.setPaymentStatus(PaymentStatus.PENDING);

        // Tutar/kur metadatası SADECE ilk defa (henüz dondurulmamışsa) yazılır; sonraki
        // "Ödemeye Devam Et" denemelerinde AYNI kalır (kur değişse bile tutar değişmez).
        if (order.getPaymentAmount() == null || order.getPaymentCurrency() == null) {
            order.setPaymentAmount(charge.amount);
            order.setPaymentCurrency(charge.currency);
            order.setPaymentExchangeRateSource(charge.rateSource);
            order.setPaymentExchangeRateFetchedAt(charge.rateFetchedAt);
            order.setPaymentExchangeRateMarginPercent(charge.marginPercent);
        }
        orderRepository.save(order);

        outcome.success = true;
        outcome.redirectUrl = result.paymentPageUrl;
        return outcome;
    }

    // ===================== Ödeme tutarı hesaplama (TRY) =====================

    private static class ChargeOutcome {
        boolean available;
        IyzicoClient.ChargeRequest chargeRequest;
        BigDecimal amount;
        String currency = "TRY";
        String rateSource;
        LocalDateTime rateFetchedAt;
        BigDecimal marginPercent;
        String errorMessage;
    }

    /**
     * Bir sipariş için ödeme tutarını belirler:
     * - Sipariş zaten daha önce hesaplanmış bir paymentAmount/paymentCurrency'ye sahipse
     *   (yani daha önce en az bir kez initiatePayment başarıyla iyzico'ya ulaşmışsa), o tutar
     *   AYNEN kullanılır (DONDURULMUŞ tutar) — kur güncel olsa da olmasa da tekrar hesaplanmaz.
     * - Yoksa ExchangeRateService'ten güncel kur istenir; kur alınamazsa available=false döner
     *   ve PaymentService ödeme başlatmaz (fallback'e sessizce düşülmez).
     */
    private ChargeOutcome buildCharge(Order order) {
        ChargeOutcome outcome = new ChargeOutcome();

        // ---- 1) Tutar zaten dondurulmuş mu? ----
        if (order.getPaymentAmount() != null && order.getPaymentCurrency() != null) {
            outcome.available = true;
            outcome.amount = order.getPaymentAmount();
            outcome.currency = order.getPaymentCurrency();
            outcome.rateSource = order.getPaymentExchangeRateSource();
            outcome.rateFetchedAt = order.getPaymentExchangeRateFetchedAt();
            outcome.marginPercent = order.getPaymentExchangeRateMarginPercent();

            // Basket kalemi: dondurulmuş TOPLAM tek kalem olarak gönderilir. Kalem bazlı kur
            // geçmişi ayrıca saklanmadığından (yalnızca toplam donduruluyor), parça parça
            // yeniden hesaplanmaz -> toplamın SABİT kalması önceliklidir. iyzico yalnızca
            // basketItems toplamının price/paidPrice'a eşit olmasını şart koşar, kalem
            // sayısını/detayını değil.
            IyzicoClient.ChargeItem singleItem = new IyzicoClient.ChargeItem();
            singleItem.name = "Sipariş #" + order.getId();
            singleItem.price = outcome.amount;

            IyzicoClient.ChargeRequest req = new IyzicoClient.ChargeRequest();
            req.totalAmount = outcome.amount;
            req.items = List.of(singleItem);
            outcome.chargeRequest = req;
            return outcome;
        }

        // ---- 2) İlk kez hesaplanıyor: güncel kuru iste. ----
        ExchangeRateService.RateResult rates = exchangeRateService.getEffectiveRates();
        if (!rates.available) {
            outcome.available = false;
            outcome.errorMessage = "Güncel döviz kuru alınamadığı için ödeme şu anda başlatılamıyor. "
                    + "Lütfen kısa süre sonra tekrar deneyin veya bizimle iletişime geçin.";
            return outcome;
        }

        // ---- "Resmi" toplam: para birimi GRUBU bazında çevir + yuvarla, sonra topla. ----
        Map<PriceCurrency, BigDecimal> totalsByCurrency = order.getTotalsByCurrency();
        BigDecimal officialTotal = BigDecimal.ZERO;
        if (totalsByCurrency != null) {
            for (Map.Entry<PriceCurrency, BigDecimal> e : totalsByCurrency.entrySet()) {
                officialTotal = officialTotal.add(convertToTry(e.getValue(), e.getKey(), rates));
            }
        }

        // ---- Kalem bazlı TRY tutarları (basketItems için). ----
        List<IyzicoClient.ChargeItem> items = new ArrayList<>();
        BigDecimal itemsSum = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            PriceCurrency itemCurrency = resolveItemCurrency(item, order);
            BigDecimal unit = (item.getPrice() != null) ? item.getPrice() : BigDecimal.ZERO;
            int quantity = (item.getQuantity() != null) ? item.getQuantity() : 0;
            BigDecimal lineOriginal = unit.multiply(BigDecimal.valueOf(quantity));
            BigDecimal lineTry = convertToTry(lineOriginal, itemCurrency, rates);

            IyzicoClient.ChargeItem ci = new IyzicoClient.ChargeItem();
            ci.name = (item.getProduct() != null && item.getProduct().getName() != null)
                    ? item.getProduct().getName() : "Ürün";
            ci.price = lineTry;
            items.add(ci);
            itemsSum = itemsSum.add(lineTry);
        }

        // ---- Yuvarlama farkını SON kaleme ekleyerek düzelt: sum(items) == officialTotal. ----
        if (!items.isEmpty()) {
            BigDecimal diff = officialTotal.subtract(itemsSum);
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                IyzicoClient.ChargeItem last = items.get(items.size() - 1);
                last.price = last.price.add(diff);
            }
        } else {
            IyzicoClient.ChargeItem single = new IyzicoClient.ChargeItem();
            single.name = "Sipariş #" + order.getId();
            single.price = officialTotal;
            items.add(single);
        }

        // ---- AŞAMA 6: Kargo ücretini (Order.shippingCost SNAPSHOT'ı) ödeme tutarına dahil et. ----
        // ÖNEMLİ: ShippingService/ShippingCostService BURADA TEKRAR ÇAĞRILMAZ — yalnızca
        // sipariş oluşturulurken (OrderService.placeOrder) hesaplanıp Order'a yazılmış olan
        // SNAPSHOT değer okunur. Bu değer zaten TRY cinsindendir (ShippingCostService'in
        // application.properties'teki base-cost/per-kg-cost ayarları TRY bazlıdır), bu yüzden
        // ayrıca bir döviz dönüşümüne TABİ TUTULMAZ. shippingCost null ise (kargo hesaplanamadı/
        // manuel inceleme gerekiyor) ödeme akışı ASLA patlamaz; kargo 0 TL kabul edilip yalnızca
        // ürün tutarı üzerinden ödeme başlatılır.
        BigDecimal shippingCost = order.getShippingCost();
        if (shippingCost == null) {
            logger.info("Order {} için shippingCost snapshot'ı null; ödeme tutarına kargo eklenmeden "
                    + "(0 TL kabul edilerek) devam ediliyor.", order.getId());
            shippingCost = BigDecimal.ZERO;
        }
        if (shippingCost.compareTo(BigDecimal.ZERO) > 0) {
            IyzicoClient.ChargeItem shippingItem = new IyzicoClient.ChargeItem();
            shippingItem.name = "Kargo Ücreti";
            shippingItem.price = shippingCost;
            items.add(shippingItem);

            officialTotal = officialTotal.add(shippingCost);
        }

        IyzicoClient.ChargeRequest req = new IyzicoClient.ChargeRequest();
        req.totalAmount = officialTotal;
        req.items = items;

        outcome.available = true;
        outcome.amount = officialTotal;
        outcome.currency = "TRY";
        outcome.rateSource = rates.source;
        outcome.rateFetchedAt = rates.fetchedAt;
        outcome.marginPercent = rates.marginPercent;
        outcome.chargeRequest = req;
        return outcome;
    }

    // Bir kalemin para birimini çözer: currencySnapshot -> ürünün para birimi -> siparişin para
    // birimi -> USD (Order.getTotalsByCurrency() ile AYNI çözümleme mantığı, tutarlılık için).
    private static PriceCurrency resolveItemCurrency(OrderItem item, Order order) {
        if (item.getCurrencySnapshot() != null) return item.getCurrencySnapshot();
        if (item.getProduct() != null && item.getProduct().getCurrency() != null) {
            return item.getProduct().getCurrency();
        }
        if (order.getCurrency() != null) return order.getCurrency();
        return PriceCurrency.USD;
    }

    /**
     * Checkout sayfasında (henüz Order oluşmadan) "Genel Toplam" ÖNİZLEMESİ göstermek için
     * kullanılır. buildCharge()'ın ödeme sırasında kullandığı BİREBİR AYNI kur kaynağını
     * (exchangeRateService.getEffectiveRates()) ve BİREBİR AYNI dönüşüm/yuvarlama mantığını
     * (convertToTry) çağırır — kod TEKRARLANMAZ, gerçekten PAYLAŞILIR. Bu sayede burada
     * gösterilen tutar ile ödeme sırasında iyzico'ya GERÇEKTEN gönderilecek tutar tutarlıdır
     * (kur, iki çağrı arasında -genelde 60 dk'lık cache sayesinde- değişmediği sürece).
     *
     * Bu metot bir SİPARİŞ OLUŞTURMAZ, hiçbir yan etkisi YOKTUR — yalnızca gösterim amaçlı bir
     * ön hesaplamadır. Kur güvenilir şekilde alınamazsa (TCMB erişilemiyor + cache yok/bayat +
     * fallback kapalı) null döner; çağıran taraf bu durumda TRY tahmini GÖSTERMEMELİDİR
     * (mevcut orijinal para birimi gösterimine geri dönmelidir).
     *
     * @param productTotalsByCurrency ürün toplamları, para birimine göre (Cart veya Order'ın
     *                                getTotalsByCurrency() metodundan — ikisi de aynı tipte)
     */
    public BigDecimal convertProductTotalsToTry(Map<PriceCurrency, BigDecimal> productTotalsByCurrency) {
        ExchangeRateService.RateResult rates = exchangeRateService.getEffectiveRates();
        if (!rates.available) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        if (productTotalsByCurrency != null) {
            for (Map.Entry<PriceCurrency, BigDecimal> e : productTotalsByCurrency.entrySet()) {
                total = total.add(convertToTry(e.getValue(), e.getKey(), rates));
            }
        }
        return total;
    }

    /**
     * Checkout sayfasında HER para birimi için AYRI AYRI TRY karşılığını göstermek amacıyla
     * kullanılır (örn. "EUR Toplam: 14,00 € (~602,00 TL)", "USD Toplam: 50,00 $ (~2.150,00 TL)").
     * convertProductTotalsToTry(...) ile BİREBİR AYNI kur kaynağını (exchangeRateService.
     * getEffectiveRates()) ve AYNI dönüşüm mantığını (convertToTry) kullanır — kod tekrarı yok.
     *
     * Kur güvenilir şekilde alınamazsa null döner; çağıran taraf bu durumda TRY karşılığı
     * GÖSTERMEMELİDİR (mevcut orijinal para birimi gösterimine güvenli şekilde geri dönmelidir).
     *
     * @return para birimi -> o para biriminin TRY karşılığı. TRY'nin kendisi de haritada
     *         bulunur (değeri kendisiyle aynıdır); çağıran taraf TRY için ayrıca bir "(~X TL)"
     *         ipucu göstermek istemiyorsa bunu kendi tarafında (currency == TRY kontrolüyle)
     *         atlayabilir.
     */
    public Map<PriceCurrency, BigDecimal> convertEachCurrencyToTry(Map<PriceCurrency, BigDecimal> totalsByCurrency) {
        ExchangeRateService.RateResult rates = exchangeRateService.getEffectiveRates();
        if (!rates.available) {
            return null;
        }
        Map<PriceCurrency, BigDecimal> result = new LinkedHashMap<>();
        if (totalsByCurrency != null) {
            for (Map.Entry<PriceCurrency, BigDecimal> e : totalsByCurrency.entrySet()) {
                result.put(e.getKey(), convertToTry(e.getValue(), e.getKey(), rates));
            }
        }
        return result;
    }

    // ExchangeRateService'ten alınan (margin uygulanmış) kurla bir tutarı TRY'ye çevirir.
    private static BigDecimal convertToTry(BigDecimal amount, PriceCurrency currency,
                                            ExchangeRateService.RateResult rates) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate;
        if (currency == null) {
            rate = BigDecimal.ONE;
        } else {
            switch (currency) {
                case TRY:
                    rate = BigDecimal.ONE;
                    break;
                case EUR:
                    rate = (rates.eurToTry != null) ? rates.eurToTry : BigDecimal.ONE;
                    break;
                case USD:
                    rate = (rates.usdToTry != null) ? rates.usdToTry : BigDecimal.ONE;
                    break;
                default:
                    rate = BigDecimal.ONE;
            }
        }
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    // ===================== Callback (değişmedi) =====================

    public static class CallbackOutcome {
        public boolean paid;
        public Long orderId;
    }

    /**
     * iyzico'nun callbackUrl'e POST ettiği token ile çağrılır. Sonucu SUNUCU TARAFINDA
     * (retrieveCheckoutForm ile) doğrular; tarayıcıdan gelen bilgiye asla güvenilmez.
     */
    @Transactional
    public CallbackOutcome handleCallback(String token) {
        CallbackOutcome outcome = new CallbackOutcome();

        if (token == null || token.isBlank()) {
            outcome.paid = false;
            return outcome;
        }

        Order order = orderRepository.findByPaymentToken(token).orElse(null);
        if (order == null) {
            outcome.paid = false;
            return outcome;
        }
        outcome.orderId = order.getId();

        IyzicoClient.RetrieveResult result = iyzicoClient.retrieveCheckoutForm(token);

        if (result.paymentSuccessful) {
            orderService.markOrderAsPaid(order.getId(), "IYZICO", token);
            outcome.paid = true;
        } else {
            orderService.markOrderAsFailed(order.getId(), "IYZICO", token);
            outcome.paid = false;
        }
        return outcome;
    }
}
