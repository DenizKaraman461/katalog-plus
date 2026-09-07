package com.denizcelikhalat.katalog.model;

/**
 * AŞAMA 3B: Sepetin toplam ağırlığına göre lojistik kategorisi.
 * Bu enum yalnızca SINIFLANDIRMA amaçlıdır — kargo ÜCRETİ hesaplaması bu kapsamda YOKTUR.
 */
public enum ShippingCategory {

    // Normal kargo ile gönderilebilir (standart eşiğin altında).
    STANDARD,

    // Ağır ürün; özel kargo/ambar gerekebilir (standart ile manuel inceleme eşiği arasında).
    HEAVY_CARGO,

    // Otomatik fiyatlandırma yapılmamalı; admin kontrol etmeli (eşik üstü VEYA ağırlık
    // güvenilir şekilde hesaplanamadığında).
    MANUAL_REVIEW
}
