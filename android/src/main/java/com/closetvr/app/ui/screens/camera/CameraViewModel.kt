package com.closetvr.app.ui.screens.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closetvr.app.data.model.ClothingCategory
import com.closetvr.app.data.repository.ClosetRepository
import com.closetvr.app.util.TokenManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CameraUiState(
    val capturedFile: File? = null,
    val name: String = "",
    val category: ClothingCategory? = null,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    /** El formulario exige nombre y categoría antes de poder enviar la prenda al armario. */
    val canSave: Boolean
        get() = capturedFile != null && name.isNotBlank() && category != null && !isSaving
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val repository = ClosetRepository()

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // Evento de una sola vez: la prenda quedó guardada en el armario
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun onPhotoCaptured(file: File) {
        _uiState.value = CameraUiState(capturedFile = file)   // formulario limpio para la nueva foto
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value.take(MAX_NAME_LENGTH), error = null) }
    }

    fun onCategoryChange(category: ClothingCategory) {
        _uiState.update { it.copy(category = category, error = null) }
    }

    fun retake() {
        _uiState.value.capturedFile?.let { runCatching { it.delete() } }
        _uiState.value = CameraUiState()
    }

    fun saveToCloset() {
        val current = _uiState.value
        val file = current.capturedFile ?: return
        val category = current.category ?: return
        val name = current.name.trim()
        if (name.isEmpty() || current.isSaving) return

        viewModelScope.launch {
            val token = tokenManager.tokenFlow.firstOrNull()
            if (token == null) {
                _uiState.update {
                    it.copy(error = "Tu sesión no tiene acceso al servidor. Inicia sesión con tu correo para guardar prendas.")
                }
                return@launch
            }

            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                // Misma llamada de siempre: foto + nombre + categoría (la categoría viaja como texto)
                repository.uploadPhoto(token, file, name, category.key)
                runCatching { file.delete() }
                _uiState.value = CameraUiState()   // vuelve a mostrar la cámara lista para otra foto
                _saved.tryEmit(Unit)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "No se pudo guardar la foto. Revisa tu conexión.") }
            }
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
    }
}
