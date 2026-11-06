// El .env vive en backend/ (una carpeta arriba de src/), no dentro de src/
require("dotenv").config({ path: require("path").join(__dirname, "..", ".env") });
const express = require("express");
const cors = require("cors");
const path = require("path");

const authRoutes = require("./routes/auth");
const productsRoutes = require("./routes/products");
const cartRoutes = require("./routes/cart");
const closetRoutes = require("./routes/closet");
const outfitsRoutes = require("./routes/outfits");
const appointmentsRoutes = require("./routes/appointments");

const app = express();

app.use(cors());
// Guardamos el cuerpo crudo (req.rawBody): Retell firma el webhook sobre el texto EXACTO que envía
app.use(
  express.json({
    verify: (req, res, buf) => {
      req.rawBody = buf.toString("utf8");
    },
  })
);

// Sirve las fotos subidas (armario) como archivos estáticos
app.use("/uploads", express.static(path.join(__dirname, "../uploads")));

// Rutas de la API
app.use("/api/auth", authRoutes);
app.use("/api/products", productsRoutes);
app.use("/api/cart", cartRoutes);
app.use("/api/closet", closetRoutes);
app.use("/api/outfits", outfitsRoutes);
// Citas + asesor (chat de texto y voz con Retell): /api/appointments, /api/chat, /api/create-call, /api/retell-webhook
app.use("/api", appointmentsRoutes);

app.get("/", (req, res) => {
  res.json({ status: "ok", service: "Closet VR API" });
});

const PORT = process.env.PORT || 3000;

// Al agregar "0.0.0.0", Express escucha en la interfaz de red local (Wi-Fi) y no solo en localhost
app.listen(PORT, "0.0.0.0", () => {
  console.log(`🚀 Closet VR backend escuchando en http://0.0.0.0:${PORT}`);
});