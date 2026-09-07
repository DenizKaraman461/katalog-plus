package com.denizcelikhalat.katalog.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Ürün referansı; ürün sonradan silinse/değişse bile burada cascade yok.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private Integer quantity;

    // Sipariş anındaki HESAPLANMIŞ birim fiyat (NONE: ürün fiyatı, CUSTOM: fiyat*ölçü,
    // PRESET: seçenek fiyatı). Ürün fiyatı sonradan değişse bile sipariş geçmişi bozulmaz.
    private BigDecimal price;

    // ===== Ölçü snapshot'ı =====

    @Column(name = "selected_measurement", length = 255)
    private String selectedMeasurement;

    @Column(name = "measurement_amount", precision = 10, scale = 2)
    private BigDecimal measurementAmount;

    // Sipariş anındaki para birimi (dönüşüm yok; ürün para birimi sonradan değişse bile sabit kalır).
    @Enumerated(EnumType.STRING)
    @Column(name = "currency_snapshot", length = 3)
    private PriceCurrency currencySnapshot;

    public OrderItem() {
    }

    public OrderItem(Long id, Order order, Product product, Integer quantity, BigDecimal price) {
        this.id = id;
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.price = price;
    }

    public OrderItem(Product product, Integer quantity, BigDecimal price) {
        this.product = product;
        this.quantity = quantity;
        this.price = price;
    }

    // Ölçü bilgisiyle birlikte oluşturma
    public OrderItem(Product product, Integer quantity, BigDecimal price,
                     String selectedMeasurement, BigDecimal measurementAmount) {
        this.product = product;
        this.quantity = quantity;
        this.price = price;
        this.selectedMeasurement = selectedMeasurement;
        this.measurementAmount = measurementAmount;
    }

    // Getter & Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
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

    public PriceCurrency getCurrencySnapshot() {
        return currencySnapshot;
    }

    public void setCurrencySnapshot(PriceCurrency currencySnapshot) {
        this.currencySnapshot = currencySnapshot;
    }
}
