package com.closetvr.app.data.repository

import com.closetvr.app.data.model.AddToCartRequest
import com.closetvr.app.data.model.CartItem
import com.closetvr.app.data.network.RetrofitClient

class CartRepository {
    private val api = RetrofitClient.apiService

    private fun formatToken(token: String): String {
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    suspend fun getCart(token: String): List<CartItem> {
        return api.getCart(formatToken(token))
    }

    suspend fun addToCart(token: String, productId: Int) {
        val request = AddToCartRequest(productId = productId, quantity = 1)
        val response = api.addToCart(formatToken(token), request)

        if (!response.isSuccessful) {
            throw Exception("Error del servidor al agregar al carrito: Código ${response.code()}")
        }
    }

    suspend fun removeFromCart(token: String, cartItemId: Int) {
        api.removeFromCart(formatToken(token), cartItemId)
    }
}