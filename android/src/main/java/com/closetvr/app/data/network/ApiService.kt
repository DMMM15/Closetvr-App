package com.closetvr.app.data.network

import com.closetvr.app.data.model.AddToCartRequest
import com.closetvr.app.data.model.AppointmentDto
import com.closetvr.app.data.model.AppointmentRequest
import com.closetvr.app.data.model.AppointmentResponse
import com.closetvr.app.data.model.AuthResponse
import com.closetvr.app.data.model.CartItem
import com.closetvr.app.data.model.ClosetItem
import com.closetvr.app.data.model.GenerateOutfitRequest
import com.closetvr.app.data.model.GenerateOutfitResponse
import com.closetvr.app.data.model.Outfit
import com.closetvr.app.data.model.Product
import com.google.gson.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<AuthResponse>

    @POST("auth/google")
    suspend fun googleLogin(@Body body: Map<String, String>): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body userData: Map<String, String>): Response<AuthResponse>

    @GET("closet")
    suspend fun getCloset(@Header("Authorization") token: String): List<ClosetItem>

    @DELETE("closet/{id}")
    suspend fun removeFromCloset(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

    @Multipart
    @POST("closet/photo")
    suspend fun uploadPhoto(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part("name") name: RequestBody,
        @Part("category") category: RequestBody
    ): ClosetItem

    @POST("closet/from-store")
    suspend fun addFromStore(
        @Header("Authorization") token: String,
        @Body body: Map<String, Int>
    ): Response<ClosetItem>

    @GET("cart")
    suspend fun getCart(@Header("Authorization") token: String): List<CartItem>

    @POST("cart")
    suspend fun addToCart(
        @Header("Authorization") token: String,
        @Body request: AddToCartRequest
    ): Response<CartItem>

    @DELETE("cart/{id}")
    suspend fun removeFromCart(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

    @GET("products")
    suspend fun getProducts(): List<Product>

    @POST("outfits/generate")
    suspend fun generateOutfit(
        @Header("Authorization") token: String,
        @Body request: GenerateOutfitRequest
    ): Response<GenerateOutfitResponse>

    @GET("outfits")
    suspend fun getOutfits(@Header("Authorization") token: String): Response<List<Outfit>>

    @DELETE("outfits/{id}")
    suspend fun deleteOutfit(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

    @POST("appointments")
    suspend fun createAppointment(
        @Header("Authorization") token: String,
        @Body request: AppointmentRequest
    ): Response<AppointmentResponse>

    // Lista las citas del usuario (la app la usa para confirmar que la llamada dejó una cita)
    @GET("appointments")
    suspend fun getAppointments(@Header("Authorization") token: String): Response<List<AppointmentDto>>

    // Al colgar: pregunta si la llamada dejó una cita (el backend la guarda y envía los correos)
    @GET("call-result/{callId}")
    suspend fun getCallResult(
        @Header("Authorization") token: String,
        @Path("callId") callId: String
    ): Response<CallResultResponse>

    // Crea la llamada de voz en Retell. Requiere sesión: el backend guarda tu user_id en la llamada
    @POST("create-call")
    suspend fun getRetellToken(@Header("Authorization") token: String): Response<RetellTokenResponse>

    // --- NUEVO: Endpoint para el chat de texto con Gemini ---
    @POST("chat")
    suspend fun sendChatMessage(
        @Header("Authorization") token: String,
        @Body request: ChatMessageRequest
    ): Response<ChatMessageResponse>
}

// status: "pending" (aún procesando) | "saved" | "duplicate" | "none" (no se acordó cita)
data class CallResultResponse(
    val status: String,
    val appointment: AppointmentDto? = null,
    val reason: String? = null,
    val emailSent: Boolean? = null
)

// --- Modelos de datos para recibir la llamada creada en Retell ---
// transport: "gateway" (WebRTC directo con Retell, lo normal hoy) o "livekit"
data class RetellTokenResponse(
    val success: Boolean,
    val accessToken: String,
    val callId: String? = null,
    val transport: String? = null,
    val iceServers: JsonElement? = null
)

// --- NUEVO: Modelos de datos para el chat de texto ---
data class ChatTurn(
    val role: String, // "user" | "assistant"
    val text: String
)

data class ChatMessageRequest(
    val message: String,
    val history: List<ChatTurn>? = null
)

data class ChatMessageResponse(
    val reply: String
)