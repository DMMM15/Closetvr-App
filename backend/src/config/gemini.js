// ============================================================================
// Cliente de Gemini compartido (outfits y asesor).
//
// Problema que resuelve: Google a veces responde 503 "This model is currently experiencing
// high demand" durante varios minutos, y a veces en varios modelos a la vez. Estrategia:
//   - Se prueba el modelo principal (GEMINI_MODEL, por defecto gemini-3.8-flash) y, si falla,
//     los modelos de RESPALDO:
//        · los de GEMINI_FALLBACK_MODEL (uno o varios separados por coma), o
//        · si no defines ninguno, el backend le pregunta a Google qué modelos "flash" estables
//          tiene tu cuenta y usa hasta 4 automáticamente.
//   - Se ROTA entre todos los modelos en hasta 3 rondas (con una pausa entre rondas): cada modelo
//     tiene su propia capacidad, así que si uno se libera la petición pasa.
//   - 404 (el modelo no existe) o 429 (límite de ese modelo): se descarta ese modelo en esta petición.
//   - Errores reales (400, 403...) no se reintentan.
//   - Presupuesto de tiempo: ~40 s (la app espera hasta 60 s).
// ============================================================================
const { GoogleGenerativeAI } = require("@google/generative-ai");

const GEMINI_MODEL = (process.env.GEMINI_MODEL || "gemini-3.8-flash").trim();
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY || "");

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const RETRYABLE_STATUS = new Set([500, 502, 503, 504]);
const ROUNDS = 3;
const MAX_FALLBACKS = 4;
const retryDelay = () => Number(process.env.GEMINI_RETRY_DELAY_MS) || 1000;
const maxWait = () => Number(process.env.GEMINI_MAX_WAIT_MS) || 40_000;
const MODELS_URL = () =>
  process.env.GEMINI_MODELS_URL || "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000";

// ---- Descubrimiento automático de modelos de respaldo (se consulta solo si hace falta) ----
let discovered = { at: 0, models: [] };
const DISCOVERY_TTL_MS = 30 * 60 * 1000;

// "gemini-3.5-flash" -> [3, 5]; "gemini-3.5-flash-lite" -> [3, 5]
const versionOf = (id) => {
  const m = id.match(/^gemini-(\d+)(?:\.(\d+))?-flash/);
  return m ? [Number(m[1]), Number(m[2] || 0)] : [0, 0];
};

async function discoverFallbackModels() {
  if (Date.now() - discovered.at < DISCOVERY_TTL_MS) return discovered.models;
  discovered = { at: Date.now(), models: [] }; // si falla, no se vuelve a intentar en bucle
  if (typeof fetch !== "function") return [];
  try {
    const resp = await fetch(MODELS_URL(), {
      headers: { "x-goog-api-key": process.env.GEMINI_API_KEY || "" },
      signal: AbortSignal.timeout(8000),
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const data = await resp.json();
    const ids = (data.models || [])
      .filter((m) => (m.supportedGenerationMethods || []).includes("generateContent"))
      .map((m) => String(m.name).replace(/^models\//, ""))
      // solo modelos estables de texto: sin preview, experimentales, imagen, voz, live, etc.
      .filter((id) => /^gemini-\d+(\.\d+)?-flash(-lite)?$/.test(id))
      .filter((id) => id !== GEMINI_MODEL);
    const byVersionDesc = (a, b) => versionOf(b)[0] - versionOf(a)[0] || versionOf(b)[1] - versionOf(a)[1];
    const full = ids.filter((id) => !id.endsWith("-lite")).sort(byVersionDesc);
    const lite = ids.filter((id) => id.endsWith("-lite")).sort(byVersionDesc);
    discovered.models = [...full, ...lite].slice(0, MAX_FALLBACKS);
    console.log(`🔎 Modelos de respaldo disponibles en tu cuenta: ${discovered.models.join(", ") || "(ninguno)"}`);
  } catch (err) {
    console.warn(`⚠️ No se pudo consultar la lista de modelos de Gemini (${err.message}).`);
  }
  return discovered.models;
}

async function resolveFallbacks() {
  const explicit = (process.env.GEMINI_FALLBACK_MODEL || "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  const list = explicit.length ? explicit : await discoverFallbackModels();
  return list.filter((m) => m !== GEMINI_MODEL);
}

async function generateWithRetry(prompt, generationConfig) {
  const startedAt = Date.now();
  const active = [GEMINI_MODEL]; // modelos que siguen "vivos" en esta petición
  let fallbacksLoaded = false;
  let lastError;

  for (let round = 1; round <= ROUNDS; round++) {
    for (let i = 0; i < active.length; i++) {
      const modelName = active[i];
      try {
        const model = genAI.getGenerativeModel({
          model: modelName,
          ...(generationConfig ? { generationConfig } : {}),
        });
        const result = await model.generateContent(prompt);
        if (modelName !== GEMINI_MODEL) console.log(`✅ Respuesta generada con el modelo de respaldo ${modelName}`);
        return result;
      } catch (err) {
        lastError = err;
        if (err.status === 404 || err.status === 429) {
          // ese modelo no existe o llegó a su límite: se descarta en esta petición
          active.splice(i, 1);
          i--;
          console.warn(`⚠️ Gemini (${modelName}) respondió ${err.status}; se descarta ese modelo.`);
        } else if (!RETRYABLE_STATUS.has(err.status)) {
          throw err; // 400, 403, etc.: error real, no se reintenta
        } else {
          console.warn(`⚠️ Gemini (${modelName}) respondió ${err.status}; ronda ${round}/${ROUNDS}`);
        }
      }

      // Tras fallar el modelo principal por primera vez se cargan los de respaldo y se prueban en esta misma ronda
      if (!fallbacksLoaded && modelName === GEMINI_MODEL) {
        fallbacksLoaded = true;
        const fallbacks = await resolveFallbacks();
        active.push(...fallbacks.filter((m) => !active.includes(m)));
        if (fallbacks.length) console.warn(`↪️ Probando modelos de respaldo: ${fallbacks.join(", ")}`);
      }
      if (Date.now() - startedAt > maxWait()) throw lastError;
    }

    if (active.length === 0) break; // todos descartados (404/429)
    if (round < ROUNDS) {
      if (Date.now() - startedAt > maxWait()) break;
      await sleep(retryDelay() * 2 * round); // 2 s, 4 s...
    }
  }
  throw lastError;
}

module.exports = { generateWithRetry, GEMINI_MODEL };