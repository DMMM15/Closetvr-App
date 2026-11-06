package com.closetvr.app.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
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
import com.closetvr.app.ui.components.OceanOutlinedButton
import com.closetvr.app.ui.components.OceanTextField
import com.closetvr.app.ui.theme.*
import com.closetvr.app.util.TokenManager

import androidx.credentials.CredentialManager
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onGoToRegister: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val viewModel: LoginViewModel = viewModel()

    val state = viewModel.uiState

    val coroutineScope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    var googleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val credentialManager = CredentialManager.create(context)
            val passwordOption = GetPasswordOption()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(passwordOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is PasswordCredential) {
                viewModel.onEmailChange(credential.id)
                viewModel.onPasswordChange(credential.password)
            }
        } catch (e: Exception) {
            println("No se autocompletó: ${e.message}")
        }
    }

    val performLogin: () -> Unit = {
        googleError = null
        viewModel.login(tokenManager) {
            coroutineScope.launch {
                try {
                    val credentialManager = CredentialManager.create(context)
                    val request = CreatePasswordRequest(
                        id = viewModel.uiState.email.trim(),
                        password = viewModel.uiState.password
                    )
                    credentialManager.createCredential(context, request)
                } catch (e: Exception) {
                    println("Guardado cancelado o fallido: ${e.message}")
                } finally {
                    onLoginSuccess()
                }
            }
        }
    }

    val performGoogleSignIn: () -> Unit = {
        googleError = null
        googleLoading = true
        coroutineScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("923277322497-348909vsol1qlrb71v2214ln1ce9bn0l.apps.googleusercontent.com")
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential

                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                    auth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // AQUÍ ESTÁ EL CAMBIO CLAVE: En vez de entrar directo, validamos con Node y guardamos el Token localmente
                            viewModel.loginWithGoogle(
                                idToken = googleIdTokenCredential.idToken,
                                tokenManager = tokenManager,
                                onSuccess = {
                                    googleLoading = false
                                    onLoginSuccess()
                                }
                            )
                        } else {
                            googleLoading = false
                            println("Error de Firebase: ${task.exception?.message}")
                            googleError = "No se pudo iniciar sesión con Google. Inténtalo de nuevo."
                        }
                    }
                } else {
                    googleLoading = false
                }
            } catch (e: GetCredentialCancellationException) {
                googleLoading = false
            } catch (e: Exception) {
                println("Error al abrir Google: ${e.message}")
                googleLoading = false
                googleError = "No se pudo abrir Google. Revisa tu conexión."
            }
        }
    }

    OceanBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(OceanAccentBrush, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Checkroom, contentDescription = null, tint = OceanAbyss, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Bienvenido a Closet VR", style = MaterialTheme.typography.headlineMedium, color = OceanWhite)
            Spacer(Modifier.height(6.dp))
            Text("Inicia sesión para continuar", style = MaterialTheme.typography.bodyMedium, color = OceanTextMuted)

            Spacer(Modifier.height(32.dp))

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
                placeholder = "••••••••",
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { performLogin() }),
                modifier = Modifier.fillMaxWidth()
            )

            // Muestra el error ya sea del login manual o del intento de Google
            val errorMessage = state.error ?: googleError
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))

            OceanButton(
                text = "Ingresar",
                onClick = performLogin,
                loading = state.isLoading,
                enabled = !googleLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = OceanOutline)
                Text("  o continúa con  ", style = MaterialTheme.typography.labelMedium, color = OceanTextMuted)
                HorizontalDivider(Modifier.weight(1f), color = OceanOutline)
            }

            Spacer(Modifier.height(20.dp))

            OceanOutlinedButton(
                text = if (googleLoading || state.isLoading) "Conectando…" else "Google",
                onClick = performGoogleSignIn,
                enabled = !googleLoading && !state.isLoading,
                leadingContent = {
                    Text("G", style = MaterialTheme.typography.titleMedium, color = OceanTurquoise)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("¿No tienes cuenta?", style = MaterialTheme.typography.bodyMedium, color = OceanTextMuted)
                TextButton(onClick = onGoToRegister) {
                    Text("Regístrate", style = MaterialTheme.typography.labelLarge, color = OceanTurquoise)
                }
            }
        }
    }
}