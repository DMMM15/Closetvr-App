package com.closetvr.app.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Store : Screen("store")
    object Cart : Screen("cart")
    object Closet : Screen("closet")
    object Camera : Screen("camera")
    object Profile : Screen("profile")
}
