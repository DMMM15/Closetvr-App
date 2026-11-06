package com.closetvr.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.closetvr.app.ui.theme.*

/** Fondo común de la app: degradado azul marino → abismo → negro puro, con un halo turquesa en la esquina. */
@Composable
fun OceanBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OceanBackgroundBrush)
            .drawBehind {
                val center = Offset(size.width * 0.9f, 0f)
                val radius = size.width * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(OceanTurquoise.copy(alpha = 0.16f), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            },
        content = content
    )
}

/** Colores de los campos de texto, reutilizables también por el dropdown de categorías. */
@Composable
fun oceanTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OceanWhite,
    unfocusedTextColor = OceanWhite,
    focusedBorderColor = OceanTurquoise,
    unfocusedBorderColor = OceanOutline,
    errorBorderColor = OceanError,
    focusedLabelColor = OceanTurquoise,
    unfocusedLabelColor = OceanTextMuted,
    cursorColor = OceanTurquoise,
    focusedContainerColor = OceanSurface.copy(alpha = 0.6f),
    unfocusedContainerColor = OceanSurface.copy(alpha = 0.6f),
    errorContainerColor = OceanSurface.copy(alpha = 0.6f),
    focusedLeadingIconColor = OceanTurquoise,
    unfocusedLeadingIconColor = OceanTextMuted,
    focusedTrailingIconColor = OceanTurquoise,
    unfocusedTrailingIconColor = OceanTextMuted,
    focusedPlaceholderColor = OceanTextMuted.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = OceanTextMuted.copy(alpha = 0.6f),
    focusedSupportingTextColor = OceanTextMuted,
    unfocusedSupportingTextColor = OceanTextMuted,
    errorSupportingTextColor = OceanError
)

@Composable
fun OceanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        leadingIcon = if (leadingIcon != null) { { Icon(leadingIcon, contentDescription = null) } } else null,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        isError = isError,
        supportingText = if (supportingText != null) { { Text(supportingText) } } else null,
        shape = RoundedCornerShape(16.dp),
        colors = oceanTextFieldColors()
    )
}

/** Botón principal con degradado azul → turquesa. */
@Composable
fun OceanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val active = enabled && !loading
    Button(
        onClick = onClick,
        enabled = active,
        modifier = modifier.height(54.dp),
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) OceanAccentBrush else Brush.horizontalGradient(listOf(OceanSurfaceHigh, OceanSurfaceHigh)),
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = OceanAbyss
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, contentDescription = null, tint = if (enabled) OceanAbyss else OceanTextMuted, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled) OceanAbyss else OceanTextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun OceanOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OceanOutline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = OceanSurface.copy(alpha = 0.5f),
            contentColor = OceanWhite,
            disabledContentColor = OceanTextMuted
        )
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Tarjeta translúcida con borde sutil: base de todas las tarjetas de la app. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = OceanSurface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, OceanOutline.copy(alpha = 0.7f))
    ) {
        Column(content = content)
    }
}

/** Cabecera de pantalla que respeta la barra de estado (edge-to-edge). */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** Estado vacío / error con icono, mensaje y acción opcional. */
@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(OceanSurfaceHigh.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = OceanTurquoise, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = OceanWhite)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = OceanTextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            OceanButton(text = actionLabel, onClick = onAction, modifier = Modifier.widthIn(min = 180.dp))
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = OceanTurquoise, strokeWidth = 3.dp)
    }
}

/** Snackbar con el estilo Deep Ocean. */
@Composable
fun OceanSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = state, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(14.dp),
            containerColor = OceanSurfaceHigh,
            contentColor = OceanWhite,
            actionColor = OceanTurquoise
        )
    }
}
