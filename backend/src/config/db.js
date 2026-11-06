// Conexión a PostgreSQL usando "pg" directo (sin ORM) para que quede
// claro y fácil de leer qué consulta se está ejecutando en cada momento.
const { Pool } = require("pg");
require("dotenv").config();

const pool = new Pool({
  host: process.env.PGHOST,
  port: process.env.PGPORT,
  database: process.env.PGDATABASE,
  user: process.env.PGUSER,
  password: process.env.PGPASSWORD,
  ssl: { rejectUnauthorized: false }
});

pool.on("connect", () => {
  console.log("✅ Conectado a PostgreSQL");
});

pool.on("error", (err) => {
  console.error("❌ Error inesperado en el pool de PostgreSQL", err);
  process.exit(1);
});

// Helper simple: query(texto, valores) -> filas
async function query(text, params) {
  const result = await pool.query(text, params);
  return result.rows;
}

module.exports = { pool, query };