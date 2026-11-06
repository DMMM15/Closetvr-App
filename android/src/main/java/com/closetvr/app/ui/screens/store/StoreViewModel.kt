package com.closetvr.app.ui.screens.store

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closetvr.app.data.model.Product
import com.closetvr.app.data.model.categoryDisplayName
import com.closetvr.app.data.repository.CartRepository
import com.closetvr.app.data.repository.ClosetRepository
import com.closetvr.app.data.repository.ProductRepository
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

data class StoreUiState(
    val products: List<Product> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCategory: String? = null   // nombre visible de la categoría; null = todas
) {
    /** Categorías que existen en los productos cargados (aparecen solas como filtros). */
    val categories: List<String>
        get() = products.map { categoryDisplayName(it.category) }.distinct()

    val visibleProducts: List<Product>
        get() = if (selectedCategory == null) products
        else products.filter { categoryDisplayName(it.category) == selectedCategory }
}

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val productRepository = ProductRepository()
    private val cartRepository = CartRepository()
    private val closetRepository = ClosetRepository()

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    // Mensajes de una sola vez para el Snackbar ("Añadido al carrito", errores, etc.)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.products.isEmpty(), error = null) }
            try {
                val products = productRepository.getProducts()
                Log.d("StoreViewModel", "Productos cargados desde la tienda: ${products.size}")
                _uiState.update { it.copy(products = products, isLoading = false) }
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Error al cargar productos de la tienda: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "No se pudieron cargar los productos. Revisa tu conexión con el servidor."
                    )
                }
            }
        }
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun addToCart(product: Product) {
        viewModelScope.launch {
            val token = tokenManager.tokenFlow.firstOrNull()
            if (token.isNullOrEmpty()) {
                Log.e("StoreViewModel", "No hay token disponible para agregar al carrito.")
                _messages.tryEmit("Necesitas una sesión de Closet VR para usar el carrito")
                return@launch
            }
            try {
                Log.d("StoreViewModel", "Intentando agregar al carrito -> Producto ID: ${product.id}, Nombre: ${product.name}")
                cartRepository.addToCart(token, product.id)
                Log.d("StoreViewModel", "Respuesta exitosa del repositorio al agregar al carrito")
                _messages.tryEmit("${product.name} se añadió al carrito")
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Error al añadir al carrito: ${e.message}", e)
                _messages.tryEmit("No se pudo añadir al carrito")
            }
        }
    }

    fun addToCloset(product: Product) {
        viewModelScope.launch {
            val token = tokenManager.tokenFlow.firstOrNull()
            if (token.isNullOrEmpty()) {
                Log.e("StoreViewModel", "No hay token disponible para guardar en armario.")
                _messages.tryEmit("Necesitas una sesión de Closet VR para guardar prendas")
                return@launch
            }
            try {
                Log.d("StoreViewModel", "Intentando guardar en armario -> Producto ID: ${product.id}, Nombre: ${product.name}")
                closetRepository.addFromStore(token, product.id)
                Log.d("StoreViewModel", "Respuesta exitosa del repositorio al guardar en armario")
                _messages.tryEmit("${product.name} se guardó en tu armario")
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Error al guardar en armario: ${e.message}", e)
                _messages.tryEmit("No se pudo guardar en el armario")
            }
        }
    }
}