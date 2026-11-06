-- ============================================
-- Closet VR - Módulo de Outfits con IA
-- ============================================

-- 1. Borrar tablas anteriores (en orden inverso por las referencias)
DROP TABLE IF EXISTS public.outfit_items;
DROP TABLE IF EXISTS public.outfits;

-- 2. Crear tabla principal de outfits
CREATE TABLE public.outfits (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    clima VARCHAR(100) NOT NULL,
    estilo VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 3. Crear tabla de relación (prendas que componen el outfit)
CREATE TABLE public.outfit_items (
    id SERIAL PRIMARY KEY,
    outfit_id INT NOT NULL REFERENCES public.outfits(id) ON DELETE CASCADE,
    closet_item_id INT NOT NULL REFERENCES public.closet_items(id) ON DELETE CASCADE,
    UNIQUE (outfit_id, closet_item_id)
);

-- 4. Índices para optimizar consultas
CREATE INDEX idx_outfits_user ON public.outfits(user_id);
CREATE INDEX idx_outfit_items_outfit ON public.outfit_items(outfit_id);

-- 5. Verificación de creación correcta
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name IN ('outfits', 'outfit_items')
ORDER BY table_name, ordinal_position;