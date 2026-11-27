package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class BitevueExtendedColors(
    val heroCardBg: Color,
    val heroEyebrow: Color,
    val heroHint: Color,
    val heroTrack: Color,
    val heroFill: Color,
    val proteinAccent: Color,
    val carbsAccent: Color,
    val fatAccent: Color,
    val macroTrack: Color,
    val waterFilled: Color,
    val waterEmpty: Color,
    val hairline: Color,
    val success: Color,
    val successContainer: Color
)

private val LightExtendedColors = BitevueExtendedColors(
    heroCardBg = BotanicalPine,
    heroEyebrow = HeroEyebrowSage,
    heroHint = HeroHintSage,
    heroTrack = HeroTrackPine,
    heroFill = HeroFillMoss,
    proteinAccent = MacroProteinSage,
    carbsAccent = MacroCarbsTerracotta,
    fatAccent = MacroFatLavender,
    macroTrack = MacroTrackWarmGray,
    waterFilled = HydrationAquaFilled,
    waterEmpty = HydrationAquaEmpty,
    hairline = SoftLineHairline,
    success = BotanicalSuccess,
    successContainer = BotanicalSuccessContainer
)

val LocalBitevueColors = staticCompositionLocalOf { LightExtendedColors }

private val BitevueLightColorScheme = lightColorScheme(
    primary = BotanicalPine,
    onPrimary = PureWhite,
    primaryContainer = PaleSageWash,
    onPrimaryContainer = BotanicalPine,
    secondary = EucalyptusSage,
    onSecondary = PureWhite,
    secondaryContainer = PaleSageWash,
    onSecondaryContainer = BotanicalPine,
    tertiary = MacroCarbsTerracotta,
    onTertiary = PureWhite,
    background = OatPaper,
    onBackground = BotanicalPine,
    surface = PureWhite,
    onSurface = BotanicalPine,
    surfaceVariant = PaleSageWash,
    onSurfaceVariant = WarmMutedStone,
    outline = SoftLineHairline,
    outlineVariant = HeroEyebrowSage,
    error = TerracottaError,
    onError = PureWhite,
    errorContainer = TerracottaErrorContainer,
    onErrorContainer = TerracottaError
)

private val BitevueShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

object BitevueThemeTokens {
    val extended: BitevueExtendedColors
        @Composable get() = LocalBitevueColors.current
}

@Composable
fun BitevueTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalBitevueColors provides LightExtendedColors) {
        MaterialTheme(
            colorScheme = BitevueLightColorScheme,
            typography = BitevueTypography,
            shapes = BitevueShapes,
            content = content
        )
    }
}
