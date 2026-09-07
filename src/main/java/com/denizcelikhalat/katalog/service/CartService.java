package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.CartItem;
import com.denizcelikhalat.katalog.model.MeasurementMode;
import com.denizcelikhalat.katalog.model.PriceCurrency;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.model.User;
import com.denizcelikhalat.katalog.repository.CartRepository;
import com.denizcelikhalat.katalog.repository.ProductRepository;
import com.denizcelikhalat.katalog.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository,
                       UserRepository userRepository,
                       ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    // Verilen e-postaya ait sepeti döndürür; (örn. eski admin gibi) sepeti yoksa oluşturur.
    @Transactional
    public Cart getCartByEmail(String email) {
        return getOrCreateCart(email);
    }

    /**
     * Sepete ürün ekler. Ürünün ölçü moduna göre birim fiyatı hesaplar ve snapshot olarak yazar.
     * Basit stok: yalnızca active ve inStock kontrol edilir (sayısal stok takibi YOK).
     * Aynı ürün + aynı ölçü varsa miktarı artırır; farklı ölçü ise ayrı kalem açar.
     */
    @Transactional
    public void addProductToCart(String email, Long productId,
                                 String selectedMeasurement, BigDecimal measurementAmount) {
        Cart cart = getOrCreateCart(email);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı: " + productId));

        // Yayın durumu
        if (Boolean.FALSE.equals(product.getActive())) {
            throw new IllegalArgumentException("Bu ürün şu anda satışta değil.");
        }
        // Basit stok durumu
        if (Boolean.FALSE.equals(product.getInStock())) {
            throw new IllegalArgumentException("Bu ürün stokta yok.");
        }

        // ===== Para birimi snapshot'ı (karışık para birimli sepete izin verilir; dönüşüm/kısıt YOK) =====
        PriceCurrency productCurrency = (product.getCurrency() != null)
                ? product.getCurrency() : PriceCurrency.USD;

        MeasurementMode mode = (product.getMeasurementMode() != null)
                ? product.getMeasurementMode() : MeasurementMode.NONE;

        String resolvedMeasurement;
        BigDecimal resolvedAmount;
        BigDecimal unitPrice;

        switch (mode) {
            case CUSTOM:
                if (measurementAmount == null || measurementAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Lütfen 0'dan büyük bir ölçü değeri girin.");
                }
                if (product.getPrice() == null) {
                    throw new IllegalArgumentException("Bu ürün için birim fiyat tanımlı değil.");
                }
                resolvedAmount = measurementAmount;
                String unitLabel = (product.getMeasurementUnitLabel() != null)
                        ? product.getMeasurementUnitLabel() : "";
                resolvedMeasurement = (measurementAmount.stripTrailingZeros().toPlainString()
                        + " " + unitLabel).trim();
                unitPrice = BigDecimal.valueOf(product.getPrice()).multiply(measurementAmount);
                break;

            case PRESET:
                if (selectedMeasurement == null || selectedMeasurement.isBlank()) {
                    throw new IllegalArgumentException("Lütfen bir ölçü seçeneği seçin.");
                }
                String wanted = selectedMeasurement.trim();
                BigDecimal presetPrice = null;
                for (Product.PresetOption opt : product.getMeasurementOptions()) {
                    if (opt.getLabel().equals(wanted)) {
                        presetPrice = opt.getPrice();
                        break;
                    }
                }
                if (presetPrice == null) {
                    throw new IllegalArgumentException("Geçersiz ölçü seçeneği: " + selectedMeasurement);
                }
                resolvedMeasurement = wanted;
                resolvedAmount = null;
                unitPrice = presetPrice;
                break;

            case PRESET_AMOUNT:
                // Müşteri hem seçenek seçer hem miktar girer: birim fiyat = seçenek fiyatı * miktar.
                if (selectedMeasurement == null || selectedMeasurement.isBlank()) {
                    throw new IllegalArgumentException("Lütfen bir ölçü seçeneği seçin.");
                }
                if (measurementAmount == null || measurementAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Lütfen 0'dan büyük bir miktar/metraj girin.");
                }
                String wantedPa = selectedMeasurement.trim();
                BigDecimal optionUnitPrice = null;
                for (Product.PresetOption opt : product.getMeasurementOptions()) {
                    if (opt.getLabel().equals(wantedPa)) {
                        optionUnitPrice = opt.getPrice();
                        break;
                    }
                }
                if (optionUnitPrice == null) {
                    throw new IllegalArgumentException("Geçersiz ölçü seçeneği: " + selectedMeasurement);
                }
                resolvedMeasurement = wantedPa;            // etiket korunur
                resolvedAmount = measurementAmount;        // girilen miktar saklanır
                unitPrice = optionUnitPrice.multiply(measurementAmount);
                break;

            case NONE:
            default:
                if (product.getPrice() == null) {
                    throw new IllegalArgumentException("Bu ürün için fiyat tanımlı değil.");
                }
                resolvedMeasurement = null;
                resolvedAmount = null;
                unitPrice = BigDecimal.valueOf(product.getPrice());
                break;
        }

        unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);

        // Aynı ürün + aynı ölçü + aynı miktar -> adet artır; farklı ölçü/miktar -> yeni kalem
        CartItem existing = findItem(cart, productId, resolvedMeasurement, resolvedAmount);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + 1);
            if (existing.getCurrencySnapshot() == null) {
                existing.setCurrencySnapshot(productCurrency);
            }
        } else {
            CartItem newItem = new CartItem(product, 1, resolvedMeasurement, resolvedAmount, unitPrice);
            newItem.setCurrencySnapshot(productCurrency);
            cart.addItem(newItem); // ilişkinin iki tarafını da senkronlar
        }

        cartRepository.save(cart);
    }

    /**
     * Sepetten ilgili ürünün (ve varsa belirtilen ölçü + miktarın) kalemini tamamen çıkarır.
     * PRESET_AMOUNT'ta aynı seçenek farklı miktarlarda ayrı kalem olabildiği için miktar da kullanılır.
     */
    @Transactional
    public void removeProductFromCart(String email, Long productId,
                                      String selectedMeasurement, BigDecimal measurementAmount) {
        Cart cart = getOrCreateCart(email);

        CartItem target = findItem(cart, productId, normalize(selectedMeasurement), measurementAmount);
        if (target == null) {
            target = firstByProductId(cart, productId);
        }
        if (target != null) {
            cart.removeItem(target);
            cartRepository.save(cart);
        }
    }

    // ---- Yardımcı (private) metotlar ----

    private Cart getOrCreateCart(String email) {
        return cartRepository.findByUserEmailWithItems(email)
                .orElseGet(() -> {
                    User user = userRepository.findByEmail(email)
                            .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + email));
                    return cartRepository.save(new Cart(user));
                });
    }

    // Ürün + ölçü + miktar eşleşmesi (NONE'da ölçü/miktar null'dır).
    private CartItem findItem(Cart cart, Long productId, String selectedMeasurement, BigDecimal measurementAmount) {
        String wanted = normalize(selectedMeasurement);
        for (CartItem item : cart.getItems()) {
            if (item.getProduct() != null
                    && item.getProduct().getId().equals(productId)
                    && Objects.equals(normalize(item.getSelectedMeasurement()), wanted)
                    && amountEquals(item.getMeasurementAmount(), measurementAmount)) {
                return item;
            }
        }
        return null;
    }

    // BigDecimal'leri ölçek farkından etkilenmeden karşılaştırır (15 == 15.00).
    private static boolean amountEquals(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    private CartItem firstByProductId(Cart cart, Long productId) {
        for (CartItem item : cart.getItems()) {
            if (item.getProduct() != null && item.getProduct().getId().equals(productId)) {
                return item;
            }
        }
        return null;
    }

    private static String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
