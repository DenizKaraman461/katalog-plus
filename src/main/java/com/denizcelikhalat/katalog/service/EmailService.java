package com.denizcelikhalat.katalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    // application.properties'teki spring.mail.username=${MAIL_USERNAME:} zaten boş varsayılana
    // sahip; buradaki ":" ek bir güvenlik ağıdır (properties dosyası ileride değişse bile).
    @Value("${spring.mail.username:}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendPlainText(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }

        // Mail yapılandırılmamış (örn. local geliştirmede MAIL_USERNAME/MAIL_PASSWORD ortam
        // değişkenleri set edilmemiş) -> uygulamayı DÜŞÜRMEDEN, karışık bir SMTP hata izi yerine
        // net/anlaşılır bir log ile gönderim atlanır. Production'da bu alan her zaman doludur.
        if (fromAddress == null || fromAddress.isBlank()) {
            System.err.println("[EmailService] Mail yapılandırılmamış (MAIL_USERNAME boş) - "
                    + "gönderim atlandı -> " + to);
            return;
        }

        String[] recipients = Arrays.stream(to.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toArray(String[]::new);

        if (recipients.length == 0) {
            return;
        }

        // Her alıcıya AYRI mail gönder: bir adres reddedilse/başarısız olsa bile
        // diğerleri etkilenmesin (tek setTo[] yerine alıcı başına gönderim).
        for (String recipient : recipients) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(recipient);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            } catch (Exception e) {
                System.err.println("[EmailService] E-posta gonderilemedi -> " + recipient + " : " + e.getMessage());
            }
        }
    }

    /**
     * AŞAMA 7B: Bir siparişin kargo SNAPSHOT bilgilerini (Order.shippingWeight/shippingCost/
     * shippingCategory/shippingMessage) e-posta gövdesine eklenecek düz metin bloğu olarak
     * biçimlendirir.
     *
     * ÇOK ÖNEMLİ: Bu metot KESİNLİKLE hesaplama YAPMAZ — ShippingService/ShippingCostService
     * çağırmaz, Cart kullanmaz. Yalnızca ZATEN Order üzerinde var olan (sipariş oluşturulurken
     * bir kez hesaplanıp yazılmış) snapshot değerlerini okuyup okunur bir metne çevirir
     * (BigDecimal -> "14,00 kg" gibi salt GÖRÜNTÜLEME biçimlendirmesi; iş kuralı/hesap YOKTUR).
     *
     * Null güvenlidir: parametrelerin TAMAMI null olsa bile (eski sipariş, kargo alanları hiç
     * yazılmamış) exception fırlatmaz; bu durumda "Kargo bilgileri sipariş oluşturulurken mevcut
     * değildi." satırını döner ve mail gönderimi normal şekilde devam eder.
     *
     * @param forAdmin true ise kategori Türkçe etiketin yanında orijinal enum adını da gösterir
     *                 (örn. "Standart Kargo (STANDARD)"); false ise yalnızca Türkçe etiket (müşteri).
     */
    public String buildShippingInfoBlock(BigDecimal shippingWeight, BigDecimal shippingCost,
                                         String shippingCategory, String shippingMessage,
                                         boolean forAdmin) {
        boolean hasAnyData = shippingWeight != null || shippingCost != null || shippingCategory != null
                || (shippingMessage != null && !shippingMessage.isBlank());

        StringBuilder sb = new StringBuilder();
        sb.append("--------------------------------\n");
        sb.append("KARGO BİLGİLERİ\n");
        sb.append("--------------------------------\n");

        if (!hasAnyData) {
            // Eski sipariş: kargo alanları hiç yazılmamış. Mail HATA VERMEZ, sadece bilgi verilir.
            sb.append("Kargo bilgileri sipariş oluşturulurken mevcut değildi.\n");
            sb.append("--------------------------------\n");
            return sb.toString();
        }

        sb.append("Ağırlık     : ")
                .append(shippingWeight != null ? formatKg(shippingWeight) : "-")
                .append("\n");

        sb.append("Kargo Tipi  : ").append(shippingCategoryLabel(shippingCategory, forAdmin)).append("\n");

        sb.append("Kargo Ücreti: ")
                .append(shippingCost != null ? formatTl(shippingCost)
                        : "Kargo ücreti manuel olarak belirlenecektir.")
                .append("\n");

        sb.append("Not         : ")
                .append((shippingMessage != null && !shippingMessage.isBlank()) ? shippingMessage : "-")
                .append("\n");

        sb.append("--------------------------------\n");
        return sb.toString();
    }

    // Ham enum adını Türkçe etikete çevirir (saf metin eşlemesi; hesaplama değildir).
    private static String shippingCategoryLabel(String category, boolean forAdmin) {
        if (category == null || category.isBlank()) {
            return "-";
        }
        String turkish;
        switch (category) {
            case "STANDARD":
                turkish = "Standart Kargo";
                break;
            case "HEAVY_CARGO":
                turkish = "Ağır Kargo";
                break;
            case "MANUAL_REVIEW":
                turkish = "Manuel İnceleme";
                break;
            default:
                turkish = category;
        }
        return forAdmin ? (turkish + " (" + category + ")") : turkish;
    }

    private static String formatKg(BigDecimal value) {
        return String.format(Locale.forLanguageTag("tr-TR"), "%,.2f", value) + " kg";
    }

    private static String formatTl(BigDecimal value) {
        return String.format(Locale.forLanguageTag("tr-TR"), "%,.2f", value) + " TL";
    }
}