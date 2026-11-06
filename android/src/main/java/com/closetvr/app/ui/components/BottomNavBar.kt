package com.closetvr.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.closetvr.app.ui.navigation.Screen
import com.closetvr.app.ui.theme.*

data class NavItem(val route: String, val label: String, val icon: ImageVector)

val bottomNavItems = listOf(
    NavItem(Screen.Store.route, "Tienda", Icons.Outlined.Storefront),
    NavItem(Screen.Closet.route, "Armario", Icons.Outlined.Checkroom),
    NavItem(Screen.Camera.route, "Cámara", Icons.Filled.CameraAlt),   // acción principal, al centro
    NavItem(Screen.Cart.route, "Carrito", Icons.Outlined.ShoppingCart),
    NavItem(Screen.Profile.route, "Perfil", Icons.Outlined.Person)
)

/** Barra de navegación flotante en forma de píldora; la cámara es el botón central destacado. */
@Composable
fun ClosetBottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            color = OceanSurface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, OceanOutline.copy(alpha = 0.8f)),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentRoute == item.route
                    if (item.route == Screen.Camera.route) {
                        CameraNavButton(item = item, selected = selected, onClick = { onNavigate(item.route) })
                    } else {
                        RegularNavItem(item = item, selected = selected, onClick = { onNavigate(item.route) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.RegularNavItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (selected) OceanTurquoise else OceanTextMuted,
        label = "navTint"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun RowScope.CameraNavButton(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 54.dp else 50.dp)
                .background(OceanAccentBrush, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = OceanAbyss,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
