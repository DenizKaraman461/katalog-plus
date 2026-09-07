package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.service.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Şifremi Unuttum / Şifre Sıfırlama" akışı. Tamamı herkese açık (permitAll,
 * bkz. SecurityConfig) — giriş yapmamış kullanıcı için tasarlanmıştır.
 */
@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // ===================== Şifremi Unuttum =====================

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot_password";
    }

    /**
     * GÜVENLİK: e-posta sistemde kayıtlı olsun ya da olmasın, kullanıcıya HER ZAMAN aynı genel
     * mesaj gösterilir. Böylece bir e-postanın sistemde kayıtlı olup olmadığı asla sızdırılmaz.
     */
    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam("email") String email,
                                       RedirectAttributes redirectAttributes) {
        passwordResetService.requestPasswordReset(email);

        redirectAttributes.addFlashAttribute("success",
                "Bu e-posta adresi sistemimizde kayıtlıysa, şifre sıfırlama bağlantısı gönderildi. "
                        + "Lütfen gelen kutunuzu (ve spam klasörünü) kontrol edin.");
        return "redirect:/forgot-password";
    }

    // ===================== Şifre Sıfırlama =====================

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(value = "token", required = false) String token, Model model) {
        PasswordResetService.TokenStatus status = passwordResetService.checkToken(token);
        boolean valid = status == PasswordResetService.TokenStatus.VALID;

        model.addAttribute("token", token);
        model.addAttribute("valid", valid);
        if (!valid) {
            model.addAttribute("statusMessage", PasswordResetService.describeStatus(status));
        }
        return "reset_password";
    }

    @PostMapping("/reset-password")
    public String resetPasswordSubmit(@RequestParam("token") String token,
                                      @RequestParam("password") String password,
                                      @RequestParam("confirmPassword") String confirmPassword,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        PasswordResetService.ResetResult result =
                passwordResetService.resetPassword(token, password, confirmPassword);

        if (!result.success) {
            model.addAttribute("token", token);
            model.addAttribute("valid", !result.tokenProblem);
            if (result.tokenProblem) {
                model.addAttribute("statusMessage", result.errorMessage);
            } else {
                model.addAttribute("error", result.errorMessage);
            }
            return "reset_password";
        }

        redirectAttributes.addFlashAttribute("success",
                "Şifreniz güncellendi. Yeni şifrenizle giriş yapabilirsiniz.");
        return "redirect:/login";
    }
}
