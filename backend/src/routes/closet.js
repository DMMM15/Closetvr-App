const express = require("express");
const path = require("path");
const multer = require("multer");
const fs = require("fs").promises;
const { pathToFileURL } = require("url"); // Módulo nativo para convertir rutas a file://
const { query } = require("../config/db");
const { requireAuth } = require("../middleware/auth");

const router = express.Router();
router.use(requireAuth);

// Configuración de almacenamiento local para imágenes subidas
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, path.join(__dirname, "../../uploads")),
  filename: (req, file, cb) => {
    const unique = `${req.userId}-${Date.now()}${path.extname(file.originalname) || ".jpg"}`;
    cb(null, unique);
  },
});

const upload = multer({ storage, limits: { fileSize: 8 * 1024 * 1024 } });

// GET /api/closet -> Obtener prendas del armario
router.get("/", async (req, res) => {
  try {
    const rows = await query(
      "SELECT * FROM closet_items WHERE user_id = $1 ORDER BY added_at DESC",
      [req.userId]
    );
    res.json(rows);
  } catch (err) {
    console.error("Error al obtener el armario:", err);
    res.status(500).json({ error: "Error al obtener el armario" });
  }
});

// POST /api/closet/photo -> Subir foto propia, procesar con IA y guardar
router.post("/photo", upload.single("photo"), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: "No se recibió ninguna imagen" });
    }

    // Carga dinámica de la IA
    const { removeBackground } = await import("@imgly/background-removal-node");

    const originalPath = req.file.path;
    console.log("Procesando imagen con IA para aislar la prenda...");

    // Convertimos la ruta de Windows (C:\...) al protocolo file:// aceptado por la IA
    const fileUrl = pathToFileURL(originalPath).href;

    // La IA procesa la imagen a través de la URI file://
    const blob = await removeBackground(fileUrl);
    const buffer = Buffer.from(await blob.arrayBuffer());

    // Nombre y ruta final para el archivo recortado (.png)
    const finalFilename = `${req.userId}-${Date.now()}_cutout.png`;
    const finalPath = path.join(__dirname, "../../uploads", finalFilename);

    // Escribimos la nueva imagen transparente y borramos la foto original con fondo
    await fs.writeFile(finalPath, buffer);
    await fs.unlink(originalPath);

    console.log("¡Fondo recortado exitosamente por la IA!");

    const imageUrl = `${process.env.BASE_URL}/uploads/${finalFilename}`;
    const { name = "Prenda propia", category = "otros" } = req.body;

    const rows = await query(
      `INSERT INTO closet_items (user_id, image_url, name, category, source) 
       VALUES ($1, $2, $3, $4, 'camera') RETURNING *`,
      [req.userId, imageUrl, name, category]
    );

    res.status(201).json(rows[0]);
  } catch (err) {
    console.error("Error en procesamiento de IA o BD:", err);
    res.status(500).json({ error: "Error al guardar la foto en el armario" });
  }
});

// POST /api/closet/from-store -> Guardar prenda desde la tienda
router.post("/from-store", async (req, res) => {
  try {
    const { productId } = req.body;
    if (!productId) {
      return res.status(400).json({ error: "productId es obligatorio" });
    }

    const product = await query("SELECT * FROM products WHERE id = $1", [productId]);
    if (product.length === 0) {
      return res.status(404).json({ error: "Producto no encontrado" });
    }
    const p = product[0];

    const rows = await query(
      `INSERT INTO closet_items (user_id, product_id, image_url, name, category, source) 
       VALUES ($1, $2, $3, $4, $5, 'store') RETURNING *`,
      [req.userId, p.id, p.image_url, p.name, p.category]
    );

    res.status(201).json(rows[0]);
  } catch (err) {
    console.error("Error al añadir desde la tienda:", err);
    res.status(500).json({ error: "Error al añadir la prenda al armario" });
  }
});

// DELETE /api/closet/:id -> Eliminar prenda del armario
router.delete("/:id", async (req, res) => {
  try {
    await query("DELETE FROM closet_items WHERE id = $1 AND user_id = $2", [
      req.params.id,
      req.userId,
    ]);
    res.status(204).send();
  } catch (err) {
    console.error("Error al eliminar la prenda:", err);
    res.status(500).json({ error: "Error al eliminar la prenda" });
  }
});

module.exports = router;