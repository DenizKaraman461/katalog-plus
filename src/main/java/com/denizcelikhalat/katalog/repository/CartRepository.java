package com.denizcelikhalat.katalog.repository;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUser(User user);

    // open-in-view=false olduğu için, sepeti view'da gösterirken
    // LazyInitializationException almamak adına items ve ürünleri tek sorguda çekiyoruz.
    @Query("SELECT c FROM Cart c " +
           "LEFT JOIN FETCH c.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE c.user.email = :email")
    Optional<Cart> findByUserEmailWithItems(@Param("email") String email);
}
