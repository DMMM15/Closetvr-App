package com.closetvr.app.data.model

import com.google.gson.annotations.SerializedName

data class AddToCartRequest(
    @SerializedName("productId") val productId: Int,
    @SerializedName("quantity") val quantity: Int = 1
)