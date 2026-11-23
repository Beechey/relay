package com.avetero.relay.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.avetero.relay.R

/** Roboto Flex (variable, bundled so TV boxes without Google fonts get it too); licence in licenses/RobotoFlex-OFL.txt. */
@OptIn(ExperimentalTextApi::class)
val RobotoFlex = FontFamily(listOf(400, 500, 600, 700, 800).map {
    Font(R.font.roboto_flex, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
})

/** Design tokens (design-spec.md): slate neutrals, amber only as the accent. Phone and TV share them. */
object Tok {
    val Surface = Color(0xFF111318)
    val ContainerLow = Color(0xFF191C20)
    val Container = Color(0xFF1D2024)
    val ContainerHigh = Color(0xFF282A2F)
    val ContainerHighest = Color(0xFF33353A)
    val OnSurface = Color(0xFFE2E2E9)
    val OnSurfaceVariant = Color(0xFFC4C6D0)
    val Outline = Color(0xFF8E9099)
    val OutlineVariant = Color(0xFF44474E)
    val Primary = Color(0xFFFFB95C)
    val OnPrimary = Color(0xFF472A00)
    val PrimaryContainer = Color(0xFF653E00)
    val OnPrimaryContainer = Color(0xFFFFDDB6)
    val SecondaryContainer = Color(0xFF2F3A52)
    val OnSecondaryContainer = Color(0xFFD8E2FF)
    val Secondary = Color(0xFFBEC6DC)
    val OnSecondary = Color(0xFF283141)
    val Tertiary = Color(0xFFA0CFD0)
    val InverseSurface = Color(0xFFE2E2E9)
    val InverseOnSurface = Color(0xFF2E3036)
    val InversePrimary = Color(0xFF8B5000)
    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF690005)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)
    val Scrim = Color(0xFF000000)
    /** "Live" red: the LIVE badge and on-air marks. */
    val Live = Color(0xFFFF5A6E)
}

val RelayColors = darkColorScheme(
    primary = Tok.Primary, onPrimary = Tok.OnPrimary, primaryContainer = Tok.PrimaryContainer, onPrimaryContainer = Tok.OnPrimaryContainer,
    inversePrimary = Tok.InversePrimary,
    secondary = Tok.Secondary, onSecondary = Tok.OnSecondary, secondaryContainer = Tok.SecondaryContainer, onSecondaryContainer = Tok.OnSecondaryContainer,
    tertiary = Tok.Tertiary,
    background = Tok.Surface, onBackground = Tok.OnSurface, surface = Tok.Surface, onSurface = Tok.OnSurface,
    surfaceVariant = Tok.ContainerHighest, onSurfaceVariant = Tok.OnSurfaceVariant, surfaceTint = Tok.Primary,
    inverseSurface = Tok.InverseSurface, inverseOnSurface = Tok.InverseOnSurface,
    error = Tok.Error, onError = Tok.OnError, errorContainer = Tok.ErrorContainer, onErrorContainer = Tok.OnErrorContainer,
    outline = Tok.Outline, outlineVariant = Tok.OutlineVariant, scrim = Tok.Scrim,
    surfaceBright = Color(0xFF37393E), surfaceDim = Tok.Surface,
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Tok.ContainerLow, surfaceContainer = Tok.Container,
    surfaceContainerHigh = Tok.ContainerHigh, surfaceContainerHighest = Tok.ContainerHighest,
)

private fun Typography.flex() = Typography(
    displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
    headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
    titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
    bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
    labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    displayLargeEmphasized = displayLargeEmphasized.f(), displayMediumEmphasized = displayMediumEmphasized.f(), displaySmallEmphasized = displaySmallEmphasized.f(),
    headlineLargeEmphasized = headlineLargeEmphasized.f(), headlineMediumEmphasized = headlineMediumEmphasized.f(), headlineSmallEmphasized = headlineSmallEmphasized.f(),
    titleLargeEmphasized = titleLargeEmphasized.f(), titleMediumEmphasized = titleMediumEmphasized.f(), titleSmallEmphasized = titleSmallEmphasized.f(),
    bodyLargeEmphasized = bodyLargeEmphasized.f(), bodyMediumEmphasized = bodyMediumEmphasized.f(), bodySmallEmphasized = bodySmallEmphasized.f(),
    labelLargeEmphasized = labelLargeEmphasized.f(), labelMediumEmphasized = labelMediumEmphasized.f(), labelSmallEmphasized = labelSmallEmphasized.f(),
)

private fun TextStyle.f() = copy(fontFamily = RobotoFlex)

val RelayTypography = Typography().flex()

private val TvTypography = androidx.tv.material3.Typography().run {
    androidx.tv.material3.Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private val TvColors = androidx.tv.material3.darkColorScheme(
    primary = Tok.Primary, onPrimary = Tok.OnPrimary, primaryContainer = Tok.PrimaryContainer, onPrimaryContainer = Tok.OnPrimaryContainer,
    inversePrimary = Tok.InversePrimary, secondary = Tok.Secondary, onSecondary = Tok.OnSecondary,
    secondaryContainer = Tok.SecondaryContainer, onSecondaryContainer = Tok.OnSecondaryContainer, tertiary = Tok.Tertiary,
    background = Tok.Surface, onBackground = Tok.OnSurface, surface = Tok.Surface, onSurface = Tok.OnSurface,
    surfaceVariant = Tok.ContainerHighest, onSurfaceVariant = Tok.OnSurfaceVariant, surfaceTint = Tok.Primary,
    inverseSurface = Tok.InverseSurface, inverseOnSurface = Tok.InverseOnSurface,
    error = Tok.Error, onError = Tok.OnError, errorContainer = Tok.ErrorContainer, onErrorContainer = Tok.OnErrorContainer,
    border = Tok.Outline, borderVariant = Tok.OutlineVariant, scrim = Tok.Scrim,
)

/**
 * Material 3 Expressive everywhere (colours, Roboto Flex, the expressive spring motion scheme); on TV the
 * Material 3 for TV theme is layered on top so tv-material components get the same tokens.
 */
@Composable
fun RelayTheme(tv: Boolean, content: @Composable () -> Unit) =
    MaterialExpressiveTheme(colorScheme = RelayColors, motionScheme = MotionScheme.expressive(), typography = RelayTypography) {
        // text and icons outside a Surface default to onSurface (not black) on both component sets
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Tok.OnSurface) {
            if (tv) androidx.tv.material3.MaterialTheme(colorScheme = TvColors, typography = TvTypography) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Tok.OnSurface, content = content)
            }
            else content()
        }
    }

private val tones = listOf(0xFF2B3A4A, 0xFF2A3A4F, 0xFF33405A, 0xFF3D2F4A, 0xFF1F3B3A, 0xFF2B3D3A, 0xFF2A3A2A, 0xFF34384A).map { Color(it) }

/** Placeholder tint for artwork that hasn't loaded (or has none), stable per item. */
fun tone(seed: Int) = tones[Math.floorMod(seed, tones.size)]
