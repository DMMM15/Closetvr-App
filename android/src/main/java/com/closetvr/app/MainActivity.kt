package com.closetvr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.closetvr.app.ui.components.ClosetBottomNavBar
import com.closetvr.app.ui.components.OceanBackground
import com.closetvr.app.ui.navigation.Screen
import com.closetvr.app.ui.screens.camera.CameraScreen
import com.closetvr.app.ui.screens.cart.CartScreen
import com.closetvr.app.ui.screens.closet.ClosetScreen
import com.closetvr.app.ui.screens.login.LoginScreen
import com.closetvr.app.ui.screens.profile.ProfileScreen
import com.closetvr.app.ui.screens.register.RegisterScreen
import com.closetvr.app.ui.screens.splash.SplashScreen
import com.closetvr.app.ui.screens.store.StoreScreen
import com.closetvr.app.ui.theme.ClosetVRTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Debe ir antes de super.onCreate: aplica el tema de arranque (fondo azul abismo, sin parpadeo blanco)
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            ClosetVRTheme {
                ClosetVRApp()
            }
        }
    }
}

// Rutas que muestran la barra de navegación inferior (todo excepto splash/login/register y chat)
private val routesWithBottomBar = setOf(
    Screen.Store.route, Screen.Closet.route, Screen.Camera.route,
    Screen.Cart.route, Screen.Profile.route
)

@Composable
fun ClosetVRApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    OceanBackground {
        Scaffold(
            containerColor = Color.Transparent,
            // Cada pantalla gestiona sus propios insets (barra de estado) para ir de borde a borde
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                AnimatedVisibility(
                    visible = currentRoute in routesWithBottomBar,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it }
                ) {
                    ClosetBottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(Screen.Store.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                // La Splash enmascara la lectura de sesión (token + Firebase) y decide el destino real
                startDestination = Screen.Splash.route,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                enterTransition = { fadeIn(tween(250)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(250)) },
                popExitTransition = { fadeOut(tween(150)) }
            ) {
                composable(Screen.Splash.route) {
                    SplashScreen(
                        onFinished = { destination ->
                            navController.navigate(destination) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(Screen.Store.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        },
                        onGoToRegister = { navController.navigate(Screen.Register.route) }
                    )
                }
                composable(Screen.Register.route) {
                    RegisterScreen(
                        onRegisterSuccess = {
                            navController.navigate(Screen.Store.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        },
                        onGoToLogin = { navController.popBackStack() }
                    )
                }
                composable(Screen.Store.route) { StoreScreen() }
                composable(Screen.Cart.route) { CartScreen() }
                composable(Screen.Closet.route) { ClosetScreen() }
                composable(Screen.Camera.route) {
                    CameraScreen(
                        onGoToCloset = {
                            navController.navigate(Screen.Closet.route) {
                                popUpTo(Screen.Store.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        onLogout = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0)
                            }
                        },
                        onNavigateToChat = {
                            navController.navigate("chat_asesor")
                        }
                    )
                }

                // --- NUEVA RUTA PARA EL CHAT ---
                composable("chat_asesor") {
                    com.closetvr.app.ui.screens.profile.ChatAsesorScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}