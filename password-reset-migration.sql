-- =====================================================================
-- ŞİFREMİ UNUTTUM / ŞİFRE SIFIRLAMA — password_reset_tokens tablosu
-- ddl-auto=validate kullanıldığı için bu SQL'i uygulamayı başlatmadan
-- ÖNCE manuel çalıştırın.
-- =====================================================================

CREATE TABLE password_reset_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,      -- SHA-256 hex; HAM TOKEN asla saklanmaz
    expires_at  DATETIME NOT NULL,
    used_flag   BIT(1) NOT NULL DEFAULT b'0',
    created_at  DATETIME NOT NULL,
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Doğrulama/temizlik sorgularını hızlandırmak için indeksler.
CREATE INDEX idx_password_reset_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_expires_at ON password_reset_tokens(expires_at);

-- (Opsiyonel) Süresi dolmuş/kullanılmış token'ları periyodik temizlemek isterseniz,
-- bir cron/scheduled job ile şu sorguyu çalıştırabilirsiniz:
-- DELETE FROM password_reset_tokens WHERE used_flag = b'1' OR expires_at < NOW();
