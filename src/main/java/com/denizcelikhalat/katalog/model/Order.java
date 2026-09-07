package com.denizcelikhalat.katalog.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "orders") // "order" MySQL'de rezerve kelime olduğu için tablo adı "orders"
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Bir kullanıcının birden çok siparişi olabilir.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private BigDecimal totalAmount;

    private LocalDateTime orderDate;

    // Enum'u ordinal yerine isim olarak sakla (ileri uyumluluk için).
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatus status;

    private String deliveryAddress;

    // Müşteri değerlendirmesi (1-5). Henüz puanlanmadıysa null.
    private Integer rating;

    // ===== Checkout snapshot alanları (sipariş anındaki müşteri/onay bilgileri) =====
    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_email", length = 180)
    private String customerEmail;

    @Column(name = "customer_phone", length = 40)
    private String customerPhone;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    // ===== AŞAMA 10.1 (revize): iyzico buyer bilgisi için müşteri şehir SNAPSHOT'ı =====
    // Checkout formunda girilir, sipariş oluşturulduğu anda (diğer checkout snapshot'ları gibi)
    // buraya yazılır ve bir daha değişmez. TC Kimlik No alanı YOKTUR — müşteriden bu veri
    // toplanmıyor (kişisel veri minimizasyonu iş kararı); IyzicoClient.buildInitializeBody
    // identityNumber'ı ayrı bir snapshot alanı olmadan, mevcut müşteri verilerinden türetir.
    @Column(name = "customer_city", length = 100)
    private String customerCity;

    // Şirket yalnızca Türkiye içine gönderim yaptığından iş kararı olarak sabit "Turkey"
    // yazılır (bkz. OrderService.placeOrder); formda ayrı bir ülke alanı YOKTUR.
    @Column(name = "customer_country", length = 100)
    private String customerCountry;

    @Column(name = "pre_info_accepted", nullable = false)
    private Boolean preInfoAccepted = false;

    @Column(name = "distance_sales_accepted", nullable = false)
    private Boolean distanceSalesAccepted = false;

    // Sipariş para birimi (sepetteki tek para biriminden alınır; dönüşüm yok). Varsayılan USD.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private PriceCurrency currency = PriceCurrency.USD;

    // ===== Ödeme (PayTR / iyzico) alanları =====
    // Ödeme durumu order.status'tan (fulfillment) BAĞIMSIZDIR. Sipariş yalnızca
    // paymentStatus = PAID olduğunda ödemesi tamamlanmış sayılır.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    // Kullanılan sağlayıcı, örn. "IYZICO" veya "PAYTR".
    @Column(name = "payment_provider", length = 30)
    private String paymentProvider;

    // Bizim ürettiğimiz, sağlayıcıya gönderilen referans (conversationId / merchant_oid).
    @Column(name = "payment_conversation_id", length = 100)
    private String paymentConversationId;

    // Sağlayıcının verdiği ödeme formu/token (iyzico checkout form token, PayTR token vb.)
    // NOT: Bu alanda ASLA kart numarası/CVV gibi kart bilgisi tutulmaz; kart bilgisi hiçbir
    // zaman bizim sunucumuza uğramaz, doğrudan sağlayıcının barındırdığı sayfada girilir.
    @Column(name = "payment_token", length = 200)
    private String paymentToken;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // iyzico'ya GERÇEKTEN gönderilen tahsilat tutarı/para birimi (her zaman TRY'ye çevrilir).
    // Ürünün/siparişin orijinal para birimi ve tutarları (currency/totalAmount/currencySnapshot)
    // BUNDAN ETKİLENMEZ; bu alanlar yalnızca "kartla ne kadar tahsil edildi" bilgisini saklar.
    @Column(name = "payment_amount", precision = 19, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "payment_currency", length = 10)
    private String paymentCurrency;

    // Ödeme anında kullanılan döviz kurunun kaynağı ("TCMB", "FALLBACK" vb.) ve ne zaman
    // çekildiği + uygulanan margin yüzdesi — yalnızca bilgi/denetim amaçlı (admin görünümü).
    @Column(name = "payment_exchange_rate_source", length = 30)
    private String paymentExchangeRateSource;

    @Column(name = "payment_exchange_rate_fetched_at")
    private LocalDateTime paymentExchangeRateFetchedAt;

    @Column(name = "payment_exchange_rate_margin_percent", precision = 6, scale = 2)
    private BigDecimal paymentExchangeRateMarginPercent;

    // ===== AŞAMA 5: Kargo SNAPSHOT'ı =====
    // Sipariş oluşturulduğu ANDA hesaplanıp buraya yazılır (bkz. OrderService.placeOrder).
    // Sonrasında BİR DAHA hesaplanmaz — sipariş her görüntülendiğinde ShippingService/
    // ShippingCostService TEKRAR çağrılmaz; burada saklanan değer kullanılır. Böylece ürün
    // fiyatı/ağırlığı veya kargo tarifeleri sonradan değişse bile bu siparişin kargo bilgisi
    // sipariş anındaki haliyle sabit kalır. Hepsi NULL olabilir (kargo hesaplanamadıysa /
    // manuel inceleme gerekiyorsa sipariş yine de oluşur, sadece bu alanlar boş kalır).
    @Column(name = "shipping_weight", precision = 10, scale = 2)
    private BigDecimal shippingWeight;

    @Column(name = "shipping_cost", precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "shipping_category", length = 50)
    private String shippingCategory;

    @Column(name = "shipping_message", length = 255)
    private String shippingMessage;

    // ===== AŞAMA 8: Kargo OPERASYON bilgileri =====
    // AŞAMA 5'teki shipping snapshot alanlarından (shippingWeight/shippingCost/shippingCategory/
    // shippingMessage — sipariş anında hesaplanan TAHMİNİ kargo bilgisi) FARKLI bir amaca hizmet
    // eder: bu alanlar admin'in siparişi FİİLEN kargoya verdiğinde girdiği OPERASYONEL bilgilerdir
    // (hangi kargo firması, takip numarası, ne zaman kargoya verildi). Hepsi nullable — sipariş
    // henüz kargoya verilmemişse veya eski bir siparişse boş kalır.
    @Column(name = "shipping_company", length = 120)
    private String shippingCompany;

    @Column(name = "shipping_tracking_number", length = 100)
    private String shippingTrackingNumber;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    // Sipariş silinince kalemleri de silinir.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(Long id, User user, BigDecimal totalAmount, LocalDateTime orderDate,
                 OrderStatus status, String deliveryAddress, List<OrderItem> items) {
        this.id = id;
        this.user = user;
        this.totalAmount = totalAmount;
        this.orderDate = orderDate;
        this.status = status;
        this.deliveryAddress = deliveryAddress;
        this.items = items;
    }

    public Order(User user, BigDecimal totalAmount, LocalDateTime orderDate,
                 OrderStatus status, String deliveryAddress) {
        this.user = user;
        this.totalAmount = totalAmount;
        this.orderDate = orderDate;
        this.status = status;
        this.deliveryAddress = deliveryAddress;
    }

    // İlişkinin iki tarafını da senkron tutmak için yardımcı metotlar
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    // Getter & Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public void setCustomerNote(String customerNote) {
        this.customerNote = customerNote;
    }

    public String getCustomerCity() {
        return customerCity;
    }

    public void setCustomerCity(String customerCity) {
        this.customerCity = customerCity;
    }

    public String getCustomerCountry() {
        return customerCountry;
    }

    public void setCustomerCountry(String customerCountry) {
        this.customerCountry = customerCountry;
    }

    public Boolean getPreInfoAccepted() {
        return preInfoAccepted;
    }

    public void setPreInfoAccepted(Boolean preInfoAccepted) {
        this.preInfoAccepted = preInfoAccepted;
    }

    public Boolean getDistanceSalesAccepted() {
        return distanceSalesAccepted;
    }

    public void setDistanceSalesAccepted(Boolean distanceSalesAccepted) {
        this.distanceSalesAccepted = distanceSalesAccepted;
    }

    public PriceCurrency getCurrency() {
        return currency;
    }

    public void setCurrency(PriceCurrency currency) {
        this.currency = currency;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public String getPaymentConversationId() {
        return paymentConversationId;
    }

    public void setPaymentConversationId(String paymentConversationId) {
        this.paymentConversationId = paymentConversationId;
    }

    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public String getPaymentCurrency() {
        return paymentCurrency;
    }

    public void setPaymentCurrency(String paymentCurrency) {
        this.paymentCurrency = paymentCurrency;
    }

    public String getPaymentExchangeRateSource() {
        return paymentExchangeRateSource;
    }

    public void setPaymentExchangeRateSource(String paymentExchangeRateSource) {
        this.paymentExchangeRateSource = paymentExchangeRateSource;
    }

    public LocalDateTime getPaymentExchangeRateFetchedAt() {
        return paymentExchangeRateFetchedAt;
    }

    public void setPaymentExchangeRateFetchedAt(LocalDateTime paymentExchangeRateFetchedAt) {
        this.paymentExchangeRateFetchedAt = paymentExchangeRateFetchedAt;
    }

    public BigDecimal getPaymentExchangeRateMarginPercent() {
        return paymentExchangeRateMarginPercent;
    }

    public void setPaymentExchangeRateMarginPercent(BigDecimal paymentExchangeRateMarginPercent) {
        this.paymentExchangeRateMarginPercent = paymentExchangeRateMarginPercent;
    }

    public BigDecimal getShippingWeight() {
        return shippingWeight;
    }

    public void setShippingWeight(BigDecimal shippingWeight) {
        this.shippingWeight = shippingWeight;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }

    public String getShippingCategory() {
        return shippingCategory;
    }

    public void setShippingCategory(String shippingCategory) {
        this.shippingCategory = shippingCategory;
    }

    public String getShippingMessage() {
        return shippingMessage;
    }

    public void setShippingMessage(String shippingMessage) {
        this.shippingMessage = shippingMessage;
    }

    public String getShippingCompany() {
        return shippingCompany;
    }

    public void setShippingCompany(String shippingCompany) {
        this.shippingCompany = shippingCompany;
    }

    public String getShippingTrackingNumber() {
        return shippingTrackingNumber;
    }

    public void setShippingTrackingNumber(String shippingTrackingNumber) {
        this.shippingTrackingNumber = shippingTrackingNumber;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    /**
     * Sipariş toplamlarını para birimine göre gruplar (dönüşüm YOK, farklı para birimleri TOPLANMAZ).
     * Karışık para birimli siparişlerde ana toplam olarak BU metot kullanılmalı; totalAmount yalnızca
     * legacy/uyumluluk içindir. Sıra: EUR, USD, TRY.
     * Her kalem: currency = item.currencySnapshot ?: item.product.currency ?: order.currency ?: USD,
     *            lineTotal = (item.price ?: 0) * (quantity ?: 0).
     */
    @Transient
    public Map<PriceCurrency, BigDecimal> getTotalsByCurrency() {
        Map<PriceCurrency, BigDecimal> acc = new LinkedHashMap<>();
        if (items != null) {
            for (OrderItem item : items) {
                if (item == null) continue;

                PriceCurrency itemCurrency = item.getCurrencySnapshot();
                if (itemCurrency == null && item.getProduct() != null) {
                    itemCurrency = item.getProduct().getCurrency();
                }
                if (itemCurrency == null) itemCurrency = this.currency;
                if (itemCurrency == null) itemCurrency = PriceCurrency.USD;

                BigDecimal unitPrice = (item.getPrice() != null) ? item.getPrice() : BigDecimal.ZERO;
                int quantity = (item.getQuantity() != null) ? item.getQuantity() : 0;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

                acc.merge(itemCurrency, lineTotal, BigDecimal::add);
            }
        }
        return Cart.orderByPreferred(acc);
    }
}
