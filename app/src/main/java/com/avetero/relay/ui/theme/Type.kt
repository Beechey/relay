package com.avetero.relay.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// shortcut: system sans instead of bundled Manrope; add font files to res/font when final art lands
val Mono = FontFamily.Monospace

@Composable
fun T(
    text: String, size: Int = 15, weight: FontWeight = FontWeight.Normal, color: Color = C.Text,
    modifier: Modifier = Modifier, mono: Boolean = false, maxLines: Int = 1, lineHeight: Int = 0,
) = Text(
    text, modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = maxLines,
    overflow = TextOverflow.Ellipsis, fontFamily = if (mono) Mono else null,
    lineHeight = if (lineHeight > 0) lineHeight.sp else TextUnit.Unspecified,
)

val Bold = FontWeight.Bold

val Heavy = FontWeight.ExtraBold

val Semi = FontWeight.SemiBold
