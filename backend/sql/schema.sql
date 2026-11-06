-- ============================================
-- Closet VR - Esquema de base de datos PostgreSQL
-- ============================================

CREATE TABLE IF NOT EXISTS users (
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    avatar_url    TEXT,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS products (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    price       NUMERIC(10,2) NOT NULL,
    category    VARCHAR(80),          -- ej: camisas, pantalones, zapatos
    image_url   TEXT NOT NULL,
    stock       INT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cart_items (
    id         SERIAL PRIMARY KEY,
    user_id    INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity   INT NOT NULL DEFAULT 1,
    added_at   TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, product_id)
);

-- Armario virtual: puede tener prendas de la tienda (product_id)
-- Y/o fotos propias tomadas con la cámara (image_url propio, product_id NULL)
CREATE TABLE IF NOT EXISTS closet_items (
    id          SERIAL PRIMARY KEY,
    user_id     INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id  INT REFERENCES products(id) ON DELETE SET NULL,
    image_url   TEXT NOT NULL,
    name        VARCHAR(150),
    category    VARCHAR(80),
    source      VARCHAR(20) NOT NULL DEFAULT 'camera', -- 'camera' | 'store'
    added_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
CREATE INDEX IF NOT EXISTS idx_closet_user ON closet_items(user_id);
CREATE INDEX IF NOT EXISTS idx_cart_user ON cart_items(user_id);

-- Datos de ejemplo para probar la tienda
INSERT INTO products (name, description, price, category, image_url, stock) VALUES
('Camiseta Oversize Blanca', 'Camiseta de algodón 100%, corte oversize', 59900, 'camisetas', 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab', 25),
('Jean Recto Azul', 'Jean clásico recto, denim resistente', 129900, 'pantalones', 'https://images.unsplash.com/photo-1542272604-787c3835535d', 15),
('Chaqueta Bomber Negra', 'Chaqueta bomber unisex, ideal para clima frío', 189900, 'chaquetas', 'https://images.unsplash.com/photo-1551028719-00167b16eac5', 10),
('Tenis Urbanos Blancos', 'Tenis casuales de cuero sintético', 159900, 'calzado', 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519', 20)
ON CONFLICT DO NOTHING;
