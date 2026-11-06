package com.closetvr.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closetvr.app.ui.components.OceanOutlinedButton
import com.closetvr.app.ui.components.ScreenHeader
import com.closetvr.app.ui.theme.*
import com.closetvr.app.util.TokenManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onNavigateToChat: () -> Unit // Parámetro para navegar al chat
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val userName by tokenManager.userNameFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val nombreAMostrar = currentUser?.displayName ?: userName ?: "Usuario"
    val correoAMostrar = currentUser?.email ?: ""
    val fotoUrl = currentUser?.photoUrl

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Perfil")

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Box(
                Modifier
                    .size(112.dp)
                    .background(OceanAccentBrush, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(OceanSurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                if (fotoUrl != null) {
                    AsyncImage(
                        model = fotoUrl,
                        contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        nombreAMostrar.trim().firstOrNull()?.uppercase() ?: "U",
                        style = MaterialTheme.typography.displaySmall,
                        color = OceanTurquoise
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(nombreAMostrar, style = MaterialTheme.typography.headlineSmall, color = OceanWhite)

            if (correoAMostrar.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(correoAMostrar, style = MaterialTheme.typography.bodyMedium, color = OceanTextMuted)
            }

            Spacer(Modifier.height(40.dp))

            // --- BOTÓN PARA ABRIR EL CHAT ---
            Button(
                onClick = { onNavigateToChat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OceanTurquoise)
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Asistente de Voz",
                    modifier = Modifier.padding(end = 8.dp),
                    tint = OceanWhite
                )
                Text(
                    text = "Hablar con Asistente para generar cita de asesoria",
                    color = OceanWhite,
                    textAlign = TextAlign.Center
                )
            }

            OceanOutlinedButton(
                text = "Cerrar sesión",
                onClick = {
                    scope.launch {
                        FirebaseAuth.getInstance().signOut()
                        tokenManager.clearSession()
                        onLogout()
                    }
                },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = OceanError)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}