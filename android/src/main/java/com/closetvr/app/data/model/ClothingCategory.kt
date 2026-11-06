package com.closetvr.app.data.model

/**
 * Categorías de prendas que el usuario puede elegir al guardar una foto.
 *
 * [key] es el valor que se envía al backend (columna `category` VARCHAR, sin cambios en la BD).
 * [aliases] permite agrupar bajo una sola sección valores que ya existen en la base de datos
 * (p. ej. la tienda guarda "camisetas" y "calzado"), sin tocar los datos.
 */
enum class ClothingCategory(val key: String, val label: String, val aliases: Set<String>) {
    SHIRTS("camisas", "Camisas", setOf("camisas", "camisa", "camisetas", "camiseta", "blusas", "blusa", "playeras", "playera")),
    PANTS("pantalones", "Pantalones", setOf("pantalones", "pantalon", "pantalón", "jeans", "jean", "shorts")),
    SHOES("calzado", "Zapatos", setOf("calzado", "zapatos", "zapato", "tenis", "botas", "sandalias")),
    JACKETS("chaquetas", "Chaquetas", setOf("chaquetas", "chaqueta", "abrigos", "abrigo", "buzos", "buzo", "sudaderas")),
    DRESSES("vestidos", "Vestidos", setOf("vestidos", "vestido", "faldas", "falda")),
    ACCESSORIES("accesorios", "Accesorios", setOf("accesorios", "accesorio", "gorras", "gorra", "bolsos", "bolso", "relojes")),
    OTHER("otros", "Otros", setOf("otros", "otro"));

    companion object {
        /** Devuelve la categoría conocida que corresponde al texto del backend, o null si es una categoría libre. */
        fun fromRaw(raw: String?): ClothingCategory? {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            if (normalized.isEmpty()) return null
            return values().firstOrNull { normalized in it.aliases }
        }
    }
}

/** Nombre legible de cualquier categoría que llegue del backend ("camisetas" -> "Camisas"). */
fun categoryDisplayName(raw: String?): String {
    ClothingCategory.fromRaw(raw)?.let { return it.label }
    val text = raw?.trim().orEmpty()
    return if (text.isEmpty()) ClothingCategory.OTHER.label else text.replaceFirstChar { it.uppercase() }
}
