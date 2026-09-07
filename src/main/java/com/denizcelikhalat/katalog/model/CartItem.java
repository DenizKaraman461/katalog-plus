package com.denizcelikhalat.katalog.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    // Ürün silinse bile cascade YOK; sepet kalemi ürünü silmez.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private Integer quantity;

    // ===== Ölçü bilgisi (snapshot) =====

    // Seçilen/oluşturulan ölçü etiketi. NONE modunda null.
    // Örn CUSTOM: "3 metre", PRESET: "5 metre".
    @Column(name = "selected_measurement", length = 255)
    private String selectedMeasurement;

    // CUSTOM modunda girilen sayısal ölçü (örn 3). Diğer modlarda null.
    @Column(name = "measurement_amount", precision = 10, scale = 2)
    private BigDecimal measurementAmount;

    // Hesaplanmış BİRİM fiyat (adetle çarpılmadan önce). Sepet/checkout bunu gösterir.
    @Column(name = "unit_price_snapshot", precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot;

    // Eklenme anındaki ürün para birimi (dönüşüm yok). Sepet tek para biriminde tutulur.
    @Enumerated(EnumType.STRING)
    @Column(name = "currency_snapshot", length = 3)
    private PriceCurrency currencySnapshot;

    public CartItem() {
    }

    public CartItem(Long id, Cart cart, Product product, Integer quantity) {
        this.id = id;
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
    }

    public CartItem(Product product, Integer quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    // Ölçü + birim fiyat snapshot'ı ile oluşturma
    public CartItem(Product product, Integer quantity, String selectedMeasurement,
                    BigDecimal measurementAmount, BigDecimal unitPriceSnapshot) {
        this.product = product;
        this.quantity = quantity;
        this.selectedMeasurement = selectedMeasurement;
        this.measurementAmount = measurementAmount;
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    // Getter & Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getSelectedMeasurement() {
        return selectedMeasurement;
    }

    public void setSelectedMeasurement(String selectedMeasurement) {
        this.selectedMeasurement = selectedMeasurement;
    }

    public BigDecimal getMeasurementAmount() {
        return measurementAmount;
    }

    public void setMeasurementAmount(BigDecimal measurementAmount) {
        this.measurementAmount = measurementAmount;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public PriceCurrency getCurrencySnapshot() {
        return currencySnapshot;
    }

    public void setCurrencySnapshot(PriceCurrency currencySnapshot) {
        this.currencySnapshot = currencySnapshot;
    }
}
