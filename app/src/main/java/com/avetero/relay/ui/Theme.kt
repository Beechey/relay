package com.avetero.relay.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tokens from board 17 / docs/04-design-system.md. */
object C {
    val Ground = Color(0xFF0B0E14)
    val Panel = Color(0xFF0E121A)
    val Surface = Color(0xFF141925)
    val Control = Color(0xFF1A2030)
    val Raised = Color(0xFF232B3D)
    val Track = Color(0xFF252C3D)
    val Text = Color(0xFFE8EBF2)
    val Soft = Color(0xFFB8BFCC)
    val Muted = Color(0xFF9AA3B5)
    val Dim = Color(0xFF7F889A)
    val Accent = Color(0xFFFFB547)
    val Live = Color(0xFFE5484D)
    val Error = Color(0xFFFF8A8F)
    val OnLight = Color(0xFF0B0E14)
    val OnLightSub = Color(0xFF3A4255)
    val LightTrack = Color(0xFFC9CED8)
}

/** True on phones (and tablets in portrait): single-column layouts with a bottom tab bar instead of the TV rail. */
val LocalCompact = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun compact() = LocalCompact.current

/** Text colour on a FocusSurface: dark on the light focus fill. */
fun fg(focused: Boolean) = if (focused) C.OnLight else C.Text
fun sub(focused: Boolean) = if (focused) C.OnLightSub else C.Muted

// shortcut: system sans instead of bundled Manrope; add font files to res/font when final art lands
val Mono = FontFamily.Monospace

@Composable
fun T(
    text: String, size: Int = 15, weight: FontWeight = FontWeight.Normal, color: Color = C.Text,
    modifier: Modifier = Modifier, mono: Boolean = false, maxLines: Int = 1, lineHeight: Int = 0,
) = Text(
    text, modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = maxLines,
    overflow = TextOverflow.Ellipsis, fontFamily = if (mono) Mono else null,
    lineHeight = if (lineHeight > 0) lineHeight.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
)

val Bold = FontWeight.Bold
val Heavy = FontWeight.ExtraBold
val Semi = FontWeight.SemiBold

/** Icons are the SVG paths from the design boards, drawn as 2px strokes (or filled). */
fun icon(d: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        if (filled) addPath(addPathNodes(d), fill = SolidColor(Color.Black))
        else addPath(addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()

object Icons {
    val Home = icon("M3 11l9-8 9 8M5 9.5V21h5v-6h4v6h5V9.5")
    val Live = icon("M4 7h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H4a2 2 0 0 1 -2-2V9a2 2 0 0 1 2-2zM17 2l-5 5-5-5")
    val Guide = icon("M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2-2V6a2 2 0 0 1 2-2zM3 10h18M9 4v16")
    val Movies = icon("M4 3h16v18H4zM8 3v18M16 3v18M4 8h4M4 16h4M16 8h4M16 16h4M4 12h16")
    val Series = icon("M7 3h13v13H7zM4 7v13h13")
    val Star = icon("M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z")
    val StarFilled = icon("M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z", filled = true)
    val Search = icon("M11 4a7 7 0 1 1 0 14a7 7 0 1 1 0-14zM21 21l-4.3-4.3")
    val Settings = icon("M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M14 4v4M8 10v4M16 16v4")
    val Play = icon("M7 4l13 8-13 8z", filled = true)
    val Pause = icon("M6 4h4v16H6zM14 4h4v16h-4z", filled = true)
    val Back = icon("M15 5l-7 7 7 7")
    val Chevron = icon("M9 5l7 7-7 7")
    val Pip = icon("M3 5h18v14H3zM12 12h6v4h-6z")
    val More = icon("M4 6h16M4 12h16M4 18h16")
    val Eye = icon("M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6")
    val EyeOff = icon("M3 3l18 18M10.6 5.1A10 10 0 0 1 12 5c6 0 10 7 10 7a17 17 0 0 1-3.2 3.9M6.6 6.6A17 17 0 0 0 2 12s4 7 10 7a9.6 9.6 0 0 0 5.4-1.6")
    val Move = icon("M12 3v18M8 7l4-4 4 4M8 17l4 4 4-4")
    val Grip = icon("M6 9h12M6 15h12")
    val Check = icon("M5 12.5l4.5 4.5L19 7.5")
    val Up = icon("M12 19V5M5 12l7-7 7 7")
    val Down = icon("M12 5v14M5 12l7 7 7-7")
    val Top = icon("M5 4h14M12 20V9M6 15l6-6 6 6")
    val Bottom = icon("M5 20h14M12 4v11M6 9l6 6 6-6")
    val Volume = icon("M4 9h4l5-4v14l-5-4H4zM16.5 9a4 4 0 0 1 0 6")
    val Muted = icon("M4 9h4l5-4v14l-5-4H4zM16 9l5 6M21 9l-5 6")
    val Warn = icon("M12 3l10 18H2zM12 10v4M12 17.5v.5")
}

@Composable
fun Ico(v: ImageVector, color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) =
    Icon(v, contentDescription = null, tint = color, modifier = modifier.size(size))

/** Logo B "Handoff": grey chevron passing to an amber one, on the dark tile (see board 00). */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    val v = androidx.compose.runtime.remember {
        ImageVector.Builder(defaultWidth = 108.dp, defaultHeight = 108.dp, viewportWidth = 108f, viewportHeight = 108f).apply {
            addPath(addPathNodes("M28 32 L50 54 L28 76"), fill = null, stroke = SolidColor(Color(0xFF5A6378)), strokeLineWidth = 12f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            addPath(addPathNodes("M54 32 L76 54 L54 76"), fill = null, stroke = SolidColor(C.Accent), strokeLineWidth = 12f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }.build()
    }
    androidx.compose.foundation.Image(v, contentDescription = "Relay", modifier = modifier.size(size)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.24f))
        .background(Color(0xFF0B0E14)))
}
