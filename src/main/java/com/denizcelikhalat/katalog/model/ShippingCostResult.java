package com.denizcelikhalat.katalog.model;

import java.math.BigDecimal;

/**
 * AŞAMA 4A: Bir siparişin/sepetin TAHMİNİ kargo ÜCRETİ hesabının sonucu.
 *
 * Girdisi ShippingCalculationResult'tır (AŞAMA 2/3B — ağırlık + kategori). Bu sınıf henüz
 * hiçbir yere (checkout, Order, ödeme tutarı) BAĞLANMAZ; yalnızca hesaplama motorunun
 * çıktısını taşıyan bağımsız bir DTO'dur (entity değildir, persist edilmez).
 */
public class ShippingCostResult {

    // Hesaplanan tahmini kargo ücreti (TL). costCalculated=false ise null'dur.
    private BigDecimal shippingCost;

    // Ücret otomatik olarak güvenilir şekilde hesaplanabildiyse true.
    private boolean costCalculated;

    // Otomatik fiyatlandırma yapılmamalıysa (MANUAL_REVIEW kategorisi veya eksik/geçersiz
    // veri) true; bu durumda shippingCost null, costCalculated false olur.
    private boolean requiresManualReview;

    // Kullanıcıya/admin'e gösterilecek açıklama (yalnızca manuel inceleme durumunda dolu).
    private String message;

    // Hesaplamanın dayandığı kategori (ShippingCalculationResult'tan aynen aktarılır).
    private ShippingCategory category;

    public ShippingCostResult() {
    }

    public ShippingCostResult(BigDecimal shippingCost, boolean costCalculated, boolean requiresManualReview,
                              String message, ShippingCategory category) {
        this.shippingCost = shippingCost;
        this.costCalculated = costCalculated;
        this.requiresManualReview = requiresManualReview;
        this.message = message;
        this.category = category;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }

    public boolean isCostCalculated() {
        return costCalculated;
    }

    public void setCostCalculated(boolean costCalculated) {
        this.costCalculated = costCalculated;
    }

    public boolean isRequiresManualReview() {
        return requiresManualReview;
    }

    public void setRequiresManualReview(boolean requiresManualReview) {
        this.requiresManualReview = requiresManualReview;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ShippingCategory getCategory() {
        return category;
    }

    public void setCategory(ShippingCategory category) {
        this.category = category;
    }
}
