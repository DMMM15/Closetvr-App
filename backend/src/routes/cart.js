const express = require("express");
const { query } = require("../config/db");
const { requireAuth } = require("../middleware/auth");

const router = express.Router();
router.use(requireAuth); // todas las rutas del carrito requieren sesión

// GET /api/cart -> items del carrito del usuario logueado, con datos del producto
router.get("/", async (req, res) => {
  try {
    const rows = await query(
      `SELECT c.id AS cart_item_id, c.quantity, p.*
       FROM cart_items c
       JOIN products p ON p.id = c.product_id
       WHERE c.user_id = $1
       ORDER BY c.added_at DESC`,
      [req.userId]
    );
    res.json(rows);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Error al obtener el carrito" });
  }
});

// POST /api/cart  { productId, quantity }
router.post("/", async (req, res) => {
  try {
    const { productId, quantity = 1 } = req.body;
    if (!productId) return res.status(400).json({ error: "productId es obligatorio" });

    const rows = await query(
      `INSERT INTO cart_items (user_id, product_id, quantity)
       VALUES ($1, $2, $3)
       ON CONFLICT (user_id, product_id)
       DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity
       RETURNING *`,
      [req.userId, productId, quantity]
    );

    res.status(201).json(rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Error al añadir al carrito" });
  }
});

// DELETE /api/cart/:cartItemId
router.delete("/:cartItemId", async (req, res) => {
  try {
    await query("DELETE FROM cart_items WHERE id = $1 AND user_id = $2", [
      req.params.cartItemId,
      req.userId,
    ]);
    res.status(204).send();
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Error al eliminar del carrito" });
  }
});

module.exports = router;
