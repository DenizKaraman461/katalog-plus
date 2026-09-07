package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.ShippingCalculationResult;
import com.denizcelikhalat.katalog.model.ShippingCategory;
import com.denizcelikhalat.katalog.model.ShippingCostResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AŞAMA 4A: ShippingCostService birim testleri. Saf POJO'larla çalışır (DB/Spring context
 * gerekmez) — @Value alanları Java alan başlatıcılarıyla (150/8/500/5) aynı varsayılana
 * sahip olduğundan Spring olmadan "new ShippingCostService()" ile de doğru çalışır.
 */
class ShippingCostServiceTest {

    private final ShippingCostService shippingCostService = new ShippingCostService();

    // ---- Yardımcı: ShippingService çalıştırılmadan doğrudan bir ShippingCalculationResult kurar ----
    private ShippingCalculationResult buildWeightResult(BigDecimal totalWeight, boolean weightCalculated,
                                                        boolean requiresManualReview, ShippingCategory category) {
        return new ShippingCalculationResult(totalWeight, weightCalculated, requiresManualReview,
                category, category != null ? category.name() : null);
    }

    /**
     * TEST 1: 14 kg, STANDARD -> 150 + (14 x 8) = 262.00 TL.
     */
    @Test
    void standardKargoUcretiDogruHesaplanmali() {
        ShippingCalculationResult weightResult = buildWeightResult(
                new BigDecimal("14.00"), true, false, ShippingCategory.STANDARD);

        ShippingCostResult result = shippingCostService.calculateShippingCost(weightResult);

        assertNotNull(result);
        assertTrue(result.isCostCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(0, new BigDecimal("262.00").compareTo(result.getShippingCost()));
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }

    /**
     * TEST 2: 100 kg, HEAVY_CARGO -> 500 + (100 x 5) = 1000.00 TL.
     */
    @Test
    void heavyCargoUcretiDogruHesaplanmali() {
        ShippingCalculationResult weightResult = buildWeightResult(
                new BigDecimal("100.00"), true, false, ShippingCategory.HEAVY_CARGO);

        ShippingCostResult result = shippingCostService.calculateShippingCost(weightResult);

        assertNotNull(result);
        assertTrue(result.isCostCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(0, new BigDecimal("1000.00").compareTo(result.getShippingCost()));
        assertEquals(ShippingCategory.HEAVY_CARGO, result.getCategory());
    }

    /**
     * TEST 3: 500 kg, MANUAL_REVIEW -> costCalculated=false, ücret hesaplanmaz.
     */
    @Test
    void manualReviewKategorisindeUcretHesaplanmamali() {
        ShippingCalculationResult weightResult = buildWeightResult(
                new BigDecimal("500.00"), true, false, ShippingCategory.MANUAL_REVIEW);

        ShippingCostResult result = shippingCostService.calculateShippingCost(weightResult);

        assertNotNull(result);
        assertFalse(result.isCostCalculated());
        assertTrue(result.isRequiresManualReview());
        assertNull(result.getShippingCost());
        assertEquals("Bu ağırlıktaki sipariş için özel nakliye fiyatı belirlenmelidir.", result.getMessage());
    }

    /**
     * TEST 4: null ShippingCalculationResult -> exception YOK, manuel inceleme.
     */
    @Test
    void nullSonucPatlamamaliVeManuelIncelemeDonmeli() {
        ShippingCostResult result = shippingCostService.calculateShippingCost(null);

        assertNotNull(result);
        assertFalse(result.isCostCalculated());
        assertTrue(result.isRequiresManualReview());
        assertNull(result.getShippingCost());
    }

    /**
     * TEST 5: 0 kg -> standart fiyat hesabı yapılabilir (150 + 0x8 = 150.00).
     */
    @Test
    void sifirKgIcinStandartFiyatHesaplanabilmeli() {
        ShippingCalculationResult weightResult = buildWeightResult(
                new BigDecimal("0.00"), true, false, ShippingCategory.STANDARD);

        ShippingCostResult result = shippingCostService.calculateShippingCost(weightResult);

        assertNotNull(result);
        assertTrue(result.isCostCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(0, new BigDecimal("150.00").compareTo(result.getShippingCost()));
    }

    // ---- Ek null-güvenlik testleri (madde 4) ----

    @Test
    void categoryNullIsePatlamamaliVeManuelIncelemeDonmeli() {
        ShippingCalculationResult weightResult = buildWeightResult(
                new BigDecimal("10.00"), true, false, null);

        ShippingCostResult result = shippingCostService.calculateShippingCost(weightResult);

        assertNotNull(result);
        assertFalse(result.isCostCalculated());
        assertTrue(result.isRequiresManualReview());
    }

    @Test
    void totalWeightNullIsePatlamamaliVeManuelIncelemeDonmeli() {
        ShippingCalculationResult weightResult = buildWeightResult(
                null, false, true, ShippingCategory.MANUAL_REVIEW);

        ShippingCostResult result = shippingCostService.calculateShippingCost(weightResult);

        assertNotNull(result);
        assertFalse(result.isCostCalculated());
        assertTrue(result.isRequiresManualReview());
    }

    @Test
    void negatifAgirlikPatlamamaliVeManuelIncelemeDonmeli() {
        ShippingCalculationResult weightResult = buildWeightResult(
                new BigDecimal("-5.00"), true, false, ShippingCategory.STANDARD);

        ShippingCostResult result = shippingCostService.calculateShippingCost(weightResult);

        assertNotNull(result);
        assertFalse(result.isCostCalculated());
        assertTrue(result.isRequiresManualReview());
    }

    /**
     * Ek test: ShippingService veri eksikliği nedeniyle requiresManualReview=true dediyse
     * (kategori teorik olarak STANDARD gelse bile) otomatik ücretlendirme yapılmamalı.
     */
    @Test
    void requiresManualReviewTrueIseKategoriNeOlursaOlsunUcretHesaplanmamali() {
        ShippingCalculationResult weightResult = buildWeightResult(
                new BigDecimal("10.00"), false, true, ShippingCategory.STANDARD);

        ShippingCostResult result = shippingCostService.calculateShippingCost(weightResult);

        assertNotNull(result);
        assertFalse(result.isCostCalculated());
        assertTrue(result.isRequiresManualReview());
    }
}
