const express = require("express");
const { query } = require("../config/db");
const router = express.Router();

router.get("/", async (req, res) => {
  try {
    const { category } = req.query;
    const rows = category
      ? await query("SELECT * FROM products WHERE category = $1 ORDER BY id DESC", [category])
      : await query("SELECT * FROM products ORDER BY id DESC");
    res.json(rows);
  } catch (err) {
    res.status(500).json({ error: "Error al obtener los productos" });
  }
});

router.get("/:id", async (req, res) => {
  try {
    const rows = await query("SELECT * FROM products WHERE id = $1", [req.params.id]);
    if (rows.length === 0) return res.status(404).json({ error: "Producto no encontrado" });
    res.json(rows[0]);
  } catch (err) {
    res.status(500).json({ error: "Error al obtener el producto" });
  }
});

module.exports = router;