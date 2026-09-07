-- =====================================================================
-- AŞAMA 8: KARGO OPERASYON YÖNETİMİ — orders tablosuna eklenen kolonlar
-- ddl-auto=validate kullanıldığı için bu SQL'i uygulamayı başlatmadan
-- ÖNCE manuel çalıştırın.
--
-- NOT: Bu kolonlar AŞAMA 5'teki shipping_weight/shipping_cost/shipping_category/
-- shipping_message (TAHMİNİ kargo SNAPSHOT'ı) ile KARIŞTIRILMAMALIDIR. Bunlar admin'in
-- siparişi FİİLEN kargoya verdiğinde girdiği OPERASYONEL bilgilerdir.
-- =====================================================================

ALTER TABLE orders ADD COLUMN shipping_company VARCHAR(120) NULL;
ALTER TABLE orders ADD COLUMN shipping_tracking_number VARCHAR(100) NULL;
ALTER TABLE orders ADD COLUMN shipped_at DATETIME NULL;

-- Mevcut (eski) sipariş kayıtları etkilenmez; bu 3 kolon onlarda NULL olarak kalır.
-- Sipariş henüz kargoya verilmemişse de bu alanlar NULL kalır — bu normal bir durumdur,
-- hata değildir.
