package com.denizcelikhalat.katalog.repository;

import com.denizcelikhalat.katalog.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // Sipariş kalemleri Order ile birlikte cascade kaydedildiği için
    // şimdilik ekstra metoda gerek yok; JpaRepository yeterli.
}
