package com.denizcelikhalat.katalog.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Şifre sıfırlama token'ı. GÜVENLİK: ham (raw) token ASLA veritabanına yazılmaz;
 * yalnızca SHA-256 hash'i saklanır (bkz. PasswordResetService). Böylece veritabanı
 * sızsa bile linkler kullanılamaz (hash'ten ham token'a geri dönülemez).
 *
 * Her token: 30 dakika geçerlidir (expiresAt) ve tek kullanımlıktır (used).
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // SHA-256 hex (64 karakter). Ham token loglanmaz/saklanmaz.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_flag", nullable = false)
    private Boolean used = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PasswordResetToken() {
    }

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

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getUsed() {
        return used;
    }

    public void setUsed(Boolean used) {
        this.used = used;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
