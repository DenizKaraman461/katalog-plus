-- V10__add_product_shipping_weight_fields.sql
-- =====================================================================
-- Amaç: Adet bazlı ürünler (radansa, soket, spanzet, aksesuar vb.) için kargo ağırlığı
-- desteği eklemek. Mevcut metre bazlı kargo hesabı (shipping_weight_per_meter) BOZULMAZ.
--
-- Yeni kolonlar:
--   shipping_weight_per_unit  DECIMAL(10,4) NULL          — bir ADEDİN kg karşılığı
--   shipping_weight_type      VARCHAR(20)   NOT NULL       — 'PER_METER' | 'PER_UNIT'
--
-- GERİYE DÖNÜK UYUMLULUK: shipping_weight_type DEFAULT 'PER_METER' ile eklenir; MySQL bu
-- sayede MEVCUT TÜM satırları otomatik olarak 'PER_METER' ile doldurur. Böylece bu migration
-- öncesi oluşturulmuş hiçbir ürünün kargo hesaplama davranışı DEĞİŞMEZ.
-- =====================================================================

ALTER TABLE product ADD COLUMN shipping_weight_per_unit DECIMAL(10,4) NULL;
ALTER TABLE product ADD COLUMN shipping_weight_type VARCHAR(20) NOT NULL DEFAULT 'PER_METER';
