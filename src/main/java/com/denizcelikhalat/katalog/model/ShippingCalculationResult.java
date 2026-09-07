package com.denizcelikhalat.katalog.model;

import java.math.BigDecimal;

/**
 * AŞAMA 2/3B: Bir sepetin toplam kargo AĞIRLIĞI ve lojistik KATEGORİSİ hesabının sonucu.
 * Kargo ÜCRETİ ile ilgisi YOKTUR (bu aşamalarda kargo ücretlendirmesi yapılmaz);
 * yalnızca ağırlık/kategori hesaplama altyapısının çıktısını taşır (bkz. ShippingService).
 *
 * Entity DEĞİLDİR (persist edilmez), CheckoutForm gibi düz bir taşıyıcı (DTO)'dır.
 */
public class ShippingCalculationResult {

    // Sepetteki hesaplanabilen kalemlerin toplam ağırlığı (kg). weightCalculated=false
    // olsa bile, hesaplanabilen kalemlerin kısmi toplamını taşır (bilgi amaçlı); ancak
    // requiresManualReview=true olduğunda bu değer NİHAİ/güvenilir kabul EDİLMEMELİDİR.
    private BigDecimal totalWeight;

    // Sepetteki TÜM kalemlerin ağırlığı güvenilir şekilde hesaplanabildiyse true.
    private boolean weightCalculated;

    // En az bir kalemin ağırlığı hesaplanamadıysa (shippingWeightPerMeter veya metre
    // miktarı eksikse) true; bu durumda kargo için manuel inceleme gerekir.
    private boolean requiresManualReview;

    // AŞAMA 3B: ağırlığa göre lojistik kategori (kargo ÜCRETİ değil, yalnızca sınıflandırma).
    private ShippingCategory category;

    // Kullanıcıya/admin'e gösterilecek Türkçe açıklama metni (örn. "Standart Kargo").
    private String categoryMessage;

    public ShippingCalculationResult() {
    }

    // Geriye dönük uyumluluk için korunur (AŞAMA 2'de kullanılan 3 alanlı constructor).
    public ShippingCalculationResult(BigDecimal totalWeight, boolean weightCalculated, boolean requiresManualReview) {
        this.totalWeight = totalWeight;
        this.weightCalculated = weightCalculated;
        this.requiresManualReview = requiresManualReview;
    }

    public ShippingCalculationResult(BigDecimal totalWeight, boolean weightCalculated, boolean requiresManualReview,
                                     ShippingCategory category, String categoryMessage) {
        this.totalWeight = totalWeight;
        this.weightCalculated = weightCalculated;
        this.requiresManualReview = requiresManualReview;
        this.category = category;
        this.categoryMessage = categoryMessage;
    }

    public BigDecimal getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(BigDecimal totalWeight) {
        this.totalWeight = totalWeight;
    }

    public boolean isWeightCalculated() {
        return weightCalculated;
    }

    public void setWeightCalculated(boolean weightCalculated) {
        this.weightCalculated = weightCalculated;
    }

    public boolean isRequiresManualReview() {
        return requiresManualReview;
    }

    public void setRequiresManualReview(boolean requiresManualReview) {
        this.requiresManualReview = requiresManualReview;
    }

    public ShippingCategory getCategory() {
        return category;
    }

    public void setCategory(ShippingCategory category) {
        this.category = category;
    }

    public String getCategoryMessage() {
        return categoryMessage;
    }

    public void setCategoryMessage(String categoryMessage) {
        this.categoryMessage = categoryMessage;
    }
}

