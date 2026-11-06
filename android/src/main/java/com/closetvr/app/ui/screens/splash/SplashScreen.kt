package com.closetvr.app.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closetvr.app.ui.components.OceanBackground
import com.closetvr.app.ui.theme.*

@Composable
fun SplashScreen(
    onFinished: (destinationRoute: String) -> Unit,
    viewModel: SplashViewModel = viewModel()
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    // Cuando la sesión ya fue validada (y la animación mínima terminó), navegamos al destino real
    LaunchedEffect(destination) {
        destination?.let(onFinished)
    }

    // Entrada: el logo aparece con escala + fundido, y luego el texto
    val logoProgress = remember { Animatable(0f) }
    val textProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        logoProgress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        textProgress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    // Ondas de sonar que se expanden de forma continua detrás del logo
    val infinite = rememberInfiniteTransition(label = "splashPulse")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    OceanBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
                Canvas(Modifier.fillMaxSize().alpha(logoProgress.value)) {
                    repeat(3) { i ->
                        val p = (phase + i / 3f) % 1f
                        drawCircle(
                            color = OceanTurquoise.copy(alpha = (1f - p) * 0.35f),
                            radius = size.minDimension / 2f * (0.3f + 0.7f * p),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .scale(0.6f + 0.4f * logoProgress.value)
                        .alpha(logoProgress.value)
                        .background(OceanAccentBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Checkroom,
                        contentDescription = null,
                        tint = OceanAbyss,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = buildAnnotatedString {
                    append("Closet ")
                    withStyle(SpanStyle(color = OceanTurquoise)) { append("VR") }
                },
                style = MaterialTheme.typography.displaySmall,
                color = OceanWhite,
                letterSpacing = (8f - 7f * textProgress.value).sp,
                modifier = Modifier.alpha(textProgress.value)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tu armario, siempre contigo",
                style = MaterialTheme.typography.bodyMedium,
                color = OceanTextMuted,
                modifier = Modifier.alpha(textProgress.value)
            )

            Spacer(Modifier.height(48.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(120.dp)
                    .alpha(textProgress.value),
                color = OceanTurquoise,
                trackColor = OceanOutline.copy(alpha = 0.5f)
            )
        }
    }
}
