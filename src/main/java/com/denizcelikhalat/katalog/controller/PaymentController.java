package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.model.Order;
import com.denizcelikhalat.katalog.model.PaymentStatus;
import com.denizcelikhalat.katalog.service.OrderService;
import com.denizcelikhalat.katalog.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * Kartla ödeme akışı: sipariş -> ödeme sayfası başlat -> yönlendir -> callback -> sonuç sayfası.
 *
 * /checkout/pay/{orderId}  : authenticated (SecurityConfig'teki "/checkout/**" kuralı kapsar)
 * /payment/callback        : herkese açık olmalı (sağlayıcı webhook'u); SecurityConfig'te
 *                            ayrıca CSRF'ten muaf tutulur (dış sağlayıcı token gönderemez).
 * /payment/success/{id}, /payment/failure/{id} : authenticated + sahiplik kontrolü.
 */
@Controller
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final OrderService orderService;

    public PaymentController(PaymentService paymentService, OrderService orderService) {
        this.paymentService = paymentService;
        this.orderService = orderService;
    }

    /**
     * Sipariş oluşturulduktan hemen sonra çağrılır (bkz. OrderController.placeOrder).
     * Ödeme başlatılabilirse iyzico'nun ödeme sayfasına yönlendirir (kart bilgisi orada girilir,
     * bizim sunucumuza hiç uğramaz).
     *
     * ÖNEMLİ (canlı ortam güvenliği): Ödeme başlatılamazsa veya beklenmedik bir hata oluşursa
     * KULLANICI ASLA sipariş başarı sayfasına (/checkout/success, /payment/success)
     * yönlendirilmez — kart bilgisi alınmadan "işlem tamamlandı" izlenimi verilmesi engellenir.
     * Bunun yerine /payment/failure/{orderId} gösterilir; sipariş PENDING/paymentStatus PENDING
     * olarak kalır ve kullanıcı "Tekrar Dene" ile yeniden ödeme başlatabilir.
     */
    @GetMapping("/checkout/pay/{orderId}")
    public String pay(@PathVariable Long orderId, Principal principal, Authentication authentication,
                      HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        Order order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return "redirect:/orders";
        }
        boolean admin = isAdmin(authentication);
        String ownerEmail = (order.getUser() != null) ? order.getUser().getEmail() : null;
        if (!admin && (ownerEmail == null || !ownerEmail.equals(principal.getName()))) {
            return "redirect:/orders";
        }

        // Sipariş zaten ödenmişse tekrar ödeme başlatılmaz; doğrudan başarı sayfasına gidilir.
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return "redirect:/payment/success/" + orderId;
        }

        String buyerIp = clientIp(request);
        PaymentService.InitiateOutcome outcome;
        try {
            outcome = paymentService.initiatePayment(orderId, buyerIp);
        } catch (Exception e) {
            // GÜVENLİ LOG: yalnızca orderId, userId ve exception mesajı loglanır.
            // API key/secret/Authorization header/raw token/kart bilgisi/kullanıcı şifresi
            // BURADA ASLA loglanmaz.
            Long userId = (order.getUser() != null) ? order.getUser().getId() : null;
            logger.error("Ödeme başlatma sırasında beklenmedik hata - orderId={}, userId={}, hata={}",
                    orderId, userId, e.getMessage());

            // Beklenmedik hata (ağ/iyzico API/parse vb.) -> ASLA success sayfasına düşürülmez.
            redirectAttributes.addFlashAttribute("error",
                    "Ödeme başlatılırken beklenmedik bir hata oluştu. Lütfen tekrar deneyin.");
            return "redirect:/payment/failure/" + orderId;
        }

        if (outcome.success && outcome.redirectUrl != null) {
            // Dış siteye (iyzico) yönlendirme.
            return "redirect:" + outcome.redirectUrl;
        }

        // Ödeme başlatılamadı: sipariş PENDING/paymentStatus PENDING olarak kalır (kaybolmaz),
        // ama kullanıcı KESİNLİKLE sipariş başarı sayfasını GÖRMEZ. Hata mesajıyla ödeme hata
        // sayfasına yönlendirilir; oradan "Tekrar Dene" ile yeniden deneyebilir.
        redirectAttributes.addFlashAttribute("error",
                "Ödeme başlatılamadı: " + (outcome.errorMessage != null ? outcome.errorMessage : "Bilinmeyen hata."));
        return "redirect:/payment/failure/" + orderId;
    }

    /**
     * iyzico Checkout Form'un callbackUrl'i: kullanıcının tarayıcısı ödeme sonrası buraya
     * POST edilir (form-encoded "token" alanıyla). Sonuç burada SUNUCU TARAFINDA
     * (PaymentService.handleCallback -> IyzicoClient.retrieveCheckoutForm) doğrulanır;
     * tarayıcıdan gelen bilgiye asla güvenilmez.
     */
    @PostMapping("/payment/callback")
    public String callback(@RequestParam(value = "token", required = false) String token) {
        PaymentService.CallbackOutcome outcome;
        try {
            outcome = paymentService.handleCallback(token);
        } catch (Exception e) {
            // Beklenmedik hata: sipariş id'si bilinmiyor olabilir -> güvenli genel hata sayfası.
            // Sipariş PAID olarak İŞARETLENMEZ (markOrderAsPaid çağrılmadı); tekrar deneme
            // gerektiğinde kullanıcı "Tekrar Dene" ile yeniden ödeme başlatabilir.
            return "redirect:/payment/failure/0";
        }

        if (outcome.orderId == null) {
            // Token eşleşmedi / geçersiz -> genel başarısızlık sayfası (sipariş id'siz).
            return "redirect:/payment/failure/0";
        }
        return outcome.paid
                ? "redirect:/payment/success/" + outcome.orderId
                : "redirect:/payment/failure/" + outcome.orderId;
    }

    @GetMapping("/payment/success/{orderId}")
    public String success(@PathVariable Long orderId, Principal principal,
                          Authentication authentication, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Order order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return "redirect:/orders";
        }
        boolean admin = isAdmin(authentication);
        String ownerEmail = (order.getUser() != null) ? order.getUser().getEmail() : null;
        if (!admin && (ownerEmail == null || !ownerEmail.equals(principal.getName()))) {
            return "redirect:/orders";
        }
        // Gerçekten ödenmemişse başarı sayfası gösterilmez.
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            return "redirect:/payment/failure/" + orderId;
        }
        model.addAttribute("order", order);
        return "payment_success";
    }

    @GetMapping("/payment/failure/{orderId}")
    public String failure(@PathVariable Long orderId, Principal principal,
                          Authentication authentication, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Order order = (orderId != null && orderId > 0) ? orderService.getOrderDetail(orderId) : null;
        if (order != null) {
            boolean admin = isAdmin(authentication);
            String ownerEmail = (order.getUser() != null) ? order.getUser().getEmail() : null;
            if (!admin && (ownerEmail == null || !ownerEmail.equals(principal.getName()))) {
                return "redirect:/orders";
            }
        }
        model.addAttribute("order", order);
        model.addAttribute("orderId", orderId);
        return "payment_failure";
    }

    // ----- yardımcılar -----
    private boolean isAdmin(Authentication authentication) {
        return authentication != null &&
                authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
