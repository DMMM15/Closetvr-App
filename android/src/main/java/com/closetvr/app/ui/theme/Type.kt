package com.closetvr.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.closetvr.app.R

// Inter: la tipografía de producto usada por grandes empresas de tecnología.
// Los .ttf viven en res/font (inter_regular, inter_medium, inter_semibold, inter_bold).
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = InterFamily,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp
)

val ClosetTypography = Typography(
    displaySmall = style(36, 44, FontWeight.Bold, -0.5),
    headlineLarge = style(32, 40, FontWeight.Bold, -0.4),
    headlineMedium = style(28, 36, FontWeight.Bold, -0.3),
    headlineSmall = style(24, 32, FontWeight.SemiBold, -0.2),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.1),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.1),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.1),
    bodySmall = style(12, 16, FontWeight.Normal, 0.2),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.3),
    labelSmall = style(11, 16, FontWeight.Medium, 0.4)
)
