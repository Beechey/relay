package com.avetero.relay.ui.theme

import androidx.compose.ui.graphics.Color

private val tones = listOf(0xFF2B3A4A, 0xFF2A3A4F, 0xFF33405A, 0xFF3D2F4A, 0xFF1F3B3A, 0xFF2B3D3A, 0xFF2A3A2A, 0xFF34384A).map { Color(it) }

fun tone(seed: Int) = tones[Math.floorMod(seed, tones.size)]

/** Tokens from board 17 / docs/04-design-system.md. */
object C {
    val Ground = Color(0xFF06080C)
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
    val Live = Color(0xFFFF4057)
    val Coral = Color(0xFFFF6B4A)
    /** Frosted panel over artwork or video. */
    val Glass = Color(0xD9121620)
    /** Pill and secondary-button fill on glass. */
    val Glass2 = Color(0x14FFFFFF)
    val Glass3 = Color(0x1FFFFFFF)
    val Error = Color(0xFFFF8A8F)
    val OnLight = Color(0xFF0B0E14)
    val OnLightSub = Color(0xFF3A4255)
    val LightTrack = Color(0xFFC9CED8)
}

/** Text colour on a FocusSurface: dark on the light focus fill. */
fun fg(focused: Boolean) = if (focused) C.OnLight else C.Text

fun sub(focused: Boolean) = if (focused) C.OnLightSub else C.Muted

/** Amber to coral: only ever means "live" or "how far through". */
val LiveGradient = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(C.Accent, C.Coral))
