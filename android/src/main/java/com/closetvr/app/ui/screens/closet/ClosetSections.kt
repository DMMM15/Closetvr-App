package com.closetvr.app.ui.screens.closet

import com.closetvr.app.data.model.ClosetItem
import com.closetvr.app.data.model.ClothingCategory

/** Una sección (header + prendas) de la pantalla del armario. */
data class ClosetSection(val title: String, val items: List<ClosetItem>)

/**
 * Agrupa las prendas por categoría de forma dinámica: solo aparecen las categorías
 * que tienen prendas. Orden: categorías conocidas, luego categorías libres (A-Z) y "Otros" al final.
 */
fun groupByCategory(items: List<ClosetItem>): List<ClosetSection> {
    val known = LinkedHashMap<ClothingCategory, MutableList<ClosetItem>>()
    val custom = sortedMapOf<String, MutableList<ClosetItem>>()   // categorías que no están en la lista
    val others = mutableListOf<ClosetItem>()

    items.forEach { item ->
        val raw = item.category?.trim().orEmpty()
        val category = ClothingCategory.fromRaw(raw)
        when {
            raw.isEmpty() || category == ClothingCategory.OTHER -> others.add(item)
            category != null -> known.getOrPut(category) { mutableListOf() }.add(item)
            else -> custom.getOrPut(raw.lowercase()) { mutableListOf() }.add(item)
        }
    }

    val sections = mutableListOf<ClosetSection>()
    ClothingCategory.values()
        .filter { it != ClothingCategory.OTHER }
        .forEach { category ->
            known[category]?.let { sections.add(ClosetSection(category.label, it)) }
        }
    custom.forEach { (key, list) ->
        sections.add(ClosetSection(key.replaceFirstChar { it.uppercase() }, list))
    }
    if (others.isNotEmpty()) sections.add(ClosetSection(ClothingCategory.OTHER.label, others))
    return sections
}
