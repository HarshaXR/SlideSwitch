package app.slideswitch.remote.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.slideswitch.remote.R

/**
 * Apple-style design tokens (clean white/black canvas, soft grey wells, one blue accent,
 * bold large titles). Deliberately not Material: no MaterialTheme, no ripples, no elevation.
 */
@Immutable
data class Palette(
    val background: Color,          // page canvas
    val card: Color,                // raised surfaces (sheets)
    val well: Color,                // grey rounded containers (device list, tiles, tab bar)
    val wellSelected: Color,        // selected segment in the tab bar / pickers
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val fill: Color,                // text-field fill
    val accent: Color,              // primary buttons
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val indigo: Color,
    val purple: Color,
    val teal: Color,
    val pink: Color,
)

val LightPalette = Palette(
    background = Color.White, card = Color.White,
    well = Color(0xFFE5E5EA), wellSelected = Color(0xFFAEAEB2),
    label = Color.Black, secondaryLabel = Color(0x993C3C43), separator = Color(0x4A3C3C43),
    fill = Color(0xFFF2F2F7),
    accent = Color(0xFF2F6FBF),
    blue = Color(0xFF007AFF), green = Color(0xFF34C759), red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500), indigo = Color(0xFF5856D6), purple = Color(0xFFAF52DE),
    teal = Color(0xFF5AC8FA), pink = Color(0xFFFF2D55),
)

val DarkPalette = Palette(
    background = Color.Black, card = Color(0xFF1C1C1E),
    well = Color(0xFF1C1C1E), wellSelected = Color(0xFF48484A),
    label = Color.White, secondaryLabel = Color(0x99EBEBF5), separator = Color(0xA6545458),
    fill = Color(0xFF2C2C2E),
    accent = Color(0xFF3D82D6),
    blue = Color(0xFF0A84FF), green = Color(0xFF30D158), red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A), indigo = Color(0xFF5E5CE6), purple = Color(0xFFBF5AF2),
    teal = Color(0xFF64D2FF), pink = Color(0xFFFF375F),
)

/** Gradients used for "gradient icons" and big buttons, iOS-style top-left → bottom-right. */
object Gradients {
    fun of(vararg colors: Long) = Brush.linearGradient(colors.map { Color(it) })
    val next = of(0xFF3D82D6, 0xFF5E5CE6)
    val previous = of(0xFF8E8E93, 0xFF636366)
    val start = of(0xFF34C759, 0xFF00B4A0)
    val end = of(0xFFFF9500, 0xFFFF3B30)
    val black = of(0xFF3A3A3C, 0xFF000000)
    val computer = of(0xFF5AC8FA, 0xFF2F6FBF)
    val keypad = of(0xFFAF52DE, 0xFF5856D6)
    val recent = of(0xFFFFCC00, 0xFFFF9500)
}

@Immutable
data class Typography(
    val hero: TextStyle,            // "SlideSwitch" wordmark
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
)

/** Inter (SIL OFL, rsms/inter): the closest freely licensed match to SF Pro Text. */
private val family = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Inter Display: tighter optical size for big titles, like SF Pro Display. */
private val display = FontFamily(Font(R.font.inter_display_bold, FontWeight.Bold))

val AppleTypography = Typography(
    hero = TextStyle(fontFamily = display, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    largeTitle = TextStyle(fontFamily = display, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    title1 = TextStyle(fontFamily = display, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    title2 = TextStyle(fontFamily = family, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    title3 = TextStyle(fontFamily = family, letterSpacing = (-0.2).sp, fontSize = 22.sp, lineHeight = 28.sp),
    headline = TextStyle(fontFamily = family, letterSpacing = (-0.2).sp, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontFamily = family, letterSpacing = (-0.2).sp, fontSize = 17.sp, lineHeight = 22.sp),
    callout = TextStyle(fontFamily = family, letterSpacing = (-0.2).sp, fontSize = 16.sp, lineHeight = 21.sp),
    subheadline = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 20.sp),
    footnote = TextStyle(fontFamily = family, fontSize = 13.sp, lineHeight = 18.sp),
    caption = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalTypography = staticCompositionLocalOf { AppleTypography }

object AppTheme {
    val colors: Palette @Composable get() = LocalPalette.current
    val type: Typography @Composable get() = LocalTypography.current
}

@Composable
fun SlideSwitchTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides if (dark) DarkPalette else LightPalette,
        LocalTypography provides AppleTypography,
        content = content,
    )
}
