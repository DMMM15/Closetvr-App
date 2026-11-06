package com.closetvr.app.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closetvr.app.data.network.RetrofitClient
import com.closetvr.app.util.TokenManager
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class LoginViewModel : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    fun onEmailChange(value: String) {
        uiState = uiState.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, error = null)
    }

    fun login(tokenManager: TokenManager, onSuccess: () -> Unit) {
        val email = uiState.email.trim()
        val password = uiState.password
        if (email.isEmpty() || password.isEmpty()) {
            uiState = uiState.copy(error = "Por favor ingresa tu correo y contraseña.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val response = RetrofitClient.apiService.login(mapOf("email" to email, "password" to password))
                if (response.isSuccessful && response.body() != null) {
                    val authData = response.body()!!
                    val token = authData.token
                    val name = authData.user?.name ?: email.substringBefore("@")

                    // Guarda la sesión en el celular
                    tokenManager.saveSession(token, name)

                    uiState = uiState.copy(isLoading = false)
                    onSuccess()
                } else {
                    val msg = response.errorBody()?.string() ?: "Credenciales incorrectas"
                    uiState = uiState.copy(isLoading = false, error = "Error de inicio de sesión: $msg")
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = "No se pudo conectar con el servidor. Revisa tu conexión.")
            }
        }
    }

    // NUEVA FUNCIÓN PARA GOOGLE
    fun loginWithGoogle(idToken: String, tokenManager: TokenManager, onSuccess: () -> Unit) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val response = RetrofitClient.apiService.googleLogin(mapOf("token" to idToken))
                if (response.isSuccessful && response.body() != null) {
                    val authData = response.body()!!
                    val token = authData.token
                    val name = authData.user?.name ?: "Usuario"

                    // ESTO ES LO QUE ARREGLA EL CARRITO: Guarda la sesión de Google
                    tokenManager.saveSession(token, name)

                    uiState = uiState.copy(isLoading = false)
                    onSuccess()
                } else {
                    val msg = response.errorBody()?.string() ?: "Error al validar con el servidor"
                    uiState = uiState.copy(isLoading = false, error = "Error de Google: $msg")
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = "No se pudo conectar con el servidor. Revisa tu conexión.")
            }
        }
    }
}