package com.denizcelikhalat.katalog.repository;

import com.denizcelikhalat.katalog.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Kullanıcının siparişleri, en yeni en üstte. Gruplu toplamlar (getTotalsByCurrency) order.items'a
    // eriştiği için open-in-view=false altında LazyInitializationException olmasın diye kalemler + ürün FETCH edilir.
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.user " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE o.user.email = :email " +
           "ORDER BY o.orderDate DESC")
    List<Order> findByUser_EmailOrderByOrderDateDesc(@Param("email") String email);

    // Admin paneli: TÜM siparişler, tarihe göre yeniden eskiye. Aynı sebeple kalemler + ürün FETCH edilir.
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.user " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "ORDER BY o.orderDate DESC")
    List<Order> findAllWithUserOrderByOrderDateDesc();

    // Başarı/detay sayfası: tek sipariş + kalemler + ürün + kullanıcı tek sorguda.
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.user " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE o.id = :id")
    Optional<Order> findDetailById(@Param("id") Long id);

    // Ödeme callback/webhook'unda sağlayıcının gönderdiği token ile siparişi bulmak için.
    Optional<Order> findByPaymentToken(String paymentToken);
}
