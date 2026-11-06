package com.closetvr.app.data.repository

import com.closetvr.app.data.model.GenerateOutfitRequest
import com.closetvr.app.data.model.Outfit
import com.closetvr.app.data.network.RetrofitClient
import com.google.gson.JsonParser
import retrofit2.Response

class OutfitRepository {
    private val api = RetrofitClient.apiService

    // Aseguramos que el token tenga el prefijo Bearer para la autenticación en Node.js
    private fun bearer(token: String) = if (token.startsWith("Bearer ")) token else "Bearer $token"

    suspend fun getOutfits(token: String): List<Outfit> {
        val response = api.getOutfits(bearer(token))
        if (response.isSuccessful) return response.body().orEmpty()
        throw Exception(errorMessage(response))
    }

    suspend fun generateOutfit(token: String, nombre: String, clima: String, estilo: String): Outfit {
        val response = api.generateOutfit(
            bearer(token),
            GenerateOutfitRequest(clima = clima, estilo = estilo, nombre = nombre)
        )
        val body = response.body()
        if (response.isSuccessful && body != null) return body.outfit
        throw Exception(errorMessage(response))
    }

    suspend fun deleteOutfit(token: String, id: Int) {
        val response = api.deleteOutfit(bearer(token), id)
        if (!response.isSuccessful) throw Exception(errorMessage(response))
    }

    // El backend responde { "error": "mensaje" }: lo mostramos tal cual al usuario
    private fun errorMessage(response: Response<*>): String {
        val backendMessage = try {
            response.errorBody()?.string()?.let {
                JsonParser.parseString(it).asJsonObject.get("error")?.asString
            }
        } catch (_: Exception) {
            null
        }
        return backendMessage ?: "Error del servidor (${response.code()})"
    }
}