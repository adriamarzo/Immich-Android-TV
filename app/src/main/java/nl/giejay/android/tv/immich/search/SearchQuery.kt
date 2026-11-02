package nl.giejay.android.tv.immich.search

/** Trims the text; returns null when nothing is left, so an empty search is never run. */
fun normalizeQuery(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
