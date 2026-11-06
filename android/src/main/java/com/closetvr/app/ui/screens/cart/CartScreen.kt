package com.closetvr.app.ui.screens.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.closetvr.app.data.model.CartItem
import com.closetvr.app.data.model.categoryDisplayName
import com.closetvr.app.ui.components.*
import com.closetvr.app.ui.theme.*
import com.closetvr.app.util.formatPrice

@Composable
fun CartScreen(viewModel: CartViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadCart() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Mi carrito",
            subtitle = if (state.items.isEmpty()) null
            else "${state.itemCount} ${if (state.itemCount == 1) "artículo" else "artículos"}"
        )

        when {
            state.isLoading -> LoadingBox()
            state.items.isEmpty() -> StateMessage(
                icon = Icons.Outlined.ShoppingCart,
                title = "Tu carrito está vacío",
                message = "Explora la tienda y añade las prendas que más te gusten."
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.items, key = { it.cart_item_id }) { item ->
                    CartRow(item, onRemove = { viewModel.remove(item) })
                }
                item {
                    TotalCard(total = state.total)
                }
            }
        }
    }
}

@Composable
fun CartRow(item: CartItem, onRemove: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.image_url,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(OceanSurfaceHigh)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = OceanWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${categoryDisplayName(item.category)} · Cant. ${item.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OceanTextMuted
                )
                Spacer(Modifier.height(6.dp))
                Text(formatPrice(item.price), style = MaterialTheme.typography.titleMedium, color = OceanTurquoise)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Quitar del carrito", tint = OceanTextMuted)
            }
        }
    }
}

@Composable
private fun TotalCard(total: Double) {
    Spacer(Modifier.height(4.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total", style = MaterialTheme.typography.titleMedium, color = OceanTextMuted, modifier = Modifier.weight(1f))
            Text(
                formatPrice(total.toString()),
                style = MaterialTheme.typography.headlineSmall,
                color = OceanWhite
            )
        }
    }
}
