package com.denizcelikhalat.katalog.repository;

import com.denizcelikhalat.katalog.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // Callback/doğrulama: token hash'i ile kaydı bulur (ham token asla sorgulanmaz).
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Yeni bir sıfırlama talebi oluşturulduğunda, o kullanıcının önceki (kullanılmamış)
    // token'larını geçersiz kılmak için temizler; aynı anda yalnızca en güncel link geçerli olur.
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
