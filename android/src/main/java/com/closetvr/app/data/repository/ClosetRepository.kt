package com.closetvr.app.data.repository

import com.closetvr.app.data.model.ClosetItem
import com.closetvr.app.data.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ClosetRepository {
    private val api = RetrofitClient.apiService

    private fun formatToken(token: String): String {
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    suspend fun getCloset(token: String): List<ClosetItem> {
        return api.getCloset(formatToken(token))
    }

    suspend fun removeFromCloset(token: String, id: Int) {
        val response = api.removeFromCloset(formatToken(token), id)
        if (!response.isSuccessful) {
            throw Exception("Error al eliminar del armario: Código ${response.code()}")
        }
    }

    suspend fun uploadPhoto(token: String, file: File, name: String, category: String): ClosetItem {
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val bodyPart = MultipartBody.Part.createFormData("photo", file.name, requestFile)
        val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
        val categoryPart = category.toRequestBody("text/plain".toMediaTypeOrNull())

        return api.uploadPhoto(formatToken(token), bodyPart, namePart, categoryPart)
    }

    suspend fun addFromStore(token: String, productId: Int) {
        // Se cambió "product_id" por "productId" (camelCase) como exige req.body en closet.js
        val response = api.addFromStore(formatToken(token), mapOf("productId" to productId))

        if (!response.isSuccessful) {
            throw Exception("Error al guardar en el armario: Código ${response.code()}")
        }
    }
}