package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.ShippingCalculationResult;
import com.denizcelikhalat.katalog.model.ShippingCategory;
import com.denizcelikhalat.katalog.model.ShippingCostResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * AŞAMA 4A: ShippingCalculationResult'ı (AŞAMA 2/3B — ağırlık + kategori) GİRDİ olarak alıp
 * TAHMİNİ kargo ÜCRETİNİ hesaplayan bağımsız motor.
 *
 * Akış: Cart -> ShippingService -> ShippingCalculationResult -> ShippingCostService -> ShippingCostResult
 *
 * Bu serviste/kapsamda KESİNLİKLE YOK: checkout ekranına bağlama, Order'a kaydetme, ödeme
 * tutarına ekleme, iyzico entegrasyonu. Yalnızca hesaplama motoru.
 */
@Service
public class ShippingCostService {

    private static final String MANUAL_REVIEW_MESSAGE =
            "Bu ağırlıktaki sipariş için özel nakliye fiyatı belirlenmelidir.";

    // ---- STANDARD KARGO (application.properties) ----
    // NOT: Alan başlatıcıları (=new BigDecimal(...)), Spring dışında (örn. birim testte
    // "new ShippingCostService()" ile) de @Value'nin SpEL varsayılanıyla AYNI değere sahip
    // olunmasını sağlar; Spring bağlamında application.properties'ten enjekte edilerek
    // bu değerlerin üzerine yazılır.
    @Value("${app.shipping.standard.base-cost:150}")
    private BigDecimal standardBaseCost = new BigDecimal("150");

    @Value("${app.shipping.standard.per-kg-cost:8}")
    private BigDecimal standardPerKgCost = new BigDecimal("8");

    // ---- HEAVY KARGO ----
    @Value("${app.shipping.heavy.base-cost:500}")
    private BigDecimal heavyBaseCost = new BigDecimal("500");

    @Value("${app.shipping.heavy.per-kg-cost:5}")
    private BigDecimal heavyPerKgCost = new BigDecimal("5");

    /**
     * ShippingCalculationResult'tan tahmini kargo ücretini hesaplar.
     *
     * Null güvenlidir: weightResult null olabilir, category null olabilir, totalWeight null
     * olabilir, ağırlık negatif olabilir — hiçbiri exception fırlatmaz; bunların hepsinde
     * requiresManualReview=true, costCalculated=false döner.
     *
     * MANUAL_REVIEW kategorisinde (eşik aşımı VEYA ShippingService'in kendisi zaten
     * requiresManualReview=true demişse — örn. eksik ürün verisi) otomatik fiyatlandırma
     * YAPILMAZ; aynı şekilde manuel inceleme sonucu döner.
     */
    public ShippingCostResult calculateShippingCost(ShippingCalculationResult weightResult) {
        if (weightResult == null) {
            return manualReview(null);
        }

        ShippingCategory category = weightResult.getCategory();
        BigDecimal weight = weightResult.getTotalWeight();

        if (category == null || weight == null || weight.compareTo(BigDecimal.ZERO) < 0) {
            return manualReview(category);
        }

        // ShippingService zaten veri eksikliği nedeniyle manuel inceleme istemişse VEYA
        // kategori doğrudan MANUAL_REVIEW ise (eşik aşımı), otomatik ücretlendirme yapılmaz.
        if (weightResult.isRequiresManualReview() || category == ShippingCategory.MANUAL_REVIEW) {
            return manualReview(category);
        }

        BigDecimal baseCost;
        BigDecimal perKgCost;

        switch (category) {
            case STANDARD:
                baseCost = (standardBaseCost != null) ? standardBaseCost : BigDecimal.ZERO;
                perKgCost = (standardPerKgCost != null) ? standardPerKgCost : BigDecimal.ZERO;
                break;
            case HEAVY_CARGO:
                baseCost = (heavyBaseCost != null) ? heavyBaseCost : BigDecimal.ZERO;
                perKgCost = (heavyPerKgCost != null) ? heavyPerKgCost : BigDecimal.ZERO;
                break;
            default:
                // Bilinmeyen/yeni bir kategori eklenirse savunma amaçlı manuel inceleme.
                return manualReview(category);
        }

        BigDecimal cost = baseCost.add(weight.multiply(perKgCost)).setScale(2, RoundingMode.HALF_UP);

        ShippingCostResult result = new ShippingCostResult();
        result.setShippingCost(cost);
        result.setCostCalculated(true);
        result.setRequiresManualReview(false);
        result.setCategory(category);
        return result;
    }

    private ShippingCostResult manualReview(ShippingCategory category) {
        ShippingCostResult result = new ShippingCostResult();
        result.setShippingCost(null);
        result.setCostCalculated(false);
        result.setRequiresManualReview(true);
        result.setMessage(MANUAL_REVIEW_MESSAGE);
        result.setCategory(category);
        return result;
    }
}
