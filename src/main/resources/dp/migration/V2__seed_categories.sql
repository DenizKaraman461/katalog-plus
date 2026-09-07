-- =========================
-- BAŞLANGIÇ KATEGORİLERİ
-- =========================

INSERT INTO category (name, image_path)
SELECT 'Çelik Halat', '/uploads/celik_halat.jpg'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Çelik Halat');

INSERT INTO category (name, image_path)
SELECT 'Zincirler', '/uploads/zincir.webp'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Zincirler');

INSERT INTO category (name, image_path)
SELECT 'Bağlantı Elemanları', '/uploads/kanca.jpg'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Bağlantı Elemanları');

INSERT INTO category (name, image_path)
SELECT 'Polyester Sapan', '/uploads/polyester.jpeg'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Polyester Sapan');

INSERT INTO category (name, image_path)
SELECT 'Kaldırma Ekipmanları', '/uploads/tel-halat.webp'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Kaldırma Ekipmanları');

INSERT INTO category (name, image_path)
SELECT 'Çelik Halat Sapan', '/uploads/halat-sapan.jpg'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Çelik Halat Sapan');
