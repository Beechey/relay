package com.avetero.relay.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDate(epochSec: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochSec * 1000))

/** 24-hour or 12-hour clock, from Settings → Appearance. */
var clock24 = true

fun fmtTime(epochSec: Long): String =
    SimpleDateFormat(if (clock24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(epochSec * 1000))

fun nowSec() = System.currentTimeMillis() / 1000

fun fmtDur(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
