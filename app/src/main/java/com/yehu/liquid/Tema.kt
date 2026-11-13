package com.yehu.liquid

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/* Nunito: redonda y clara, la misma de tus otras apps */
val Nunito = FontFamily(
    Font(R.font.nunito_400, FontWeight.Normal),
    Font(R.font.nunito_600, FontWeight.SemiBold),
    Font(R.font.nunito_700, FontWeight.Bold),
    Font(R.font.nunito_800, FontWeight.ExtraBold),
    Font(R.font.nunito_900, FontWeight.Black)
)

private val ClaroBase = lightColorScheme(
    primary = Color(0xFF1F6BFF),
    onPrimary = Color.White,
    background = Color(0xFFF3F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E8EF)
)
private val OscuroBase = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF00296B),
    background = Color(0xFF111216),
    surface = Color(0xFF1B1C21),
    surfaceVariant = Color(0xFF2A2C33)
)

/* Si es tema oscuro (para el vidrio y los textos sobre él) */
val LocalOscuro = staticCompositionLocalOf { false }

@Composable
fun TemaLiquid(oscuroForzado: Boolean? = null, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val oscuro = oscuroForzado ?: isSystemInDarkTheme()
    /* Material You: los colores salen de tu fondo de pantalla (Android 12+) */
    val esquema: ColorScheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (oscuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        oscuro -> OscuroBase
        else -> ClaroBase
    }
    val b = Typography()
    val tipo = Typography(
        displayLarge = b.displayLarge.copy(fontFamily = Nunito),
        displayMedium = b.displayMedium.copy(fontFamily = Nunito),
        displaySmall = b.displaySmall.copy(fontFamily = Nunito),
        headlineLarge = b.headlineLarge.copy(fontFamily = Nunito),
        headlineMedium = b.headlineMedium.copy(fontFamily = Nunito),
        headlineSmall = b.headlineSmall.copy(fontFamily = Nunito),
        titleLarge = b.titleLarge.copy(fontFamily = Nunito),
        titleMedium = b.titleMedium.copy(fontFamily = Nunito),
        titleSmall = b.titleSmall.copy(fontFamily = Nunito),
        bodyLarge = b.bodyLarge.copy(fontFamily = Nunito),
        bodyMedium = b.bodyMedium.copy(fontFamily = Nunito),
        bodySmall = b.bodySmall.copy(fontFamily = Nunito),
        labelLarge = b.labelLarge.copy(fontFamily = Nunito),
        labelMedium = b.labelMedium.copy(fontFamily = Nunito),
        labelSmall = b.labelSmall.copy(fontFamily = Nunito)
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalOscuro provides oscuro) {
        MaterialTheme(colorScheme = esquema, typography = tipo, content = content)
    }
}
