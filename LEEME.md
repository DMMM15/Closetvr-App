# ClosetVR — Proyecto completo (Android + Backend)

Este paquete junta en un solo lugar la **app Android más actualizada** (versión rediseño "Deep Ocean")
y el **backend Node.js** del zip anterior, con el **módulo de Outfits con IA** terminado en ambos lados.

```
closetvr-completo/
├── LEEME.md                  ← este archivo
├── android/                  ← proyecto Android (abrir ESTA carpeta en Android Studio)
│   ├── src/main/java/com/closetvr/app/...
│   ├── build.gradle.kts, settings.gradle.kts, gradlew, google-services.json, local.properties
│   └── (sin carpeta build/.gradle/.idea: Android Studio las regenera)
└── backend/                  ← API Node.js + Express + PostgreSQL
    ├── src/
    │   ├── index.js          (sin cambios; ya monta /api/outfits)
    │   ├── config/db.js      (sin cambios; pool de PostgreSQL con variables PG*)
    │   ├── middleware/auth.js
    │   └── routes/           auth.js, cart.js, closet.js, products.js, outfits.js  ← outfits.js es el nuevo
    ├── sql/
    │   ├── schema.sql        (tablas originales: users, products, cart_items, closet_items)
    │   └── outfits.sql       ← NUEVO: tablas de outfits (se ejecuta A MANO en Aiven)
    ├── uploads/              (carpeta donde se guardan las fotos; DEBE existir, el código no la crea)
    ├── .env                  (tu archivo original, ya con BASE_URL de ngrok)
    ├── .env.example          (plantilla sin secretos)
    ├── .gitignore, nodemon.json, package.json, package-lock.json
```

> No incluí `node_modules` (se instala con `npm install`), ni las fotos viejas de `uploads/`,
> ni el README viejo del backend, ni `build/`, `.gradle/`, `.idea/` ni el volcado `java_pid29236.hprof` (765 MB) de la app.

---

## 1. Qué hace el módulo de Outfits con IA

### Flujo completo
1. En **Armario** el usuario toca **✨ Combinar con IA** (aparece cuando tiene 2 o más prendas).
2. Se abre un diálogo que pide **nombre del outfit**, **clima** y **estilo**. Aún no se llama a la API.
3. Al tocar **Crear outfit**, la app envía `POST /api/outfits/generate` con `{clima, estilo, nombre}`.
   El botón muestra un `CircularProgressIndicator` y el diálogo no se puede cerrar mientras la IA trabaja.
4. El backend lee las prendas del usuario (`closet_items`), se las manda a Gemini y **le ordena responder únicamente
   un arreglo JSON de IDs** (ej. `[10, 12]`).
5. El backend valida esos IDs, guarda el outfit y devuelve el outfit completo.
6. La app cierra el diálogo, abre la pestaña **Mis Outfits**, pone el nuevo outfit arriba y muestra un aviso.
   Si algo falla, el error aparece dentro del diálogo y se puede reintentar sin volver a escribir el nombre.

### Backend — `backend/src/routes/outfits.js` (único archivo del backend modificado)

**POST `/api/outfits/generate`** (requiere sesión; el usuario sale del token, nunca del body)
- Valida que `clima`, `estilo` y `nombre` existan y no pasen de 100 caracteres → si no, `400`.
- Exige al menos 2 prendas en el armario → si no, `400`.
- Usa el modelo **`gemini-3.8-flash`** (se puede cambiar con `GEMINI_MODEL` en el `.env`) con
  `responseMimeType: application/json`, y un prompt que pide *únicamente* el arreglo JSON de IDs.
- Limpia la respuesta (tolera ```` ```json ````), y **descarta IDs repetidos, inexistentes o que no sean del usuario**.
  Si quedan menos de 2 prendas válidas → `502` y no se guarda nada.
- Guarda en **una sola transacción**: maestro en `public.outfits` (con `nombre`) y detalle en `public.outfit_items`.
  Si algo falla se hace `ROLLBACK`; nunca queda un outfit a medias.
- Responde `201` con `{ success, outfit: { id, name, clima, estilo, created_at, prendas: [...] } }`.
- Errores amables: Gemini `429` → `429` ("límite de uso"), modelo inexistente (`404`) → mensaje que apunta a `GEMINI_MODEL`.

**GET `/api/outfits`** (requiere sesión)
- Devuelve **todos los outfits del usuario**, más nuevos primero, con `JOIN` a `outfit_items` y `closet_items`
  y cada prenda adjunta como `{ id, name, category, image_url }`.

Usa el pool existente (`config/db.js`) y variables de entorno (`process.env`). **No crea tablas por código.**

> Corrección importante respecto al borrador anterior: ya no se "traga" los errores de base de datos ni inventa IDs;
> si algo falla, la app recibe el error real.

### Android — qué se tocó (todo dentro de las carpetas que ya existían)
| Archivo | Cambio |
|---|---|
| `data/model/OutfitModels.kt` | La petición ahora incluye `nombre`; modelos nuevos `Outfit`, `OutfitPrenda`; `GenerateOutfitResponse` trae el outfit completo. |
| `data/network/ApiService.kt` | Nuevo `GET outfits` (`getOutfits`). Las rutas son relativas porque `BASE_URL` termina en `/api/`. |
| `data/repository/OutfitRepository.kt` | `getOutfits` y `generateOutfit`; muestra el mensaje de error que manda el backend. |
| `ui/screens/outfits/OutfitViewModel.kt` | Estados **Loading / Success / Error** para la lista y para la generación. |
| `ui/screens/outfits/GenerateOutfitDialog.kt` | `AlertDialog` con nombre + clima + estilo, estilo Deep Ocean, indicador de carga y error integrado. |
| `ui/screens/outfits/OutfitsTab.kt` | **Nuevo.** Lista de outfits (tarjeta con nombre, clima • estilo • n.º de prendas y miniaturas), spinner, error con *Reintentar* y *empty state*. |
| `ui/screens/closet/ClosetScreen.kt` | `TabRow` con **Mis Prendas** (cuadrícula por categorías, como ya la tenías) y **Mis Outfits**; botón ✨ Combinar con IA. |
| `data/network/RetrofitClient.kt` | **Sin cambios**: ya tenía timeout de 60 s y el header `ngrok-skip-browser-warning: true`. |

---

## 2. Contrato app ↔ backend (revisado)
Rutas de la app (sobre `https://obliged-output-dole.ngrok-free.dev/api/`) contra el backend: `auth/login`, `auth/register`,
`auth/google`, `products`, `cart` (GET/POST/DELETE `cart/{cart_item_id}`), `closet` (GET, `closet/photo`, `closet/from-store`, DELETE),
`outfits` (GET) y `outfits/generate` (POST). Todas coinciden con las rutas y los nombres de campos del backend.

---

## 3. Pasos manuales para dejarlo funcionando

### 3.1 Crear las tablas en Aiven (una sola vez)
Abre `backend/sql/outfits.sql`, pégalo en la consola SQL de Aiven y ejecútalo. Crea `public.outfits` y `public.outfit_items`
(con índices y `ON DELETE CASCADE`). Las columnas coinciden 1 a 1 con los `INSERT`/`SELECT` de `outfits.js`.
Al final trae una consulta que lista las columnas creadas para que verifiques.
Si habías creado antes estas tablas con otros nombres de columna, el archivo trae (comentadas) las líneas `DROP TABLE` para recrearlas.

### 3.2 Revisar `backend/.env`
Debe tener: `PORT`, `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE`, `JWT_SECRET`, `GEMINI_API_KEY` y `BASE_URL`.
(Este proyecto usa las variables `PG*`, no `DATABASE_URL`.) `BASE_URL=https://obliged-output-dole.ngrok-free.dev` **sin "/" al final**.
Opcional: `GEMINI_MODEL=...` si `gemini-3.8-flash` no existe en tu cuenta.

### 3.3 Levantar el backend (PowerShell)
```powershell
cd closetvr-completo\backend
npm install
npm run dev
```
Debe imprimir `🚀 Closet VR backend escuchando en http://0.0.0.0:3000`. Prueba: `curl.exe http://localhost:3000/`.

### 3.4 Abrir el túnel de ngrok (otra terminal)
```powershell
ngrok http --url=obliged-output-dole.ngrok-free.dev 3000
```
(En versiones antiguas de ngrok usa `--domain=` en lugar de `--url=`.)
Prueba: `curl.exe https://obliged-output-dole.ngrok-free.dev/` → `{"status":"ok","service":"Closet VR API"}`.

### 3.5 Android
1. Android Studio → **File → Open** → carpeta `closetvr-completo\android`.
2. **Sync Project with Gradle Files** y **Run ▶**. No hay dependencias nuevas.
3. `RetrofitClient.kt` ya apunta a `https://obliged-output-dole.ngrok-free.dev/api/`.

### 3.6 Probar el flujo
Inicia sesión → agrega al menos 2 prendas (cámara o tienda) → **Armario → Mis Outfits → ✨ Combinar con IA** →
escribe un nombre, elige clima y estilo → **Crear outfit**.

---

## 4. Qué se verificó (y qué no)
**Verificado de verdad**: levanté PostgreSQL 16 local con tu `schema.sql` + `outfits.sql` y corrí el backend real contra él
(solo la llamada a Gemini fue simulada, porque no hay acceso a internet/API aquí). Resultados:
sin token → 401; falta `nombre` → 400; textos >100 caracteres → 400; la IA devuelve markdown + IDs repetidos/ajenos/inexistentes → se filtran y se guarda
solo lo válido (201); respuesta basura o con 1 solo ID válido → 502 **sin guardar nada**; Gemini 429 → 429 amable; `GET` devuelve los outfits
más nuevos primero con las prendas adjuntas; un usuario **no** ve los outfits de otro. También: `npm ci` limpio del backend empaquetado y sintaxis de todos los `.js`.
**No verificado**: la compilación de la app Android (no hay SDK de Android aquí) ni la respuesta real de Gemini con `gemini-3.8-flash`
(no pude comprobar que ese nombre de modelo exista). Si Android Studio marca algo al sincronizar o Gemini responde 404, avísame con el mensaje exacto.

## 5. Problemas frecuentes
- **"No se pudo conectar con el servidor"** en la app → el backend o ngrok no están corriendo, o la URL de `RetrofitClient.kt` no coincide con la de ngrok.
- **Las fotos no cargan** → las prendas guardadas con otra `BASE_URL` (por ejemplo una IP local) apuntan a un host viejo. Re-súbelas o actualiza `image_url` en `closet_items`.
  Las fotos viejas del zip anterior no se incluyeron: copia tu contenido de `uploads/` a `backend/uploads/` si quieres conservarlas.
- **Error 404 del modelo de IA** → pon `GEMINI_MODEL=<modelo disponible>` en `.env` y reinicia el backend.
- **`relation "outfits" does not exist`** → falta ejecutar `sql/outfits.sql` en Aiven.

## 6. Seguridad
`backend/.env` es tu archivo real (con contraseña de Aiven, clave de Gemini y JWT). **No lo compartas ni lo subas a git**
(`.gitignore` ya lo excluye). Si este zip sale de tu equipo, rota esas claves y comparte `.env.example` en su lugar.
