package com.denizcelikhalat.katalog.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Yasal / güven sayfaları (iyzico Sanal POS başvurusu için gerekli statik sayfalar).
 * Herkese açık, kimlik doğrulama gerektirmez (bkz. SecurityConfig permitAll listesi).
 *
 * NOT: Bu sayfaların İÇERİĞİ hukuki bir TASLAKTIR; yayına almadan önce bir hukuk
 * danışmanına / avukata onaylatılması önerilir. Şirket bilgileri (unvan, adres,
 * MERSİS/vergi no vb.) şablonlarda PLACEHOLDER olarak bırakılmıştır.
 */
@Controller
public class LegalController {

    @GetMapping("/mesafeli-satis-sozlesmesi")
    public String distanceSalesAgreement() {
        return "mesafeli_satis_sozlesmesi";
    }

    @GetMapping("/teslimat-ve-iade")
    public String deliveryAndReturns() {
        return "teslimat_ve_iade";
    }

    @GetMapping("/gizlilik-politikasi")
    public String privacyPolicy() {
        return "gizlilik_politikasi";
    }
}
