package com.denizcelikhalat.katalog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * KÖK SORUN:
 *   "IllegalStateException: Cannot create a session after the response has been committed"
 *
 * Büyük (inline CSS'li) sayfalarda Thymeleaf, içerideki <form th:action=...> öğesine
 * gelmeden ÖNCE yanıt arabelleği dolup yanıt "committed" hâle gelebiliyor. Spring Security
 * tam o formu işlerken CSRF token'ını HttpSession'a yazmak ister; session henüz yoksa ve
 * yanıt commit olduysa session OLUŞTURULAMAZ → istek patlar, hata sayfası da basılamaz,
 * tarayıcı yarım yanıtla asılı kalır (donma hissi).
 *
 * ÇÖZÜM (iki katman):
 *   1) Yanıt arabelleğini büyüt → sayfa render bitmeden flush/commit olmasın.
 *   2) CSRF token'ını isteğin başında ERKEN materialize et → session, render (ve commit)
 *      başlamadan oluşturulsun. Bu, soruna boyuttan bağımsız kalıcı çözümdür.
 *
 * Filtre güvenlik zincirinden SONRA çalışır (LOWEST_PRECEDENCE); bu noktada CsrfFilter,
 * CsrfToken'ı request attribute olarak çoktan yerleştirmiş olur.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class CsrfTokenEagerLoaderFilter extends OncePerRequestFilter {

    private static final int TARGET_BUFFER = 64 * 1024; // 64 KB

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1) Yanıt arabelleğini büyüt (henüz hiçbir şey yazılmadan).
        try {
            if (response.getBufferSize() < TARGET_BUFFER) {
                response.setBufferSize(TARGET_BUFFER);
            }
        } catch (IllegalStateException ignored) {
            // Yanıta yazım başlamışsa sessizce geç.
        }

        // 2) CSRF token'ını erken materialize et → session erken oluşur (commit'ten önce).
        Object attr = request.getAttribute(CsrfToken.class.getName());
        if (attr instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }

        filterChain.doFilter(request, response);
    }
}
