package com.closetvr.app.ui.screens.store

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.closetvr.app.data.model.Product
import com.closetvr.app.data.model.categoryDisplayName
import com.closetvr.app.ui.components.*
import com.closetvr.app.ui.theme.*
import com.closetvr.app.util.formatPrice

@Composable
fun StoreScreen(viewModel: StoreViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.loadProducts() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "Tienda", subtitle = "Descubre prendas y llévalas a tu armario")

            if (state.categories.size > 1) {
                CategoryChips(
                    categories = state.categories,
                    selected = state.selectedCategory,
                    onSelect = viewModel::selectCategory
                )
            }

            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.products.isEmpty() -> StateMessage(
                    icon = Icons.Outlined.CloudOff,
                    title = "Sin conexión",
                    message = state.error ?: "Error al cargar productos",
                    actionLabel = "Reintentar",
                    onAction = viewModel::loadProducts
                )
                state.visibleProducts.isEmpty() -> StateMessage(
                    icon = Icons.Outlined.Storefront,
                    title = "Aún no hay productos",
                    message = "Vuelve pronto: estamos preparando nuevas prendas."
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.visibleProducts, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            onAddToCart = { viewModel.addToCart(product) },
                            onAddToCloset = { viewModel.addToCloset(product) }
                        )
                    }
                }
            }
        }

        OceanSnackbarHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
private fun CategoryChips(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        item { OceanChip(label = "Todo", selected = selected == null, onClick = { onSelect(null) }) }
        items(categories) { category ->
            OceanChip(label = category, selected = selected == category, onClick = { onSelect(category) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OceanChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = OceanSurface.copy(alpha = 0.7f),
            labelColor = OceanTextMuted,
            selectedContainerColor = OceanTurquoise,
            selectedLabelColor = OceanAbyss
        ),
        border = BorderStroke(1.dp, if (selected) Color.Transparent else OceanOutline)
    )
}

@Composable
fun ProductCard(product: Product, onAddToCart: () -> Unit, onAddToCloset: () -> Unit) {
    GlassCard(shape = RoundedCornerShape(20.dp)) {
        Box {
            AsyncImage(
                model = product.image_url,
                contentDescription = product.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.9f)
                    .background(OceanSurfaceHigh)
            )
            // Degradado inferior para que la etiqueta de categoría siempre se lea
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, OceanBlack.copy(alpha = 0.75f))))
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                shape = RoundedCornerShape(50),
                color = OceanBlack.copy(alpha = 0.55f)
            ) {
                Text(
                    categoryDisplayName(product.category),
                    style = MaterialTheme.typography.labelSmall,
                    color = OceanWhite,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Column(Modifier.padding(12.dp)) {
            Text(
                product.name,
                style = MaterialTheme.typography.titleSmall,
                color = OceanWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatPrice(product.price),
                style = MaterialTheme.typography.titleMedium,
                color = OceanTurquoise
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Guardar en el armario
                IconButton(
                    onClick = onAddToCloset,
                    modifier = Modifier
                        .size(42.dp)
                        .background(OceanSurfaceHigh, CircleShape)
                ) {
                    Icon(Icons.Outlined.Checkroom, contentDescription = "Guardar en el armario", tint = OceanAqua)
                }
                // Añadir al carrito
                Button(
                    onClick = onAddToCart,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(21.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OceanTurquoise, contentColor = OceanAbyss)
                ) {
                    Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
