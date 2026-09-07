package com.denizcelikhalat.katalog.repository;

import com.denizcelikhalat.katalog.model.QuoteRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, Long> {

    // Admin paneli: en yeni talep en üstte.
    List<QuoteRequest> findAllByOrderByCreatedAtDesc();

    // "Tekliflerim": kullanıcının kendi talepleri. Yeni kayıtlar userEmail ile eşleşir;
    // userEmail'i boş (eski) kayıtlar, form email'i giriş emaili ile aynıysa görünür.
    @Query("SELECT q FROM QuoteRequest q " +
           "WHERE q.userEmail = :email OR (q.userEmail IS NULL AND q.email = :email) " +
           "ORDER BY q.createdAt DESC")
    List<QuoteRequest> findForCustomer(@Param("email") String email);
}
