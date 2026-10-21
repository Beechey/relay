package com.avetero.relay.data.model

import java.util.Collections

enum class Move { Top, Up, Down, Bottom }

/** Moves the selected ids in [list]: Top/Bottom gather them at that end; Up/Down shift each one place past an unselected neighbour. */
internal fun moveIds(list: List<String>, sel: Set<String>, how: Move): List<String> = when (how) {
    Move.Top -> list.filter { it in sel } + list.filter { it !in sel }
    Move.Bottom -> list.filter { it !in sel } + list.filter { it in sel }
    Move.Up -> list.toMutableList().also { l -> for (i in 1 until l.size) if (l[i] in sel && l[i - 1] !in sel) Collections.swap(l, i, i - 1) }
    Move.Down -> list.toMutableList().also { l -> for (i in l.size - 2 downTo 0) if (l[i] in sel && l[i + 1] !in sel) Collections.swap(l, i, i + 1) }
}
