package com.closetvr.app.ui.screens.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closetvr.app.data.model.Outfit
import com.closetvr.app.data.model.OutfitPrenda
import com.closetvr.app.ui.components.GlassCard
import com.closetvr.app.ui.components.LoadingBox
import com.closetvr.app.ui.components.StateMessage
import com.closetvr.app.ui.theme.*

/** Contenido de la pestaña "Mis Outfits": Loading / Error / Empty / lista de conjuntos. */
@Composable
fun OutfitsTab(
    state: OutfitListState,
    canGenerate: Boolean,
    deletingId: Int?,
    onDelete: (Outfit) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        is OutfitListState.Loading -> LoadingBox()

        is OutfitListState.Error -> StateMessage(
            icon = Icons.Outlined.ErrorOutline,
            title = "No pudimos cargar tus outfits",
            message = state.message,
            actionLabel = "Reintentar",
            onAction = onRetry
        )

        is OutfitListState.Success -> {
            if (state.outfits.isEmpty()) {
                StateMessage(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Aún no tienes outfits",
                    message = if (canGenerate) {
                        "Toca «Combinar con IA» y crea tu primer conjunto con las prendas de tu armario."
                    } else {
                        "Agrega al menos 2 prendas a tu armario y la IA podrá armarte tu primer outfit."
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.outfits, key = { it.id }) { outfit ->
                        OutfitCard(
                            outfit = outfit,
                            isDeleting = deletingId == outfit.id,
                            onDelete = { onDelete(outfit) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutfitCard(outfit: Outfit, isDeleting: Boolean, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = outfit.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = OceanWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val count = outfit.prendas.size
                    val subtitle = listOfNotNull(
                        outfit.clima,
                        outfit.estilo,
                        "$count ${if (count == 1) "prenda" else "prendas"}"
                    ).joinToString(" • ")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OceanTextMuted
                    )
                }
                if (isDeleting) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = OceanTurquoise,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Eliminar outfit",
                            tint = OceanTextMuted
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(outfit.prendas, key = { it.id }) { prenda ->
                    PrendaThumb(prenda)
                }
            }
        }
    }
}

@Composable
private fun PrendaThumb(prenda: OutfitPrenda) {
    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(OceanSurfaceHigh)
        ) {
            AsyncImage(
                model = prenda.imageUrl,
                contentDescription = prenda.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
            if (prenda.source == "store") {
                StoreBadge(Modifier.align(Alignment.TopStart).padding(4.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = prenda.name ?: "Prenda",
            style = MaterialTheme.typography.labelSmall,
            color = OceanTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Etiqueta "Tienda": marca las prendas que el usuario guardó en su armario desde la tienda. */
@Composable
fun StoreBadge(modifier: Modifier = Modifier) {
    Text(
        text = "Tienda",
        style = MaterialTheme.typography.labelSmall,
        color = OceanAbyss,
        modifier = modifier
            .background(OceanTurquoise, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}