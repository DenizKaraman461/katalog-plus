package com.denizcelikhalat.katalog.model;

/**
 * Ürün/sipariş para birimi. Dönüşüm YOK: fiyat hangi para biriminde tanımlıysa o şekilde
 * saklanır ve gösterilir. measurement_options_text içindeki sayılar da ürünün para biriminde
 * değerlerdir (örn. "8 mm K.Öz | 3.56" -> currency EUR ise 3,56 €).
 */
public enum PriceCurrency {
    TRY("\u20BA", "TRY"), // ₺
    USD("$", "USD"),
    EUR("\u20AC", "EUR"); // €

    private final String symbol;
    private final String label;

    PriceCurrency(String symbol, String label) {
        this.symbol = symbol;
        this.label = label;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getLabel() {
        return label;
    }
}
