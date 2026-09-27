package `in`.dalc.pkdmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sem dynamic color (ADR 0001): a paleta vem sempre dos tokens do PKD.

private val PkdLightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightActiveBg,
    onPrimaryContainer = LightAccent,
    secondary = LightAccent,
    secondaryContainer = LightActiveBg,
    onSecondaryContainer = LightAccent,
    background = LightBg,
    onBackground = LightText,
    surface = LightPanel,
    onSurface = LightText,
    surfaceVariant = LightBg,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    outlineVariant = LightBorder,
)

private val PkdDarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color(0xFF111111),
    primaryContainer = DarkActiveBg,
    onPrimaryContainer = DarkAccent,
    secondary = DarkAccent,
    secondaryContainer = DarkActiveBg,
    onSecondaryContainer = DarkAccent,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkPanel,
    onSurface = DarkText,
    surfaceVariant = DarkBg,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
)

@Composable
fun PkdMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PkdDarkColors else PkdLightColors,
        content = content,
    )
}
