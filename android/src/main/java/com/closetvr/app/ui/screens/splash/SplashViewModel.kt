package com.closetvr.app.ui.screens.splash

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closetvr.app.ui.navigation.Screen
import com.closetvr.app.util.TokenManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Valida la sesión mientras la Splash anima.
 *
 * Misma regla de siempre (hay token JWT guardado -> Tienda, si no -> Login), y además se
 * reconoce una sesión de Firebase activa (inicio con Google) para no pedir login de nuevo.
 * La Splash dura un mínimo para que la animación se vea completa y la transición sea fluida.
 */
class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)

    private val _destination = MutableStateFlow<String?>(null)
    val destination: StateFlow<String?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()

            val token = tokenManager.tokenFlow.firstOrNull()
            val firebaseUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

            val target = if (token != null || firebaseUser != null) Screen.Store.route else Screen.Login.route

            val remaining = MIN_SPLASH_MILLIS - (SystemClock.elapsedRealtime() - startedAt)
            if (remaining > 0) delay(remaining)

            _destination.value = target
        }
    }

    private companion object {
        const val MIN_SPLASH_MILLIS = 2000L
    }
}
