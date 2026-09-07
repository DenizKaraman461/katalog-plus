package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.CartItem;
import com.denizcelikhalat.katalog.model.CheckoutForm;
import com.denizcelikhalat.katalog.model.Order;
import com.denizcelikhalat.katalog.model.OrderItem;
import com.denizcelikhalat.katalog.model.OrderStatus;
import com.denizcelikhalat.katalog.model.PaymentStatus;
import com.denizcelikhalat.katalog.model.PriceCurrency;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.model.ShippingCalculationResult;
import com.denizcelikhalat.katalog.model.ShippingCostResult;
import com.denizcelikhalat.katalog.model.User;
import com.denizcelikhalat.katalog.repository.CartRepository;
import com.denizcelikhalat.katalog.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final EmailService emailService;
    private final ShippingService shippingService;
    private final ShippingCostService shippingCostService;

    @Value("${app.company.email:info@denizcelikhalat.com}")
    private String companyEmail;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        EmailService emailService,
                        ShippingService shippingService,
                        ShippingCostService shippingCostService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.emailService = emailService;
        this.shippingService = shippingService;
        this.shippingCostService = shippingCostService;
    }

    /**
     * Checkout formundaki bilgilerle siparişi oluşturur. Sepeti alır, Order + OrderItem'ları
     * kaydeder; ödeme onayı gelmeden mail gönderilmez.
     *
     * ÖNEMLİ: Sepet burada ARTIK TEMİZLENMEZ. Ödeme henüz alınmadığı için (paymentStatus=PENDING)
     * kullanıcı iyzico ekranından geri dönerse veya ödeme başarısız/iptal olursa sepetini kaybetmez.
     * Sepet yalnızca ödeme GERÇEKTEN başarılı olduğunda, markOrderAsPaid(...) içinde temizlenir.
     *
     * NOT (bilinen sınırlama / gelecekte iyileştirilebilir): Sepet checkout'ta boşaltılmadığı için,
     * kullanıcı ödeme sayfasına gitmeden/ödemeyi tamamlamadan checkout'u tekrar tekrar gönderirse
     * her seferinde ayrı bir PENDING sipariş oluşabilir (duplicate pending order). Bu davranış
     * ÖNCEKİ sürümde de zaten mümkündü (farklı bir nedenle) ve kapsamlı bir engelleme (örn. aynı
     * sepet için açık bir PENDING sipariş varsa onu yeniden kullanmak) bu değişikliğin kapsamı
     * dışında bırakıldı; büyük bir refactor gerektirir. İstenirse ayrı bir görev olarak ele alınabilir.
     *
     * Basit stok: sayısal stok takibi YOK. Yalnızca ürün active/inStock değilse sipariş reddedilir.
     * Sipariş ve ödeme durumu PENDING olarak ayarlanır (ödeme, iyzico callback'i ile onaylanır).
     */
    @Transactional
    public Order placeOrder(String email, CheckoutForm form) {
        Cart cart = cartRepository.findByUserEmailWithItems(email)
                .orElseThrow(() -> new IllegalStateException("Sepet bulunamadı."));

        List<CartItem> items = cart.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("Sepetiniz boş, sipariş oluşturulamaz.");
        }

        User user = cart.getUser();

        Order order = new Order();
        order.setUser(user);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING); // ödeme yok -> PENDING
        order.setPaymentStatus(PaymentStatus.PENDING);

        // ===== Checkout snapshot'ları =====
        order.setCustomerName(form.getCustomerName());
        order.setCustomerPhone(form.getCustomerPhone());
        order.setDeliveryAddress(form.getDeliveryAddress());
        order.setCustomerNote(form.getCustomerNote());
        order.setCustomerEmail(user != null ? user.getEmail() : email);
        order.setPreInfoAccepted(form.isPreInfoAccepted());
        order.setDistanceSalesAccepted(form.isDistanceSalesAccepted());

        // AŞAMA 10.1 (revize): iyzico buyer bilgisi için müşteri şehir SNAPSHOT'ı. TC Kimlik No
        // toplanmıyor/kaydedilmiyor — bkz. IyzicoClient.buildInitializeBody (identityNumber
        // mevcut müşteri verilerinden türetilir, ayrı bir snapshot alanı gerekmez).
        order.setCustomerCity(form.getCustomerCity());
        // Şirket yalnızca Türkiye içine gönderim yapıyor -> sabit iş kararı (form alanı yok).
        order.setCustomerCountry("Turkey");

        // ===== Karışık para birimine izin verilir: toplamlar para birimine göre gruplanır (dönüşüm YOK) =====
        Map<PriceCurrency, BigDecimal> totalsByCurrency = new LinkedHashMap<>();

        for (CartItem item : items) {
            Product product = item.getProduct();
            int quantity = (item.getQuantity() != null) ? item.getQuantity() : 0;

            // Yayın & basit stok doğrulaması (sayısal stok yok)
            if (product != null) {
                if (Boolean.FALSE.equals(product.getActive())) {
                    throw new IllegalStateException("'" + product.getName()
                            + "' ürünü artık satışta değil. Lütfen sepetten çıkarın.");
                }
                if (Boolean.FALSE.equals(product.getInStock())) {
                    throw new IllegalStateException("'" + product.getName()
                            + "' ürünü stokta yok. Lütfen sepetten çıkarın.");
                }
            }

            // Kalemin para birimi (tek para birimi zorunluluğu YOK)
            PriceCurrency itemCurrency = resolveItemCurrency(item);

            BigDecimal unitPrice = (item.getUnitPriceSnapshot() != null)
                    ? item.getUnitPriceSnapshot()
                    : ((product != null && product.getPrice() != null)
                        ? BigDecimal.valueOf(product.getPrice()) : BigDecimal.ZERO);

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            OrderItem orderItem = new OrderItem(product, quantity, unitPrice,
                    item.getSelectedMeasurement(), item.getMeasurementAmount());
            orderItem.setCurrencySnapshot(itemCurrency);
            order.addItem(orderItem);

            totalsByCurrency.merge(itemCurrency, lineTotal, BigDecimal::add);
        }

        // ===== Legacy uyumluluk: tek para birimliyse o toplam/para birimi; karışıksa ZERO/USD fallback =====
        if (totalsByCurrency.size() == 1) {
            Map.Entry<PriceCurrency, BigDecimal> only = totalsByCurrency.entrySet().iterator().next();
            order.setCurrency(only.getKey());
            order.setTotalAmount(only.getValue());
        } else {
            // Karışık (veya boş) -> totalAmount gösterimde KULLANILMAZ; şablon/e-posta getTotalsByCurrency() kullanır.
            order.setCurrency(PriceCurrency.USD);
            order.setTotalAmount(BigDecimal.ZERO);
        }

        // ===== AŞAMA 5: Kargo SNAPSHOT'ı =====
        // Sipariş oluşturulduğu ANDA (kaydedilmeden hemen önce) hesaplanır ve Order'a yazılır.
        // ÖNEMLİ: Bu, siparişin YAŞAM BOYU kargo bilgisidir — sipariş oluştuktan sonra BİR
        // DAHA hesaplanmaz (ürün ağırlığı/fiyatı veya kargo tarifeleri sonradan değişse bile
        // bu sipariş kaydı etkilenmez). Kargo hesaplanamazsa (manuel inceleme gerekiyorsa,
        // ürün eski ve shippingWeightPerMeter tanımlı değilse vb.) shippingCost NULL kalır;
        // sipariş YİNE DE oluşturulur, hiçbir exception fırlatılmaz (null-safe zincir).
        ShippingCalculationResult shippingCalculation = shippingService.calculateCartWeight(cart);
        ShippingCostResult shippingCostResult = shippingCostService.calculateShippingCost(shippingCalculation);

        if (shippingCalculation != null) {
            order.setShippingWeight(shippingCalculation.getTotalWeight());
            if (shippingCalculation.getCategory() != null) {
                order.setShippingCategory(shippingCalculation.getCategory().name());
            }
        }
        if (shippingCostResult != null) {
            // costCalculated=false ise (manuel inceleme) shippingCost bilinçli olarak NULL
            // bırakılır; otomatik/varsayılan bir ücret UYDURULMAZ.
            if (shippingCostResult.isCostCalculated()) {
                order.setShippingCost(shippingCostResult.getShippingCost());
            }
            if (shippingCostResult.getMessage() != null) {
                order.setShippingMessage(shippingCostResult.getMessage());
            }
        }

        // cascade = ALL -> order_items da kaydedilir.
        Order saved = orderRepository.save(order);

        // ===== ÖNEMLİ: Sepet BURADA temizlenmez! =====
        // Ödeme henüz alınmadı (paymentStatus=PENDING); kullanıcı iyzico ekranından geri
        // dönerse veya ödeme başarısız/iptal olursa sepeti kaybetmemesi gerekir. Sepet
        // yalnızca ödeme GERÇEKTEN başarılı olduğunda, markOrderAsPaid(...) içinde temizlenir
        // (bkz. aşağıdaki ÖDEME bölümü).

        // ===== ÖNEMLİ: Burada MÜŞTERİYE/ADMİN'E HİÇBİR MAIL GÖNDERİLMEZ. =====
        // Sipariş bu noktada yalnızca status=PENDING, paymentStatus=PENDING olarak DB'ye kaydedilir.
        // Kart bilgisi henüz alınmadığı (ödeme iyzico'da tamamlanmadığı) için "Siparişiniz Alındı"
        // maili göndermek yanlış olur -> ödeme başarısız kalırsa müşteri boşuna sipariş onayı almış
        // olur. Tek ve gerçek sipariş/ödeme onay maili, ödeme sağlayıcısı callback'i sonrası
        // markOrderAsPaid(...) çalıştığında gönderilir (bkz. aşağıdaki ÖDEME bölümü).
        return saved;
    }

    @Transactional
    public void rateOrder(Long orderId, Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Puan 1 ile 5 arasında olmalıdır.");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı: " + orderId));
        order.setRating(rating);
        orderRepository.save(order);
    }

    /**
     * Başarı/detay sayfası için tek siparişi kalemleri + kullanıcısıyla birlikte getirir.
     */
    @Transactional(readOnly = true)
    public Order getOrderDetail(Long orderId) {
        return orderRepository.findDetailById(orderId).orElse(null);
    }

    private static String formatMoney(BigDecimal amount, PriceCurrency currency) {
        if (amount == null) amount = BigDecimal.ZERO;
        String symbol = (currency != null) ? currency.getSymbol() : "$";
        return String.format(Locale.forLanguageTag("tr-TR"), "%,.2f", amount) + " " + symbol;
    }

    // "Kartla Tahsil Edilen Tutar" bilgisi için: order.paymentAmount her zaman TRY olduğundan
    // formatMoney(...,PriceCurrency.TRY) yerine (o "₺" sembolü kullanır) burada özellikle "TL"
    // ibaresiyle biçimlendirilir.
    private static String formatTryAmount(BigDecimal amount) {
        if (amount == null) return null;
        return String.format(Locale.forLanguageTag("tr-TR"), "%,.2f", amount) + " TL";
    }

    // Bir kalemin para birimini snapshot'tan, yoksa üründen, o da yoksa USD olarak çözer.
    private static PriceCurrency resolveItemCurrency(CartItem item) {
        if (item.getCurrencySnapshot() != null) return item.getCurrencySnapshot();
        if (item.getProduct() != null && item.getProduct().getCurrency() != null) {
            return item.getProduct().getCurrency();
        }
        return PriceCurrency.USD;
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllWithUserOrderByOrderDateDesc();
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByEmail(String email) {
        return orderRepository.findByUser_EmailOrderByOrderDateDesc(email);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı: " + orderId));
        order.setStatus(status);
        orderRepository.save(order);

        // Durum güncelleme bilgilendirmesi (async + fail-safe; güncellemeyi bozmaz)
        sendStatusUpdateEmails(order);
    }

    /**
     * AŞAMA 8: Kargo operasyon yönetimi. Admin bir siparişi FİİLEN kargoya verdiğinde
     * ("Kargoya Ver" butonu) çağrılır: kargo firması + takip numarası kaydedilir, gönderim
     * zamanı (shippedAt) o anki zaman olarak yazılır ve sipariş durumu SHIPPED yapılır.
     *
     * ÖNEMLİ (bilinçli tasarım kararı): Bu metot mevcut updateOrderStatus(...)'tan AYRIDIR ve
     * onun aksine HİÇBİR mail göndermez — görev tanımı gereği bu aşamada mail entegrasyonu
     * YAPILMAMASI istendi ("sadece altyapıyı hazırla"). Admin, durumu mevcut "Durum Güncelle"
     * formundan (updateOrderStatus) manuel olarak SHIPPED yaparsa o yol hâlâ eskisi gibi mail
     * gönderir; bu yeni metot ise kargo operasyon verisini kaydetmeye ODAKLIDIR ve mail
     * göndermeden yalnızca veriyi/durumu günceller. Ödeme akışına (PaymentService/IyzicoClient/
     * EmailService) HİÇ dokunmaz.
     *
     * Null güvenliği: shippingCompany/shippingTrackingNumber boş/null gelse bile hata vermez;
     * olduğu gibi (null veya boş string) kaydedilir.
     */
    @Transactional
    public void markOrderAsShipped(Long orderId, String shippingCompany, String shippingTrackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı: " + orderId));

        order.setShippingCompany(shippingCompany);
        order.setShippingTrackingNumber(shippingTrackingNumber);
        order.setShippedAt(LocalDateTime.now());
        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);

        // AŞAMA 8: Bilinçli olarak mail GÖNDERİLMİYOR (bkz. yukarıdaki açıklama).
        // İleride istenirse ayrı bir aşamada (örn. "Kargoya Verildi" bildirim maili) eklenebilir.
    }

    // ===================== ÖDEME (PayTR / iyzico) =====================

    /**
     * Ödeme sağlayıcısından "başarılı" onayı geldiğinde çağrılır (PaymentService.handleCallback).
     * Sipariş yalnızca burada gerçekten "ödemesi tamamlanmış" sayılır.
     *
     * İDEMPOTENT: Sipariş zaten PAID ise hiçbir şey değiştirilmez, mail TEKRAR gönderilmez.
     * (Webhook'lar sağlayıcı tarafında ağ sorunları nedeniyle birden fazla kez tetiklenebilir;
     * bu durumda ikinci/üçüncü çağrı sessizce yok sayılır.)
     */
    @Transactional
    public void markOrderAsPaid(Long orderId, String provider, String token) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı: " + orderId));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            // Zaten ödenmiş: paidAt/mail vb. tekrar tetiklenmesin (idempotency).
            return;
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentProvider(provider);
        order.setPaymentToken(token);
        order.setPaidAt(LocalDateTime.now());
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // Sepet YALNIZCA burada, ödeme gerçekten onaylandıktan sonra temizlenir.
        // (placeOrder() sırasında artık temizlenmiyor -> kullanıcı iyzico ekranından geri
        // dönerse veya ödeme başarısız/iptal olursa sepeti kaybetmemiş olur.)
        clearCartForUser(order.getUser());

        sendPaymentResultEmails(order, true);
    }

    // Ödeme onaylanan kullanıcının o anki sepetini boşaltır (orphanRemoval -> cart_items silinir).
    // NOT: Kullanıcının GÜNCEL sepeti temizlenir; bu sipariş oluşturulduktan sonra sepete yeni
    // ürün eklendiyse (nadir bir durum), onlar da bu ödeme onayında temizlenmiş olur -
    // placeOrder()'ın eski davranışıyla aynı varsayım (siparişin ait olduğu sepet = kullanıcının
    // o anki sepeti).
    private void clearCartForUser(User user) {
        if (user == null || user.getEmail() == null) {
            return;
        }
        cartRepository.findByUserEmailWithItems(user.getEmail()).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });
    }

    /**
     * Ödeme sağlayıcısından "başarısız" onayı geldiğinde çağrılır. order.status (fulfillment)
     * DEĞİŞTİRİLMEZ; sipariş PENDING kalır ki müşteri isterse tekrar ödeme deneyebilsin.
     *
     * İDEMPOTENT / GÜVENLİ: Sipariş zaten PAID ise ASLA FAILED'a düşürülmez (gecikmeli/duplike
     * webhook, race condition gibi durumlarda ödenmiş bir siparişin durumu geriye alınamaz).
     * Zaten FAILED ise mail tekrar gönderilmez (webhook retry'ları spam yaratmasın diye).
     */
    @Transactional
    public void markOrderAsFailed(Long orderId, String provider, String token) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı: " + orderId));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            // Ödenmiş bir sipariş ASLA FAILED durumuna düşürülmez.
            return;
        }
        if (order.getPaymentStatus() == PaymentStatus.FAILED) {
            // Zaten FAILED: tekrar mail göndermeye gerek yok (idempotency).
            return;
        }

        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setPaymentProvider(provider);
        order.setPaymentToken(token);
        orderRepository.save(order);

        sendPaymentResultEmails(order, false);
    }

    /**
     * Ödeme sonucu bildirimleri. BAŞARILI durumda bu, müşterinin sipariş/ödeme onayına dair
     * aldığı TEK ana mail'dir (sipariş kodu, sipariş içeriği, toplam tutar, teslimat adresi,
     * "ödemeniz başarıyla alınmıştır" bilgisi) — placeOrder() sırasında ARTIK mail gönderilmez.
     * BAŞARISIZ durumda ise kısa bir bilgilendirme gönderilir; sipariş onayı ile
     * karıştırılmaması için içerik/adres/tutar bilgisi İÇERMEZ.
     */
    private void sendPaymentResultEmails(Order order, boolean success) {
        String code = "#DCH-" + order.getId();
        String customerEmail = (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank())
                ? order.getCustomerEmail()
                : ((order.getUser() != null) ? order.getUser().getEmail() : null);
        String fullName = (order.getCustomerName() != null && !order.getCustomerName().isBlank())
                ? order.getCustomerName() : "Müşterimiz";
        String phone = (order.getCustomerPhone() != null && !order.getCustomerPhone().isBlank())
                ? order.getCustomerPhone() : "-";
        String address = (order.getDeliveryAddress() != null && !order.getDeliveryAddress().isBlank())
                ? order.getDeliveryAddress() : "-";

        if (success) {
            String itemsText = buildOrderItemsText(order);
            String totalsText = buildTotalsText(order);
            // Ürün toplamları ORİJİNAL para birimleriyle kalır (yukarıdaki totalsText);
            // bu satır SADECE ek bilgi olarak "kartla gerçekten ne kadar TRY tahsil edildi"yi gösterir.
            String paymentAmountLine = (order.getPaymentAmount() != null)
                    ? ("Kartla Tahsil Edilen Tutar: " + formatTryAmount(order.getPaymentAmount()) + "\n\n")
                    : "";

            // AŞAMA 7B: Kargo SNAPSHOT'ı mail gövdesine eklenir. ÖNEMLİ: burada hiçbir hesaplama
            // yapılmaz — ShippingService/ShippingCostService/Cart hiç kullanılmaz; yalnızca
            // Order'a sipariş anında yazılmış snapshot alanları (shippingWeight/shippingCost/
            // shippingCategory/shippingMessage) okunup EmailService'teki saf biçimlendirme
            // yardımcısına devredilir. Eski siparişlerde (tüm alanlar null) bile hata vermez.
            String shippingInfoBlockCustomer = emailService.buildShippingInfoBlock(
                    order.getShippingWeight(), order.getShippingCost(),
                    order.getShippingCategory(), order.getShippingMessage(), false);
            String shippingInfoBlockAdmin = emailService.buildShippingInfoBlock(
                    order.getShippingWeight(), order.getShippingCost(),
                    order.getShippingCategory(), order.getShippingMessage(), true);

            // --- Müşteriye: TEK ana sipariş/ödeme onay maili ---
            if (customerEmail != null && !customerEmail.isBlank()) {
                String subject = "Ödemeniz Alındı - " + code + " | Deniz Çelik Halat";
                String body = "Sayın " + fullName + ",\n\n"
                        + "Ödemeniz başarıyla alınmıştır. Siparişiniz onaylandı ve hazırlık sürecine\n"
                        + "alınacaktır. Teşekkür ederiz!\n\n"
                        + "Sipariş Kodu : " + code + "\n\n"
                        + "Sipariş İçeriği:\n"
                        + itemsText + "\n"
                        + totalsText + "\n"
                        + paymentAmountLine
                        + shippingInfoBlockCustomer + "\n"
                        + "Teslimat Adresi:\n" + address + "\n\n"
                        + "Deniz Çelik Halat\nBornova, İzmir";
                emailService.sendPlainText(customerEmail, subject, body);
            }

            // --- Admin'e: bu siparişle ilgili admin'in aldığı İLK ve TEK bildirimdir.
            //     Ödeme onaylanmadan admin'e "yeni sipariş" maili ASLA gönderilmez. ---
            String adminSubject = "Ödeme Alındı - Yeni Sipariş - " + code;
            String adminBody = "Ödemesi tamamlanmış yeni bir sipariş var.\n\n"
                    + "Sipariş Kodu : " + code + "\n"
                    + "Müşteri      : " + fullName + " (" + (customerEmail != null ? customerEmail : "-") + ")\n"
                    + "Telefon      : " + phone + "\n\n"
                    + "Teslimat Adresi:\n" + address + "\n\n"
                    + "Sipariş İçeriği:\n"
                    + itemsText + "\n"
                    + totalsText + "\n"
                    + paymentAmountLine
                    + shippingInfoBlockAdmin;
            emailService.sendPlainText(companyEmail, adminSubject, adminBody);

        } else {
            // --- Müşteriye: KISA bilgilendirme. Sipariş onayı/sepet içeriği/adres/tutar
            //     KESİNLİKLE içermez -> sipariş onaylanmış izlenimi asla verilmez. ---
            if (customerEmail != null && !customerEmail.isBlank()) {
                String subject = "Ödeme Tamamlanamadı - " + code + " | Deniz Çelik Halat";
                String body = "Sayın " + fullName + ",\n\n"
                        + "Siparişiniz (" + code + ") için ödeme işlemi tamamlanamadı.\n\n"
                        + "Kartınızdan herhangi bir tutar çekilmediyse endişelenmenize gerek yok. "
                        + "Sitemizden tekrar ödeme deneyebilir veya bizimle iletişime geçebilirsiniz.\n\n"
                        + "Deniz Çelik Halat\nBornova, İzmir";
                emailService.sendPlainText(customerEmail, subject, body);
            }

            String adminSubject = "Ödeme Başarısız - " + code;
            String adminBody = "Bir siparişin ödemesi başarısız oldu (sipariş ONAYLANMADI).\n\n"
                    + "Sipariş Kodu : " + code + "\n"
                    + "Müşteri      : " + fullName + " (" + (customerEmail != null ? customerEmail : "-") + ")\n";
            emailService.sendPlainText(companyEmail, adminSubject, adminBody);
        }
    }

    // Ödeme başarılı olunca sipariş içeriğini OrderItem listesinden (CartItem DEĞİL, sepet bu
    // noktada zaten boşaltılmış olabilir) yeniden oluşturur.
    private String buildOrderItemsText(Order order) {
        StringBuilder sb = new StringBuilder();
        List<OrderItem> items = order.getItems();
        if (items != null) {
            for (OrderItem item : items) {
                Product product = item.getProduct();
                String productName = (product != null && product.getName() != null) ? product.getName() : "Ürün";
                int quantity = (item.getQuantity() != null) ? item.getQuantity() : 0;
                BigDecimal unitPrice = (item.getPrice() != null) ? item.getPrice() : BigDecimal.ZERO;
                PriceCurrency currency = (item.getCurrencySnapshot() != null)
                        ? item.getCurrencySnapshot()
                        : ((product != null && product.getCurrency() != null) ? product.getCurrency() : PriceCurrency.USD);
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

                sb.append("  - ").append(productName).append("  x ").append(quantity);
                if (item.getSelectedMeasurement() != null && !item.getSelectedMeasurement().isBlank()) {
                    sb.append("  | Ölçü: ").append(item.getSelectedMeasurement());
                }
                sb.append("   =   ").append(formatMoney(lineTotal, currency)).append("\n");
            }
        }
        if (sb.length() == 0) {
            sb.append("  (Sipariş kalemi bulunamadı)\n");
        }
        return sb.toString();
    }

    // Para birimine göre gruplu toplam tutar bloğu (order.getTotalsByCurrency() -> dönüşüm yok).
    private String buildTotalsText(Order order) {
        Map<PriceCurrency, BigDecimal> totals = order.getTotalsByCurrency();
        StringBuilder totalsBlock = new StringBuilder("Toplam Tutar:\n");
        if (totals == null || totals.isEmpty()) {
            totalsBlock.append("  - ").append(formatMoney(order.getTotalAmount(), order.getCurrency())).append("\n");
        } else {
            for (Map.Entry<PriceCurrency, BigDecimal> e : totals.entrySet()) {
                totalsBlock.append("  - ").append(e.getKey().getLabel()).append(": ")
                        .append(formatMoney(e.getValue(), e.getKey())).append("\n");
            }
        }
        return totalsBlock.toString();
    }

    private void sendStatusUpdateEmails(Order order) {
        String code = "#DCH-" + order.getId();
        String label = turkishOrderStatus(order.getStatus());
        String customerEmail = (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank())
                ? order.getCustomerEmail()
                : ((order.getUser() != null) ? order.getUser().getEmail() : null);
        String fullName = (order.getCustomerName() != null && !order.getCustomerName().isBlank())
                ? order.getCustomerName() : "Müşterimiz";

        // Müşteriye
        if (customerEmail != null && !customerEmail.isBlank()) {
            String subject = "Sipariş Durumu Güncellendi - " + code + " | Deniz Çelik Halat";
            String body = "Sayın " + fullName + ",\n\n"
                    + "Siparişinizin durumu güncellendi.\n\n"
                    + "Sipariş Kodu : " + code + "\n"
                    + "Yeni Durum   : " + label + "\n\n"
                    + "Deniz Çelik Halat\nBornova, İzmir";
            emailService.sendPlainText(customerEmail, subject, body);
        }

        // Şirkete
        String adminSubject = "Sipariş Durumu Güncellendi - " + code;
        String adminBody = "Bir siparişin durumu güncellendi.\n\n"
                + "Sipariş Kodu : " + code + "\n"
                + "Müşteri      : " + fullName + " (" + (customerEmail != null ? customerEmail : "-") + ")\n"
                + "Yeni Durum   : " + label + "\n";
        emailService.sendPlainText(companyEmail, adminSubject, adminBody);
    }

    private static String turkishOrderStatus(OrderStatus status) {
        if (status == null) return "-";
        switch (status) {
            case PENDING:   return "Bekliyor";
            case PAID:      return "Ödendi";
            case PREPARING: return "Hazırlanıyor";
            case SHIPPED:   return "Kargoya Verildi";
            case DELIVERED: return "Teslim Edildi";
            case CANCELLED: return "İptal Edildi";
            default:        return status.name();
        }
    }
}
