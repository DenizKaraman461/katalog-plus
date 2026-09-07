package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.CartItem;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.model.ShippingCalculationResult;
import com.denizcelikhalat.katalog.model.ShippingCategory;
import com.denizcelikhalat.katalog.model.ShippingWeightType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AŞAMA 2: Ağırlık hesaplama + AŞAMA 3B: lojistik kategori (ShippingCategory) birim testleri.
 * Saf POJO'larla çalışır (DB/Spring context gerekmez) — ShippingService'teki standardMaxWeight/
 * heavyMaxWeight alanları @Value varsayılanlarıyla AYNI Java alan başlatıcılarına sahip
 * olduğundan (50 / 300), Spring olmadan "new ShippingService()" ile de doğru çalışır.
 */
class ShippingServiceTest {

    private final ShippingService shippingService = new ShippingService();

    // ---- Yardımcılar ----

    // PER_METER ürün (varsayılan tip — Product.shippingWeightType Java initializer'ı zaten
    // PER_METER olduğundan bu yardımcı hiçbir şeyi değiştirmez, mevcut testler AYNEN çalışır).
    private Product buildProduct(BigDecimal shippingWeightPerMeter) {
        Product product = new Product();
        product.setShippingWeightPerMeter(shippingWeightPerMeter);
        return product;
    }

    // YENİ: PER_UNIT ürün (radansa/soket/spanzet gibi adet bazlı ürünler).
    private Product buildUnitProduct(BigDecimal shippingWeightPerUnit) {
        Product product = new Product();
        product.setShippingWeightType(ShippingWeightType.PER_UNIT);
        product.setShippingWeightPerUnit(shippingWeightPerUnit);
        return product;
    }

    // YENİ: 6x36 WS gibi, ölçüye göre kg/m değişen ürünler için — measurementOptionsText
    // "etiket | fiyat | kg/m" (opsiyonel 3. segment) formatında.
    private Product buildOptionProduct(String measurementOptionsText) {
        Product product = new Product();
        product.setMeasurementOptionsText(measurementOptionsText);
        return product;
    }

    private CartItem buildItem(Product product, BigDecimal measurementAmount) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setMeasurementAmount(measurementAmount);
        return item;
    }

    // quantity belirtilen overload (yeni testte kullanılır; mevcut testler eski overload'ı
    // kullanmaya devam eder, quantity=null -> davranış değişmez).
    private CartItem buildItem(Product product, BigDecimal measurementAmount, Integer quantity) {
        CartItem item = buildItem(product, measurementAmount);
        item.setQuantity(quantity);
        return item;
    }

    private Cart buildCart(CartItem... items) {
        Cart cart = new Cart();
        cart.setItems(List.of(items));
        return cart;
    }

    // ---- Testler ----

    /**
     * Test 1: 6mm halat, 0.1400 kg/m, 100 metre -> 14 kg -> STANDARD.
     */
    @Test
    void tekUrunAgirligiDogruHesaplanmali() {
        Product halat6mm = buildProduct(new BigDecimal("0.1400"));
        CartItem item = buildItem(halat6mm, new BigDecimal("100"));
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("14.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }

    /**
     * YENİ TEST (düzeltme sonrası): 6mm halat, 0.14 kg/m, 100 metre, quantity=2
     * -> 100 x 0.14 x 2 = 28 kg. CUSTOM/PRESET_AMOUNT'ta aynı ölçü tekrar sepete eklenince
     * CartService measurementAmount'ı değil quantity'yi artırdığı için bu çarpan ZORUNLUDUR.
     */
    @Test
    void ayniOlcuIkinciKezEkleninceQuantityAgirligaDahilEdilmeli() {
        Product halat6mm = buildProduct(new BigDecimal("0.14"));
        CartItem item = buildItem(halat6mm, new BigDecimal("100"), 2);
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("28.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }

    /**
     * Test 2: 6mm (100m x 0.14 = 14kg) + 10mm (50m x 0.40 = 20kg) = 34 kg.
     */
    @Test
    void birdenFazlaUrununAgirligiToplanmali() {
        Product halat6mm = buildProduct(new BigDecimal("0.1400"));
        Product halat10mm = buildProduct(new BigDecimal("0.4000"));

        CartItem item1 = buildItem(halat6mm, new BigDecimal("100"));
        CartItem item2 = buildItem(halat10mm, new BigDecimal("50"));
        Cart cart = buildCart(item1, item2);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("34.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }

    /**
     * Yeni Test: 200 metre x 0.5 kg/m = 100 kg -> HEAVY_CARGO
     * (standard-max-weight=50 ile heavy-max-weight=300 arasında).
     */
    @Test
    void agirUrunHeavyCargoOlarakSiniflandirilmali() {
        Product product = buildProduct(new BigDecimal("0.5000"));
        CartItem item = buildItem(product, new BigDecimal("200"));
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.HEAVY_CARGO, result.getCategory());
    }

    /**
     * Yeni Test: 1000 metre x 0.5 kg/m = 500 kg -> MANUAL_REVIEW
     * (heavy-max-weight=300'ü aştığı için). Bu durumda requiresManualReview YİNE DE false
     * kalır (veri eksik değil, ağırlık güvenilir şekilde hesaplandı) — ama category
     * MANUAL_REVIEW'dır (eşik aşıldığı için otomatik fiyatlandırma yapılmamalı).
     */
    @Test
    void esikUstuAgirlikManualReviewOlarakSiniflandirilmali() {
        Product product = buildProduct(new BigDecimal("0.5000"));
        CartItem item = buildItem(product, new BigDecimal("1000"));
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.MANUAL_REVIEW, result.getCategory());
    }

    /**
     * Test 3: shippingWeightPerMeter null olan bir ürün -> weightCalculated=false,
     * requiresManualReview=true. Sistem PATLAMAMALI (exception fırlatılmamalı).
     */
    @Test
    void shippingWeightPerMeterNullIseManuelIncelemeIsaretlenmeli() {
        Product weightBilgisiOlmayanUrun = buildProduct(null);
        CartItem item = buildItem(weightBilgisiOlmayanUrun, new BigDecimal("25"));
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertFalse(result.isWeightCalculated());
        assertTrue(result.isRequiresManualReview());
        assertEquals(ShippingCategory.MANUAL_REVIEW, result.getCategory());
    }

    /**
     * Ek test: measurementAmount null olan bir kalem (örn. NONE/PRESET modunda adet bazlı
     * satılan ürün) -> sistem PATLAMAMALI; bu kalem de manuel inceleme gerektirir sayılır
     * (quantity/adet, metre yerine kullanılmaz) -> category MANUAL_REVIEW.
     */
    @Test
    void olcuMiktariNullIseManuelIncelemeIsaretlenmeliVePatlamamali() {
        Product product = buildProduct(new BigDecimal("0.1400"));
        CartItem item = buildItem(product, null); // örn. NONE modunda measurementAmount hiç set edilmez
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertFalse(result.isWeightCalculated());
        assertTrue(result.isRequiresManualReview());
        assertEquals(ShippingCategory.MANUAL_REVIEW, result.getCategory());
    }

    /**
     * Ek test: sepet null -> exception fırlatılmamalı, boş/başarılı sonuç dönmeli
     * (hesaplanamayan kalem yok, dolayısıyla manuel inceleme de gerekmez).
     */
    @Test
    void sepetNullIsePatlamamaliVeSifirAgirlikDonmeli() {
        ShippingCalculationResult result = shippingService.calculateCartWeight(null);

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory()); // 0 kg <= standard-max-weight
    }

    /**
     * Ek test: sepet boş (kalem yok) -> 0 kg, weightCalculated=true, requiresManualReview=false,
     * category=STANDARD.
     */
    @Test
    void bosSepetSifirAgirlikDonmeli() {
        Cart cart = buildCart(); // hiç kalem yok

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }

    // ===================== YENİ: PER_UNIT (adet bazlı ürün) testleri =====================

    /**
     * PER_UNIT: radansa benzeri bir ürün, 0.05 kg/adet, 1 adet (measurementAmount YOK —
     * NONE/PRESET modu gibi) -> 0.05 kg, STANDARD. Daha önce bu senaryo (measurementAmount
     * null olduğu için) MANUAL_REVIEW'a düşüyordu; artık PER_UNIT formülüyle hesaplanabiliyor.
     */
    @Test
    void perUnitUrunTekAdetDogruHesaplanmali() {
        Product radansa = buildUnitProduct(new BigDecimal("0.05"));
        CartItem item = buildItem(radansa, null); // measurementAmount YOK, PER_UNIT'te gerekmiyor
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("0.05").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }

    /**
     * PER_UNIT: aynı ürün 10 adet -> weightPerUnit x quantity = 0.05 x 10 = 0.5 kg.
     */
    @Test
    void perUnitUrunQuantityCarpaniDogruUygulanmali() {
        Product soket = buildUnitProduct(new BigDecimal("0.05"));
        CartItem item = buildItem(soket, null, 10);
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("0.50").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
    }

    /**
     * PER_UNIT ama shippingWeightPerUnit null (admin doldurmamış) -> MANUAL_REVIEW,
     * sistem PATLAMAMALI.
     */
    @Test
    void perUnitShippingWeightPerUnitNullIseManuelIncelemeIsaretlenmeli() {
        Product spanzet = buildUnitProduct(null);
        CartItem item = buildItem(spanzet, null, 3);
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertFalse(result.isWeightCalculated());
        assertTrue(result.isRequiresManualReview());
        assertEquals(ShippingCategory.MANUAL_REVIEW, result.getCategory());
    }

    /**
     * Karışık sepet: bir PER_METER kalemi (6mm halat, 100m x 0.14 = 14 kg) + bir PER_UNIT
     * kalemi (radansa, 5 adet x 0.05 = 0.25 kg) -> toplam 14.25 kg. İki farklı tipin AYNI
     * sepette doğru şekilde toplanabildiğini doğrular.
     */
    @Test
    void karisikSepettePerMeterVePerUnitDogruToplanmali() {
        Product halat = buildProduct(new BigDecimal("0.1400")); // PER_METER (varsayılan)
        CartItem halatItem = buildItem(halat, new BigDecimal("100"));

        Product radansa = buildUnitProduct(new BigDecimal("0.05")); // PER_UNIT
        CartItem radansaItem = buildItem(radansa, null, 5);

        Cart cart = buildCart(halatItem, radansaItem);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("14.25").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
    }

    // ===================== YENİ: Seçenek-seviyesi ağırlık (6x36 WS) testleri =====================

    /**
     * 6x36 WS örneği: "16 mm K.Öz" seçildi, 3 metre alındı. Seçeneğin kendi kg/m'si (0.98)
     * ürün-seviyesi shippingWeightPerMeter'dan (hiç set edilmemiş, null) ÖNCELİKLİDİR.
     * Beklenen: 0.98 x 3 metre x 1 adet = 2.94 kg.
     */
    @Test
    void secenekSeviyesiAgirlikOncelikliKullanilmali() {
        Product halat = buildOptionProduct("16 mm K.Öz | 7.00 | 0.98\n16 mm Ç.Öz | 7.60 | 1.05");
        CartItem item = buildItem(halat, new BigDecimal("3"));
        item.setSelectedMeasurement("16 mm K.Öz");

        Cart cart = buildCart(item);
        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("2.94").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }

    /**
     * Aynı ürün, FARKLI bir seçenek ("32 mm Ç.Öz") seçilirse FARKLI bir kg/m kullanılmalı —
     * seçenek-seviyesi ağırlığın gerçekten seçime göre değiştiğini kanıtlar.
     */
    @Test
    void farkliSecenekFarkliAgirlikVermeli() {
        Product halat = buildOptionProduct("16 mm K.Öz | 7.00 | 0.98\n32 mm Ç.Öz | 28.10 | 4.20");
        CartItem item = buildItem(halat, new BigDecimal("2"));
        item.setSelectedMeasurement("32 mm Ç.Öz");

        Cart cart = buildCart(item);
        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        // 4.20 x 2 metre x 1 adet = 8.40 kg
        assertEquals(0, new BigDecimal("8.40").compareTo(result.getTotalWeight()));
    }

    /**
     * GERİYE DÖNÜK UYUMLULUK: 3. segment (kg/m) OLMAYAN eski format satırları hâlâ ürün-seviyesi
     * shippingWeightPerMeter'a düşmeli (fallback), sistem PATLAMAMALI. Bu, mevcut ürünlerin
     * (henüz kg/m eklenmemiş) davranışının DEĞİŞMEDİĞİNİ kanıtlar.
     */
    @Test
    void ucuncuSegmentYoksaUrunSeviyesineDusulmeli() {
        Product halat = buildOptionProduct("16 mm K.Öz | 7.00"); // eski format, kg/m YOK
        halat.setShippingWeightPerMeter(new BigDecimal("0.50")); // ürün-seviyesi fallback değeri
        CartItem item = buildItem(halat, new BigDecimal("4"));
        item.setSelectedMeasurement("16 mm K.Öz");

        Cart cart = buildCart(item);
        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        // Seçenek-seviyesi kg/m yok -> ürün-seviyesine düşüldü: 0.50 x 4 = 2.00 kg
        assertEquals(0, new BigDecimal("2.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
    }

    /**
     * Seçilen etiket, measurement_options_text'teki HİÇBİR seçenekle eşleşmiyorsa (örn. ürün
     * sonradan düzenlenip etiket değişmiş) -> ürün-seviyesine düşülür; o da yoksa MANUAL_REVIEW.
     * Sistem PATLAMAMALI (exception fırlatılmamalı).
     */
    @Test
    void esleseniyorEtiketYoksaGuvenliSekildeDusulmeliVePatlamamali() {
        Product halat = buildOptionProduct("16 mm K.Öz | 7.00 | 0.98");
        // hiçbir ürün-seviyesi kg/m de yok -> tamamen hesaplanamaz olmalı
        CartItem item = buildItem(halat, new BigDecimal("3"));
        item.setSelectedMeasurement("EŞLEŞMEYEN ETİKET");

        Cart cart = buildCart(item);
        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertFalse(result.isWeightCalculated());
        assertTrue(result.isRequiresManualReview());
        assertEquals(ShippingCategory.MANUAL_REVIEW, result.getCategory());
    }

    /**
     * PRESET modu (measurementAmount YOK — sabit paket): seçeneğin kg/m'si "paketin toplam
     * ağırlığı" olarak yorumlanır -> weightPerMeter x quantity (paket adedi).
     * Örnek: "5 metre" paketi, paket başına 0.70 kg, 3 paket alındı -> 0.70 x 3 = 2.10 kg.
     */
    @Test
    void presetModundaSabitPaketAgirligiQuantityIleCarpilmali() {
        Product urun = buildOptionProduct("5 metre | 450 | 0.70");
        CartItem item = buildItem(urun, null, 3); // measurementAmount YOK (PRESET), quantity=3
        item.setSelectedMeasurement("5 metre");

        Cart cart = buildCart(item);
        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("2.10").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
    }

    /**
     * Duplicate etiket: aynı etiket iki kez tanımlanmışsa (admin hatası), İLK eşleşen kullanılır
     * — CartService'in fiyat için kullandığı davranışla TUTARLI (o da ilk eşleşmede durur).
     */
    @Test
    void duplicateEtikettelkEslesenKullanilmali() {
        Product urun = buildOptionProduct("16 mm K.Öz | 7.00 | 0.98\n16 mm K.Öz | 7.50 | 1.20");
        CartItem item = buildItem(urun, new BigDecimal("1"));
        item.setSelectedMeasurement("16 mm K.Öz");

        Cart cart = buildCart(item);
        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("0.98").compareTo(result.getTotalWeight())); // ilk satır (0.98), ikinci (1.20) değil
    }

    /**
     * selectedMeasurement boş/null olan bir kalem (örn. CUSTOM modu, hiç seçenek yok) ->
     * seçenek-seviyesi arama devreye HİÇ girmemeli, doğrudan ürün-seviyesine geçmeli.
     */
    @Test
    void selectedMeasurementYoksaSecenekAramasiAtlanmali() {
        Product halat = buildProduct(new BigDecimal("0.1400")); // CUSTOM tarzı, saf ürün-seviyesi
        CartItem item = buildItem(halat, new BigDecimal("50")); // selectedMeasurement set edilmedi (null)

        Cart cart = buildCart(item);
        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("7.00").compareTo(result.getTotalWeight())); // 0.14 x 50 = 7.00
    }
}
