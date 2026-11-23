package com.avetero.relay.ui.components

import java.text.Normalizer

/** A category as the lists show it: id, name and how many items it holds. */
data class Group(val id: String, val name: String, val count: Int)

/** Lowercase, accents stripped, so "Telé" matches "tele". */
fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

fun List<Group>.matching(q: String): List<Group> = if (q.isBlank()) this else norm(q.trim()).let { n -> filter { norm(it.name).contains(n) } }
