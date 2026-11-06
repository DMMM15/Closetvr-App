package com.closetvr.app.data.repository

import com.closetvr.app.data.model.Product
import com.closetvr.app.data.network.RetrofitClient

class ProductRepository {
    private val api = RetrofitClient.apiService

    suspend fun getProducts(): List<Product> {
        return api.getProducts()
    }
}
