package com.avetero.relay.data.model

/** Something the VOD player can play and save progress for. */
data class PlayItem(val url: String, val title: String, val meta: String, val image: String, val resumeKey: String, val vodId: Int = 0, val seriesId: Int = 0)
