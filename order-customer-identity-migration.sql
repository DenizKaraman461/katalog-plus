-- =====================================================================
-- AŞAMA 10.1 (revize): İYZİCO GERÇEK MÜŞTERİ ŞEHİR BİLGİSİ — orders tablosuna eklenen kolonlar
-- ddl-auto=validate kullanıldığı için bu SQL'i uygulamayı başlatmadan
-- ÖNCE manuel çalıştırın.
--
-- Amaç: IyzicoClient'ta önceden sabit gönderilen city/country alanlarının yerine gerçek
-- müşteri verisini (checkout formundan) SNAPSHOT olarak saklamak.
--
-- TC Kimlik No KOLONU YOKTUR —
-- müşteriden bu veri toplanmaz ve Order tablosunda saklanmaz.
-- Iyzico için gerekli identityNumber alanı müşteri verisiyle ilişkilendirilmez;
-- entegrasyon ayarı olarak yönetilir.-- =====================================================================

ALTER TABLE orders ADD COLUMN customer_city VARCHAR(100) NULL;
ALTER TABLE orders ADD COLUMN customer_country VARCHAR(100) NULL;

-- Mevcut (eski) sipariş kayıtları etkilenmez; bu 2 kolon onlarda NULL kalır.
-- IyzicoClient bu durumda (city null) ödeme başlatmayı GÜVENLİ şekilde reddeder.

-- =====================================================================
-- NOT (yalnızca bu migration'ın ÖNCEKİ sürümünü zaten çalıştırdıysanız gerekir):
-- Önceki sürüm "customer_identity_number VARCHAR(20)" kolonunu da ekliyordu. Entity artık bu
-- alanı içermediğinden (ddl-auto=validate bunu sorun etmez, fazladan/eşlenmemiş bir kolon
-- hataya yol açmaz) bu kolonu DB'de tutmakta bir sakınca yoktur. Temizlemek isterseniz:
-- ALTER TABLE orders DROP COLUMN customer_identity_number;
-- =====================================================================
