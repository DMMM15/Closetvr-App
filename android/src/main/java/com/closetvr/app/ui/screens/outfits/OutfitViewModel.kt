package com.closetvr.app.ui.screens.outfits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closetvr.app.data.model.Outfit
import com.closetvr.app.data.repository.OutfitRepository
import com.closetvr.app.util.TokenManager
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException

/** Estado de la lista "Mis Outfits": Loading / Success / Error. */
sealed class OutfitListState {
    object Loading : OutfitListState()
    data class Success(val outfits: List<Outfit>) : OutfitListState()
    data class Error(val message: String) : OutfitListState()
}

/** Estado de la generación con IA (puede tardar varios segundos). */
sealed class GenerateState {
    object Idle : GenerateState()
    object Loading : GenerateState()
    data class Success(val outfit: Outfit) : GenerateState()
    data class Error(val message: String) : GenerateState()
}

class OutfitViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OutfitRepository()
    private val tokenManager = TokenManager(application)

    private val _outfitsState = MutableStateFlow<OutfitListState>(OutfitListState.Loading)
    val outfitsState: StateFlow<OutfitListState> = _outfitsState.asStateFlow()

    private val _generateState = MutableStateFlow<GenerateState>(GenerateState.Idle)
    val generateState: StateFlow<GenerateState> = _generateState.asStateFlow()

    // Id del outfit que se está eliminando (para mostrar un spinner en su tarjeta)
    private val _deletingId = MutableStateFlow<Int?>(null)
    val deletingId: StateFlow<Int?> = _deletingId.asStateFlow()

    // Mensajes de una sola vez para mostrar en un Snackbar (outfit eliminado / error)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun loadOutfits() {
        viewModelScope.launch {
            val token = tokenManager.tokenFlow.firstOrNull()
            if (token.isNullOrEmpty()) {
                _outfitsState.value = OutfitListState.Error("No hay sesión activa. Inicia sesión de nuevo.")
                return@launch
            }
            // Con outfits ya en pantalla se refresca en silencio, sin spinner
            if (_outfitsState.value !is OutfitListState.Success) {
                _outfitsState.value = OutfitListState.Loading
            }
            try {
                _outfitsState.value = OutfitListState.Success(repository.getOutfits(token))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Si ya había datos en pantalla, no los reemplazamos por una pantalla de error
                if (_outfitsState.value !is OutfitListState.Success) {
                    _outfitsState.value = OutfitListState.Error(e.toUserMessage())
                }
            }
        }
    }

    fun generateOutfit(nombre: String, clima: String, estilo: String) {
        if (_generateState.value is GenerateState.Loading) return
        viewModelScope.launch {
            _generateState.value = GenerateState.Loading

            val token = tokenManager.tokenFlow.firstOrNull()
            if (token.isNullOrEmpty()) {
                _generateState.value = GenerateState.Error("No hay sesión activa. Inicia sesión de nuevo.")
                return@launch
            }

            try {
                val outfit = repository.generateOutfit(token, nombre.trim(), clima, estilo)
                // Si la lista ya está cargada, el nuevo outfit aparece arriba al instante
                val current = _outfitsState.value
                if (current is OutfitListState.Success) {
                    _outfitsState.value = OutfitListState.Success(listOf(outfit) + current.outfits)
                }
                _generateState.value = GenerateState.Success(outfit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _generateState.value = GenerateState.Error(e.toUserMessage())
            }
        }
    }

    fun deleteOutfit(outfit: Outfit) {
        if (_deletingId.value != null) return
        viewModelScope.launch {
            val token = tokenManager.tokenFlow.firstOrNull()
            if (token.isNullOrEmpty()) {
                _messages.tryEmit("No hay sesión activa. Inicia sesión de nuevo.")
                return@launch
            }
            _deletingId.value = outfit.id
            try {
                repository.deleteOutfit(token, outfit.id)
                val current = _outfitsState.value
                if (current is OutfitListState.Success) {
                    _outfitsState.value = OutfitListState.Success(current.outfits.filter { it.id != outfit.id })
                }
                _messages.tryEmit("Outfit \"${outfit.name}\" eliminado")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.toUserMessage())
            } finally {
                _deletingId.value = null
            }
        }
    }

    /** La pantalla lo llama al cerrar el diálogo o después de mostrar el resultado. */
    fun consumeGenerateResult() {
        _generateState.value = GenerateState.Idle
    }
}

// Convierte cualquier excepción en un mensaje entendible para el usuario
private fun Throwable.toUserMessage(): String = when (this) {
    is SocketTimeoutException -> "La IA tardó demasiado en responder. Intenta de nuevo."
    is IOException -> "No se pudo conectar con el servidor. Revisa tu internet y que ngrok esté activo."
    is JsonParseException -> "El servidor respondió algo inesperado. Intenta de nuevo."
    else -> message ?: "Ocurrió un error inesperado."
}