-- =====================================================================
-- AŞAMA 5: ORDER KARGO SNAPSHOT — orders tablosuna eklenen kolonlar
-- ddl-auto=validate kullanıldığı için bu SQL'i uygulamayı başlatmadan
-- ÖNCE manuel çalıştırın.
--
-- NOT: Bu kolonlar SİPARİŞ ANINDA hesaplanan kargo bilgisinin SNAPSHOT'ını saklar.
-- Sipariş oluşturulduktan sonra bir daha hesaplanmaz/güncellenmez (ürün ağırlığı veya
-- kargo tarifeleri sonradan değişse bile bu siparişin kaydı sabit kalır). Hepsi NULL
-- olabilir: kargo hesaplanamadıysa (manuel inceleme gerekiyorsa, ürün eski ve
-- shippingWeightPerMeter tanımlı değilse vb.) sipariş yine de bu alanlar boş olarak oluşur.
-- =====================================================================

ALTER TABLE orders ADD COLUMN shipping_weight DECIMAL(10,2) NULL;
ALTER TABLE orders ADD COLUMN shipping_cost DECIMAL(10,2) NULL;
ALTER TABLE orders ADD COLUMN shipping_category VARCHAR(50) NULL;
ALTER TABLE orders ADD COLUMN shipping_message VARCHAR(255) NULL;

-- Mevcut (eski) sipariş kayıtları etkilenmez; bu 4 kolon onlarda NULL olarak kalır
-- (geriye dönük hesaplama/backfill YAPILMAZ — snapshot mantığı yalnızca BUNDAN SONRA
-- oluşturulacak siparişler için geçerlidir).
