package com.denizcelikhalat.katalog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * EUR/TRY ve USD/TRY kurunu TCMB (today.xml) üzerinden çeker. GÜVENLİK GEREĞİ: kur güvenilir
 * şekilde alınamazsa veya elimizdeki kur bayatsa, "fallback" bir kurla SESSİZCE devam ETMEZ —
 * çağıran taraf (PaymentService) ödeme başlatmayı reddetmelidir.
 *
 * Fallback (manuel) kurlar YALNIZCA app.payment.exchange.allow-fallback=true olduğunda
 * kullanılabilir. Bu bayrak production application.properties'te YOKTUR/false'tur; yalnızca
 * geliştirme/test profilinde bilinçli olarak açılmalıdır. Production'da TCMB başarısız olursa
 * ve cache de geçerli değilse getEffectiveRates() "available=false" döner; PaymentService bu
 * durumda ödeme BAŞLATMAZ.
 *
 * BİLİNEN SINIRLAMA: Cache tek JVM içinde tutulur (in-memory). Birden fazla uygulama sunucusu
 * (yatay ölçekleme) varsa, kurlar sunucular arası paylaşılmaz; her sunucu kendi cache'ini tutar.
 * Çoklu sunucu senaryosunda paylaşımlı bir cache (Redis/DB) gerekebilir — bu implementasyonun
 * kapsamı dışındadır.
 */
@Service
public class ExchangeRateService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);

    @Value("${app.payment.exchange.tcmb-url:https://www.tcmb.gov.tr/kurlar/today.xml}")
    private String tcmbUrl;

    @Value("${app.payment.exchange.margin-percent:0}")
    private BigDecimal marginPercent;

    @Value("${app.payment.exchange.max-age-minutes:60}")
    private long maxAgeMinutes;

    // GÜVENLİK: varsayılan false. Yalnızca development/test ortamında bilinçli olarak true
    // yapılmalıdır. Production'da bu satır application.properties'te YOK/false olmalı.
    @Value("${app.payment.exchange.allow-fallback:false}")
    private boolean allowFallback;

    @Value("${app.payment.exchange.fallback.eur-to-try:0}")
    private BigDecimal fallbackEurToTry;

    @Value("${app.payment.exchange.fallback.usd-to-try:0}")
    private BigDecimal fallbackUsdToTry;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // Basit in-memory cache. "synchronized" ile korunur (düşük trafikli bir işlem; performans
    // kritik değil, doğruluk/tutarlılık önceliklidir).
    private volatile RateSnapshot cachedSnapshot;

    /**
     * Bir para birimi çiftinin (EUR/TRY, USD/TRY) o anki "etkin" (margin uygulanmış) kurunu
     * ve kaynağını/zamanını döner. Kur güvenilir şekilde elde edilemezse available=false döner
     * — bu durumda ÇAĞIRAN TARAF ödeme başlatmamalıdır.
     */
    public synchronized RateResult getEffectiveRates() {
        RateResult result = new RateResult();

        // 1) Geçerli (bayat olmayan) cache varsa doğrudan kullan.
        if (cachedSnapshot != null && !isStale(cachedSnapshot.fetchedAt)) {
            return toResult(cachedSnapshot, result);
        }

        // 2) Cache yok/bayat -> TCMB'den taze kur çekmeyi dene.
        RateSnapshot fetched = fetchFromTcmb();
        if (fetched != null) {
            cachedSnapshot = fetched;
            return toResult(fetched, result);
        }

        // 3) TCMB başarısız. YALNIZCA allow-fallback=true ise (dev/test) manuel kurları kullan.
        if (allowFallback
                && fallbackEurToTry != null && fallbackEurToTry.compareTo(BigDecimal.ZERO) > 0
                && fallbackUsdToTry != null && fallbackUsdToTry.compareTo(BigDecimal.ZERO) > 0) {
            logger.warn("TCMB kur servisinden veri alınamadı; app.payment.exchange.allow-fallback=true "
                    + "olduğu için FALLBACK (manuel) kurlar kullanılıyor. Bu yalnızca development/test "
                    + "ortamında beklenen bir durumdur; production'da allow-fallback=false OLMALIDIR.");
            result.available = true;
            result.eurToTry = applyMargin(fallbackEurToTry);
            result.usdToTry = applyMargin(fallbackUsdToTry);
            result.source = "FALLBACK";
            result.fetchedAt = LocalDateTime.now();
            result.marginPercent = (marginPercent != null) ? marginPercent : BigDecimal.ZERO;
            return result;
        }

        // 4) Hiçbir güvenilir kaynak yok -> ödeme başlatılamaz.
        result.available = false;
        result.errorMessage = "Güncel döviz kuru alınamadı (TCMB erişilemedi, cache yok/bayat, "
                + "fallback devre dışı).";
        logger.warn("Döviz kuru alınamadı: {}", result.errorMessage);
        return result;
    }

    private RateResult toResult(RateSnapshot snap, RateResult result) {
        result.available = true;
        result.eurToTry = applyMargin(snap.eurRaw);
        result.usdToTry = applyMargin(snap.usdRaw);
        result.source = snap.source;
        result.fetchedAt = snap.fetchedAt;
        result.marginPercent = (marginPercent != null) ? marginPercent : BigDecimal.ZERO;
        return result;
    }

    private boolean isStale(LocalDateTime fetchedAt) {
        if (fetchedAt == null) {
            return true;
        }
        return fetchedAt.isBefore(LocalDateTime.now().minusMinutes(maxAgeMinutes));
    }

    // effectiveRate = fetchedRate * (1 + marginPercent/100), 4 ondalığa yuvarlanır (hassasiyet için;
    // nihai TRY tutarı zaten ayrıca 2 ondalığa yuvarlanacaktır).
    private BigDecimal applyMargin(BigDecimal rawRate) {
        if (rawRate == null) {
            return null;
        }
        BigDecimal margin = (marginPercent != null) ? marginPercent : BigDecimal.ZERO;
        BigDecimal multiplier = BigDecimal.ONE.add(margin.divide(BigDecimal.valueOf(100)));
        return rawRate.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * TCMB today.xml'den EUR ve USD "Döviz Satış" (ForexSelling) kurlarını çeker.
     * Herhangi bir sorun olursa (ağ hatası, HTTP hata kodu, XML ayrıştırma hatası, eksik/geçersiz
     * değer) null döner — bu durum "kur alınamadı" olarak ele alınır, asla exception fırlatmaz.
     */
    private RateSnapshot fetchFromTcmb() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tcmbUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                logger.warn("TCMB kur servisi beklenmeyen yanıt döndü (HTTP {}).", response.statusCode());
                return null;
            }

            BigDecimal eur = parseForexSelling(response.body(), "EUR");
            BigDecimal usd = parseForexSelling(response.body(), "USD");

            if (eur == null || usd == null
                    || eur.compareTo(BigDecimal.ZERO) <= 0 || usd.compareTo(BigDecimal.ZERO) <= 0) {
                logger.warn("TCMB kur XML'inde EUR/USD Döviz Satış (ForexSelling) değeri bulunamadı/geçersiz.");
                return null;
            }

            RateSnapshot snap = new RateSnapshot();
            snap.eurRaw = eur;
            snap.usdRaw = usd;
            snap.fetchedAt = LocalDateTime.now();
            snap.source = "TCMB";
            return snap;
        } catch (Exception e) {
            // GÜVENLİ LOG: yalnızca hata mesajı loglanır; API key/token/kart bilgisi burada zaten yok.
            logger.warn("TCMB kur servisine bağlanılamadı: {}", e.getMessage());
            return null;
        }
    }

    // TCMB today.xml yapısı: <Currency CurrencyCode="EUR">...<ForexSelling>...</ForexSelling></Currency>
    // "Döviz Satış" = ForexSelling kullanılır (bknot satışı DEĞİL).
    private BigDecimal parseForexSelling(String xml, String currencyCode) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // XXE (XML External Entity) koruması: dış varlık/DTD işlenmesin.
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList currencies = doc.getElementsByTagName("Currency");
            for (int i = 0; i < currencies.getLength(); i++) {
                Element el = (Element) currencies.item(i);
                if (currencyCode.equals(el.getAttribute("CurrencyCode"))) {
                    NodeList sellingNodes = el.getElementsByTagName("ForexSelling");
                    if (sellingNodes.getLength() > 0) {
                        String text = sellingNodes.item(0).getTextContent();
                        if (text != null && !text.isBlank()) {
                            return new BigDecimal(text.trim().replace(",", "."));
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("TCMB kur XML ayrıştırma hatası ({}): {}", currencyCode, e.getMessage());
        }
        return null;
    }

    // ===================== Sonuç taşıyıcıları =====================

    public static class RateResult {
        public boolean available;
        public BigDecimal eurToTry;   // margin uygulanmış, kullanıma hazır kur
        public BigDecimal usdToTry;
        public String source;         // "TCMB" | "FALLBACK"
        public LocalDateTime fetchedAt;
        public BigDecimal marginPercent; // uygulanan margin yüzdesi (bilgi amaçlı, örn. Order'a kaydetmek için)
        public String errorMessage;   // available=false ise doldurulur
    }

    private static class RateSnapshot {
        BigDecimal eurRaw;
        BigDecimal usdRaw;
        LocalDateTime fetchedAt;
        String source;
    }
}
