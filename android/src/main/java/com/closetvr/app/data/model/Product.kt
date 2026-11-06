package com.closetvr.app.data.model

data class Product(
    val id: Int,
    val name: String,
    val category: String? = null,
    val price: String = "0",
    val image_url: String? = null
)
