package com.denizcelikhalat.katalog.model;

/**
 * Checkout (ödeme/sipariş onayı) formu için basit DTO. Entity değildir, DB'ye yazılmaz.
 * customerEmail formda yok; giriş yapan kullanıcıdan alınır.
 *
 * AŞAMA 10.1 (revize): customerCity eklendi — iyzico'nun Checkout Form Initialize isteğinde
 * zorunlu tuttuğu buyer.city alanı için gerçek müşteri verisi toplanır. TC Kimlik No
 * KULLANICIDAN TOPLANMAZ (gereksiz kişisel veri) — bkz. IyzicoClient.java: identityNumber
 * artık mevcut müşteri verilerinden (telefon vb.) türetiliyor, ayrı bir form alanı yok.
 */
public class CheckoutForm {

    private String customerName;
    private String customerPhone;
    private String customerCity;
    private String deliveryAddress;
    private String customerNote;
    private boolean preInfoAccepted;
    private boolean distanceSalesAccepted;

    public CheckoutForm() {
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerCity() {
        return customerCity;
    }

    public void setCustomerCity(String customerCity) {
        this.customerCity = customerCity;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public void setCustomerNote(String customerNote) {
        this.customerNote = customerNote;
    }

    public boolean isPreInfoAccepted() {
        return preInfoAccepted;
    }

    public void setPreInfoAccepted(boolean preInfoAccepted) {
        this.preInfoAccepted = preInfoAccepted;
    }

    public boolean isDistanceSalesAccepted() {
        return distanceSalesAccepted;
    }

    public void setDistanceSalesAccepted(boolean distanceSalesAccepted) {
        this.distanceSalesAccepted = distanceSalesAccepted;
    }
}
