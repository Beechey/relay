package com.avetero.relay.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class StepState { Todo, Busy, Done, Failed }

data class SyncStep(val label: String, val state: StepState = StepState.Todo, val detail: String = "", val unit: String = "", val progress: StepProgress = StepProgress())

/** Live progress of one sync step; written from background threads while it downloads and reads. */
class StepProgress {
    /** 0..1, or -1 while the size isn't known */
    var fraction by mutableFloatStateOf(-1f)
    var bytes by mutableLongStateOf(0L)
    var count by mutableIntStateOf(0)
    var reading by mutableStateOf(false)
    fun onBytes(read: Long, total: Long) { bytes = read; fraction = if (total > 0) (read.toFloat() / total).coerceAtMost(1f) else -1f }
}
