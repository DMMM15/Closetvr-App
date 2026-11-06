package com.closetvr.app.ui.screens.profile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.closetvr.app.data.network.CallResultResponse
import com.closetvr.app.data.network.ChatMessageRequest
import com.closetvr.app.data.network.ChatTurn
import com.closetvr.app.data.network.RetrofitClient
import com.closetvr.app.ui.theme.OceanSurfaceHigh
import com.closetvr.app.ui.theme.OceanTurquoise
import com.closetvr.app.ui.theme.OceanWhite
import com.closetvr.app.ui.theme.OceanError
import com.closetvr.app.util.TokenManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response

private const val GREETING =
    "¡Hola! Soy tu asesor de estilo de ClosetVR. Escribe un mensaje o presiona el micrófono para hablar y agendar tu cita."

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean
)

/**
- Puente WebView -> Kotlin. El JavaScript de la llamada de voz avisa por aquí:

-  - onTranscriptUpdate: transcripción en vivo (si Retell la envía)

-  - onCallState: "connected" | "ended" | "error" (con el detalle del error)

 */
class WebAppInterface(
    private val scope: CoroutineScope,
    private val onUpdate: (List<ChatMessage>) -> Unit,
    private val stateCallback: (state: String, detail: String) -> Unit
) {
    @JavascriptInterface
    fun onTranscriptUpdate(jsonStr: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val array = JSONArray(jsonStr)
                val newList = mutableListOf<ChatMessage>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val role = obj.optString("role", "")
                    val content = obj.optString("content", "")
                    if (content.isNotBlank()) {
                        newList.add(ChatMessage(text = content, isFromUser = (role == "user")))
                    }
                }
                onUpdate(newList)
            } catch (e: Exception) {
                Log.e("RetellChat", "Error al procesar transcripción: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun onCallState(state: String, detail: String) {
        scope.launch(Dispatchers.Main) { stateCallback(state, detail) }
    }
}

// Mensaje de error legible: usa el {"error": "..."} que manda el backend
private fun backendError(response: Response<*>, fallback: String): String {
    val raw = try { response.errorBody()?.string() } catch (e: Exception) { null }
    val message = try {
        raw?.let { JsonParser.parseString(it).asJsonObject.get("error")?.asString }
    } catch (e: Exception) {
        null
    }
    return message ?: "$fallback (código ${response.code()})"
}

// Página oculta que maneja la llamada de voz. LiveKit solo se usa si Retell lo pide;
// hoy Retell entrega las llamadas web por "gateway" (WebRTC directo), que se maneja en el JS.
private val RETELL_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<script src="https://cdn.jsdelivr.net/npm/livekit-client@2.5.7/dist/livekit-client.umd.js"></script>
</head>
<body>
<script>
(function () {
  var RETELL_API = "https://api.retellai.com";
  var SDK_VERSION = "3.0.3";
  var LIVEKIT_HOST = "wss://retell-ai-4ihahnq7.livekit.cloud";
  var session = null;
  var ended = true;

  function notify(state, detail) {
    try {
      if (window.Android && window.Android.onCallState) window.Android.onCallState(state, detail || "");
    } catch (e) {}
  }

  function friendly(err) {
    var name = err && err.name;
    if (name === "NotAllowedError" || name === "SecurityError") return "Permiso de micrófono denegado.";
    if (name === "NotFoundError") return "No se encontró un micrófono en el dispositivo.";
    return (err && err.message) ? err.message : "Error desconocido al conectar la llamada.";
  }

  function handleServerEvent(ev) {
    if (ev && ev.event_type === "update" && ev.transcript) {
      try { window.Android.onTranscriptUpdate(JSON.stringify(ev.transcript)); } catch (e) {}
    }
  }

  function finish(state, detail) {
    if (ended) return;
    ended = true;
    var s = session;
    session = null;
    try { if (s) s.stop(); } catch (e) {}
    notify(state, detail);
  }

  // Retell moderno: la llamada web va por "gateway" (WebRTC directo con la API de Retell)
  async function startGateway(cfg) {
    if (!cfg.callId) throw new Error("Falta callId para el transporte gateway.");
    var base = RETELL_API + "/webrtc-proxy/" + cfg.callId;
    function headers(extra) {
      var h = { "Authorization": "Bearer " + cfg.accessToken, "X-Retell-Client-JS-SDK-Version": SDK_VERSION };
      if (extra) { for (var k in extra) h[k] = extra[k]; }
      return h;
    }
    var ice = (cfg.iceServers && cfg.iceServers.length) ? cfg.iceServers : [{ urls: "stun:stun.l.google.com:19302" }];
    var pc = new RTCPeerConnection({ iceServers: ice });
    var stream = null, audioEl = null, dc = null, sessionId = null, connected = false, dropTimer = null;
    var pending = [];

    function sendCandidate(c) {
      if (!sessionId) return;
      fetch(base + "/v1/webrtc/sessions/" + sessionId, {
        method: "PATCH",
        headers: headers({ "Content-Type": "application/json" }),
        body: JSON.stringify({ candidate: c.toJSON() })
      }).catch(function (e) { console.log("trickle ICE falló:", e); });
    }

    function stop() {
      if (dropTimer) { clearTimeout(dropTimer); dropTimer = null; }
      if (sessionId) {
        fetch(base + "/v1/webrtc/sessions/" + sessionId, { method: "DELETE", headers: headers() }).catch(function () {});
      }
      try { if (dc) dc.close(); } catch (e) {}
      try { if (stream) stream.getTracks().forEach(function (t) { t.stop(); }); } catch (e) {}
      try { pc.close(); } catch (e) {}
      try { if (audioEl) { audioEl.pause(); audioEl.srcObject = null; } } catch (e) {}
      sessionId = null;
    }

    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
      stream.getTracks().forEach(function (t) { pc.addTrack(t, stream); });

      dc = pc.createDataChannel("control");
      dc.onmessage = function (ev) { try { handleServerEvent(JSON.parse(ev.data)); } catch (e) {} };

      pc.ontrack = function (ev) {
        var remote = (ev.streams && ev.streams[0]) ? ev.streams[0] : new MediaStream([ev.track]);
        audioEl = new Audio();
        audioEl.autoplay = true;
        audioEl.srcObject = remote;
        audioEl.play().catch(function () {});
      };
      pc.onicecandidate = function (ev) {
        if (!ev.candidate) return;
        if (!sessionId) { pending.push(ev.candidate); return; }
        sendCandidate(ev.candidate);
      };
      pc.onconnectionstatechange = function () {
        var s = pc.connectionState;
        if (!connected) return;
        if (s === "failed" || s === "closed") { finish("ended"); return; }
        if (s === "disconnected") {
          if (!dropTimer) dropTimer = setTimeout(function () { finish("ended"); }, 5000);
        } else if (dropTimer) { clearTimeout(dropTimer); dropTimer = null; }
      };

      var offer = await pc.createOffer();
      await pc.setLocalDescription(offer);

      var resp = await fetch(base + "/v1/webrtc/sessions", {
        method: "POST",
        headers: headers({ "Content-Type": "application/json" }),
        body: JSON.stringify({ identity: "client", sdp: pc.localDescription.sdp })
      });
      if (!resp.ok) {
        var txt = "";
        try { txt = await resp.text(); } catch (e) {}
        throw new Error("Retell rechazó la conexión (" + resp.status + "). " + txt);
      }
      var answer = await resp.json();
      sessionId = answer.session_id;
      connected = true;
      await pc.setRemoteDescription({ type: "answer", sdp: answer.sdp });
      var flush = pending; pending = [];
      flush.forEach(sendCandidate);
    } catch (err) {
      stop();
      throw err;
    }
    return { stop: stop };
  }

  // Compatibilidad: si Retell devuelve transporte "livekit"
  async function startLiveKit(cfg) {
    var LK = window.LivekitClient;
    if (!LK) throw new Error("No se pudo cargar el cliente de voz (LiveKit). Revisa tu conexión a internet.");
    var room = new LK.Room({
      audioCaptureDefaults: { autoGainControl: true, echoCancellation: true, noiseSuppression: true, channelCount: 1 }
    });
    var els = [];
    var connected = false;
    room.on(LK.RoomEvent.Disconnected, function () { if (connected) finish("ended"); });
    room.on(LK.RoomEvent.TrackSubscribed, function (track) {
      if (track.kind === "audio") {
        var el = track.attach();
        document.body.appendChild(el);
        els.push(el);
        try { el.play().catch(function () {}); } catch (e) {}
      }
    });
    room.on(LK.RoomEvent.DataReceived, function (payload, participant) {
      if (participant && participant.identity !== "server") return;
      try { handleServerEvent(JSON.parse(new TextDecoder().decode(payload))); } catch (e) {}
    });
    function stop() {
      try { room.disconnect(); } catch (e) {}
      els.forEach(function (el) { try { el.remove(); } catch (e) {} });
    }
    try {
      await room.connect(cfg.url || LIVEKIT_HOST, cfg.accessToken);
      connected = true;
      await room.localParticipant.setMicrophoneEnabled(true);
    } catch (err) {
      stop();
      throw err;
    }
    return { stop: stop };
  }

  window.startRetellCall = async function (cfg) {
    if (!ended) finish("ended");
    ended = false;
    try {
      if (!cfg || !cfg.accessToken) throw new Error("Token de acceso vacío.");
      var s = (cfg.transport === "livekit") ? await startLiveKit(cfg) : await startGateway(cfg);
      if (ended) { try { s.stop(); } catch (e) {} return; }
      session = s;
      notify("connected", cfg.transport || "gateway");
    } catch (err) {
      console.log("Error al conectar la llamada:", err);
      finish("error", friendly(err));
    }
  };

  window.stopRetellCall = function () { finish("ended"); };
})();
</script>
</body>
</html>
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatAsesorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }

    // --- CONTROLADOR DE AUDIO PARA EL ALTAVOZ ---
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var isCalling by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var callActive by remember { mutableStateOf(false) } // hubo una llamada iniciada (para confirmar la cita al terminar)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val microphonePermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val listState = rememberLazyListState()

    val voiceStartIndex = remember { intArrayOf(0) }        // desde dónde se reemplaza la transcripción
    val currentCallId = remember { arrayOfNulls<String>(1) } // id de la llamada en curso (para consultar su resultado)
    val attemptCounter = remember { intArrayOf(0) }         // identifica cada intento de llamada (para el timeout)

    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(ChatMessage(text = GREETING, isFromUser = false))
        }
    }

    LaunchedEffect(messages.size, isSending) {
        val count = messages.size + if (isSending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // ---- Al colgar: el backend revisa la llamada en Retell y, si se acordó una cita, la guarda ----
    suspend fun confirmAppointmentAfterCall(callId: String?) {
        if (callId.isNullOrBlank()) {
            messages.add(ChatMessage(text = "No pude identificar la llamada para revisar la cita.", isFromUser = false))
            return
        }
        var result: CallResultResponse? = null
        var errorText: String? = null

        // Retell tarda unos segundos en tener la transcripción lista: consultamos hasta ~60 s
        repeat(15) {
            if (result == null && errorText == null) {
                delay(4_000L)
                val token = tokenManager.tokenFlow.firstOrNull()
                if (token == null) {
                    errorText = "Tu sesión expiró. Inicia sesión de nuevo."
                } else {
                    try {
                        val r = RetrofitClient.apiService.getCallResult("Bearer $token", callId)
                        if (r.isSuccessful) {
                            val body = r.body()
                            if (body != null && body.status != "pending") result = body
                        } else {
                            errorText = backendError(r, "No pude consultar el resultado de la llamada")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // sin conexión momentánea: se reintenta en la siguiente vuelta
                    }
                }
            }
        }

        val finalResult = result
        val finalError = errorText
        val appointment = finalResult?.appointment
        val text = when {
            finalError != null -> "⚠️ $finalError"
            finalResult == null ->
                "Todavía estoy procesando la llamada. Revisa en unos minutos si quedó tu cita."
            finalResult.status == "saved" && appointment != null ->
                "✅ Cita registrada: ${appointment.service_name} — ${appointment.appointment_date}" +
                        if (finalResult.emailSent == true) "\n📧 Te enviamos la confirmación a tu correo." else ""
            finalResult.status == "duplicate" && appointment != null ->
                "Ya tenías una cita en ese horario: ${appointment.service_name} — ${appointment.appointment_date}"
            else ->
                "No se acordó ninguna cita en esta llamada. Si quieres agendar, dime el servicio, el día y la hora."
        }
        messages.add(ChatMessage(text = text, isFromUser = false))
    }

    // ---- Avisos que llegan desde el WebView (conectada / terminó / error) ----
    val handleCallState: (String, String) -> Unit = { state, detail ->
        when (state) {
            "connected" -> {
                isConnecting = false
                isCalling = true
            }
            "ended" -> {
                isConnecting = false
                isCalling = false
                // --- APAGAR ALTAVOZ AL TERMINAR ---
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL

                if (callActive) {
                    callActive = false
                    messages.add(
                        ChatMessage(text = "Llamada finalizada. Estoy revisando si quedó una cita agendada…", isFromUser = false)
                    )
                    val finishedCallId = currentCallId[0]
                    scope.launch { confirmAppointmentAfterCall(finishedCallId) }
                }
            }
            "error" -> {
                isConnecting = false
                isCalling = false
                callActive = false
                // --- APAGAR ALTAVOZ POR ERROR ---
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
                messages.add(ChatMessage(text = "⚠️ No pude conectar la llamada: $detail", isFromUser = false))
            }
        }
    }
    val latestCallStateHandler by rememberUpdatedState(handleCallState)

    // ---- Iniciar la llamada de voz ----
    suspend fun startVoiceCall() {
        val token = tokenManager.tokenFlow.firstOrNull()
        if (token.isNullOrEmpty()) {
            Toast.makeText(context, "Inicia sesión de nuevo para usar el asesor.", Toast.LENGTH_LONG).show()
            return
        }
        val webView = webViewRef
        if (webView == null) {
            Toast.makeText(context, "La voz aún no está lista. Intenta de nuevo.", Toast.LENGTH_SHORT).show()
            return
        }

        isConnecting = true
        attemptCounter[0] += 1
        val attempt = attemptCounter[0]
        try {
            // El token de Retell dura solo ~30 s: se usa de inmediato
            val response = RetrofitClient.apiService.getRetellToken("Bearer $token")
            val body = response.body()
            if (!response.isSuccessful || body == null || body.accessToken.isBlank()) {
                isConnecting = false
                messages.add(
                    ChatMessage(text = "⚠️ " + backendError(response, "No se pudo iniciar la llamada"), isFromUser = false)
                )
                return
            }

            voiceStartIndex[0] = messages.size
            currentCallId[0] = body.callId
            callActive = true

            // --- ENCENDER ALTAVOZ ANTES DE CONECTAR ---
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true

            val ice = body.iceServers
            val config = JSONObject().apply {
                put("accessToken", body.accessToken)
                put("callId", body.callId ?: "")
                put("transport", body.transport ?: "gateway")
                put("iceServers", if (ice != null && ice.isJsonArray) JSONArray(ice.toString()) else JSONArray())
            }
            webView.evaluateJavascript("window.startRetellCall($config);", null)

            // Si el WebView no responde en 30 s, no dejamos el botón bloqueado
            delay(30_000L)
            if (isConnecting && attemptCounter[0] == attempt) {
                isConnecting = false
                callActive = false
                webView.evaluateJavascript("window.stopRetellCall();", null)
                // --- APAGAR ALTAVOZ POR TIMEOUT ---
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
                messages.add(ChatMessage(text = "⚠️ La llamada tardó demasiado en conectar. Intenta de nuevo.", isFromUser = false))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            isConnecting = false
            callActive = false
            // --- APAGAR ALTAVOZ POR EXCEPCIÓN DE RED ---
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            messages.add(
                ChatMessage(
                    text = "⚠️ No se pudo conectar con el servidor. Revisa tu internet y que ngrok esté activo.",
                    isFromUser = false
                )
            )
        }
    }

    // ---- Enviar un mensaje de texto al asesor ----
    suspend fun sendTextMessage(userText: String, history: List<ChatTurn>) {
        isSending = true
        try {
            val token = tokenManager.tokenFlow.firstOrNull()
            if (token.isNullOrEmpty()) {
                messages.add(ChatMessage(text = "⚠️ Tu sesión expiró. Inicia sesión de nuevo.", isFromUser = false))
                return
            }
            val response = RetrofitClient.apiService.sendChatMessage(
                token = "Bearer $token",
                request = ChatMessageRequest(message = userText, history = history)
            )
            val reply = response.body()?.reply
            if (response.isSuccessful && !reply.isNullOrBlank()) {
                messages.add(ChatMessage(text = reply, isFromUser = false))
            } else {
                messages.add(
                    ChatMessage(text = "⚠️ " + backendError(response, "No pude procesar tu solicitud"), isFromUser = false)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messages.add(
                ChatMessage(
                    text = "⚠️ No se pudo conectar con el servidor. Revisa tu internet y que ngrok esté activo.",
                    isFromUser = false
                )
            )
        } finally {
            isSending = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isCalling -> "En llamada..."
                            isConnecting -> "Conectando..."
                            else -> "Asesor de Imagen IA"
                        },
                        color = OceanWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isCalling || isConnecting) {
                            webViewRef?.evaluateJavascript("window.stopRetellCall();", null)
                            // --- APAGAR ALTAVOZ AL VOLVER ATRÁS ---
                            audioManager.isSpeakerphoneOn = false
                            audioManager.mode = AudioManager.MODE_NORMAL
                        }
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = OceanWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isCalling) Color(0xFF00796B) else OceanTurquoise
                )
            )
        },
        bottomBar = {
            var inputText by remember { mutableStateOf("") }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe o usa el micrófono...") },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OceanSurfaceHigh,
                        unfocusedContainerColor = OceanSurfaceHigh
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (inputText.isBlank()) {
                    FloatingActionButton(
                        onClick = {
                            if (!microphonePermissionState.status.isGranted) {
                                microphonePermissionState.launchPermissionRequest()
                            } else if (isCalling || isConnecting) {
                                webViewRef?.evaluateJavascript("window.stopRetellCall();", null)
                                // --- APAGAR ALTAVOZ AL COLGAR MANUALMENTE ---
                                audioManager.isSpeakerphoneOn = false
                                audioManager.mode = AudioManager.MODE_NORMAL
                                isCalling = false
                                isConnecting = false
                            } else {
                                scope.launch { startVoiceCall() }
                            }
                        },
                        containerColor = if (isCalling) OceanError else OceanTurquoise,
                        contentColor = OceanWhite,
                        shape = RoundedCornerShape(50)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = OceanWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isCalling) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = if (isCalling) "Colgar" else "Hablar"
                            )
                        }
                    }
                } else {
                    FloatingActionButton(
                        onClick = {
                            val userText = inputText.trim()
                            if (userText.isNotEmpty() && !isSending) {
                                // El historial se arma ANTES de agregar el mensaje nuevo
                                val history = messages.takeLast(10).map {
                                    ChatTurn(role = if (it.isFromUser) "user" else "assistant", text = it.text)
                                }
                                messages.add(ChatMessage(text = userText, isFromUser = true))
                                inputText = ""
                                scope.launch { sendTextMessage(userText, history) }
                            }
                        },
                        containerColor = OceanTurquoise,
                        contentColor = OceanWhite,
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Enviar texto")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                reverseLayout = false
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
                if (isSending) {
                    item { ChatBubble(message = ChatMessage(id = "typing", text = "Escribiendo…", isFromUser = false)) }
                }
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mediaPlaybackRequiresUserGesture = false
                            domStorageEnabled = true
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) {
                                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                            }

                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                                Log.d("WebViewRetell", "Consola Web: ${consoleMessage.message()} (Línea: ${consoleMessage.lineNumber()})")
                                return true
                            }
                        }

                        addJavascriptInterface(
                            WebAppInterface(
                                scope = scope,
                                onUpdate = { newMessages ->
                                    // Reemplaza solo la parte de la llamada; el chat de texto previo se conserva
                                    while (messages.size > voiceStartIndex[0]) messages.removeAt(messages.lastIndex)
                                    messages.addAll(newMessages)
                                },
                                stateCallback = { state, detail -> latestCallStateHandler(state, detail) }
                            ),
                            "Android"
                        )

                        loadDataWithBaseURL("https://closetvr.app", RETELL_HTML, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                modifier = Modifier.size(1.dp).alpha(0f)
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isFromUser) OceanTurquoise else OceanSurfaceHigh
    val textColor = if (message.isFromUser) OceanWhite else Color.White
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (message.isFromUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Text(text = message.text, color = textColor)
        }
    }
}