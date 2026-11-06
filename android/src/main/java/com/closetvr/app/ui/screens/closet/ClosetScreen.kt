package com.closetvr.app.ui.screens.closet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.closetvr.app.data.model.ClosetItem
import com.closetvr.app.data.model.Outfit
import com.closetvr.app.ui.components.*
import com.closetvr.app.ui.screens.outfits.GenerateOutfitDialog
import com.closetvr.app.ui.screens.outfits.GenerateState
import com.closetvr.app.ui.screens.outfits.OutfitViewModel
import com.closetvr.app.ui.screens.outfits.OutfitsTab
import com.closetvr.app.ui.screens.outfits.StoreBadge
import com.closetvr.app.ui.theme.*

@Composable
fun ClosetScreen(
    viewModel: ClosetViewModel = viewModel(),
    outfitViewModel: OutfitViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val outfitsState by outfitViewModel.outfitsState.collectAsStateWithLifecycle()
    val generateState by outfitViewModel.generateState.collectAsStateWithLifecycle()
    val deletingId by outfitViewModel.deletingId.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var itemToDelete by remember { mutableStateOf<ClosetItem?>(null) }
    var outfitToDelete by remember { mutableStateOf<Outfit?>(null) }
    var showAiDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // La IA combina las prendas del armario (fotos propias + las guardadas desde la tienda) y necesita al menos 2
    val canGenerate = state.items.size >= 2

    LaunchedEffect(Unit) { viewModel.loadCloset() }

    // Avisos del ViewModel de outfits (outfit eliminado / error al eliminar)
    LaunchedEffect(Unit) {
        outfitViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Al abrir "Mis Outfits" se carga/refresca la lista (por si se borró alguna prenda de un outfit)
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) outfitViewModel.loadOutfits()
    }

    // Resultado de la IA: si sale bien, cierra el diálogo, abre "Mis Outfits" y avisa.
    // Si falla, el error se muestra dentro del diálogo para poder reintentar sin volver a escribir el nombre.
    LaunchedEffect(generateState) {
        val result = generateState
        if (result is GenerateState.Success) {
            showAiDialog = false
            selectedTab = 1
            snackbarHostState.showSnackbar("✨ ¡Outfit '${result.outfit.name}' creado con éxito!")
            outfitViewModel.consumeGenerateResult()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { OceanSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (canGenerate) {
                ExtendedFloatingActionButton(
                    onClick = { showAiDialog = true },
                    containerColor = OceanTurquoise,
                    contentColor = OceanBlack,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Text("✨") },
                    text = { Text("Combinar con IA", style = MaterialTheme.typography.labelLarge) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "Mi armario",
                    subtitle = if (state.items.isEmpty()) null
                    else "${state.items.size} ${if (state.items.size == 1) "prenda" else "prendas"}"
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = OceanTurquoise
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        selectedContentColor = OceanTurquoise,
                        unselectedContentColor = OceanTextMuted,
                        text = { Text("Mis Prendas", style = MaterialTheme.typography.labelLarge) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        selectedContentColor = OceanTurquoise,
                        unselectedContentColor = OceanTextMuted,
                        text = { Text("Mis Outfits", style = MaterialTheme.typography.labelLarge) }
                    )
                }

                when (selectedTab) {
                    0 -> PrendasTab(state = state, onRemove = { itemToDelete = it })
                    else -> OutfitsTab(
                        state = outfitsState,
                        canGenerate = canGenerate,
                        deletingId = deletingId,
                        onDelete = { outfitToDelete = it },
                        onRetry = { outfitViewModel.loadOutfits() }
                    )
                }
            }

            // Diálogo: pide el nombre (y clima/estilo) antes de llamar a la API
            if (showAiDialog) {
                GenerateOutfitDialog(
                    onDismiss = {
                        showAiDialog = false
                        outfitViewModel.consumeGenerateResult()
                    },
                    onGenerate = { nombre, clima, estilo ->
                        outfitViewModel.generateOutfit(nombre, clima, estilo)
                    },
                    isLoading = generateState is GenerateState.Loading,
                    errorMessage = (generateState as? GenerateState.Error)?.message
                )
            }
        }
    }

    // Modal para confirmar eliminación de un outfit (las prendas NO se borran)
    outfitToDelete?.let { outfit ->
        AlertDialog(
            onDismissRequest = { outfitToDelete = null },
            containerColor = OceanSurfaceHigh,
            titleContentColor = OceanWhite,
            textContentColor = OceanTextMuted,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Eliminar outfit") },
            text = { Text("¿Quieres eliminar el outfit \"${outfit.name}\"? Tus prendas no se borrarán, solo este conjunto.") },
            confirmButton = {
                TextButton(onClick = {
                    outfitViewModel.deleteOutfit(outfit)
                    outfitToDelete = null
                }) { Text("Eliminar", color = OceanError) }
            },
            dismissButton = {
                TextButton(onClick = { outfitToDelete = null }) { Text("Cancelar", color = OceanTurquoise) }
            }
        )
    }

    // Modal para confirmar eliminación de prenda
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = OceanSurfaceHigh,
            titleContentColor = OceanWhite,
            textContentColor = OceanTextMuted,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Quitar prenda") },
            text = { Text("¿Quieres eliminar \"${item.name ?: "esta prenda"}\" de tu armario?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(item)
                    itemToDelete = null
                }) { Text("Eliminar", color = OceanError) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancelar", color = OceanTurquoise) }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Pestaña "Mis Prendas": cuadrícula separada por categorías (como ya estaba)
// ---------------------------------------------------------------------------
@Composable
private fun PrendasTab(state: ClosetUiState, onRemove: (ClosetItem) -> Unit) {
    when {
        state.isLoading -> LoadingBox()
        state.items.isEmpty() -> StateMessage(
            icon = Icons.Outlined.Checkroom,
            title = "Tu armario está vacío",
            message = "Usa la cámara para fotografiar tus prendas o guarda alguna desde la tienda."
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.sections.forEach { section ->
                item(key = "header_${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                    CategoryHeader(title = section.title, count = section.items.size)
                }
                items(section.items, key = { "item_${it.id}" }) { item ->
                    ClosetTile(item = item, onRemove = { onRemove(item) })
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = OceanWhite)
        Spacer(Modifier.width(10.dp))
        Surface(shape = RoundedCornerShape(50), color = OceanSurfaceHigh) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = OceanAqua,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = OceanOutline.copy(alpha = 0.6f))
    }
}

@Composable
fun ClosetTile(item: ClosetItem, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(16.dp))
            .background(OceanSurfaceHigh)
    ) {
        AsyncImage(
            model = item.image_url,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, OceanBlack.copy(alpha = 0.8f))))
        )
        Text(
            text = item.name ?: "Prenda",
            style = MaterialTheme.typography.labelMedium,
            color = OceanWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        if (item.source == "store") {
            StoreBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(OceanBlack.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Eliminar", tint = OceanWhite, modifier = Modifier.size(16.dp))
        }
    }
}