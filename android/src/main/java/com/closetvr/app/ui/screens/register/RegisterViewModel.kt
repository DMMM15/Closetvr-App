package com.closetvr.app.ui.screens.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closetvr.app.data.network.RetrofitClient
import com.closetvr.app.util.TokenManager
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class RegisterViewModel : ViewModel() {
    var uiState by mutableStateOf(RegisterUiState())
        private set

    fun onNameChange(value: String) {
        uiState = uiState.copy(name = value, error = null)
    }

    fun onEmailChange(value: String) {
        uiState = uiState.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, error = null)
    }

    fun register(tokenManager: TokenManager, onSuccess: () -> Unit) {
        val name = uiState.name.trim()
        val email = uiState.email.trim()
        val password = uiState.password

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            uiState = uiState.copy(error = "Por favor completa todos los campos.")
            return
        }
        if (password.length < 6) {
            uiState = uiState.copy(error = "La contraseña debe tener al menos 6 caracteres.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val response = RetrofitClient.apiService.register(
                    mapOf("name" to name, "email" to email, "password" to password)
                )
                if (response.isSuccessful && response.body() != null) {
                    val authData = response.body()!!
                    tokenManager.saveSession(authData.token, name)
                    uiState = uiState.copy(isLoading = false)
                    onSuccess()
                } else {
                    val msg = response.errorBody()?.string() ?: "Error al registrar usuario"
                    uiState = uiState.copy(isLoading = false, error = msg)
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = "No se pudo conectar con el servidor.")
            }
        }
    }
}
