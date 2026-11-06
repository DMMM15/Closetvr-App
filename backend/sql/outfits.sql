-- ============================================
-- Closet VR - Tablas del módulo de Outfits con IA
-- Ejecutar MANUALMENTE en la consola de Aiven
-- ============================================

-- OPCIONAL: solo si ya creaste antes estas tablas con otros nombres de columna
-- (por ejemplo id_usuario / id_outfit / id_armario_prenda) y no tienen datos que quieras conservar.
-- DROP TABLE IF EXISTS public.outfit_items;
-- DROP TABLE IF EXISTS public.outfits;

CREATE TABLE IF NOT EXISTS public.outfits (
    id         SERIAL PRIMARY KEY,
    user_id    INT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    clima      VARCHAR(100) NOT NULL,
    estilo     VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.outfit_items (
    id             SERIAL PRIMARY KEY,
    outfit_id      INT NOT NULL REFERENCES public.outfits(id) ON DELETE CASCADE,
    closet_item_id INT NOT NULL REFERENCES public.closet_items(id) ON DELETE CASCADE,
    UNIQUE (outfit_id, closet_item_id)
);

CREATE INDEX IF NOT EXISTS idx_outfits_user ON public.outfits(user_id);
CREATE INDEX IF NOT EXISTS idx_outfit_items_outfit ON public.outfit_items(outfit_id);

-- Verificación
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name IN ('outfits', 'outfit_items')
ORDER BY table_name, ordinal_position;
