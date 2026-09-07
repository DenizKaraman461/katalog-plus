-- =====================================================================
-- ÖDEME ENTEGRASYONU (iyzico) — orders tablosuna eklenen kolonlar
-- ddl-auto=validate kullanıldığı için bu SQL'i uygulamayı başlatmadan
-- ÖNCE manuel çalıştırın.
-- =====================================================================

ALTER TABLE orders ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE orders ADD COLUMN payment_provider VARCHAR(30) NULL;
ALTER TABLE orders ADD COLUMN payment_conversation_id VARCHAR(100) NULL;
ALTER TABLE orders ADD COLUMN payment_token VARCHAR(200) NULL;
ALTER TABLE orders ADD COLUMN paid_at DATETIME NULL;

-- NOT: orders.status kolonu zaten VARCHAR(20) (OrderStatus enum STRING olarak saklanıyor).
-- Yeni "PAID" değeri bu uzunluğa sığdığı için status kolonunda migration GEREKMEZ.

-- (Opsiyonel) Var olan eski siparişleri payment_status='PENDING' olarak bırakmak yeterlidir;
-- gerçek ödeme geçmişiniz yoksa geriye dönük PAID işaretlemeyin.

ALTER TABLE orders
MODIFY COLUMN status ENUM('PENDING','PAID','PREPARING','SHIPPED','DELIVERED','CANCELLED') DEFAULT 'PENDING';