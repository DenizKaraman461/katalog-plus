package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * iyzico "Checkout Form" (ödeme sayfası iyzico'da barındırılır) entegrasyonu.
 *
 * Kart bilgisi (numara/CVV/son kullanma) HİÇBİR ZAMAN bizim sunucumuza uğramaz: kullanıcı
 * initialize çağrısından dönen paymentPageUrl'e yönlendirilir ve kartını doğrudan iyzico'nun
 * barındırdığı sayfada girer.
 *
 * TRY DÖNÜŞÜMÜ: Bu sınıf artık döviz kuru/dönüşüm mantığı İÇERMEZ ("dumb" HTTP istemcisi).
 * Hangi tutarın (TRY) ve hangi sepet kalemlerinin gönderileceğine PaymentService karar verir
 * (ExchangeRateService'ten aldığı güncel/dondurulmuş kur bilgisiyle) ve ChargeRequest olarak
 * buraya iletir. Böylece kur güvenilirliği/donma/eşik gibi iş kuralları tek yerde (PaymentService)
 * yönetilir; bu sınıf yalnızca "verilen tutarı, verilen kalemlerle TRY olarak gönder" işini yapar.
 *
 * NOT (önemli): Aşağıdaki istek/yanıt alanları iyzico'nun Checkout Form Initialize / Retrieve
 * uç noktalarının dokümante edilmiş yapısına göre yazılmıştır (IYZWSv2 imzalama şeması).
 * iyzico API'si zaman içinde güncellenebileceğinden, canlıya almadan önce
 * https://docs.iyzico.com adresinden alan adlarını ve uç nokta yollarını doğrulayın.
 *
 * AŞAMA 10.1 (son revizyon): buyer.city artık sabit/sahte değer DEĞİL — Order snapshot'ından
 * (checkout formunda toplanan gerçek müşteri şehri) okunur; eksikse ödeme güvenli şekilde
 * reddedilir. buyer.identityNumber için TC Kimlik No KULLANICIDAN TOPLANMAZ (gereksiz kişisel
 * veri) VE başka verilerden (telefon/e-posta/kullanıcı ID) TÜRETİLMEZ — böyle bir türetme,
 * gerçek olmayan bir kimlik numarasını gerçekmiş gibi göstermeye çalışmak anlamına gelirdi.
 * Bunun yerine HER SİPARİŞTE AYNI, application.properties'ten AYARLANABİLİR bir placeholder
 * değer gönderilir (bkz. identityNumberPlaceholder alanı) — bu değerin iyzico'nun canlı onay
 * sürecinde kabul edilip edilmeyeceği DOĞRULANMAMIŞTIR, canlıya geçmeden önce iyzico ile
 * teyit edilmelidir.
 */
@Service
public class IyzicoClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.payment.iyzico.base-url:https://sandbox-api.iyzipay.com}")
    private String baseUrl;

    @Value("${app.payment.iyzico.api-key:}")
    private String apiKey;

    @Value("${app.payment.iyzico.secret-key:}")
    private String secretKey;

    // AŞAMA 10.1 (son revizyon) + configurable güncelleme: iyzico'nun buyer.identityNumber alanı
    // ZORUNLUDUR, ancak TC Kimlik No müşteriden TOPLANMAZ (iş kararı) ve başka verilerden
    // TÜRETİLMEZ (bkz. sınıf üstü açıklama). Bu yüzden HER SİPARİŞTE AYNI bir placeholder
    // gönderilir — bu değer application.properties üzerinden ayarlanabilir (sandbox/
    // production ortamlarında farklı bir değer gerekirse kod değiştirmeden değiştirilebilir).
    // Property tanımlı değilse ":11111111111" varsayılanı kullanılır (mevcut davranışla aynı).
    // Bu değer resmi bir TC Kimlik No DEĞİLDİR ve öyle sunulmaz; iyzico'nun bunu canlı ortamda
    // kabul edip etmeyeceği doğrulanmamıştır — canlıya geçmeden önce iyzico ile teyit edin.
    @Value("${app.payment.iyzico.identity-placeholder:11111111111}")
    private String identityNumberPlaceholder;

    /**
     * PaymentService tarafından hesaplanmış tek bir sepet kalemi (adı + TRY tutarı).
     */
    public static class ChargeItem {
        public String name;
        public BigDecimal price; // TRY, 2 ondalık
    }

    /**
     * PaymentService tarafından hesaplanmış "ne kadar TRY tahsil edilecek + hangi kalemlerle"
     * bilgisi. items'ın price toplamı totalAmount'a BİREBİR eşit olmalıdır (PaymentService
     * bunu garanti eder, örn. yuvarlama farkını son kaleme ekleyerek).
     */
    public static class ChargeRequest {
        public BigDecimal totalAmount; // TRY; price/paidPrice olarak gönderilir
        public List<ChargeItem> items;
    }

    /**
     * iyzico Checkout Form başlatma çağrısı sonucu.
     * NOT: Bu sınıftaki alanların hiçbiri hassas veri İÇERMEZ (API key/secret/Authorization
     * header/kart bilgisi burada yer almaz) — PaymentService bu alanları güvenle loglayabilir.
     */
    public static class InitializeResult {
        public boolean success;
        public String token;          // iyzico checkout form token - LOGLANMAZ (bkz. PaymentService)
        public String paymentPageUrl;
        public String status;         // iyzico yanıtındaki "status" (success/failure)
        public String errorCode;      // iyzico hata kodu (varsa)
        public String errorGroup;     // iyzico hata grubu (varsa)
        public String conversationId; // iyzico'nun yanıtta echo'ladığı conversationId (varsa)
        public String errorMessage;   // insan-okunur hata mesajı (hassas veri içermez, loglanabilir)
    }

    /**
     * iyzico Checkout Form sonucu sorgulama (callback sonrası doğrulama) sonucu.
     */
    public static class RetrieveResult {
        public boolean success;          // İstek teknik olarak başarılı mı (status=success)
        public boolean paymentSuccessful; // Ödeme gerçekten tamamlandı mı (paymentStatus=SUCCESS)
        public String conversationId;
        public String errorMessage;
    }

    /**
     * Ödeme sayfasını başlatır. TRY tutarı ve sepet kalemleri PaymentService tarafından
     * ÖNCEDEN hesaplanıp charge parametresiyle verilir (bkz. sınıf üstü açıklama).
     * conversationId bizim ürettiğimiz, sağlayıcıya gönderilen referans değerdir
     * (Order.paymentConversationId olarak saklanır).
     */
    public InitializeResult initializeCheckoutForm(Order order, String conversationId,
                                                    String buyerIp, String callbackUrl,
                                                    ChargeRequest charge) {
        InitializeResult result = new InitializeResult();

        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            result.success = false;
            result.errorMessage = "iyzico API anahtarları tanımlı değil (IYZICO_API_KEY / IYZICO_SECRET_KEY).";
            return result;
        }
        if (charge == null || charge.totalAmount == null || charge.totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            result.success = false;
            result.errorMessage = "Geçersiz ödeme tutarı, ödeme başlatılamadı.";
            return result;
        }
        // AŞAMA 10.1 (son revizyon): iyzico'nun zorunlu tuttuğu buyer.city bilgisi Order
        // snapshot'ından okunur (bkz. buildInitializeBody). TC Kimlik No KULLANICIDAN TOPLANMAZ
        // (gereksiz kişisel veri) — identityNumber alanı için ayarlanabilir bir placeholder
        // gönderilir (aşağıda identityNumberPlaceholder), bu yüzden identityNumber bu gate'e dahil
        // DEĞİLDİR (her zaman doludur, kontrol gerektirmez).
        // city eksikse (örn. bu alan eklenmeden önce oluşturulmuş eski bir sipariş) ödeme
        // güvenli şekilde reddedilir; mevcut hata akışı üzerinden (PaymentService/
        // PaymentController değişmeden) zaten doğru şekilde /payment/failure'a yönlendirir.
        if (order == null || order.getCustomerCity() == null || order.getCustomerCity().isBlank()) {
            result.success = false;
            result.errorMessage = "Müşteri şehir bilgisi eksik olduğu için ödeme başlatılamıyor. "
                    + "Lütfen bizimle iletişime geçin.";
            return result;
        }

        try {
            Map<String, Object> body = buildInitializeBody(order, conversationId, buyerIp, callbackUrl, charge);
            String uriPath = "/payment/iyzipos/checkoutform/initialize/auth/ecom";
            String json = objectMapper.writeValueAsString(body);

            HttpResponse<String> response = post(uriPath, json);
            JsonNode root = objectMapper.readTree(response.body());

            String status = root.path("status").asText(""); // "success" | "failure"
            result.status = status;
            result.conversationId = root.path("conversationId").asText(null);

            if ("success".equals(status)) {
                result.success = true;
                result.token = root.path("token").asText(null);
                result.paymentPageUrl = root.path("paymentPageUrl").asText(null);
            } else {
                result.success = false;
                result.errorCode = root.path("errorCode").asText(null);
                result.errorGroup = root.path("errorGroup").asText(null);
                result.errorMessage = root.path("errorMessage").asText("iyzico ödeme başlatma başarısız.");
            }
        } catch (Exception e) {
            result.success = false;
            result.errorMessage = "iyzico bağlantı hatası: " + e.getMessage();
        }
        return result;
    }

    /**
     * Callback'te gelen token ile ödeme sonucunu iyzico'dan SUNUCU TARAFINDA doğrular.
     * (Kullanıcının tarayıcısından gelen bilgiye asla güvenilmez; her zaman bu sorgu yapılır.)
     */
    public RetrieveResult retrieveCheckoutForm(String token) {
        RetrieveResult result = new RetrieveResult();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("locale", "tr");
            body.put("conversationId", conversationIdFallback());
            body.put("token", token);

            String uriPath = "/payment/iyzipos/checkoutform/auth/ecom/detail";
            String json = objectMapper.writeValueAsString(body);

            HttpResponse<String> response = post(uriPath, json);
            JsonNode root = objectMapper.readTree(response.body());

            String status = root.path("status").asText(""); // "success" | "failure"
            String paymentStatus = root.path("paymentStatus").asText(""); // "SUCCESS" | "FAILURE" | ...

            result.success = "success".equals(status);
            result.paymentSuccessful = result.success && "SUCCESS".equalsIgnoreCase(paymentStatus);
            result.conversationId = root.path("conversationId").asText(null);
            if (!result.success) {
                result.errorMessage = root.path("errorMessage").asText("iyzico sonuç sorgulama başarısız.");
            }
        } catch (Exception e) {
            result.success = false;
            result.paymentSuccessful = false;
            result.errorMessage = "iyzico bağlantı hatası: " + e.getMessage();
        }
        return result;
    }

    // ===================== İstek gövdesi oluşturma =====================

    private Map<String, Object> buildInitializeBody(Order order, String conversationId,
                                                     String buyerIp, String callbackUrl,
                                                     ChargeRequest charge) {
        String priceStr = charge.totalAmount.toPlainString();
        String currency = "TRY"; // her zaman TRY gönderilir

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("locale", "tr");
        body.put("conversationId", conversationId);
        body.put("price", priceStr);
        body.put("paidPrice", priceStr);
        body.put("currency", currency);
        body.put("basketId", "ORDER-" + order.getId());
        body.put("paymentGroup", "PRODUCT");
        body.put("callbackUrl", callbackUrl);
        body.put("enabledInstallments", List.of(1));

        // ---- Alıcı bilgisi ----
        // city: checkout formunda toplanır (Order.customerCity snapshot'ı, yukarıdaki gate
        // ile eksikse zaten ödeme başlatılmaz). identityNumber: TOPLANMAZ, application.
        // properties'ten ayarlanabilir identityNumberPlaceholder gönderilir (bkz. alan tanımı).
        String fullName = (order.getCustomerName() != null && !order.getCustomerName().isBlank())
                ? order.getCustomerName() : "Musteri Musteri";
        String[] nameParts = fullName.trim().split("\\s+", 2);
        String firstName = nameParts.length > 0 ? nameParts[0] : "Musteri";
        String lastName = nameParts.length > 1 ? nameParts[1] : "Musteri";

        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("id", "USER-" + (order.getUser() != null ? order.getUser().getId() : order.getId()));
        buyer.put("name", firstName);
        buyer.put("surname", lastName);
        buyer.put("gsmNumber", order.getCustomerPhone() != null ? order.getCustomerPhone() : "+905000000000");
        buyer.put("email", order.getCustomerEmail() != null ? order.getCustomerEmail() : "musteri@denizcelikhalat.com");
        // AŞAMA 10.1 (son revizyon): TC Kimlik No müşteriden TOPLANMAZ (iş kararı) ve başka
        // verilerden (telefon/e-posta/kullanıcı ID) TÜRETİLMEZ. iyzico'nun Checkout Form
        // Initialize isteği buyer.identityNumber alanını yine de ZORUNLU tuttuğundan, HER
        // SİPARİŞTE BİREBİR AYNI, application.properties'ten ayarlanabilir bir placeholder
        // değer gönderilir (identityNumberPlaceholder, bkz. yukarıdaki alan tanımı).
        // DÜRÜST UYARI: Bu değerin iyzico'nun canlı/production hesap onay sürecinde kabul
        // edilip edilmeyeceği DOĞRULANMAMIŞTIR — canlıya geçmeden önce iyzico entegrasyon
        // desteğiyle bu alanın gerçekten zorunlu olup olmadığı, ya da bu şekilde ayarlanabilir
        // bir placeholder değerle gönderilmesinin kabul edilip edilmeyeceği MUTLAKA teyit edilmelidir.
        buyer.put("identityNumber", identityNumberPlaceholder);
        buyer.put("registrationAddress", order.getDeliveryAddress() != null ? order.getDeliveryAddress() : "-");
        buyer.put("ip", (buyerIp != null && !buyerIp.isBlank()) ? buyerIp : "127.0.0.1");
        buyer.put("city", order.getCustomerCity());
        // Ülke bilgisi form alanı DEĞİLDİR (şirket yalnızca Türkiye içine gönderim yapar);
        // Order.customerCountry normalde her zaman "Turkey" olarak doldurulur (placeOrder),
        // yine de null gelirse (çok eski sipariş) güvenli/doğru varsayılan olarak "Turkey" kullanılır
        // — bu, kimlik/konum verisi UYDURMAK değildir, şirketin gerçek operasyon coğrafyasıdır.
        buyer.put("country", order.getCustomerCountry() != null ? order.getCustomerCountry() : "Turkey");
        body.put("buyer", buyer);

        Map<String, Object> address = new LinkedHashMap<>();
        address.put("contactName", fullName);
        address.put("city", order.getCustomerCity());
        address.put("country", order.getCustomerCountry() != null ? order.getCustomerCountry() : "Turkey");
        address.put("address", order.getDeliveryAddress() != null ? order.getDeliveryAddress() : "-");
        body.put("shippingAddress", address);
        body.put("billingAddress", address);

        // ---- Sepet kalemleri (PaymentService'ten hazır TRY tutarlarıyla gelir) ----
        List<Map<String, Object>> basketItems = new ArrayList<>();
        if (charge.items != null) {
            for (int i = 0; i < charge.items.size(); i++) {
                ChargeItem item = charge.items.get(i);
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("id", "ITEM-" + i);
                b.put("name", (item.name != null && !item.name.isBlank()) ? item.name : "Ürün");
                b.put("category1", "Çelik Halat");
                b.put("itemType", "PHYSICAL");
                b.put("price", (item.price != null ? item.price : BigDecimal.ZERO).toPlainString());
                basketItems.add(b);
            }
        }
        if (basketItems.isEmpty()) {
            // Güvenlik ağı: kalem yoksa tek satır olarak toplam tutarı gönder.
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("id", "ORDER-" + order.getId());
            b.put("name", "Sipariş #" + order.getId());
            b.put("category1", "Çelik Halat");
            b.put("itemType", "PHYSICAL");
            b.put("price", priceStr);
            basketItems.add(b);
        }
        body.put("basketItems", basketItems);

        return body;
    }

    private String conversationIdFallback() {
        return "RETRIEVE-" + System.currentTimeMillis();
    }

    // ===================== HTTP + IYZWSv2 imzalama =====================

    private HttpResponse<String> post(String uriPath, String jsonBody) throws Exception {
        String randomKey = System.currentTimeMillis() + "" + secureRandomDigits();
        String authorization = buildAuthorizationHeader(uriPath, jsonBody, randomKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + uriPath))
                .header("Authorization", authorization)
                .header("x-iyzi-rnd", randomKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * iyzico IYZWSv2 imzalama: signature = HMACSHA256(secretKey, randomKey + uriPath + requestBody)
     * authorization = "IYZWSv2 " + Base64("apiKey:...&randomKey:...&signature:...")
     */
    private String buildAuthorizationHeader(String uriPath, String jsonBody, String randomKey) throws Exception {
        String dataToSign = randomKey + uriPath + jsonBody;
        String signature = hmacSha256Hex(secretKey, dataToSign);

        String authorizationParams = "apiKey:" + apiKey + "&randomKey:" + randomKey + "&signature:" + signature;
        String encoded = Base64.getEncoder().encodeToString(authorizationParams.getBytes(StandardCharsets.UTF_8));
        return "IYZWSv2 " + encoded;
    }

    private static String hmacSha256Hex(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String secureRandomDigits() {
        SecureRandom random = new SecureRandom();
        return String.valueOf(100000 + random.nextInt(900000));
    }
}
