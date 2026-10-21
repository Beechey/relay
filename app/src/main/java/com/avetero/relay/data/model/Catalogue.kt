package com.avetero.relay.data.model

data class Category(val id: String, val name: String)

data class Channel(val id: Int, val num: Int, val name: String, val categoryId: String, val icon: String = "", val archive: Boolean = false, val epgId: String = "")

data class Vod(val id: Int, val name: String, val icon: String, val rating: Double, val categoryId: String, val ext: String, val added: Long)

data class Series(val id: Int, val name: String, val cover: String, val rating: Double, val categoryId: String, val plot: String, val year: String)

data class VodInfo(val plot: String, val cast: String, val director: String, val genre: String, val year: String, val duration: String, val backdrop: String)

data class Episode(val id: Int, val season: Int, val num: Int, val title: String, val ext: String, val plot: String, val duration: String, val image: String)

data class SeriesInfo(val plot: String, val genre: String, val year: String, val cover: String, val episodes: List<Episode>)

/** start/end are epoch seconds. */
data class Programme(val title: String, val description: String, val start: Long, val end: Long)
