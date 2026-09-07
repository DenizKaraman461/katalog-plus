package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.model.QuoteRequest;
import com.denizcelikhalat.katalog.repository.QuoteRequestRepository;
import com.denizcelikhalat.katalog.service.EmailService;
import com.denizcelikhalat.katalog.service.ProductService;
import com.denizcelikhalat.katalog.service.ReturnUrlSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
public class QuoteController {

    private final QuoteRequestRepository quoteRequestRepository;
    private final ProductService productService;
    private final EmailService emailService;

    @Value("${app.company.email:info@denizcelikhalat.com}")
    private String companyEmail;

    public QuoteController(QuoteRequestRepository quoteRequestRepository,
                           ProductService productService,
                           EmailService emailService) {
        this.quoteRequestRepository = quoteRequestRepository;
        this.productService = productService;
        this.emailService = emailService;
    }

    @PostMapping("/quote/create")
    public String createQuote(@ModelAttribute QuoteRequest quoteRequest,
                              @RequestParam(value = "returnUrl", required = false) String returnUrl,
                              java.security.Principal principal,
                              RedirectAttributes redirectAttributes) {

        Long productId = quoteRequest.getProductId();
        Product product = (productId != null) ? productService.getById(productId) : null;
        if (product != null) {
            quoteRequest.setProductName(product.getName());
        }

        if (principal != null) {
            quoteRequest.setUserEmail(principal.getName());
            if (quoteRequest.getEmail() == null || quoteRequest.getEmail().isBlank()) {
                quoteRequest.setEmail(principal.getName());
            }
        }

        if (quoteRequest.getCertificateRequested() == null) {
            quoteRequest.setCertificateRequested(Boolean.FALSE);
        }
        if (quoteRequest.getStatus() == null || quoteRequest.getStatus().isBlank()) {
            quoteRequest.setStatus("NEW");
        }
        quoteRequest.setCreatedAt(LocalDateTime.now());

        quoteRequestRepository.save(quoteRequest);

        sendQuoteCreatedEmails(quoteRequest);

        redirectAttributes.addFlashAttribute("success", "Teklif talebiniz alındı. En kısa sürede sizinle iletişime geçeceğiz.");

        // "Hangi liste sayfasından geldim" bilgisi (returnUrl), teklif başarıyla gönderilse bile
        // doğrulanarak taşınır -> ürün sayfasına dönünce "Ürünlere Dön" linki hâlâ doğru sayfayı gösterir.
        if (productId != null) {
            redirectAttributes.addAttribute("returnUrl", ReturnUrlSupport.sanitize(returnUrl));
            return "redirect:/products/" + productId;
        }
        return "redirect:/products";
    }

    @GetMapping("/my-quotes")
    public String myQuotes(java.security.Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("quotes", quoteRequestRepository.findForCustomer(principal.getName()));
        return "my_quotes";
    }

    /**
     * Admin: gelen teklif talepleri listesi.
     */
    @GetMapping("/admin/quotes")
    public String adminQuotes(Model model) {
        model.addAttribute("quotes", quoteRequestRepository.findAllByOrderByCreatedAtDesc());
        return "admin_quotes";
    }

    /**
     * Admin: bir teklif talebinin durumunu günceller. /admin/** altında olduğu için yalnızca ADMIN.
     * CSRF açık (form token gönderir). Sonra tekrar /admin/quotes'a döner.
     */
    @PostMapping("/admin/quotes/status")
    public String updateQuoteStatus(@RequestParam("quoteId") Long quoteId,
                                    @RequestParam("status") String status,
                                    RedirectAttributes redirectAttributes) {
        QuoteRequest quote = quoteRequestRepository.findById(quoteId).orElse(null);
        if (quote == null) {
            redirectAttributes.addFlashAttribute("error", "Teklif talebi bulunamadı.");
            return "redirect:/admin/quotes";
        }
        quote.setStatus((status != null && !status.isBlank()) ? status : "NEW");
        quoteRequestRepository.save(quote);

        // Durum güncelleme bilgilendirmesi (async + fail-safe)
        sendQuoteStatusEmails(quote);

        redirectAttributes.addFlashAttribute("success", "Teklif durumu güncellendi.");
        return "redirect:/admin/quotes";
    }

    // ===================== E-posta yardımcıları =====================

    private void sendQuoteCreatedEmails(QuoteRequest q) {
        String code = "#TKL-" + q.getId();
        String product = (q.getProductName() != null && !q.getProductName().isBlank())
                ? q.getProductName() : ("Ürün ID: " + q.getProductId());
        String details = buildQuoteDetails(q);

        // Müşteriye (form email + giriş yapan userEmail; ikisi de doluysa İKİSİNE de)
        String customerRecipients = joinRecipients(q.getEmail(), q.getUserEmail());

        if (!customerRecipients.isBlank()) {
            String subject = "Teklif Talebiniz Alındı - " + code + " | Deniz Çelik Halat";
            String body = "Sayın " + safe(q.getCustomerName(), "Müşterimiz") + ",\n\n"
                    + "Teklif talebiniz tarafımıza ulaştı. En kısa sürede sizinle iletişime geçeceğiz.\n\n"
                    + "Talep No : " + code + "\n"
                    + "Ürün     : " + product + "\n\n"
                    + details
                    + "\nDeniz Çelik Halat\nBornova, İzmir";

            emailService.sendPlainText(customerRecipients, subject, body);
        }
        // Şirkete
        String adminSubject = "Yeni Teklif Talebi - " + code;
        String adminBody = "Yeni bir teklif talebi alındı.\n\n"
                + "Talep No : " + code + "\n"
                + "Ürün     : " + product + "\n\n"
                + "Müşteri:\n"
                + "  Ad Soyad : " + safe(q.getCustomerName(), "-") + "\n"
                + "  Telefon  : " + safe(q.getPhone(), "-") + "\n"
                + "  E-posta  : " + safe(q.getEmail(), "-") + "\n"
                + "  Firma    : " + safe(q.getCompanyName(), "-") + "\n\n"
                + details;
        emailService.sendPlainText(companyEmail, adminSubject, adminBody);
    }

    private void sendQuoteStatusEmails(QuoteRequest q) {
        String code = "#TKL-" + q.getId();
        String label = quoteStatusLabel(q.getStatus());
        String product = (q.getProductName() != null && !q.getProductName().isBlank())
                ? q.getProductName() : ("Ürün ID: " + q.getProductId());

        String customerRecipients = joinRecipients(q.getEmail(), q.getUserEmail());
        if (!customerRecipients.isBlank()) {
            String subject = "Teklif Talebiniz Güncellendi - " + code + " | Deniz Çelik Halat";
            String body = "Sayın " + safe(q.getCustomerName(), "Müşterimiz") + ",\n\n"
                    + "Teklif talebinizin durumu güncellendi.\n\n"
                    + "Talep No : " + code + "\n"
                    + "Ürün     : " + product + "\n"
                    + "Yeni Durum : " + label + "\n\n"
                    + "Deniz Çelik Halat\nBornova, İzmir";
            emailService.sendPlainText(customerRecipients, subject, body);
        }

        String adminSubject = "Teklif Durumu Güncellendi - " + code;
        String adminBody = "Bir teklif talebinin durumu güncellendi.\n\n"
                + "Talep No   : " + code + "\n"
                + "Ürün       : " + product + "\n"
                + "Müşteri    : " + safe(q.getCustomerName(), "-") + " (" + safe(q.getEmail(), "-") + ")\n"
                + "Yeni Durum : " + label + "\n";
        emailService.sendPlainText(companyEmail, adminSubject, adminBody);
    }

    private String buildQuoteDetails(QuoteRequest q) {
        StringBuilder sb = new StringBuilder("Talep Detayları:\n");
        appendIf(sb, "Sapan Tipi", q.getSlingType());
        appendIf(sb, "Kol Sayısı", q.getLegCount() != null ? String.valueOf(q.getLegCount()) : null);
        appendIf(sb, "Halat Çapı", q.getRopeDiameter());
        appendIf(sb, "Halat Tipi", q.getRopeType());
        appendIf(sb, "Uzunluk", q.getLength());
        appendIf(sb, "Taşıma Kapasitesi", q.getCapacity());
        appendIf(sb, "Çalışma Açısı", q.getWorkingAngle());
        appendIf(sb, "Üst Bağlantı", q.getTopConnection());
        appendIf(sb, "Alt Bağlantı", q.getBottomConnection());
        appendIf(sb, "Adet", q.getQuantity() != null ? String.valueOf(q.getQuantity()) : null);
        sb.append("  Sertifika : ").append(Boolean.TRUE.equals(q.getCertificateRequested()) ? "İsteniyor" : "İstenmiyor").append("\n");
        if (q.getNote() != null && !q.getNote().isBlank()) {
            sb.append("\nNot:\n").append(q.getNote()).append("\n");
        }
        return sb.toString();
    }

    private static void appendIf(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("  ").append(label).append(" : ").append(value).append("\n");
        }
    }

    private static String safe(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v : fallback;
    }

    private static String quoteStatusLabel(String status) {
        if (status == null) return "Yeni Talep";
        switch (status) {
            case "NEW":        return "Yeni Talep";
            case "CONTACTED":  return "Müşteri Arandı";
            case "OFFER_SENT": return "Teklif Gönderildi";
            case "CLOSED":     return "Tamamlandı";
            case "CANCELLED":  return "İptal";
            default:           return status;
        }
    }

    private static String joinRecipients(String... emails) {
        if (emails == null) return "";

        return java.util.Arrays.stream(emails)
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }
}
