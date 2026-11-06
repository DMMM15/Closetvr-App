package com.closetvr.app.data.model

data class CartItem(
    val cart_item_id: Int,
    val name: String = "",
    val price: String = "0",
    val quantity: Int = 1,
    val category: String? = null,
    val image_url: String? = null
)
