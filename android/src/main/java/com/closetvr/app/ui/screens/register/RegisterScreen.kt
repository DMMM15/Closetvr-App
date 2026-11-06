package com.closetvr.app.ui.screens.register

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closetvr.app.ui.components.OceanBackground
import com.closetvr.app.ui.components.OceanButton
import com.closetvr.app.ui.components.OceanTextField
import com.closetvr.app.ui.theme.*
import com.closetvr.app.util.TokenManager

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onGoToLogin: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val viewModel: RegisterViewModel = viewModel()
    val state = viewModel.uiState

    val performRegister: () -> Unit = { viewModel.register(tokenManager, onRegisterSuccess) }

    OceanBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            IconButton(onClick = onGoToLogin) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = OceanWhite)
            }

            Spacer(Modifier.height(16.dp))
            Text("Crear cuenta", style = MaterialTheme.typography.headlineLarge, color = OceanWhite)
            Spacer(Modifier.height(6.dp))
            Text("Únete a Closet VR y digitaliza tu armario", style = MaterialTheme.typography.bodyMedium, color = OceanTextMuted)

            Spacer(Modifier.height(32.dp))

            OceanTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Nombre completo",
                leadingIcon = Icons.Outlined.Person,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            OceanTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Correo electrónico",
                placeholder = "ejemplo@correo.com",
                leadingIcon = Icons.Outlined.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            OceanTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Contraseña",
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                supportingText = "Mínimo 6 caracteres",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { performRegister() }),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))

            OceanButton(
                text = "Registrarme",
                onClick = performRegister,
                loading = state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("¿Ya tienes cuenta?", style = MaterialTheme.typography.bodyMedium, color = OceanTextMuted)
                TextButton(onClick = onGoToLogin) {
                    Text("Inicia sesión", style = MaterialTheme.typography.labelLarge, color = OceanTurquoise)
                }
            }
        }
    }
}
