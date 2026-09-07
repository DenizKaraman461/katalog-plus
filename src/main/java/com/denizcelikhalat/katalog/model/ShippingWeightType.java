package com.denizcelikhalat.katalog.model;

/**
 * Bir ürünün kargo ağırlığının HANGİ birime göre hesaplanacağını belirtir.
 *
 * PER_METER: Metre bazlı ürünler (çelik halat, zincir vb.) — ağırlık
 *            shippingWeightPerMeter × satın alınan metre (measurementAmount) × adet.
 * PER_UNIT:  Adet bazlı ürünler (radansa, soket, spanzet, aksesuar vb.) — ağırlık
 *            shippingWeightPerUnit × satın alınan adet (quantity).
 *
 * Geriye dönük uyumluluk: Product.shippingWeightType alanı NOT NULL ve varsayılan
 * PER_METER'dır — migration'da da mevcut tüm satırlar PER_METER ile doldurulur, böylece
 * bu alan eklenmeden önce oluşturulmuş ürünlerin kargo hesaplama davranışı DEĞİŞMEZ.
 */
public enum ShippingWeightType {
    PER_METER,
    PER_UNIT
}
