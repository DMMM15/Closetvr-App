// ============================================================================
// Asesor de imagen: citas + chat de texto (Gemini) + voz (Retell AI)
//
// Rutas (todas bajo /api):
//   POST /appointments    (sesión)  crea una cita a mano
//   GET  /appointments    (sesión)  lista las citas del usuario
//   POST /chat            (sesión)  chat de texto con el asesor; puede agendar la cita
//   POST /ai-assistant    (sesión)  recomendación puntual (se conserva por compatibilidad)
//   POST /create-call     (sesión)  crea la llamada de voz en Retell y devuelve lo que necesita la app
//   GET  /call-result/:callId (sesión)  al colgar: ¿la llamada de voz dejó una cita? (la guarda y envía correos)
//   POST /retell-webhook  (pública, firmada por Retell)  respaldo: guarda la cita acordada por voz
// ============================================================================
const express = require("express");
const Retell = require("retell-sdk");
const { generateWithRetry, GEMINI_MODEL } = require("../config/gemini");
const { query } = require("../config/db"); // db.js exporta { pool, query }: query(texto, valores) -> FILAS
const { requireAuth } = require("../middleware/auth");

const router = express.Router();

const TZ = "America/Bogota"; // Colombia no usa horario de verano: siempre UTC-5
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// Servicios que ofrece el asesor (edítalos a tu gusto; el primero es el servicio por defecto)
const SERVICES = [
  "Asesoría de Imagen Personalizada",
  "Asesoría de Armario y Outfits",
  "Compra Asistida en Tienda",
];

// ---------------------------------------------------------------------------
// Utilidades de fecha (todo se guarda como hora local de Bogotá: "YYYY-MM-DD HH:MM:SS")
// ---------------------------------------------------------------------------
const pad = (n) => String(n).padStart(2, "0");

// Date (instante real) -> "YYYY-MM-DD HH:MM:SS" en hora de Bogotá
function formatInBogota(date) {
  const shifted = new Date(date.getTime() - 5 * 60 * 60 * 1000);
  return shifted.toISOString().slice(0, 19).replace("T", " ");
}

function nowBogota() {
  return formatInBogota(new Date());
}

// Acepta "2026-11-10 10:00", "2026-11-10T10:00:00" (se toma como hora de Bogotá)
// o con zona horaria ("...Z", "...-05:00") (se convierte a hora de Bogotá).
// Devuelve "YYYY-MM-DD HH:MM:SS" o null si no es una fecha válida.
function normalizeDate(input) {
  if (input === null || input === undefined) return null;
  const s = String(input).trim();
  if (!s) return null;

  const local = s.match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?$/);
  if (local) {
    const [, y, mo, d, h, mi, se = "00"] = local;
    const probe = new Date(Date.UTC(+y, +mo - 1, +d, +h, +mi, +se));
    const valid =
      probe.getUTCFullYear() === +y &&
      probe.getUTCMonth() === +mo - 1 &&
      probe.getUTCDate() === +d &&
      probe.getUTCHours() === +h &&
      probe.getUTCMinutes() === +mi;
    return valid ? `${y}-${mo}-${d} ${h}:${mi}:${pad(+se)}` : null;
  }

  if (/(Z|[+-]\d{2}:?\d{2})$/i.test(s)) {
    const parsed = new Date(s);
    if (!Number.isNaN(parsed.getTime())) return formatInBogota(parsed);
  }
  return null;
}

// "2026-11-10 15:00:00" -> "martes 10 de noviembre de 2026, 3:00 p. m."
function prettyDate(normalized) {
  const [d, t] = normalized.split(" ");
  const [y, mo, da] = d.split("-").map(Number);
  const [h, mi] = t.split(":").map(Number);
  const asUtc = new Date(Date.UTC(y, mo - 1, da, h, mi));
  return new Intl.DateTimeFormat("es-CO", {
    timeZone: "UTC",
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  }).format(asUtc);
}

function weekdayBogota() {
  return new Intl.DateTimeFormat("es-CO", { timeZone: TZ, weekday: "long" }).format(new Date());
}

// ---------------------------------------------------------------------------
// Acceso a datos
// ---------------------------------------------------------------------------
const APPOINTMENT_COLUMNS = `id, user_id, service_name,
  to_char(appointment_date, 'YYYY-MM-DD HH24:MI:SS') AS appointment_date, status`;

// Crea la cita (o avisa si el usuario ya tiene una en ese mismo horario).
// Si es nueva, envía los correos de confirmación (cliente + correo de la app) sin bloquear la respuesta.
async function createAppointment(userId, serviceName, date, origin = "app") {
  const dup = await query(
    `SELECT ${APPOINTMENT_COLUMNS} FROM public.appointments
     WHERE user_id = $1 AND appointment_date = $2 AND status <> 'CANCELLED'`,
    [userId, date]
  );
  if (dup.length > 0) return { duplicate: true, appointment: dup[0] };

  const rows = await query(
    `INSERT INTO public.appointments (user_id, service_name, appointment_date, status)
     VALUES ($1, $2, $3, 'CONFIRMED')
     RETURNING ${APPOINTMENT_COLUMNS}`,
    [userId, serviceName, date]
  );
  notifyAppointmentCreated(userId, rows[0], origin);
  return { duplicate: false, appointment: rows[0] };
}

async function getUserName(userId) {
  const rows = await query("SELECT name FROM public.users WHERE id = $1", [userId]);
  return rows[0]?.name || "cliente";
}

// ---------------------------------------------------------------------------
// Correos de confirmación (nodemailer + SMTP).
// Si no hay SMTP configurado (o falta instalar nodemailer) todo lo demás funciona igual.
// Variables: SMTP_USER, SMTP_PASS (obligatorias), SMTP_HOST (smtp.gmail.com), SMTP_PORT (465),
//            MAIL_FROM, ADMIN_EMAIL (correo de la app que recibe copia; por defecto SMTP_USER)
// ---------------------------------------------------------------------------
let mailTransport; // undefined = aún no se creó, null = no disponible
function getMailTransport() {
  if (mailTransport !== undefined) return mailTransport;
  const { SMTP_USER, SMTP_PASS } = process.env;
  if (!SMTP_USER || !SMTP_PASS) {
    if (!makeConfigured()) {
      console.warn("📧 Correos desactivados: faltan SMTP_USER y SMTP_PASS en el .env (las citas se guardan igual).");
    }
    return (mailTransport = null);
  }
  let nodemailer;
  try {
    nodemailer = require("nodemailer");
  } catch (_) {
    console.warn("📧 Correos desactivados: falta instalar nodemailer. Ejecuta: npm install nodemailer");
    return (mailTransport = null);
  }
  const port = Number(process.env.SMTP_PORT) || 465;
  const secure = process.env.SMTP_SECURE ? process.env.SMTP_SECURE === "true" : port === 465;
  mailTransport = nodemailer.createTransport({
    host: process.env.SMTP_HOST || "smtp.gmail.com",
    port,
    secure,
    auth: { user: SMTP_USER, pass: SMTP_PASS },
  });
  return mailTransport;
}

const emailsEnabled = () => !!getMailTransport();

// ---------------------------------------------------------------------------
// Make (Integromat): al crearse una cita se le envía un "webhook" con todos los datos.
// En Make se arma el escenario que guarda en Google Sheets / Drive, crea el evento en Google Calendar
// e invita por correo al cliente y a la organización. Variable: MAKE_WEBHOOK_URL (+ APPOINTMENT_DURATION_MIN).
// ---------------------------------------------------------------------------
function makeConfigured() {
  return !!(process.env.MAKE_WEBHOOK_URL || "").trim();
}
// ¿Se enviará confirmación por correo? (por Make o por SMTP directo)
const confirmationEnabled = () => makeConfigured() || emailsEnabled();

const APPOINTMENT_DURATION_MIN = Number(process.env.APPOINTMENT_DURATION_MIN) > 0 ? Number(process.env.APPOINTMENT_DURATION_MIN) : 60;

// "YYYY-MM-DD HH:MM:SS" (Bogotá) + minutos -> mismo formato
function addMinutes(normalized, minutes) {
  const [d, t] = normalized.split(" ");
  const [y, mo, da] = d.split("-").map(Number);
  const [h, mi, s] = t.split(":").map(Number);
  const moved = new Date(Date.UTC(y, mo - 1, da, h, mi, s || 0) + minutes * 60000);
  return moved.toISOString().slice(0, 19).replace("T", " ");
}

// "2026-11-10 15:00:00" (Bogotá) -> "2026-11-10T15:00:00-05:00"
const toIsoBogota = (normalized) => `${normalized.replace(" ", "T")}-05:00`;

// "2026-11-10 15:00:00" (Bogotá) -> "20261110T200000Z" (UTC, formato de archivos .ics)
function toIcsUtc(normalized) {
  const [d, t] = normalized.split(" ");
  const [y, mo, da] = d.split("-").map(Number);
  const [h, mi, s] = t.split(":").map(Number);
  const utc = new Date(Date.UTC(y, mo - 1, da, h + 5, mi, s || 0));
  return utc.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}

// Archivo de calendario (.ics) que el cliente puede abrir para agregar la cita a su calendario
function buildIcs({ appointment, startLocal, endLocal, clientName, clientEmail }) {
  const esc = (s) =>
    String(s).replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\r?\n/g, "\\n");
  const stamp = new Date().toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
  return (
    [
      "BEGIN:VCALENDAR",
      "VERSION:2.0",
      "PRODID:-//ClosetVR//Citas//ES",
      "CALSCALE:GREGORIAN",
      "METHOD:PUBLISH",
      "BEGIN:VEVENT",
      `UID:cita-${appointment.id}@closetvr`,
      `DTSTAMP:${stamp}`,
      `DTSTART:${toIcsUtc(startLocal)}`,
      `DTEND:${toIcsUtc(endLocal)}`,
      `SUMMARY:${esc(`ClosetVR - ${appointment.service_name}`)}`,
      `DESCRIPTION:${esc(`Cliente: ${clientName} (${clientEmail})\nServicio: ${appointment.service_name}\nCita #${appointment.id}`)}`,
      "STATUS:CONFIRMED",
      "BEGIN:VALARM",
      "TRIGGER:-PT30M",
      "ACTION:DISPLAY",
      "DESCRIPTION:Tu cita en ClosetVR empieza en 30 minutos",
      "END:VALARM",
      "END:VEVENT",
      "END:VCALENDAR",
    ].join("\r\n") + "\r\n"
  );
}

async function sendToMake(userId, appointment, origin) {
  const url = (process.env.MAKE_WEBHOOK_URL || "").trim();
  if (!url) return;
  if (typeof fetch !== "function") {
    console.error("❌ Make: este Node no tiene fetch. Actualiza a Node 18 o superior.");
    return;
  }

  const rows = await query("SELECT name, email FROM public.users WHERE id = $1", [userId]);
  const user = rows[0];
  if (!user) return;

  const startLocal = appointment.appointment_date; // "YYYY-MM-DD HH:MM:SS" (hora de Bogotá)
  const endLocal = addMinutes(startLocal, APPOINTMENT_DURATION_MIN);
  const [date, time] = startLocal.split(" ");
  const clientName = user.name || "cliente";

  const payload = {
    event: "appointment_created",
    appointment_id: appointment.id,
    status: appointment.status,
    service: appointment.service_name,
    channel: origin,
    date, // 2026-11-10
    time: time.slice(0, 5), // 15:00
    date_pretty: prettyDate(startLocal),
    start_local: startLocal,
    end_local: endLocal,
    start_iso: toIsoBogota(startLocal), // para Google Calendar
    end_iso: toIsoBogota(endLocal),
    duration_minutes: APPOINTMENT_DURATION_MIN,
    timezone: TZ,
    client_id: userId,
    client_name: clientName,
    client_email: user.email,
    org_email: (process.env.ADMIN_EMAIL || "").trim(),
    created_at: new Date().toISOString(),
    ics: buildIcs({ appointment, startLocal, endLocal, clientName, clientEmail: user.email }),
  };

  for (let attempt = 1; attempt <= 2; attempt++) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 10_000);
    try {
      const resp = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        signal: controller.signal,
      });
      if (!resp.ok) throw new Error(`Make respondió ${resp.status}`);
      console.log(`🔗 Cita #${appointment.id} enviada a Make`);
      return;
    } catch (err) {
      if (attempt === 2) {
        console.error(`❌ No se pudo enviar la cita #${appointment.id} a Make: ${err.message}`);
      } else {
        await sleep(2000);
      }
    } finally {
      clearTimeout(timer);
    }
  }
}

const escapeHtml = (s) =>
  String(s).replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch]));

async function sendAppointmentEmails(userId, appointment, origin) {
  const transport = getMailTransport();
  if (!transport) return;

  const rows = await query("SELECT name, email FROM public.users WHERE id = $1", [userId]);
  const user = rows[0];
  if (!user) return;

  const when = prettyDate(appointment.appointment_date);
  const service = appointment.service_name;
  const from = process.env.MAIL_FROM || `ClosetVR <${process.env.SMTP_USER}>`;
  const adminTo = (process.env.ADMIN_EMAIL || process.env.SMTP_USER).trim();
  const name = user.name || "cliente";

  const clientMail = {
    from,
    to: user.email,
    subject: `✅ Tu cita en ClosetVR: ${service} — ${when}`,
    text:
      `Hola ${name},\n\nTu cita quedó agendada:\n` +
      `• Servicio: ${service}\n• Fecha y hora: ${when} (hora de Bogotá)\n• Estado: Confirmada\n• Número de cita: #${appointment.id}\n\n` +
      `Si necesitas cambiarla, responde a este correo o escríbenos desde la app.\n\n— Equipo ClosetVR`,
    html:
      `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#12263f">` +
      `<h2 style="color:#16a8b3">¡Tu cita está confirmada!</h2><p>Hola ${escapeHtml(name)},</p>` +
      `<table style="border-collapse:collapse;width:100%">` +
      `<tr><td style="padding:6px 0"><b>Servicio</b></td><td>${escapeHtml(service)}</td></tr>` +
      `<tr><td style="padding:6px 0"><b>Fecha y hora</b></td><td>${escapeHtml(when)} <small>(hora de Bogotá)</small></td></tr>` +
      `<tr><td style="padding:6px 0"><b>Estado</b></td><td>Confirmada</td></tr>` +
      `<tr><td style="padding:6px 0"><b>N.º de cita</b></td><td>#${appointment.id}</td></tr></table>` +
      `<p>Si necesitas cambiarla, responde a este correo o escríbenos desde la app.</p><p>— Equipo ClosetVR</p></div>`,
  };

  const adminMail = {
    from,
    to: adminTo,
    subject: `📅 Nueva cita #${appointment.id}: ${name} — ${when}`,
    text:
      `Se agendó una nueva cita en ClosetVR.\n\nCliente: ${name} <${user.email}>\nServicio: ${service}\n` +
      `Fecha y hora: ${when} (hora de Bogotá)\nCanal: ${origin}\nNúmero de cita: #${appointment.id}`,
    html:
      `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#12263f">` +
      `<h2 style="color:#16a8b3">Nueva cita agendada</h2>` +
      `<p><b>Cliente:</b> ${escapeHtml(name)} &lt;${escapeHtml(user.email)}&gt;<br>` +
      `<b>Servicio:</b> ${escapeHtml(service)}<br><b>Fecha y hora:</b> ${escapeHtml(when)} (hora de Bogotá)<br>` +
      `<b>Canal:</b> ${escapeHtml(origin)}<br><b>N.º de cita:</b> #${appointment.id}</p></div>`,
  };

  const results = await Promise.allSettled([transport.sendMail(clientMail), transport.sendMail(adminMail)]);
  results.forEach((r, i) => {
    const dest = i === 0 ? user.email : adminTo;
    if (r.status === "fulfilled") {
      console.log(`📧 Correo de la cita #${appointment.id} enviado a ${dest}`);
    } else {
      const reason = r.reason || {};
      const hint =
        reason.code === "EAUTH"
          ? " (Gmail: usa una CONTRASEÑA DE APLICACIÓN con la verificación en 2 pasos activada, no tu contraseña normal)"
          : "";
      console.error(`❌ No se pudo enviar el correo a ${dest}: ${reason.message || reason}${hint}`);
    }
  });
}

function notifyAppointmentCreated(userId, appointment, origin) {
  // Correos directos por SMTP (opcional) y/o aviso a Make; ninguno bloquea la respuesta al usuario
  sendAppointmentEmails(userId, appointment, origin).catch((err) =>
    console.error("❌ Error preparando los correos de la cita:", err.message)
  );
  sendToMake(userId, appointment, origin).catch((err) =>
    console.error("❌ Error enviando la cita a Make:", err.message)
  );
}

// ---------------------------------------------------------------------------
// Gemini (los reintentos y el modelo de respaldo viven en config/gemini.js)
// ---------------------------------------------------------------------------
function geminiErrorResponse(err) {
  if (err.status === 503) {
    return { status: 503, error: "El asesor está recibiendo muchas solicitudes ahora mismo. Intenta de nuevo en unos segundos." };
  }
  if (err.status === 429) {
    return { status: 429, error: "El asesor alcanzó su límite de uso. Intenta de nuevo en un minuto." };
  }
  if (err.status === 404) {
    return { status: 502, error: `El modelo de IA "${GEMINI_MODEL}" no está disponible. Revisa GEMINI_MODEL.` };
  }
  return { status: 502, error: "El asesor virtual no está disponible temporalmente." };
}

function extractJsonObject(text) {
  const cleaned = String(text).replace(/```json|```/gi, "").trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start === -1 || end <= start) throw new Error("La IA no devolvió un objeto JSON");
  return JSON.parse(cleaned.slice(start, end + 1));
}

// El historial que manda la app: [{ role: "user" | "assistant", text }]
function sanitizeHistory(raw) {
  if (!Array.isArray(raw)) return [];
  return raw
    .slice(-10)
    .map((m) => ({
      role: m?.role === "user" ? "Cliente" : "Asesor",
      text: String(m?.text ?? "").trim().slice(0, 600),
    }))
    .filter((m) => m.text);
}

// Elige el servicio de la lista que más se parezca al que dijo la IA (o el primero)
function matchService(raw) {
  const wanted = String(raw || "").trim().toLowerCase();
  if (!wanted) return SERVICES[0];
  return (
    SERVICES.find((s) => s.toLowerCase() === wanted) ||
    SERVICES.find((s) => wanted.includes(s.toLowerCase()) || s.toLowerCase().includes(wanted)) ||
    SERVICES[0]
  );
}

// ---------------------------------------------------------------------------
// 1) Crear cita manualmente (la identidad sale del token, NO del body)
// ---------------------------------------------------------------------------
router.post("/appointments", requireAuth, async (req, res) => {
  const serviceName = String(req.body?.serviceName ?? "").trim();
  const date = normalizeDate(req.body?.appointmentDate);

  if (!serviceName || serviceName.length > 150) {
    return res.status(400).json({ error: "El servicio es obligatorio (máx. 150 caracteres)." });
  }
  if (!date) {
    return res.status(400).json({ error: "Fecha inválida. Usa el formato AAAA-MM-DD HH:MM (ej. 2026-11-10 15:00)." });
  }
  if (date <= nowBogota()) {
    return res.status(400).json({ error: "La fecha de la cita debe ser futura." });
  }

  try {
    const { duplicate, appointment } = await createAppointment(req.userId, serviceName, date, "manual (formulario)");
    if (duplicate) {
      return res.status(409).json({ error: "Ya tienes una cita agendada en ese horario." });
    }
    res.status(201).json({ success: true, message: "¡Cita agendada con éxito!", appointment });
  } catch (err) {
    console.error("Error al agendar la cita:", err);
    res.status(500).json({ error: "No se pudo registrar la cita en este momento." });
  }
});

// ---------------------------------------------------------------------------
// 2) Listar las citas del usuario
// ---------------------------------------------------------------------------
router.get("/appointments", requireAuth, async (req, res) => {
  try {
    const rows = await query(
      `SELECT ${APPOINTMENT_COLUMNS} FROM public.appointments
       WHERE user_id = $1
       ORDER BY appointment_date DESC, id DESC
       LIMIT 50`,
      [req.userId]
    );
    res.json(rows);
  } catch (err) {
    console.error("Error al listar citas:", err);
    res.status(500).json({ error: "No se pudieron cargar tus citas." });
  }
});

// ---------------------------------------------------------------------------
// 3) Chat de texto con el asesor (Gemini). Si el cliente confirma servicio y
//    horario, la cita se guarda y la respuesta lo confirma.
// ---------------------------------------------------------------------------
router.post("/chat", requireAuth, async (req, res) => {
  const message = String(req.body?.message ?? "").trim();
  if (!message) return res.status(400).json({ error: "El mensaje no puede estar vacío." });
  if (message.length > 1000) return res.status(400).json({ error: "El mensaje es demasiado largo (máx. 1000)." });
  if (!process.env.GEMINI_API_KEY) {
    return res.status(500).json({ error: "El servidor no tiene configurada la GEMINI_API_KEY" });
  }

  const history = sanitizeHistory(req.body?.history);

  try {
    const userName = await getUserName(req.userId);
    const conversation = history.length
      ? history.map((m) => `${m.role}: ${m.text}`).join("\n")
      : "(sin mensajes previos)";

    const prompt = `
Eres el asesor de imagen de ClosetVR, una app de moda con armario virtual y tienda.
Hablas en español, con tono cálido y profesional, en máximo 4 frases cortas.
Puedes dar recomendaciones de estilo y agendar citas de asesoría.

Servicios disponibles: ${SERVICES.join(" | ")}.
Fecha y hora actual en Bogotá: ${nowBogota()} (${weekdayBogota()}).
Nombre del cliente: ${userName}.

Reglas para agendar:
- Para agendar necesitas el servicio y la fecha y hora exactas. Si falta algo, pregúntalo (una cosa a la vez) y NO agendes todavía.
- Agenda solo cuando el cliente lo haya confirmado claramente.
- Solo fechas futuras. Resuelve "mañana", "el viernes", etc. usando la fecha actual.
- Si no vas a agendar, "appointment" debe ser null.

Responde ÚNICAMENTE con un objeto JSON, sin texto adicional ni markdown, con esta forma exacta:
{"reply": "texto para el cliente", "appointment": null}
o, cuando el cliente confirmó la cita:
{"reply": "texto para el cliente", "appointment": {"service": "uno de los servicios", "date": "YYYY-MM-DD HH:mm"}}

Conversación previa:
${conversation}

Mensaje nuevo del cliente: ${message}
`;

    const result = await generateWithRetry(prompt, { responseMimeType: "application/json", temperature: 0.6 });

    let parsed;
    try {
      parsed = extractJsonObject(result.response.text());
    } catch (_) {
      // Si la IA no respetó el formato, devolvemos su texto tal cual (sin agendar)
      return res.json({ reply: String(result.response.text()).trim() || "¿Podrías repetirme eso, por favor?" });
    }

    let reply = String(parsed.reply || "").trim() || "Con gusto te ayudo. ¿Qué necesitas?";
    let appointment = null;

    if (parsed.appointment && typeof parsed.appointment === "object") {
      const date = normalizeDate(parsed.appointment.date);
      if (!date) {
        reply += "\n\n⚠️ No pude entender la fecha. ¿Me la confirmas con día y hora? (ej. 10 de noviembre a las 3 pm)";
      } else if (date <= nowBogota()) {
        reply += "\n\n⚠️ Esa fecha ya pasó. ¿Qué otro día y hora te sirve?";
      } else {
        const service = matchService(parsed.appointment.service);
        const { duplicate, appointment: saved } = await createAppointment(req.userId, service, date, "chat de texto");
        if (duplicate) {
          reply += `\n\n⚠️ Ya tienes una cita en ese horario (${prettyDate(date)}). ¿Quieres otro horario?`;
        } else {
          appointment = saved;
          reply += `\n\n✅ Cita agendada: ${service}, ${prettyDate(date)}`;
          if (confirmationEnabled()) reply += "\n📧 Te enviaremos la confirmación a tu correo.";
        }
      }
    }

    res.json({ reply, appointment });
  } catch (err) {
    console.error("Error en el chat del asesor:", err);
    const { status, error } = geminiErrorResponse(err);
    res.status(status).json({ error });
  }
});

// ---------------------------------------------------------------------------
// 4) Recomendación puntual (se conserva; ahora exige sesión)
// ---------------------------------------------------------------------------
router.post("/ai-assistant", requireAuth, async (req, res) => {
  const userPreference = String(req.body?.userPreference ?? "").trim();
  if (!userPreference) return res.status(400).json({ error: "Falta userPreference." });
  if (!process.env.GEMINI_API_KEY) {
    return res.status(500).json({ error: "El servidor no tiene configurada la GEMINI_API_KEY" });
  }
  try {
    const prompt = `
Actúa como asistente de ventas y gestor de citas de una tienda de moda (ClosetVR).
Servicios disponibles: ${SERVICES.join(" | ")}.
El cliente dice: "${userPreference.slice(0, 1000)}".
Recomiéndale el servicio ideal y un horario sugerido. Responde amable, breve y en español.
`;
    const result = await generateWithRetry(prompt);
    res.json({ success: true, recommendation: result.response.text() });
  } catch (err) {
    console.error("Error con la IA en citas:", err);
    const { status, error } = geminiErrorResponse(err);
    res.status(status).json({ error });
  }
});

// ---------------------------------------------------------------------------
// 5) Crear la llamada de voz en Retell y devolverle a la app lo que necesita
// ---------------------------------------------------------------------------
function retellErrorMessage(err) {
  switch (err?.status) {
    case 401:
      return "Retell rechazó la RETELL_API_KEY (inválida o sin permisos). Revisa el .env.";
    case 402:
      return "Tu cuenta de Retell no tiene saldo/créditos para iniciar llamadas.";
    case 404:
    case 422:
      return "Retell no encontró el agente. Revisa RETELL_AGENT_ID en el .env (y que el agente esté publicado).";
    case 429:
      return "Retell está limitando las llamadas (demasiadas simultáneas). Intenta en un momento.";
    default:
      return "No se pudo iniciar la llamada de voz con Retell.";
  }
}

router.post("/create-call", requireAuth, async (req, res) => {
  const apiKey = (process.env.RETELL_API_KEY || "").trim();
  const agentId = (process.env.RETELL_AGENT_ID || "").trim();
  if (!apiKey || !agentId) {
    return res.status(500).json({ error: "Faltan RETELL_API_KEY o RETELL_AGENT_ID en el .env del servidor." });
  }

  try {
    const userName = await getUserName(req.userId);
    const retell = new Retell({ apiKey });

    const call = await retell.call.createWebCall({
      agent_id: agentId,
      // metadata: Retell la devuelve en el webhook -> así sabemos de QUIÉN es la cita
      metadata: { user_id: req.userId },
      // Variables que el agente puede usar en su prompt como {{user_name}}, {{current_datetime}}...
      retell_llm_dynamic_variables: {
        user_name: userName,
        current_datetime: `${nowBogota()} (${weekdayBogota()}), hora de Bogotá`,
        available_services: SERVICES.join(", "),
      },
    });

    if (!call.access_token) {
      console.error("Respuesta de Retell sin access_token:", call);
      return res.status(502).json({ error: "Retell no devolvió un token de acceso válido." });
    }

    console.log(`📞 Llamada web creada: ${call.call_id} (transporte: ${call.transport || "livekit"}) para el usuario ${req.userId}`);

    res.json({
      success: true,
      accessToken: call.access_token,
      callId: call.call_id,
      // Retell decide cómo se conecta el navegador/WebView: "gateway" (WebRTC) o "livekit"
      transport: call.transport || "livekit",
      iceServers: call.ice_servers || [],
      expiresAt: call.expires_at,
    });
  } catch (err) {
    console.error("Error al crear la llamada con Retell:", err?.status, err?.message);
    res.status(502).json({ error: retellErrorMessage(err), detail: err?.message });
  }
});

// ---------------------------------------------------------------------------
// 6) Resultado de la llamada de voz: ¿se acordó una cita?
//    Se usa por DOS caminos (el primero basta; el segundo es respaldo):
//      a) la app consulta GET /call-result/:callId al colgar (no depende de webhooks)
//      b) Retell llama al webhook (evento call_analyzed)
//    La cita sale de call_analysis.custom_analysis_data (si configuraste Post-Call Analysis en Retell)
//    o, si no está, Gemini la deduce de la transcripción de la llamada.
// ---------------------------------------------------------------------------
const callResultCache = new Map(); // callId -> resultado final (evita repetir Gemini, citas y correos)

async function extractAppointmentFromTranscript(transcript) {
  const prompt = `
Revisa la transcripción de una llamada entre el asesor de imagen de ClosetVR (agent) y un cliente (user).
Fecha y hora actual en Bogotá: ${nowBogota()} (${weekdayBogota()}).
Servicios disponibles: ${SERVICES.join(" | ")}.

Determina si el cliente y el asesor ACORDARON una cita con servicio y fecha/hora concretas (el cliente aceptó o confirmó).
No inventes datos: si no hay una fecha y hora claras y aceptadas, devuelve "appointment": null.
Resuelve "mañana", "el viernes", etc. usando la fecha actual.

Responde ÚNICAMENTE con JSON, sin markdown:
{"appointment": null}
o
{"appointment": {"service": "uno de los servicios", "date": "YYYY-MM-DD HH:mm"}}

Transcripción:
${String(transcript).slice(0, 8000)}
`;
  const result = await generateWithRetry(prompt, { responseMimeType: "application/json", temperature: 0 });
  return extractJsonObject(result.response.text()).appointment || null;
}

// Devuelve { status: "pending" | "saved" | "duplicate" | "none", userId, appointment?, reason? }
async function processCall(call) {
  const callId = call?.call_id;
  if (callId && callResultCache.has(callId)) return callResultCache.get(callId);

  const analysis = call.call_analysis || call.analysis || {}; // Retell usa call_analysis
  const data = analysis.custom_analysis_data || {};
  const userId = Number(call.metadata?.user_id ?? data.user_id);

  const final = (r) => {
    const out = { userId: Number.isInteger(userId) ? userId : null, ...r };
    if (callId) callResultCache.set(callId, out);
    return out;
  };

  if (!Number.isInteger(userId) || userId <= 0) {
    console.warn("[retell] llamada sin user_id en metadata; no se puede asignar la cita.");
    return final({ status: "none", reason: "sin user_id" });
  }
  if (["registered", "ongoing"].includes(call.call_status)) return { status: "pending", userId };
  if (["not_connected", "error"].includes(call.call_status)) return final({ status: "none", reason: "la llamada no se conectó" });

  let service = String(data.service_name || "").trim();
  let date = normalizeDate(data.appointment_date);

  if (!date) {
    // Sin campos de análisis válidos: la IA revisa la transcripción
    const transcript = String(call.transcript || "").trim();
    if (!transcript) {
      const ageMs = call.end_timestamp ? Date.now() - call.end_timestamp : 0;
      if (ageMs > 90_000) return final({ status: "none", reason: "la llamada no tiene transcripción" });
      return { status: "pending", userId, reason: "esperando la transcripción" };
    }
    try {
      const found = await extractAppointmentFromTranscript(transcript);
      if (!found) return final({ status: "none", reason: "no se acordó una cita" });
      date = normalizeDate(found.date);
      service = matchService(found.service);
    } catch (err) {
      console.error("[retell] no se pudo analizar la transcripción:", err.message);
      return { status: "pending", userId, reason: "analizando la llamada" }; // se reintenta en la próxima consulta
    }
  }

  if (!date) return final({ status: "none", reason: "fecha no interpretable" });
  if (date <= nowBogota()) return final({ status: "none", reason: "la fecha acordada ya pasó" });

  const exists = await query("SELECT id FROM public.users WHERE id = $1", [userId]);
  if (exists.length === 0) return final({ status: "none", reason: "usuario inexistente" });

  const { duplicate, appointment } = await createAppointment(
    userId,
    (service || "Asesoría por Voz").slice(0, 150),
    date,
    "llamada de voz"
  );
  console.log(
    duplicate
      ? `[retell] cita duplicada ignorada (usuario ${userId}, ${date}).`
      : `✅ [retell] cita guardada #${appointment.id} (usuario ${userId}, ${date}).`
  );
  return final({ status: duplicate ? "duplicate" : "saved", appointment });
}

// La app consulta aquí al colgar (cada pocos segundos hasta que deje de estar "pending")
router.get("/call-result/:callId", requireAuth, async (req, res) => {
  const callId = String(req.params.callId || "").trim();
  if (!callId || callId.length > 100) return res.status(400).json({ error: "callId inválido" });

  const cached = callResultCache.get(callId);
  if (cached) {
    if (cached.userId !== req.userId) return res.status(403).json({ error: "Esta llamada no es tuya." });
    return res.json({ status: cached.status, appointment: cached.appointment || null, reason: cached.reason || null, emailSent: confirmationEnabled() });
  }

  const apiKey = (process.env.RETELL_API_KEY || "").trim();
  if (!apiKey) return res.status(500).json({ error: "Falta RETELL_API_KEY en el .env del servidor." });

  try {
    const call = await new Retell({ apiKey }).call.retrieve(callId);
    if (Number(call.metadata?.user_id) !== req.userId) {
      return res.status(403).json({ error: "Esta llamada no es tuya." });
    }
    const result = await processCall(call);
    res.json({ status: result.status, appointment: result.appointment || null, reason: result.reason || null, emailSent: confirmationEnabled() });
  } catch (err) {
    console.error("Error consultando la llamada en Retell:", err?.status, err?.message);
    if (err?.status === 404) return res.status(404).json({ error: "Retell no encontró esa llamada." });
    res.status(502).json({ error: retellErrorMessage(err), detail: err?.message });
  }
});

// ---------------------------------------------------------------------------
// 7) Webhook de Retell (opcional, respaldo del camino anterior)
//    Retell debe apuntar a:  https://<tu-ngrok>/api/retell-webhook   (evento call_analyzed)
// ---------------------------------------------------------------------------
async function handleRetellEvent(body) {
  const { event, call } = body || {};
  console.log(`📨 [retell] webhook recibido: ${event}`);
  if (event !== "call_analyzed" || !call) return;
  const result = await processCall(call);
  console.log(`[retell] llamada ${call.call_id}: ${result.status}${result.reason ? ` (${result.reason})` : ""}`);
}

router.post("/retell-webhook", async (req, res) => {
  // Verificación de firma: evita que cualquiera con tu URL de ngrok invente citas.
  // (Para depurar puedes poner RETELL_VERIFY_WEBHOOK=false en el .env, pero no lo dejes así.)
  if (process.env.RETELL_VERIFY_WEBHOOK !== "false") {
    const signature = req.get("x-retell-signature");
    let valid = false;
    try {
      valid =
        !!signature &&
        !!process.env.RETELL_API_KEY &&
        (await Retell.verify(req.rawBody || JSON.stringify(req.body), process.env.RETELL_API_KEY.trim(), signature));
    } catch (_) {
      valid = false;
    }
    if (!valid) {
      console.warn(
        "⚠️ [retell] webhook rechazado: firma inválida. Usa en .env una RETELL_API_KEY con la insignia de webhook en el panel de Retell."
      );
      return res.status(401).json({ error: "Firma inválida" });
    }
  }

  // Respondemos 200 enseguida (si Retell no recibe 2xx reintenta) y procesamos después
  res.status(200).json({ received: true });
  try {
    await handleRetellEvent(req.body);
  } catch (err) {
    console.error("Error procesando el webhook de Retell:", err);
  }
});

module.exports = router;