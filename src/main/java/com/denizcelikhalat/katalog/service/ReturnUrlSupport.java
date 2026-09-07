package com.denizcelikhalat.katalog.service;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ürün listesi sayfalarından ("/products" veya "/category/{sayısal-id}") ürün detayına gelen
 * "hangi sayfadan geldim" bilgisini (returnUrl) doğrulayan/temizleyen yardımcı sınıf.
 *
 * GÜVENLİK: returnUrl, kullanıcı tarafından değiştirilebilen bir query param / gizli form
 * alanıdır / session içeriğidir. Doğrulanmadan doğrudan bir <a href> veya redirect hedefi
 * olarak kullanılırsa "open redirect" güvenlik açığına yol açar. Bu yüzden SIKI BİR WHITELIST
 * uygulanır:
 *
 * 1) Yol TAM OLARAK "/products" ya da TAM OLARAK "/category/<sadece rakamlar>" olmalıdır.
 *    Prefix eşleşmesi YAPILMAZ -> "/productsXYZ" veya "/category/../admin/orders" reddedilir.
 * 2) Query'de YALNIZCA "page", "size", "keyword" anahtarlarına izin verilir; başka herhangi
 *    bir parametre varsa TÜM returnUrl reddedilir.
 * 3) "page"/"size" değerleri sadece rakam olabilir (negatif/harf/özel karakter reddedilir).
 * 4) Şema (http://, https://), protokol-göreli (//...), backslash (\), fragment (#...) veya
 *    kontrol karakteri (satır sonu, tab, görünmez karakterler vb.) içeren HERHANGİ bir değer
 *    baştan reddedilir. Fragment (#product-{id}) YALNIZCA ProductController tarafından,
 *    doğrulamadan SONRA, sunucu taraflı ve güvenilir bir ürün ID'siyle eklenir — kullanıcı
 *    girdisinin BİR PARÇASI olarak asla kabul edilmez.
 * 5) Çıktı, girdinin ham/işlenmemiş hali DEĞİLDİR — yalnızca doğrulanmış parçalardan SIFIRDAN
 *    yeniden inşa edilir (defense-in-depth).
 *
 * KODLAMA: keyword değeri java.net.URLEncoder ile (uygulama tarafında ProductController.
 * buildListReturnUrl'de) kodlanır; burada da EŞLEŞEN java.net.URLDecoder kullanılır. Bu simetrik
 * çift sayesinde "&", "+", boşluk ve Türkçe karakterler (çÇşŞğĞüÜöÖıİ) round-trip'te doğru
 * şekilde korunur (Spring'in UriComponentsBuilder'ı ile karıştırılmaz — bu, "+" karakterinin
 * farklı yorumlanmasına yol açabilecek bir tutarsızlık kaynağıydı).
 *
 * İKİ AYRI YÖNTEM — NEDEN GEREKLİ:
 * sanitize(...) her zaman KULLANILABİLİR bir değer döner (geçersizse "/products"a düşer) —
 * "sonuçta bir şey lazım" durumları için (örn. CartController/QuoteController redirect'i).
 * sanitizeOrNull(...) ise GEÇERSİZSE null döner — çağıran taraf "bu değer GERÇEKTEN geçerli
 * miydi, yoksa sessizce mi varsayılana düştü" bilgisini KAYBETMEDEN bir sonraki öncelik
 * kaynağına (örn. HttpSession'daki LAST_PRODUCT_LIST_URL) geçebilsin diye (bkz.
 * ProductController.productDetail — 3 katmanlı öncelik zinciri).
 */
public final class ReturnUrlSupport {

    private static final String DEFAULT_RETURN_URL = "/products";

    private static final Pattern PRODUCTS_PATH = Pattern.compile("^/products$");
    private static final Pattern CATEGORY_PATH = Pattern.compile("^/category/[0-9]+$");
    private static final Pattern NUMERIC_VALUE = Pattern.compile("^[0-9]+$");

    private static final Set<String> ALLOWED_PARAMS = Set.of("page", "size", "keyword");
    private static final Set<String> NUMERIC_PARAMS = Set.of("page", "size");

    private ReturnUrlSupport() {
    }

    /**
     * returnUrl'i doğrular; geçerli değilse güvenli varsayılan olan "/products"'a düşer.
     * Exception FIRLATMAZ. "Ne olursa olsun kullanılabilir bir URL istiyorum" durumları için.
     */
    public static String sanitize(String returnUrl) {
        String result = sanitizeOrNull(returnUrl);
        return (result != null) ? result : DEFAULT_RETURN_URL;
    }

    /**
     * returnUrl'i doğrular; GEÇERSİZSE (null/boş/whitelist dışı/şüpheli) null döner —
     * sanitize()'ın aksine sessizce "/products"a DÜŞMEZ. Çağıran taraf null aldığında "bu
     * kaynak geçersizdi" bilgisini kullanıp bir SONRAKİ öncelik kaynağını deneyebilir.
     * Exception FIRLATMAZ.
     */
    public static String sanitizeOrNull(String returnUrl) {
        if (returnUrl == null) {
            return null;
        }
        String trimmed = returnUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Kontrol karakteri / backslash / fragment / şema / protokol-göreli -> KESİNLİKLE reddet.
        if (containsControlCharacter(trimmed)
                || trimmed.indexOf('\\') >= 0
                || trimmed.indexOf('#') >= 0
                || trimmed.contains("://")
                || trimmed.startsWith("//")) {
            return null;
        }

        String path;
        String query;
        int qIdx = trimmed.indexOf('?');
        if (qIdx >= 0) {
            path = trimmed.substring(0, qIdx);
            query = trimmed.substring(qIdx + 1);
        } else {
            path = trimmed;
            query = "";
        }

        // Yalnızca TAM "/products" veya TAM "/category/<sayısal id>" -> prefix taklidi
        // ("/productsXYZ") veya path traversal ("/category/../admin/orders") reddedilir.
        boolean isProducts = PRODUCTS_PATH.matcher(path).matches();
        boolean isCategory = CATEGORY_PATH.matcher(path).matches();
        if (!isProducts && !isCategory) {
            return null;
        }

        // Query'yi ayrıştır: SADECE whitelist'teki anahtarlara (page/size/keyword) izin verilir.
        Map<String, String> safeParams = new LinkedHashMap<>();
        if (!query.isBlank()) {
            for (String pair : query.split("&", -1)) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String rawKey = (eq >= 0) ? pair.substring(0, eq) : pair;
                String rawValue = (eq >= 0) ? pair.substring(eq + 1) : "";

                String key;
                String value;
                try {
                    key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
                    value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    // Bozuk/geçersiz percent-encoding -> geçersiz say.
                    return null;
                }

                if (!ALLOWED_PARAMS.contains(key)) {
                    return null; // whitelist dışı parametre -> reddet
                }
                if (containsControlCharacter(key) || containsControlCharacter(value)) {
                    return null;
                }
                if (NUMERIC_PARAMS.contains(key) && !NUMERIC_VALUE.matcher(value).matches()) {
                    return null; // page/size sadece rakam olabilir
                }

                safeParams.put(key, value);
            }
        }

        // Temiz URL'yi SIFIRDAN, yalnızca doğrulanmış parçalardan yeniden inşa et
        // (defense-in-depth: çıktı asla saldırganın ham girdisinin işlenmemiş hali değildir).
        StringBuilder result = new StringBuilder(path);
        boolean first = true;
        for (Map.Entry<String, String> e : safeParams.entrySet()) {
            result.append(first ? '?' : '&');
            first = false;
            result.append(e.getKey()).append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    /**
     * returnUrl'in geçerli (whitelist'e uyan) olup olmadığını döner. sanitizeOrNull(...) != null
     * ile eşdeğerdir; okunabilirlik için ayrı bir metot olarak da sunulur.
     */
    public static boolean isValid(String returnUrl) {
        return sanitizeOrNull(returnUrl) != null;
    }

    private static boolean containsControlCharacter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
