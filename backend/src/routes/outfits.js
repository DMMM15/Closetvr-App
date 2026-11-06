const express = require("express");
const { pool, query } = require("../config/db");
const { generateWithRetry, GEMINI_MODEL } = require("../config/gemini");
const { requireAuth } = require("../middleware/auth");

const router = express.Router();
router.use(requireAuth);

const MAX_TEXT = 100; // debe coincidir con los VARCHAR(100) del SQL

// Limpia la respuesta de la IA y devuelve un arreglo de enteros
function extractIdArray(text) {
  const cleaned = String(text).replace(/```json|```/gi, "").trim();
  const start = cleaned.indexOf("[");
  const end = cleaned.lastIndexOf("]");
  if (start === -1 || end <= start) {
    throw new Error("La IA no devolvió un arreglo JSON");
  }
  const parsed = JSON.parse(cleaned.slice(start, end + 1));
  if (!Array.isArray(parsed)) {
    throw new Error("La respuesta de la IA no es un arreglo");
  }
  return parsed.map(Number).filter(Number.isInteger);
}

// POST /api/outfits/generate -> la IA elige prendas y se guarda el outfit
router.post("/generate", async (req, res) => {
  const clima = String(req.body?.clima ?? "").trim();
  const estilo = String(req.body?.estilo ?? "").trim();
  const nombre = String(req.body?.nombre ?? "").trim();

  if (!clima || !estilo || !nombre) {
    return res.status(400).json({ error: "Faltan datos obligatorios (clima, estilo o nombre)" });
  }
  if (clima.length > MAX_TEXT || estilo.length > MAX_TEXT || nombre.length > MAX_TEXT) {
    return res.status(400).json({ error: `Los textos no pueden superar ${MAX_TEXT} caracteres` });
  }
  if (!process.env.GEMINI_API_KEY) {
    return res.status(500).json({ error: "El servidor no tiene configurada la GEMINI_API_KEY" });
  }

  let client;
  try {
    // SOLO prendas que están en el armario del usuario (tabla closet_items): las de su cámara (source = 'camera')
    // y las que él mismo guardó desde la tienda (source = 'store'). Nunca se toca la tabla products ni el carrito.
    const prendas = await query(
      "SELECT id, name, category FROM public.closet_items WHERE user_id = $1 ORDER BY id",
      [req.userId]
    );

    if (prendas.length < 2) {
      return res.status(400).json({ error: "Debes tener al menos 2 prendas en tu armario." });
    }

    const prompt = `
Eres un estilista experto. Este es el armario de un usuario en formato JSON:
${JSON.stringify(prendas)}

Crea un outfit de estilo "${estilo}" ideal para clima "${clima}" usando SOLO prendas de esa lista.
Combina prendas lógicas (por ejemplo una parte superior y una inferior, más calzado si hay).

Responde ÚNICAMENTE con un arreglo JSON de números con los "id" de las prendas elegidas.
Sin texto adicional, sin comillas invertidas y sin formato markdown. Ejemplo exacto: [1, 5, 8]
`;

    const result = await generateWithRetry(prompt, { responseMimeType: "application/json", temperature: 0.7 });
    const rawIds = extractIdArray(result.response.text());

    // Seguridad: solo aceptamos IDs que realmente son prendas de este usuario
    const idsValidos = new Set(prendas.map((p) => p.id));
    const ids = [...new Set(rawIds)].filter((id) => idsValidos.has(id));

    if (ids.length < 2) {
      return res
        .status(502)
        .json({ error: "La IA no pudo armar un outfit válido. Intenta de nuevo." });
    }

    // Maestro + detalle en una sola transacción: o se guarda todo o nada
    client = await pool.connect();
    await client.query("BEGIN");

    const outfitInsert = await client.query(
      `INSERT INTO public.outfits (user_id, name, clima, estilo)
       VALUES ($1, $2, $3, $4)
       RETURNING id, name, clima, estilo, created_at`,
      [req.userId, nombre, clima, estilo]
    );
    const outfit = outfitInsert.rows[0];

    await client.query(
      `INSERT INTO public.outfit_items (outfit_id, closet_item_id)
       SELECT $1::int, UNNEST($2::int[])`,
      [outfit.id, ids]
    );

    await client.query("COMMIT");

    const detalle = await query(
      `SELECT id, name, category, image_url, source
       FROM public.closet_items
       WHERE user_id = $1 AND id = ANY($2::int[])
       ORDER BY id`,
      [req.userId, ids]
    );

    res.status(201).json({ success: true, outfit: { ...outfit, prendas: detalle } });
  } catch (err) {
    if (client) await client.query("ROLLBACK").catch(() => {});
    console.error("Error generando outfit:", err);

    let status = 500;
    let message = "Error al generar el outfit con IA";
    if (err.status === 503) {
      status = 503;
      message = "La IA está recibiendo muchas solicitudes ahora mismo. Intenta de nuevo en unos segundos.";
    } else if (err.status === 429) {
      status = 429;
      message = "La IA alcanzó su límite de uso. Intenta de nuevo en un minuto.";
    } else if (err.status === 404) {
      status = 502;
      message = `El modelo de IA "${GEMINI_MODEL}" no está disponible. Revisa GEMINI_MODEL.`;
    } else if (err.status || err instanceof SyntaxError || /IA/.test(err.message)) {
      status = 502;
      message = "La IA no respondió correctamente. Intenta de nuevo.";
    }
    res.status(status).json({ error: message });
  } finally {
    if (client) client.release();
  }
});

// GET /api/outfits -> outfits del usuario con el detalle de cada prenda
router.get("/", async (req, res) => {
  try {
    const rows = await query(
      `SELECT o.id, o.name, o.clima, o.estilo, o.created_at,
              json_agg(
                json_build_object(
                  'id', ci.id,
                  'name', ci.name,
                  'category', ci.category,
                  'image_url', ci.image_url,
                  'source', ci.source
                ) ORDER BY ci.id
              ) AS prendas
       FROM public.outfits o
       JOIN public.outfit_items oi ON oi.outfit_id = o.id
       JOIN public.closet_items ci ON ci.id = oi.closet_item_id
       WHERE o.user_id = $1
       GROUP BY o.id
       ORDER BY o.created_at DESC, o.id DESC`,
      [req.userId]
    );
    res.json(rows);
  } catch (err) {
    console.error("Error obteniendo outfits:", err);
    res.status(500).json({ error: "Error al cargar los outfits" });
  }
});

// DELETE /api/outfits/:id -> elimina un outfit del usuario (sus outfit_items se borran en cascada)
// Las prendas del armario NO se borran: solo el conjunto.
router.delete("/:id", async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) {
    return res.status(400).json({ error: "ID de outfit inválido" });
  }
  try {
    const rows = await query(
      "DELETE FROM public.outfits WHERE id = $1 AND user_id = $2 RETURNING id",
      [id, req.userId]
    );
    if (rows.length === 0) {
      return res.status(404).json({ error: "Outfit no encontrado" });
    }
    res.status(204).send();
  } catch (err) {
    console.error("Error eliminando outfit:", err);
    res.status(500).json({ error: "Error al eliminar el outfit" });
  }
});

module.exports = router;