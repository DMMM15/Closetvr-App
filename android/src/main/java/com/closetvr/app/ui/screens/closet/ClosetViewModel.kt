package com.closetvr.app.ui.screens.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closetvr.app.data.model.ClosetItem
import com.closetvr.app.data.repository.ClosetRepository
import com.closetvr.app.util.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClosetUiState(
    val items: List<ClosetItem> = emptyList(),
    val isLoading: Boolean = false
) {
    // Las secciones se derivan de las prendas: aparecen solas conforme se añaden prendas de cada categoría
    val sections: List<ClosetSection>
        get() = groupByCategory(items)
}

class ClosetViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val repository = ClosetRepository()

    private val _uiState = MutableStateFlow(ClosetUiState())
    val uiState: StateFlow<ClosetUiState> = _uiState.asStateFlow()

    fun loadCloset() {
        viewModelScope.launch {
            val token = tokenManager.tokenFlow.firstOrNull() ?: return@launch
            // Con prendas ya en pantalla se refresca en silencio, sin spinner
            _uiState.update { it.copy(isLoading = it.items.isEmpty()) }
            try {
                val items = repository.getCloset(token)
                _uiState.update { it.copy(items = items) }
            } catch (_: Exception) {
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun remove(item: ClosetItem) {
        viewModelScope.launch {
            val token = tokenManager.tokenFlow.firstOrNull() ?: return@launch
            try {
                repository.removeFromCloset(token, item.id)
                _uiState.update { state -> state.copy(items = state.items.filter { it.id != item.id }) }
            } catch (_: Exception) { }
        }
    }
}
