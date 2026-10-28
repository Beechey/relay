package com.avetero.relay.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avetero.relay.ui.components.Art
import com.avetero.relay.ui.components.Pill
import com.avetero.relay.ui.theme.C

/** Board 05: artwork fills the screen; the story side and the bottom strip fade into the ground so text stays readable. */
@Composable
internal fun DetailBackdrop(image: String, seed: Int, content: @Composable BoxScope.() -> Unit) = Box(Modifier.fillMaxSize().background(C.Ground)) {
    Art(image, "", seed, Modifier.fillMaxSize(), showTitle = false)
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
        0f to C.Ground.copy(alpha = .97f), .34f to C.Ground.copy(alpha = .82f), .68f to C.Ground.copy(alpha = .15f), .85f to Color.Transparent)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.58f to Color.Transparent, 1f to C.Ground)))
    content()
}

/** Rating (in the gradient) then plain facts: year, length, seasons, genre. */
@Composable
internal fun FactPills(rating: Double, facts: List<String>) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rating > 0) Box(Modifier.background(com.avetero.relay.ui.theme.LiveGradient, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))) {
            Pill("★ %.1f".format(rating), bg = Color.Transparent, color = C.OnLight)
        }
        facts.filter { it.isNotBlank() }.take(4).forEach { Pill(it) }
    }

/** "00:41:12" or "41 min" from the provider, as "41 m" / "1 h 12 m". */
internal fun shortDur(d: String): String {
    val p = d.split(':').mapNotNull { it.trim().toIntOrNull() }
    val mins = when (p.size) { 3 -> p[0] * 60 + p[1]; 2 -> p[0]; else -> return d }
    return if (mins >= 60) "${mins / 60} h ${mins % 60} m" else "$mins m"
}
