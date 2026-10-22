package com.cyberrick.phoneinspector.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Direction A from the mockups: near black, one green accent, severity by colour and always by a word too. */
object Cpi {
    val Background = Color(0xFF0B0F17)
    val Surface = Color(0xFF141C2B)
    val SurfaceHigh = Color(0xFF1B2538)
    val Outline = Color(0xFF27324A)
    val Text = Color(0xFFE8EDF5)
    val Muted = Color(0xFF9FB0C9)
    val Accent = Color(0xFF2EE6A6)
    val OnAccent = Color(0xFF06251A)
    val High = Color(0xFFFF4D4D)
    val HighText = Color(0xFFFF8080)
    val HighBack = Color(0xFF3A1414)
    val Medium = Color(0xFFFFB547)
    val MediumText = Color(0xFFFFC772)
    val MediumBack = Color(0xFF3A2A10)
    val Note = Color(0xFF6F7D96)
}

private val scheme =
    darkColorScheme(
        primary = Cpi.Accent,
        onPrimary = Cpi.OnAccent,
        secondary = Cpi.Muted,
        background = Cpi.Background,
        onBackground = Cpi.Text,
        surface = Cpi.Surface,
        onSurface = Cpi.Text,
        surfaceVariant = Cpi.SurfaceHigh,
        onSurfaceVariant = Cpi.Muted,
        outline = Cpi.Outline,
        error = Cpi.High,
    )

private val type =
    Typography(
        headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold),
        headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    )

@Composable
fun CpiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
