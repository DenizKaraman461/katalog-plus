package com.denizcelikhalat.katalog.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Ölçüye/projeye özel (measurement_mode = CUSTOM) ürünler için müşteri teklif talebi.
 * Sepet/sipariş akışından bağımsızdır; yalnızca talep bilgisi saklanır.
 */
@Entity
@Table(name = "quote_requests")
public class QuoteRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Hangi ürün için talep edildiği (gevşek bağ: sadece id + ad snapshot'ı tutulur).
    @Column(name = "product_id")
    private Long productId;

    // Giriş yapan kullanıcıyla eşleştirme (Tekliflerim sayfası). Anonim taleplerde null kalabilir.
    @Column(name = "user_email", length = 180)
    private String userEmail;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "customer_name", length = 160)
    private String customerName;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "company_name", length = 180)
    private String companyName;

    @Column(name = "sling_type", length = 120)
    private String slingType;

    @Column(name = "leg_count")
    private Integer legCount;

    @Column(name = "rope_diameter", length = 80)
    private String ropeDiameter;

    @Column(name = "rope_type", length = 120)
    private String ropeType;

    @Column(name = "length", length = 80)
    private String length;

    @Column(name = "capacity", length = 80)
    private String capacity;

    @Column(name = "working_angle", length = 80)
    private String workingAngle;

    @Column(name = "top_connection", length = 120)
    private String topConnection;

    @Column(name = "bottom_connection", length = 120)
    private String bottomConnection;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "certificate_requested")
    private Boolean certificateRequested = false;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // Talep durumu: NEW, CONTACTED, OFFER_SENT, CLOSED, CANCELLED. Varsayılan NEW.
    @Column(name = "status", length = 50)
    private String status = "NEW";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public QuoteRequest() {
    }

    // Getter & Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getSlingType() {
        return slingType;
    }

    public void setSlingType(String slingType) {
        this.slingType = slingType;
    }

    public Integer getLegCount() {
        return legCount;
    }

    public void setLegCount(Integer legCount) {
        this.legCount = legCount;
    }

    public String getRopeDiameter() {
        return ropeDiameter;
    }

    public void setRopeDiameter(String ropeDiameter) {
        this.ropeDiameter = ropeDiameter;
    }

    public String getRopeType() {
        return ropeType;
    }

    public void setRopeType(String ropeType) {
        this.ropeType = ropeType;
    }

    public String getLength() {
        return length;
    }

    public void setLength(String length) {
        this.length = length;
    }

    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    public String getWorkingAngle() {
        return workingAngle;
    }

    public void setWorkingAngle(String workingAngle) {
        this.workingAngle = workingAngle;
    }

    public String getTopConnection() {
        return topConnection;
    }

    public void setTopConnection(String topConnection) {
        this.topConnection = topConnection;
    }

    public String getBottomConnection() {
        return bottomConnection;
    }

    public void setBottomConnection(String bottomConnection) {
        this.bottomConnection = bottomConnection;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Boolean getCertificateRequested() {
        return certificateRequested;
    }

    public void setCertificateRequested(Boolean certificateRequested) {
        this.certificateRequested = certificateRequested;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
