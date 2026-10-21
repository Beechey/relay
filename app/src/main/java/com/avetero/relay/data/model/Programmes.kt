package com.avetero.relay.data.model

import com.avetero.relay.util.nowSec

fun List<Programme>?.current() = this?.firstOrNull { nowSec() in it.start until it.end }

fun List<Programme>?.upcoming() = this?.filter { it.start >= nowSec() }.orEmpty()

fun Programme.progress() = ((nowSec() - start).toFloat() / (end - start)).coerceIn(0f, 1f)

fun Programme.minutesLeft() = ((end - nowSec()) / 60).let { if (it >= 60) "${it / 60} h ${it % 60} min left" else "$it min left" }
