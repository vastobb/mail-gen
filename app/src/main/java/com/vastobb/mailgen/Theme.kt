package com.vastobb.mailgen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Palette lifted verbatim from the user's tt.html theme file, which defines
 * three schemes (dark terminal / light paper / slate). The dark one is the
 * default because the source design is a terminal UI.
 */
object Tokens {
    // dark
    val AppBg = Color(0xFF0D0D0D)
    val SidebarBg = Color(0xFF111111)
    val PanelBg = Color(0xFF171717)
    val Border = Color(0xFF292929)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFF929292)
    val BtnBg = Color(0xFFF5F5F5)
    val BtnText = Color(0xFF111111)

    // slate (accent surface)
    val SlateBg = Color(0xFF101114)
    val SlatePanel = Color(0xFF191B20)
    val SlateBorder = Color(0xFF2A2C32)
    val SlateText = Color(0xFFEDEEF0)
    val SlateSecondary = Color(0xFF9699A2)

    // light
    val LightBg = Color(0xFFF7F7F5)
    val LightPanel = Color(0xFFFFFFFF)
    val LightBorder = Color(0xFFE5E5E0)
    val LightText = Color(0xFF171717)
    val LightSecondary = Color(0xFF737373)

    val Mono = FontFamily.Monospace

    // status accents kept desaturated to match the source's restraint
    val Ok = Color(0xFF7FD1A0)
    val Warn = Color(0xFFE0C07A)
    val Danger = Color(0xFFE08A8A)
}

private val DarkScheme = darkColorScheme(
    primary = Tokens.TextPrimary,
    onPrimary = Tokens.BtnText,
    background = Tokens.AppBg,
    onBackground = Tokens.TextPrimary,
    surface = Tokens.PanelBg,
    onSurface = Tokens.TextPrimary,
    surfaceVariant = Tokens.SidebarBg,
    onSurfaceVariant = Tokens.TextSecondary,
    outline = Tokens.Border,
    error = Tokens.Danger,
)

private val LightScheme = lightColorScheme(
    primary = Tokens.LightText,
    onPrimary = Color.White,
    background = Tokens.LightBg,
    onBackground = Tokens.LightText,
    surface = Tokens.LightPanel,
    onSurface = Tokens.LightText,
    surfaceVariant = Color(0xFFF0F0ED),
    onSurfaceVariant = Tokens.LightSecondary,
    outline = Tokens.LightBorder,
    error = Color(0xFFB3261E),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = Tokens.Mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Tokens.Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Tokens.Mono,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Tokens.Mono,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    ),
)

@Composable
fun MailGenTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
