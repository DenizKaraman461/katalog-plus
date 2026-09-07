package com.denizcelikhalat.katalog.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Double price;

    private String description;

    private String imagePath;

    private String tableImagePath;  // Yeni alan eklendi

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    // ===== Ölçü / Fiyatlandırma =====

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_mode", nullable = false, length = 20)
    private MeasurementMode measurementMode = MeasurementMode.NONE;

    // CUSTOM modunda gösterilecek birim etiketi: metre, cm, kg ...
    @Column(name = "measurement_unit_label", length = 50)
    private String measurementUnitLabel;

    // PRESET modunda her satır "etiket | fiyat" formatında. Örn:
    // 1 metre | 100
    // 5 metre | 450
    @Column(name = "measurement_options_text", columnDefinition = "TEXT")
    private String measurementOptionsText;

    // ===== Stok & Yayın durumu =====

    // Metre/cm/kg gibi satılan ürünler olabildiği için BigDecimal.
    // NONE -> adet, CUSTOM -> toplam ölçü miktarı (örn. toplam metre), PRESET -> satılabilir adet.
    @Column(name = "stock_quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    // Yayında mı? false ise herkese açık sayfalarda görünmez/satılmaz (admin görebilir/düzenleyebilir).
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    // Satın alınabilir mi? (basit stok: sayısal takip yok). false -> "Stokta yok".
    @Column(name = "in_stock", nullable = false)
    private Boolean inStock = true;

    // ===== Para birimi =====
    // Ürün, resmi fiyat listesindeki kendi para biriminde satılır (dönüşüm yok). Varsayılan USD.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private PriceCurrency currency = PriceCurrency.USD;

    // ===== Kargo (AŞAMA 1: yalnızca veri modeli — kargo HESAPLAMASI burada yapılmaz) =====
    // Bir metre ürünün kilogram karşılığı (örn. 6mm çelik halat: 0.14, 10mm: 0.40).
    // Eski ürünler için NULL olabilir. Negatif değer kabul edilmez (bkz. ProductServiceImpl).
    @Column(name = "shipping_weight_per_meter", precision = 10, scale = 4)
    private BigDecimal shippingWeightPerMeter;

    // Bir ADEDİN kilogram karşılığı (örn. radansa: 0.05, soket: 0.12). Adet bazlı ürünler
    // için kullanılır (shippingWeightType = PER_UNIT). Eski ürünler için NULL olabilir.
    // Negatif değer kabul edilmez (bkz. ProductServiceImpl).
    @Column(name = "shipping_weight_per_unit", precision = 10, scale = 4)
    private BigDecimal shippingWeightPerUnit;

    // Kargo ağırlığı hangi birime göre hesaplanacak: PER_METER veya PER_UNIT.
    // NOT NULL, varsayılan PER_METER (geriye dönük uyumluluk — mevcut ürünlerin davranışı
    // değişmez, migration'da da tüm mevcut satırlar PER_METER ile doldurulur).
    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_weight_type", nullable = false, length = 20)
    private ShippingWeightType shippingWeightType = ShippingWeightType.PER_METER;

    // ===== Admin formu için TEK ağırlık inputu (DB'ye YAZILMAZ) =====
    // Formda "Kargo Ağırlığı (kg)" başlığı altında TEK bir sayı alanı gösterilir; hangi gerçek
    // alana (shippingWeightPerMeter / shippingWeightPerUnit) yazılacağı seçilen
    // shippingWeightType'a göre ProductServiceImpl.save()/update() içinde belirlenir.
    // Düzenleme formunu (GET /edit/{id}) önceden doldurmak için getter, mevcut tipe göre
    // ilgili GERÇEK alanın değerini döner (bkz. getShippingWeightValue()).
    @Transient
    private BigDecimal shippingWeightValue;

    public Product() {
    }

    public Product(Long id, String name, Double price, String description, String imagePath, String tableImagePath, Category category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.description = description;
        this.imagePath = imagePath;
        this.tableImagePath = tableImagePath;  // Constructor güncellendi
        this.category = category;
    }

    // ===== PRESET seçeneklerini ayrıştıran yardımcı (DB'ye yazılmaz) =====
    // Format: "etiket | fiyat" (mevcut, değişmedi) VEYA "etiket | fiyat | kg/m" (yeni, OPSİYONEL
    // üçüncü segment). Üçüncü segment yoksa/boşsa/geçersizse weightPerMeter null kalır — bu
    // satır ESKİDEN OLDUĞU GİBİ (geriye dönük uyumlu) davranır, ShippingService ürün-seviyesi
    // shippingWeightPerMeter/Unit'e düşer.
    @Transient
    public List<PresetOption> getMeasurementOptions() {
        List<PresetOption> options = new ArrayList<>();
        if (measurementOptionsText == null || measurementOptionsText.isBlank()) {
            return options;
        }
        for (String raw : measurementOptionsText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\|", -1); // -1: sondaki boş segmentleri de korur
            if (parts.length < 2) continue; // en az "etiket | fiyat" gerekli

            String label = parts[0].trim();
            String priceStr = parts[1].trim().replace(",", ".");
            if (label.isEmpty() || priceStr.isEmpty()) continue;

            BigDecimal price;
            try {
                price = new BigDecimal(priceStr);
            } catch (NumberFormatException ignored) {
                continue; // Hatalı fiyat satırını yok say (mevcut davranış)
            }

            // Opsiyonel 3. segment: kg/m. Yok/boş/geçersizse sessizce null bırakılır
            // (exception FIRLATILMAZ) — bu satır fiyat açısından yine geçerli sayılır.
            BigDecimal weightPerMeter = null;
            if (parts.length >= 3) {
                String weightStr = parts[2].trim().replace(",", ".");
                if (!weightStr.isEmpty()) {
                    try {
                        BigDecimal parsedWeight = new BigDecimal(weightStr);
                        if (parsedWeight.compareTo(BigDecimal.ZERO) > 0) {
                            weightPerMeter = parsedWeight;
                        }
                        // Negatif/sıfır ağırlık sessizce yok sayılır (null kalır) — admin panelde
                        // ayrıca bir hata göstermiyoruz çünkü bu, KG bilgisi olmayan geçerli bir
                        // satırdır (fallback zinciri zaten bunu güvenli şekilde ele alıyor).
                    } catch (NumberFormatException ignored) {
                        // Hatalı kg/m değerini yok say; fiyat yine de geçerli kalır.
                    }
                }
            }

            options.add(new PresetOption(label, price, weightPerMeter));
        }
        return options;
    }

    /** Şablon ve servis için basit (entity olmayan) seçenek taşıyıcı. */
    public static class PresetOption {
        private final String label;
        private final BigDecimal price;
        private final BigDecimal weightPerMeter; // opsiyonel; yoksa null (kg/m, seçenek başına)

        public PresetOption(String label, BigDecimal price) {
            this(label, price, null);
        }

        public PresetOption(String label, BigDecimal price, BigDecimal weightPerMeter) {
            this.label = label;
            this.price = price;
            this.weightPerMeter = weightPerMeter;
        }

        public String getLabel() {
            return label;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public BigDecimal getWeightPerMeter() {
            return weightPerMeter;
        }
    }

    // Getter & Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getTableImagePath() {
        return tableImagePath;
    }

    public void setTableImagePath(String tableImagePath) {
        this.tableImagePath = tableImagePath;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public MeasurementMode getMeasurementMode() {
        return measurementMode;
    }

    public void setMeasurementMode(MeasurementMode measurementMode) {
        this.measurementMode = measurementMode;
    }

    public String getMeasurementUnitLabel() {
        return measurementUnitLabel;
    }

    public void setMeasurementUnitLabel(String measurementUnitLabel) {
        this.measurementUnitLabel = measurementUnitLabel;
    }

    public String getMeasurementOptionsText() {
        return measurementOptionsText;
    }

    public void setMeasurementOptionsText(String measurementOptionsText) {
        this.measurementOptionsText = measurementOptionsText;
    }

    public BigDecimal getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(BigDecimal stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getInStock() {
        return inStock;
    }

    public void setInStock(Boolean inStock) {
        this.inStock = inStock;
    }

    public PriceCurrency getCurrency() {
        return currency;
    }

    public void setCurrency(PriceCurrency currency) {
        this.currency = currency;
    }

    public BigDecimal getShippingWeightPerMeter() {
        return shippingWeightPerMeter;
    }

    public void setShippingWeightPerMeter(BigDecimal shippingWeightPerMeter) {
        this.shippingWeightPerMeter = shippingWeightPerMeter;
    }

    public BigDecimal getShippingWeightPerUnit() {
        return shippingWeightPerUnit;
    }

    public void setShippingWeightPerUnit(BigDecimal shippingWeightPerUnit) {
        this.shippingWeightPerUnit = shippingWeightPerUnit;
    }

    public ShippingWeightType getShippingWeightType() {
        return shippingWeightType;
    }

    public void setShippingWeightType(ShippingWeightType shippingWeightType) {
        this.shippingWeightType = shippingWeightType;
    }

    /**
     * Admin formundaki TEK ağırlık inputu için taşıyıcı. Form submit edilmişse (POST) o anda
     * set edilmiş ham değeri döner. Henüz set edilmemişse (örn. GET /edit/{id} ile DB'den
     * yüklenen bir ürün) mevcut shippingWeightType'a göre GERÇEK alanlardan birini (
     * shippingWeightPerMeter veya shippingWeightPerUnit) geriye dönük doldurma amacıyla döner.
     */
    public BigDecimal getShippingWeightValue() {
        if (shippingWeightValue != null) {
            return shippingWeightValue;
        }
        return (shippingWeightType == ShippingWeightType.PER_UNIT) ? shippingWeightPerUnit : shippingWeightPerMeter;
    }

    public void setShippingWeightValue(BigDecimal shippingWeightValue) {
        this.shippingWeightValue = shippingWeightValue;
    }
}
