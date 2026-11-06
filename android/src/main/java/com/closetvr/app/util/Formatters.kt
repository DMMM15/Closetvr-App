package com.closetvr.app.util

import java.text.NumberFormat
import java.util.Locale

/**
 * El backend entrega el precio como texto (NUMERIC de Postgres, p. ej. "59900.00").
 * Lo mostramos con separador de miles: "$59.900".
 */
fun formatPrice(raw: String): String {
    val value = raw.toDoubleOrNull() ?: return "$$raw"
    val format = NumberFormat.getNumberInstance(Locale("es", "CO")).apply {
        maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }
    return "$" + format.format(value)
}
