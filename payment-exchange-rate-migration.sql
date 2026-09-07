-- =====================================================================
-- CANLI KUR (TCMB) İLE ÖDEME — orders tablosuna eklenen kur meta-veri kolonları
-- ddl-auto=validate kullanıldığı için bu SQL'i uygulamayı başlatmadan
-- ÖNCE manuel çalıştırın.
--
-- NOT: payment_amount / payment_currency kolonları önceki migration'da
-- (payment-try-conversion-migration.sql) zaten eklenmiş olmalı. Bu dosya SADECE
-- yeni kur kaynağı/zamanı/margin bilgisi kolonlarını ekler.
-- =====================================================================

ALTER TABLE orders ADD COLUMN payment_exchange_rate_source VARCHAR(30) NULL;
ALTER TABLE orders ADD COLUMN payment_exchange_rate_fetched_at DATETIME NULL;
ALTER TABLE orders ADD COLUMN payment_exchange_rate_margin_percent DECIMAL(6,2) NULL;

-- Bu alanlar yalnızca bilgi/denetim amaçlıdır: bir siparişin ödeme tutarının HANGİ kurla,
-- HANGİ kaynaktan (TCMB/FALLBACK) ve NE ZAMAN hesaplandığını admin panelinde görebilmek için.
