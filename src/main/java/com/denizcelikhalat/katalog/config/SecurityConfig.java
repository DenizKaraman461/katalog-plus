package com.denizcelikhalat.katalog.config;

import com.denizcelikhalat.katalog.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService) {
        this.customUserDetailsService = customUserDetailsService;
    }

    // Şifreleri BCrypt ile hash'lemek/doğrulamak için encoder.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Kimlik doğrulama DB'deki kullanıcılar (CustomUserDetailsService) üzerinden yapılır.
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authenticationProvider(authenticationProvider())
                // CSRF genel olarak AÇIK bırakıldı (devre dışı bırakılmıyor). Thymeleaf formlarına
                // token otomatik eklenir; AJAX istekleri token'ı meta etiketinden header ile gönderir.
                // TEK istisna: /payment/callback -> ödeme sağlayıcısının (iyzico) sunucudan sunucuya
                // POST ettiği webhook; bizim CSRF token'ımızı taşıyamaz. Sonuç yine de
                // PaymentService.handleCallback içinde token bazlı sunucu-taraflı doğrulamadan geçer,
                // dolayısıyla bu tek yol için CSRF muafiyeti güvenlik açığı YARATMAZ.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/payment/callback"))
                .authorizeHttpRequests(auth -> auth
                        // --- Login/Logout uç noktaları admin kuralından ÖNCE serbest olmalı ---
                        .requestMatchers("/login", "/admin/login", "/register").permitAll()
                        // Şifremi Unuttum / Şifre Sıfırlama: giriş yapmamış kullanıcı için, herkese açık.
                        .requestMatchers("/forgot-password", "/reset-password").permitAll()
                        // Çıkış yolu (şablonlardaki th:action="@{/logout}" ile uyumlu) serbest.
                        .requestMatchers("/logout").permitAll()

                        // --- Sadece ADMIN ---
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/add", "/edit/**", "/delete/**").hasRole("ADMIN")

                        // --- Sadece giriş yapmış kullanıcılar ---
                        .requestMatchers("/cart/**", "/checkout/**", "/orders/**", "/my-quotes").authenticated()

                        // --- Teklif talebi oluşturma herkese açık (admin listesi /admin/** altında ADMIN ister) ---
                        .requestMatchers("/quote/create").permitAll()

                        // --- Ödeme sağlayıcısının (iyzico/PayTR) callback/webhook uç noktası herkese açık
                        //     olmalı: dış sistemden gelir, oturum/cookie taşımaz. CSRF'ten de ayrıca
                        //     muaf tutulur (aşağıdaki .csrf(...) bloğuna bakın). /payment/success/**
                        //     ve /payment/failure/** BİLEREK burada YOK -> anyRequest().authenticated()
                        //     ile korunuyorlar, sahiplik kontrolü PaymentController içinde yapılır. ---
                        .requestMatchers("/payment/callback").permitAll()

                        // --- Herkese açık sayfalar ve statik kaynaklar ---
                        .requestMatchers(
                                "/", "/products", "/products/**", "/category/**",
                                "/uploads/**", "/css/**", "/js/**", "/images/**",
                                "/webjars/**", "/favicon.ico", "/favicon.png",
                                "/google088d3bb2b1c4ce4a.html", "/error",
                                // Yasal/güven sayfaları (iyzico Sanal POS başvurusu için)
                                "/mesafeli-satis-sozlesmesi", "/teslimat-ve-iade", "/gizlilik-politikasi"
                        ).permitAll()

                        // --- Üretim güvenliği: yukarıda eşleşmeyen HER ŞEY giriş ister (default-deny) ---
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        // Giriş formundaki kullanıcı adı alanı e-posta olmalı (username = email)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }
}
