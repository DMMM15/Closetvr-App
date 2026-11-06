package com.closetvr.app.data.model

import com.google.gson.annotations.SerializedName

data class ClosetItem(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("image_url") val image_url: String? = null,
    @SerializedName("source") val source: String? = null
)