package com.denizcelikhalat.katalog.model;

public enum OrderStatus {
    PENDING,     // Sipariş alındı, ödeme/onay bekliyor
    PAID,        // Ödeme tamamlandı (kart ile ödeme başarılı oldu)
    PREPARING,   // Hazırlanıyor
    SHIPPED,     // Kargoya verildi
    DELIVERED,   // Teslim edildi
    CANCELLED    // İptal edildi
}
