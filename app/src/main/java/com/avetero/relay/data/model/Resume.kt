package com.avetero.relay.data.model

/** A "continue watching" entry. key is m<vodId> or e<episodeId>. */
data class Resume(
    val key: String, val title: String, val meta: String, val image: String,
    val pos: Long, val dur: Long, val vodId: Int = 0, val seriesId: Int = 0,
)
