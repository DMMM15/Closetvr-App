package com.closetvr.app.ui.screens.camera

import android.Manifest
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.closetvr.app.data.model.ClothingCategory
import com.closetvr.app.ui.components.*
import com.closetvr.app.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onGoToCloset: () -> Unit,
    viewModel: CameraViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Al guardar: confirmación con acceso directo al armario
    LaunchedEffect(Unit) {
        viewModel.saved.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Prenda guardada en tu armario",
                    actionLabel = "Ver armario"
                )
                if (result == SnackbarResult.ActionPerformed) onGoToCloset()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            !cameraPermissionState.status.isGranted -> PermissionRequest(
                onRequest = { cameraPermissionState.launchPermissionRequest() }
            )
            state.capturedFile == null -> CameraPreviewWithCapture(
                onPhotoCaptured = viewModel::onPhotoCaptured
            )
            else -> CapturedPhotoForm(
                state = state,
                onNameChange = viewModel::onNameChange,
                onCategoryChange = viewModel::onCategoryChange,
                onRetake = viewModel::retake,
                onSave = viewModel::saveToCloset
            )
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
fun PermissionRequest(onRequest: () -> Unit) {
    StateMessage(
        icon = Icons.Outlined.CameraAlt,
        title = "Activa tu cámara",
        message = "Closet VR necesita acceso a la cámara para capturar tus prendas.",
        actionLabel = "Permitir cámara",
        onAction = onRequest
    )
}

@Composable
fun CameraPreviewWithCapture(onPhotoCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Estado para intercalar entre cámara trasera y frontal (por defecto la trasera)
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val previewView = remember { PreviewView(context) }

    // Re-vincula CameraX cada vez que cambia el estado de lensFacing
    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(Modifier.fillMaxSize().background(OceanBlack)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Cabecera superior con degradado
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(OceanBlack.copy(alpha = 0.7f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Captura tu prenda", style = MaterialTheme.typography.headlineSmall, color = OceanWhite)
            Text("Colócala sobre una superficie lisa y bien iluminada", style = MaterialTheme.typography.bodySmall, color = OceanFoam)
        }

        // Marco guía central
        ViewfinderFrame(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .aspectRatio(0.78f)
        )

        // Botón para alternar cámara (Ubicado en la parte inferior izquierda)
        IconButton(
            onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 44.dp)
                .size(48.dp)
                .background(OceanSurface.copy(alpha = 0.75f), CircleShape)
                .border(1.dp, OceanOutline, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Cambiar cámara",
                tint = OceanWhite
            )
        }

        // Botón principal de captura (Ubicado al centro en la parte inferior)
        ShutterButton(
            onClick = { takePhoto(context, imageCapture, onPhotoCaptured) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        )
    }
}

@Composable
private fun ViewfinderFrame(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val length = 34.dp.toPx()
        val stroke = 4.dp.toPx()

        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(OceanAqua, Offset(x, y), Offset(x + dx * length, y), stroke, StrokeCap.Round)
            drawLine(OceanAqua, Offset(x, y), Offset(x, y + dy * length), stroke, StrokeCap.Round)
        }
        corner(0f, 0f, 1f, 1f)
        corner(size.width, 0f, -1f, 1f)
        corner(0f, size.height, 1f, -1f)
        corner(size.width, size.height, -1f, -1f)
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.9f else 1f, label = "shutterScale")

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Tomar foto",
                onClick = onClick
            )
            .border(4.dp, OceanWhite, CircleShape)
            .padding(7.dp)
            .background(OceanAccentBrush, CircleShape)
    )
}

private fun takePhoto(context: Context, imageCapture: ImageCapture, onPhotoCaptured: (File) -> Unit) {
    val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
    val photoFile = File(context.cacheDir, "CLOSET_$fileName.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onPhotoCaptured(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        }
    )
}

/** Foto capturada + formulario en un panel inferior que se desliza hacia arriba. */
@Composable
private fun CapturedPhotoForm(
    state: CameraUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (ClothingCategory) -> Unit,
    onRetake: () -> Unit,
    onSave: () -> Unit
) {
    var panelVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { panelVisible = true }

    Box(Modifier.fillMaxSize().background(OceanBlack)) {
        AsyncImage(
            model = state.capturedFile,
            contentDescription = "Foto capturada",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(OceanBlack.copy(alpha = 0.7f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Nueva prenda", style = MaterialTheme.typography.headlineSmall, color = OceanWhite)
        }

        AnimatedVisibility(
            visible = panelVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding(),
            enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = OceanSurface.copy(alpha = 0.97f),
                border = BorderStroke(1.dp, OceanOutline.copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 20.dp)
                ) {
                    // Tirador visual del panel
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 40.dp, height = 4.dp)
                            .background(OceanOutline, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Detalles de la prenda", style = MaterialTheme.typography.titleLarge, color = OceanWhite)
                    Text(
                        "Dale un nombre y elige su categoría para ordenarla en tu armario.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OceanTextMuted
                    )
                    Spacer(Modifier.height(18.dp))

                    OceanTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = "Nombre de la prenda",
                        placeholder = "Ej. Camisa azul",
                        leadingIcon = Icons.Outlined.Checkroom,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    CategoryDropdown(
                        selected = state.category,
                        onSelected = onCategoryChange,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OceanOutlinedButton(
                            text = "Repetir",
                            onClick = onRetake,
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f)
                        )
                        OceanButton(
                            text = "Guardar en armario",
                            onClick = onSave,
                            enabled = state.canSave,
                            loading = state.isSaving,
                            leadingIcon = Icons.Filled.Check,
                            modifier = Modifier.weight(1.6f)
                        )
                    }
                }
            }
        }
    }
}

/** Menú desplegable de categorías con el estilo Deep Ocean. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: ClothingCategory?,
    onSelected: (ClothingCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Categoría") },
            placeholder = { Text("Selecciona una categoría") },
            leadingIcon = { Icon(Icons.Outlined.Category, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = oceanTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(OceanSurfaceHigh)
        ) {
            ClothingCategory.values().forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    },
                    trailingIcon = if (category == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = OceanTurquoise) }
                    } else null,
                    colors = MenuDefaults.itemColors(textColor = OceanWhite)
                )
            }
        }
    }
}