-- =====================================================================
-- KARGO ALTYAPISI — AŞAMA 1: yalnızca veri modeli (kargo HESAPLAMASI YOK)
-- product tablosuna eklenen kolon.
-- ddl-auto=validate kullanıldığı için bu SQL'i uygulamayı başlatmadan
-- ÖNCE manuel çalıştırın.
-- =====================================================================

ALTER TABLE product ADD COLUMN shipping_weight_per_meter DECIMAL(10,4) NULL;

-- NOT: Kolon NULL olabilir (eski ürünler için varsayılan boş kalır).
-- Negatif/sıfır değer uygulama katmanında (ProductServiceImpl) reddedilir;
-- veritabanı seviyesinde ayrıca bir CHECK kısıtı eklenmedi (ddl-auto=validate ile
-- Hibernate'in ürettiği şemayla bire bir uyumlu kalması için).
