package com.closetvr.app.ui.screens.outfits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.closetvr.app.ui.components.OceanButton
import com.closetvr.app.ui.components.OceanTextField
import com.closetvr.app.ui.theme.*

/**
 * Diálogo "Combinar con IA": pide el nombre del outfit, el clima y el estilo
 * ANTES de llamar a la API. Mientras la IA trabaja muestra un CircularProgressIndicator
 * en el botón y no se puede cerrar; si falla, muestra el error dentro del mismo diálogo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateOutfitDialog(
    onDismiss: () -> Unit,
    onGenerate: (nombre: String, clima: String, estilo: String) -> Unit,
    isLoading: Boolean,
    errorMessage: String? = null
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var selectedClima by rememberSaveable { mutableStateOf("Templado") }
    var selectedEstilo by rememberSaveable { mutableStateOf("Casual") }

    val climas = listOf("Cálido", "Templado", "Frío")
    val estilos = listOf("Casual", "Formal", "Deportivo", "Elegante")

    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = OceanSurface,
        labelColor = OceanWhite,
        selectedContainerColor = OceanTurquoise,
        selectedLabelColor = OceanAbyss
    )

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = OceanSurfaceHigh,
        titleContentColor = OceanWhite,
        textContentColor = OceanTextMuted,
        shape = RoundedCornerShape(24.dp),
        title = { Text("✨ Generar outfit con IA", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ponle nombre y elige las condiciones para combinar tu armario:",
                    style = MaterialTheme.typography.bodyMedium
                )

                OceanTextField(
                    value = nombre,
                    onValueChange = { nombre = it.take(100) },
                    label = "Nombre del outfit",
                    placeholder = "Ej: Salida de viernes",
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Clima", style = MaterialTheme.typography.labelLarge, color = OceanWhite)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    climas.forEach { clima ->
                        FilterChip(
                            selected = selectedClima == clima,
                            onClick = { selectedClima = clima },
                            enabled = !isLoading,
                            colors = chipColors,
                            label = { Text(clima) }
                        )
                    }
                }

                Text(text = "Estilo", style = MaterialTheme.typography.labelLarge, color = OceanWhite)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    estilos.forEach { estilo ->
                        FilterChip(
                            selected = selectedEstilo == estilo,
                            onClick = { selectedEstilo = estilo },
                            enabled = !isLoading,
                            colors = chipColors,
                            label = { Text(estilo) }
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = OceanError
                    )
                }
            }
        },
        confirmButton = {
            OceanButton(
                text = "Crear outfit",
                onClick = { onGenerate(nombre.trim(), selectedClima, selectedEstilo) },
                enabled = nombre.isNotBlank(),
                loading = isLoading,
                modifier = Modifier.widthIn(min = 150.dp)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar", color = OceanTurquoise)
            }
        }
    )
}
