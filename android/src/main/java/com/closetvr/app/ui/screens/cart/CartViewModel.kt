package com.closetvr.app.ui.screens.cart

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closetvr.app.data.model.CartItem
import com.closetvr.app.data.repository.CartRepository
import com.closetvr.app.util.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartUiState(
    val items: List<CartItem> = emptyList(),
    val isLoading: Boolean = false
) {
    // Total real = precio × cantidad de cada línea
    val total: Double
        get() = items.sumOf { (it.price.toDoubleOrNull() ?: 0.0) * it.quantity }

    val itemCount: Int
        get() = items.sumOf { it.quantity }
}

class CartViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val repository = CartRepository()

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    fun loadCart() {
        viewModelScope.launch {
            val token = tokenManager.tokenFlow.firstOrNull()

            // Verificamos si hay token antes de intentar la petición
            if (token.isNullOrEmpty()) {
                Log.e("CartViewModel", "Error: El token es nulo o vacío. Abortando petición.")
                return@launch
            }

            _uiState.update { it.copy(isLoading = it.items.isEmpty()) }

            try {
                Log.d("CartViewModel", "Haciendo petición GET al carrito con token...")
                val items = repository.getCart(token)
                Log.d("CartViewModel", "Éxito. Prendas recibidas: ${items.size}")
                _uiState.update { it.copy(items = items) }
            } catch (e: Exception) {
                // ESTO IMPRIMIRÁ EL ERROR REAL EN LA CONSOLA
                Log.e("CartViewModel", "Fallo al cargar el carrito: ${e.message}", e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun remove(item: CartItem) {
        viewModelScope.launch {
            val token = tokenManager.tokenFlow.firstOrNull() ?: return@launch
            try {
                repository.removeFromCart(token, item.cart_item_id)
                _uiState.update { state ->
                    state.copy(items = state.items.filter { it.cart_item_id != item.cart_item_id })
                }
            } catch (e: Exception) {
                // También evitamos silenciar errores aquí
                Log.e("CartViewModel", "Fallo al remover el item del carrito: ${e.message}", e)
            }
        }
    }
}