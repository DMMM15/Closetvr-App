-- ============================================
-- Closet VR - Módulo de Agendamiento de Citas (Retail)
-- ============================================

CREATE TABLE IF NOT EXISTS public.appointments (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    service_name VARCHAR(150) NOT NULL,
    appointment_date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, CONFIRMED, CANCELLED
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Crear índice para optimizar búsquedas por usuario
CREATE INDEX IF NOT EXISTS idx_appointments_user ON public.appointments(user_id);