package com.denizcelikhalat.katalog.model;

/**
 * Ürün fiyatlandırma / ölçü modu.
 *  NONE   : Ölçü yok, sabit fiyat (product.price).
 *  CUSTOM : Müşteri ölçü girer; birim fiyat = product.price * girilen ölçü.
 *  PRESET : Admin'in tanımladığı hazır seçeneklerden biri seçilir; fiyat seçeneğin fiyatıdır.
 *  PRESET_AMOUNT : Müşteri hem hazır seçeneklerden birini seçer HEM de miktar/metraj girer;
 *                  birim fiyat = seçenek fiyatı * girilen miktar. (örn. 8 mm K.Öz 120₺/m × 15 m)
 */
public enum MeasurementMode {
    NONE,
    CUSTOM,
    PRESET,
    PRESET_AMOUNT
}
