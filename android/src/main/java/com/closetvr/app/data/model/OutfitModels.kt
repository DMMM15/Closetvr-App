package com.closetvr.app.data.model

import com.google.gson.annotations.SerializedName

/** Cuerpo de POST outfits/generate: el backend pide clima, estilo y nombre del outfit. */
data class GenerateOutfitRequest(
    @SerializedName("clima") val clima: String,
    @SerializedName("estilo") val estilo: String,
    @SerializedName("nombre") val nombre: String
)

/** Respuesta de POST outfits/generate: devuelve el outfit ya guardado con sus prendas. */
data class GenerateOutfitResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("outfit") val outfit: Outfit
)

/** Una prenda dentro de un outfit (viene del JOIN con closet_items). */
data class OutfitPrenda(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("source") val source: String? = null
)

/** Un outfit armado (GET outfits). */
data class Outfit(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("clima") val clima: String? = null,
    @SerializedName("estilo") val estilo: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("prendas") val prendas: List<OutfitPrenda> = emptyList()
)
