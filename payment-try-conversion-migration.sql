-- =====================================================================
-- EUR/USD -> TRY ÖDEME DÖNÜŞÜMÜ — orders tablosuna eklenen kolonlar
-- ddl-auto=validate kullanıldığı için bu SQL'i uygulamayı başlatmadan
-- ÖNCE manuel çalıştırın.
-- =====================================================================

ALTER TABLE orders ADD COLUMN payment_amount DECIMAL(19,2) NULL;
ALTER TABLE orders ADD COLUMN payment_currency VARCHAR(10) NULL;

-- NOT: Bu kolonlar iyzico'ya GERÇEKTEN gönderilen (TRY'ye çevrilmiş) tahsilat tutarını saklar.
-- order.total_amount / order.currency / order_items.currency_snapshot kolonları DEĞİŞMEDİ;
-- onlar hâlâ ürünün/siparişin ORİJİNAL para birimini ve tutarını gösterir.
