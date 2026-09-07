package com.denizcelikhalat.katalog.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sepetin sahibi olan kullanıcı. FK (user_id) bu tabloda tutulur.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // Sepet silinince içindeki kalemler de silinir (cascade + orphanRemoval).
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItem> items = new ArrayList<>();

    public Cart() {
    }

    public Cart(Long id, User user, List<CartItem> items) {
        this.id = id;
        this.user = user;
        this.items = items;
    }

    public Cart(User user) {
        this.user = user;
    }

    // İlişkinin iki tarafını da senkron tutmak için yardımcı metotlar
    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        item.setCart(null);
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

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }

    /**
     * Sepet toplamlarını para birimine göre gruplar (dönüşüm YOK, farklı para birimleri TOPLANMAZ).
     * Gösterim sırası sabit olsun diye LinkedHashMap; tercih sırası EUR, USD, TRY.
     * Her kalem: currency = item.currencySnapshot ?: item.product.currency ?: USD,
     *            lineTotal = (unitPriceSnapshot ?: product.price ?: 0) * (quantity ?: 0).
     */
    @Transient
    public Map<PriceCurrency, BigDecimal> getTotalsByCurrency() {
        Map<PriceCurrency, BigDecimal> acc = new LinkedHashMap<>();
        if (items != null) {
            for (CartItem item : items) {
                if (item == null) continue;

                PriceCurrency currency = item.getCurrencySnapshot();
                if (currency == null && item.getProduct() != null) {
                    currency = item.getProduct().getCurrency();
                }
                if (currency == null) currency = PriceCurrency.USD;

                BigDecimal unitPrice = item.getUnitPriceSnapshot();
                if (unitPrice == null) {
                    unitPrice = (item.getProduct() != null && item.getProduct().getPrice() != null)
                            ? BigDecimal.valueOf(item.getProduct().getPrice()) : BigDecimal.ZERO;
                }

                int quantity = (item.getQuantity() != null) ? item.getQuantity() : 0;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

                acc.merge(currency, lineTotal, BigDecimal::add);
            }
        }
        return orderByPreferred(acc);
    }

    // EUR, USD, TRY tercih sırasıyla yeniden sıralar; listede olmayan para birimi atlanır.
    public static Map<PriceCurrency, BigDecimal> orderByPreferred(Map<PriceCurrency, BigDecimal> acc) {
        Map<PriceCurrency, BigDecimal> ordered = new LinkedHashMap<>();
        for (PriceCurrency c : new PriceCurrency[]{PriceCurrency.EUR, PriceCurrency.USD, PriceCurrency.TRY}) {
            if (acc.containsKey(c)) ordered.put(c, acc.get(c));
        }
        // Beklenmedik bir para birimi varsa sona ekle
        for (Map.Entry<PriceCurrency, BigDecimal> e : acc.entrySet()) {
            ordered.putIfAbsent(e.getKey(), e.getValue());
        }
        return ordered;
    }
}
