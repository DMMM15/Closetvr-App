const express = require("express");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const { query } = require("../config/db");

// 1. Importar la librería de Google
const { OAuth2Client } = require("google-auth-library");
// Este es tu Client ID de Android que configuraste
const client = new OAuth2Client("923277322497-348909vsol1qlrb71v2214ln1ce9bn0l.apps.googleusercontent.com");

const router = express.Router();

router.post("/register", async (req, res) => {
  try {
    const { name, email, password } = req.body;
    if (!name || !email || !password) return res.status(400).json({ error: "name, email y password son obligatorios" });

    const existing = await query("SELECT id FROM users WHERE email = $1", [email]);
    if (existing.length > 0) return res.status(409).json({ error: "Ya existe una cuenta con ese correo" });

    const passwordHash = await bcrypt.hash(password, 10);
    const rows = await query(
      `INSERT INTO users (name, email, password_hash) VALUES ($1, $2, $3) RETURNING id, name, email, avatar_url, created_at`,
      [name, email, passwordHash]
    );

    const user = rows[0];
    const token = jwt.sign({ userId: user.id }, process.env.JWT_SECRET, { expiresIn: "30d" });
    res.status(201).json({ token, user });
  } catch (err) {
    res.status(500).json({ error: "Error al registrar el usuario" });
  }
});

router.post("/login", async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) return res.status(400).json({ error: "email y password son obligatorios" });

    const rows = await query("SELECT * FROM users WHERE email = $1", [email]);
    if (rows.length === 0) return res.status(401).json({ error: "Correo o contraseña incorrectos" });

    const user = rows[0];
    
    // NOTA: Validar contraseñas viejas sin encriptar y nuevas encriptadas
    let validPassword = false;
    if (user.password_hash.startsWith('$2b$') || user.password_hash.startsWith('$2a$')) {
        validPassword = await bcrypt.compare(password, user.password_hash);
    } else {
        validPassword = (password === user.password_hash);
    }
    
    if (!validPassword) return res.status(401).json({ error: "Correo o contraseña incorrectos" });

    const token = jwt.sign({ userId: user.id }, process.env.JWT_SECRET, { expiresIn: "30d" });
    res.json({ token, user: { id: user.id, name: user.name, email: user.email, avatar_url: user.avatar_url } });
  } catch (err) {
    res.status(500).json({ error: "Error al iniciar sesión" });
  }
});

// 2. NUEVA RUTA: INGRESO CON GOOGLE
router.post("/google", async (req, res) => {
  try {
    const { token: googleToken } = req.body;
    if (!googleToken) return res.status(400).json({ error: "El token de Google es obligatorio" });

    // a) Validamos que el token sea auténtico y sacamos el correo
    const ticket = await client.verifyIdToken({
      idToken: googleToken,
      audience: "923277322497-348909vsol1qlrb71v2214ln1ce9bn0l.apps.googleusercontent.com",
    });
    
    const payload = ticket.getPayload();
    const email = payload.email;
    const name = payload.name;
    const avatar_url = payload.picture || null; // Google nos regala la foto de perfil

    // b) Revisamos si este correo ya existe en tu tabla de usuarios
    const existing = await query("SELECT * FROM users WHERE email = $1", [email]);
    let user;

    if (existing.length > 0) {
      // Si ya existía (ej. se registró manual antes y ahora usó Google), solo lo tomamos
      user = existing[0];
    } else {
      // c) Si es un usuario totalmente nuevo, lo registramos automáticamente en PostgreSQL
      const rows = await query(
        `INSERT INTO users (name, email, password_hash, avatar_url) VALUES ($1, $2, $3, $4) RETURNING id, name, email, avatar_url, created_at`,
        [name, email, "google_sso_no_password", avatar_url] 
      );
      user = rows[0];
    }

    // d) Le generamos su sesión oficial de ClosetVR para que pueda usar el carrito
    const token = jwt.sign({ userId: user.id }, process.env.JWT_SECRET, { expiresIn: "30d" });
    
    // e) Le enviamos los datos al celular
    res.json({ 
        token, 
        user: { 
            id: user.id, 
            name: user.name, 
            email: user.email, 
            avatar_url: user.avatar_url 
        } 
    });
  } catch (err) {
    console.error("Error en login de Google:", err);
    res.status(401).json({ error: "Token de Google inválido o caducado" });
  }
});

module.exports = router;