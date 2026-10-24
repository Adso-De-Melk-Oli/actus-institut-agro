package io.github.adsodemelkoli.actus.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Orange de la marque, celui du favicon et du titre du site. */
val Brand = Color(0xFFF8AC00)

// Palette Material 3 générée à partir de l'orange de la marque
private val Light = lightColorScheme(
    primary = Color(0xFF7F5700), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB0), onPrimaryContainer = Color(0xFF281800),
    secondary = Color(0xFF6E5B40), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8DEBB), onSecondaryContainer = Color(0xFF261904),
    tertiary = Color(0xFF50643F), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD2EABB), onTertiaryContainer = Color(0xFF0E2004),
    background = Color(0xFFFFF8F3), onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFF8F3), onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF0E0CF), onSurfaceVariant = Color(0xFF4F4539),
    outline = Color(0xFF817567), outlineVariant = Color(0xFFD3C4B4),
    inverseSurface = Color(0xFF352F2A), inverseOnSurface = Color(0xFFF9EFE6), inversePrimary = Color(0xFFFDBA4A),
    surfaceDim = Color(0xFFE3D8CC), surfaceBright = Color(0xFFFFF8F3),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFDF2E6),
    surfaceContainer = Color(0xFFF7ECE0), surfaceContainerHigh = Color(0xFFF1E7DB), surfaceContainerHighest = Color(0xFFECE1D5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFDBA4A), onPrimary = Color(0xFF432C00),
    primaryContainer = Color(0xFF604100), onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFDBC2A0), onSecondary = Color(0xFF3D2E16),
    secondaryContainer = Color(0xFF554429), onSecondaryContainer = Color(0xFFF8DEBB),
    tertiary = Color(0xFFB6CEA1), onTertiary = Color(0xFF233515),
    tertiaryContainer = Color(0xFF394C29), onTertiaryContainer = Color(0xFFD2EABB),
    background = Color(0xFF17130B), onBackground = Color(0xFFECE1D5),
    surface = Color(0xFF17130B), onSurface = Color(0xFFECE1D5),
    surfaceVariant = Color(0xFF4F4539), onSurfaceVariant = Color(0xFFD3C4B4),
    outline = Color(0xFF9C8E7F), outlineVariant = Color(0xFF4F4539),
    inverseSurface = Color(0xFFECE1D5), inverseOnSurface = Color(0xFF352F27), inversePrimary = Color(0xFF7F5700),
    surfaceDim = Color(0xFF17130B), surfaceBright = Color(0xFF3E3830),
    surfaceContainerLowest = Color(0xFF120D07), surfaceContainerLow = Color(0xFF201B13),
    surfaceContainer = Color(0xFF241F17), surfaceContainerHigh = Color(0xFF2F2921), surfaceContainerHighest = Color(0xFF3A342B),
)

/**
 * Thème Material 3. Par défaut les couleurs de l'Institut Agro ; sur Android 12 et plus, l'option
 * « Couleurs du téléphone » reprend celles du fond d'écran (Material You).
 */
@Composable
fun ActusTheme(dynamic: Boolean = false, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
