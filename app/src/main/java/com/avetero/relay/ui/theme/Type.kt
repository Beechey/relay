package com.avetero.relay.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.avetero.relay.R

/** Manrope (variable, bundled so Fire TV gets it too); licence in licenses/Manrope-OFL.txt. */
@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(listOf(400, 500, 600, 700, 800).map {
    Font(R.font.manrope, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
})

val Mono = FontFamily.Monospace

@Composable
fun T(
    text: String, size: Int = 15, weight: FontWeight = FontWeight.Normal, color: Color = C.Text,
    modifier: Modifier = Modifier, mono: Boolean = false, maxLines: Int = 1, lineHeight: Int = 0,
) = Text(
    text, modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = maxLines,
    overflow = TextOverflow.Ellipsis, fontFamily = if (mono) Mono else Manrope,
    // display sizes tighten up, the way the designs set headlines
    letterSpacing = if (size >= 24 && !mono) (-0.025 * size).sp else TextUnit.Unspecified,
    lineHeight = if (lineHeight > 0) lineHeight.sp else TextUnit.Unspecified,
)

val Bold = FontWeight.Bold

val Heavy = FontWeight.ExtraBold

val Semi = FontWeight.SemiBold
