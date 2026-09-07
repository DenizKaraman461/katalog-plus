-- =========================
-- KATEGORİ TABLOSU
-- =========================
CREATE TABLE IF NOT EXISTS category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  image_path VARCHAR(255) NULL,
  CONSTRAINT uq_category_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================
-- ÜRÜN TABLOSU
-- =========================
CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(180) NOT NULL,
  price DECIMAL(12,2) NOT NULL DEFAULT 0,
  description TEXT NULL,
  image_path VARCHAR(255) NULL,
  table_image_path VARCHAR(255) NULL,
  category_id BIGINT NOT NULL,

  CONSTRAINT fk_product_category
    FOREIGN KEY (category_id) REFERENCES category(id)
    ON UPDATE CASCADE
    ON DELETE RESTRICT,

  CONSTRAINT chk_price_nonneg CHECK (price >= 0),
  CONSTRAINT uq_product_name_per_category UNIQUE (name, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================
-- INDEXLER
-- =========================
CREATE INDEX IF NOT EXISTS idx_product_category ON product(category_id);
CREATE INDEX IF NOT EXISTS idx_product_name ON product(name);
