package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.CartItem;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.model.ShippingCalculationResult;
import com.denizcelikhalat.katalog.model.ShippingCategory;
import com.denizcelikhalat.katalog.model.ShippingWeightType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * AŞAMA 2: Sepetin toplam kargo AĞIRLIĞINI hesaplayan altyapı.
 * AŞAMA 3B: Ağırlığa göre lojistik KATEGORİ (ShippingCategory) belirleme eklendi.
 *
 * Bu serviste/kapsamda KESİNLİKLE YOK: kargo ÜCRETİ hesaplama, checkout/Order/iyzico/mail
 * entegrasyonu. Bunlar sonraki bir aşamada, bu servisin sonucunu KULLANARAK eklenecektir.
 *
 * Ağırlık formülü (kalem başına) — öncelik sırasıyla:
 * 1) SEÇENEK-SEVİYESİ (varsa): measurement_options_text'teki opsiyonel 3. segment
 *    ("etiket | fiyat | kg/m") — 6x36 WS gibi ölçüye göre kg/m değişen ürünler için.
 * 2) ÜRÜN-SEVİYESİ (yoksa, mevcut/varsayılan davranış):
 *    - PER_METER: product.shippingWeightPerMeter * cartItem.measurementAmount * cartItem.quantity.
 *    - PER_UNIT: product.shippingWeightPerUnit * cartItem.quantity.
 * (quantity null ise 1 kabul edilir.)
 *
 * MeasurementMode uyumu: measurementAmount, CartItem'da yalnızca METRE bazlı satılan
 * ürünlerde (CUSTOM / PRESET_AMOUNT modları — bkz. CartService) dolu olur; NONE/PRESET
 * modlarında (adet veya sabit paket bazlı satış) null'dur. Bu durumda o kalemin metre
 * karşılığı bilinmediğinden ağırlığı GÜVENİLİR şekilde hesaplanamaz — quantity (adet)
 * alanını metre yerine kullanmak yanlış bir varsayım olacağından, böyle bir kalem
 * "hesaplanamadı / manuel inceleme gerekir" olarak işaretlenir; sistemi asla patlatmaz.
 */
@Service
public class ShippingService {

    // AŞAMA 3B: sabit konfigürasyon (application.properties). Kargo ÜCRETİ değil, yalnızca
    // hangi eşiğin altında/üstünde kalındığına göre lojistik SINIFLANDIRMA için kullanılır.
    // NOT: Alan başlatıcıları (=new BigDecimal(...)), Spring dışında (örn. birim testte
    // "new ShippingService()" ile) de @Value'nin SpEL varsayılanıyla AYNI değere sahip
    // olunmasını sağlar; Spring bağlamında normal şekilde application.properties'ten
    // enjekte edilerek bu değerlerin üzerine yazılır.
    @Value("${app.shipping.standard-max-weight:50}")
    private BigDecimal standardMaxWeight = new BigDecimal("50");

    @Value("${app.shipping.heavy-max-weight:300}")
    private BigDecimal heavyMaxWeight = new BigDecimal("300");

    /**
     * Bir sepetin toplam ağırlığını (kg) ve buna göre lojistik kategorisini hesaplar.
     *
     * Null güvenlidir: cart null olabilir, cart.getItems() null/boş olabilir, bir kalemin
     * product'ı, shippingWeightPerMeter'ı veya measurementAmount'ı null olabilir — hiçbiri
     * exception fırlatmaz.
     *
     * Sepetteki TÜM kalemlerin ağırlığı hesaplanabildiyse weightCalculated=true döner ve
     * kategori toplam ağırlığa göre (STANDARD/HEAVY_CARGO/MANUAL_REVIEW) belirlenir.
     * En az bir kalem hesaplanamadıysa weightCalculated=false, requiresManualReview=true VE
     * category=MANUAL_REVIEW döner (güvenilir bir ağırlık olmadan sınıflandırma yapılmaz).
     */
    public ShippingCalculationResult calculateCartWeight(Cart cart) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        boolean allItemsResolved = true;

        List<CartItem> items = (cart != null) ? cart.getItems() : null;
        if (items != null) {
            for (CartItem item : items) {
                if (item == null) {
                    continue;
                }

                BigDecimal itemWeight = calculateItemWeight(item);
                if (itemWeight != null) {
                    totalWeight = totalWeight.add(itemWeight);
                } else {
                    allItemsResolved = false;
                }
            }
        }

        totalWeight = totalWeight.setScale(2, RoundingMode.HALF_UP);
        boolean requiresManualReview = !allItemsResolved;

        ShippingCategory category = determineCategory(totalWeight, allItemsResolved);
        String categoryMessage = categoryMessage(category);

        return new ShippingCalculationResult(totalWeight, allItemsResolved, requiresManualReview,
                category, categoryMessage);
    }

    /**
     * Tek bir sepet kaleminin ağırlığını hesaplar. Öncelik sırası:
     *
     * 1) SEÇENEK-SEVİYESİ ağırlık (YENİ — 6x36 WS gibi, ölçüye göre kg/m değişen ürünler için):
     *    item.getSelectedMeasurement() doluysa ve product.getMeasurementOptions() içinde bu
     *    etikete karşılık gelen bir seçenek varsa VE o seçeneğin weightPerMeter'ı doluysa,
     *    bu değer kullanılır: weightPerMeter x (measurementAmount varsa onu, yoksa 1) x quantity.
     *    Eşleştirme, CartService'in fiyat için kullandığı AYNI opt.getLabel().equals(...) deseniyle
     *    yapılır. Eşleşme yoksa veya o seçeneğin ağırlığı tanımlı değilse (2)'ye düşülür.
     *
     * 2) ÜRÜN-SEVİYESİ ağırlık (MEVCUT, DEĞİŞMEDİ) — shippingWeightType'a göre:
     *    PER_METER: weightPerMeter x measurementAmount x quantity.
     *    PER_UNIT:  weightPerUnit x quantity.
     *
     * Hiçbiri hesaplanamıyorsa (gerekli veri eksikse) null döner (exception FIRLATMAZ).
     *
     * DÜZELTME (PER_METER/seçenek-seviyesi için, önceki turdan korunuyor): CUSTOM/PRESET_AMOUNT
     * modlarında aynı ürün + aynı ölçü tekrar sepete eklenirse CartService measurementAmount'ı
     * DEĞİL, quantity'yi artırır (örn. "100 metre" iki kez eklenirse: measurementAmount=100,
     * quantity=2 -> gerçekte 200 metre). Bu yüzden quantity çarpanı HER ZAMAN dahil edilir.
     * quantity null ise 1 kabul edilir.
     */
    private BigDecimal calculateItemWeight(CartItem item) {
        Product product = item.getProduct();
        if (product == null) {
            return null;
        }

        BigDecimal quantity = (item.getQuantity() != null)
                ? BigDecimal.valueOf(item.getQuantity())
                : BigDecimal.ONE;

        // ---- 1) Seçenek-seviyesi ağırlık (varsa öncelikli) ----
        BigDecimal optionWeight = resolveOptionLevelWeight(product, item, quantity);
        if (optionWeight != null) {
            return optionWeight;
        }

        // ---- 2) Ürün-seviyesi ağırlık (mevcut mantık, BİREBİR korunuyor) ----
        ShippingWeightType type = (product.getShippingWeightType() != null)
                ? product.getShippingWeightType() : ShippingWeightType.PER_METER;

        if (type == ShippingWeightType.PER_UNIT) {
            BigDecimal weightPerUnit = product.getShippingWeightPerUnit();
            if (weightPerUnit == null) {
                return null;
            }
            return weightPerUnit.multiply(quantity);
        }

        // PER_METER (varsayılan) — mevcut mantık BİREBİR korunuyor.
        BigDecimal weightPerMeter = product.getShippingWeightPerMeter();
        if (weightPerMeter == null) {
            return null;
        }

        BigDecimal amount = item.getMeasurementAmount();
        if (amount == null) {
            return null;
        }

        return weightPerMeter.multiply(amount).multiply(quantity);
    }

    /**
     * SEÇENEK-SEVİYESİ ağırlığı çözer (6x36 WS gibi ölçüye göre kg/m değişen ürünler için).
     * item.getSelectedMeasurement() boşsa, product null'sa, veya eşleşen seçenek/ağırlık yoksa
     * null döner — bu durumda calculateItemWeight() ürün-seviyesi mantığa (mevcut, değişmeyen)
     * düşer. Exception FIRLATMAZ.
     *
     * Eşleştirme, CartService'in PRESET/PRESET_AMOUNT dallarında fiyat için kullandığı AYNI
     * "opt.getLabel().equals(...)" deseniyle yapılır — tutarlılık için.
     *
     * measurementAmount varsa (PRESET_AMOUNT — müşteri metraj girmiş): weightPerMeter GERÇEK
     * metre başına ağırlık olarak yorumlanır -> weightPerMeter x measurementAmount x quantity.
     * measurementAmount yoksa (PRESET — sabit paket, örn. "5 metre" bundle): weightPerMeter,
     * o SABİT paketin TOPLAM ağırlığı olarak yorumlanır -> weightPerMeter x quantity
     * (kaç paket alındığı).
     */
    private BigDecimal resolveOptionLevelWeight(Product product, CartItem item, BigDecimal quantity) {
        String selected = item.getSelectedMeasurement();
        if (selected == null || selected.isBlank()) {
            return null;
        }

        String wanted = selected.trim();
        BigDecimal optionWeightPerMeter = null;
        for (Product.PresetOption opt : product.getMeasurementOptions()) {
            if (opt.getLabel().equals(wanted)) {
                optionWeightPerMeter = opt.getWeightPerMeter();
                break;
            }
        }
        if (optionWeightPerMeter == null) {
            return null;
        }

        BigDecimal amount = (item.getMeasurementAmount() != null) ? item.getMeasurementAmount() : BigDecimal.ONE;
        return optionWeightPerMeter.multiply(amount).multiply(quantity);
    }

    /**
     * Toplam ağırlığa göre lojistik kategoriyi belirler.
     * - Ağırlık güvenilir şekilde hesaplanamadıysa (allItemsResolved=false) HER ZAMAN
     *   MANUAL_REVIEW döner (eşik değerlendirmesi güvenilir olmayan bir sayı üzerinden yapılmaz).
     * - 0 - standardMaxWeight (dahil)  -> STANDARD
     * - standardMaxWeight - heavyMaxWeight (dahil) -> HEAVY_CARGO
     * - heavyMaxWeight üzeri -> MANUAL_REVIEW
     */
    private ShippingCategory determineCategory(BigDecimal totalWeight, boolean allItemsResolved) {
        if (!allItemsResolved) {
            return ShippingCategory.MANUAL_REVIEW;
        }
        if (totalWeight.compareTo(standardMaxWeight) <= 0) {
            return ShippingCategory.STANDARD;
        }
        if (totalWeight.compareTo(heavyMaxWeight) <= 0) {
            return ShippingCategory.HEAVY_CARGO;
        }
        return ShippingCategory.MANUAL_REVIEW;
    }

    private String categoryMessage(ShippingCategory category) {
        switch (category) {
            case STANDARD:
                return "Standart Kargo";
            case HEAVY_CARGO:
                return "Ağır Kargo";
            case MANUAL_REVIEW:
            default:
                return "Manuel İnceleme Gerekli";
        }
    }
}

