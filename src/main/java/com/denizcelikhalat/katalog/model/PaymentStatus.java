package com.denizcelikhalat.katalog.model;

/**
 * Bir siparişin ÖDEME durumu. order.status (fulfillment/kargo durumu) alanından bağımsızdır.
 * Sipariş yalnızca paymentStatus = PAID olduğunda "ödemesi tamamlanmış" sayılır.
 */
public enum PaymentStatus {
    PENDING,    // Ödeme başlatıldı/bekleniyor (henüz sonuç yok)
    PAID,       // Ödeme başarıyla tamamlandı
    FAILED,     // Ödeme başarısız / reddedildi
    CANCELLED   // Ödeme kullanıcı tarafından iptal edildi / yarım bırakıldı
}
