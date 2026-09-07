package com.denizcelikhalat.katalog.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Ürün arama sorgusunu ve ürünlerin aranabilir metnini normalize eden, token'lara ayıran ve
 * relevance skoru hesaplayan SAF (Spring/DB'ye bağımlı olmayan) yardımcı sınıf.
 *
 * Amaç: büyük/küçük harf, Türkçe karakter (ç/ğ/ı/İ/ö/ş/ü), noktalama/ayraç farklılıkları
 * (G-80 = g 80 = G80, 6x36 = 6 x 36) ve kelime sırası farkı gözetmeksizin arama yapabilmek —
 * veritabanı şeması değişmeden, tamamen Java tarafında.
 *
 * NASIL ÇALIŞIR:
 * 1) Hem sorgu hem her ürünün aranabilir alanları "compact" forma çevrilir: küçük harf +
 *    Türkçe katlama + TÜM alfanumerik-olmayan karakterler (boşluk dahil) silinir. Böylece
 *    "G-80", "g 80", "G80" hepsi "g80" olur; "6x36", "6 x 36", "6-x-36" hepsi "6x36" olur.
 * 2) Sorgu, alfanumerik-olmayan her karakter ayraç kabul edilerek token'lara ayrılır (her biri
 *    ayrıca "compact" edilir). "g80 pimli kanca" -> ["g80","pimli","kanca"]; "6x36" -> ["6x36"]
 *    (aralarında ayraç yok, tek token kalır); "6 x 36" -> ["6","x","36"].
 * 3) Bir ürün, sorgudaki TÜM token'lar (herhangi bir alanda olmak kaydıyla) onun aranabilir
 *    alanlarında ALT-DİZE olarak bulunuyorsa eşleşmiş sayılır (AND mantığı).
 * 4) Eşleşen ürünler, alan ağırlıklı bir puanla sıralanır (bkz. score()).
 *
 * Bu sınıf hiçbir yan etkiye/duruma sahip değildir (thread-safe, saf fonksiyonlar) ve JPA/
 * Product entity'sine bağımlı değildir — çağıran taraf (ProductServiceImpl) entity alanlarını
 * SearchableFields'e kendisi eşler.
 */
public final class ProductSearchSupport {

    private ProductSearchSupport() {
    }

    /**
     * Bir ürünün aranabilir alanları. null alanlar güvenle boş string'e çevrilir.
     */
    public static final class SearchableFields {
        public final String name;
        public final String description;
        public final String categoryName;
        public final String measurementOptionsText;
        public final String measurementUnitLabel;

        public SearchableFields(String name, String description, String categoryName,
                                 String measurementOptionsText, String measurementUnitLabel) {
            this.name = name == null ? "" : name;
            this.description = description == null ? "" : description;
            this.categoryName = categoryName == null ? "" : categoryName;
            this.measurementOptionsText = measurementOptionsText == null ? "" : measurementOptionsText;
            this.measurementUnitLabel = measurementUnitLabel == null ? "" : measurementUnitLabel;
        }
    }

    // Türkçe karakter katlama (küçük+büyük harf, genel küçük harfe çevirmeden ÖNCE uygulanır).
    private static String foldTurkish(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'ç': case 'Ç': sb.append('c'); break;
                case 'ğ': case 'Ğ': sb.append('g'); break;
                case 'ı': sb.append('i'); break;
                case 'İ': sb.append('i'); break;
                case 'I': sb.append('i'); break; // ascii I de "i"ye katlanır -> tutarlılık
                case 'ö': case 'Ö': sb.append('o'); break;
                case 'ş': case 'Ş': sb.append('s'); break;
                case 'ü': case 'Ü': sb.append('u'); break;
                default: sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * Metni "compact" forma çevirir: Türkçe katlama + yalnızca [a-z0-9] karakterleri korunur,
     * geri kalan her şey (boşluk, tire, noktalama vb.) silinir. null-safe, asla null dönmez.
     */
    public static String toCompact(String text) {
        if (text == null) return "";
        String folded = foldTurkish(text);
        StringBuilder sb = new StringBuilder(folded.length());
        for (int i = 0; i < folded.length(); i++) {
            char c = folded.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Arama sorgusunu token'lara ayırır: Türkçe katlama uygulanır, alfanumerik-olmayan her
     * karakter ayraç kabul edilir (alfanumerik karakter dizileri bir arada kalır, örn. "6x36").
     * Boş token'lar listeye eklenmez. null/boş girdi için boş liste döner.
     */
    public static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) return tokens;
        String folded = foldTurkish(query);
        StringBuilder current = new StringBuilder();
        for (int i = 0; i <= folded.length(); i++) {
            char c = (i < folded.length()) ? folded.charAt(i) : ' ';
            boolean isAlnum = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (isAlnum) {
                current.append(c);
            } else if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        return tokens;
    }

    /**
     * queryTokens'taki TÜM token'lar (herhangi bir alanda olmak kaydıyla) fields içinde
     * alt-dize olarak bulunuyorsa true döner (AND mantığı). queryTokens boşsa/null ise true
     * döner (boş arama = herkes eşleşir; çağıran taraf boş-kelime durumunu zaten ayrı ele alır,
     * bu metot normalde boş sorguyla hiç çağrılmaz).
     */
    public static boolean matches(SearchableFields fields, List<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) return true;

        String nameC = toCompact(fields.name);
        String descC = toCompact(fields.description);
        String catC = toCompact(fields.categoryName);
        String optC = toCompact(fields.measurementOptionsText);
        String unitC = toCompact(fields.measurementUnitLabel);

        for (String token : queryTokens) {
            if (token.isEmpty()) continue;
            boolean found = nameC.contains(token) || descC.contains(token)
                    || catC.contains(token) || optC.contains(token) || unitC.contains(token);
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Relevance skoru hesaplar. Bu metot eşleşme kontrolü YAPMAZ — matches() true dönmüş
     * (zaten eşleşmiş) bir ürün için çağrılmalıdır, sadece puanlar.
     *
     * Puanlama:
     *  - normalize edilmiş ürün adı, normalize edilmiş TÜM sorguyla (boşluksuz) birebir
     *    eşleşiyorsa: +100
     *  - (üsttekiyle aynı anda sayılmaz) ürün adı sorgu ile başlıyorsa: +60
     *  - sorgudaki her token ürün adında bulunuyorsa, token başına: +20
     *  - kategori isminde bulunan her token için: +10
     *  - açıklamada bulunan her token için: +5
     *  - measurementOptionsText içinde bulunan her token için: +3
     *
     * @param wholeQueryCompact toCompact(orijinal sorgu) — tam/başlangıç eşleşmesi kontrolü için
     */
    public static int score(SearchableFields fields, List<String> queryTokens, String wholeQueryCompact) {
        String nameC = toCompact(fields.name);
        String descC = toCompact(fields.description);
        String catC = toCompact(fields.categoryName);
        String optC = toCompact(fields.measurementOptionsText);

        int total = 0;

        if (wholeQueryCompact != null && !wholeQueryCompact.isEmpty()) {
            if (nameC.equals(wholeQueryCompact)) {
                total += 100;
            } else if (nameC.startsWith(wholeQueryCompact)) {
                total += 60;
            }
        }

        if (queryTokens != null) {
            for (String token : queryTokens) {
                if (token.isEmpty()) continue;
                if (nameC.contains(token)) total += 20;
                if (catC.contains(token)) total += 10;
                if (descC.contains(token)) total += 5;
                if (optC.contains(token)) total += 3;
            }
        }

        return total;
    }
}
