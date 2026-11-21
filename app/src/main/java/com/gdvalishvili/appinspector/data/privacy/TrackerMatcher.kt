package com.gdvalishvili.appinspector.data.privacy

import android.content.Context
import org.json.JSONObject

/** One tracker from the Exodus database. Categories: Analytics, Advertisement, Location, Profiling, Identification, Crash reporting. */
data class TrackerDef(
    val id: Int,
    val name: String,
    val categories: List<String>,
    val signatures: List<String>,
    val website: String,
)

/**
 * Finds trackers by class-name signatures (Exodus method).
 * A signature is a class-name prefix ("com.flurry."); one starting with "." means "contains" (".tealium.").
 * Pure Kotlin: unit-tested.
 */
class TrackerMatcher(trackers: List<TrackerDef>) {

    private val prefixSignatures = trackers.flatMap { t -> t.signatures.filter { !it.startsWith(".") }.map { it to t } }
    private val containsSignatures = trackers.flatMap { t -> t.signatures.filter { it.startsWith(".") }.map { it to t } }

    fun match(classNames: Collection<String>): List<TrackerDef> {
        if (classNames.isEmpty()) return emptyList()
        val sorted = classNames.toTypedArray().apply { sort() }
        val found = LinkedHashSet<TrackerDef>()
        for ((signature, tracker) in prefixSignatures) {
            if (tracker !in found && hasPrefix(sorted, signature)) found += tracker
        }
        for ((signature, tracker) in containsSignatures) {
            if (tracker !in found && sorted.any { it.contains(signature) }) found += tracker
        }
        return found.sortedBy { it.name.lowercase() }
    }

    /** Binary search: the first name >= prefix starts with the prefix if any name does. */
    private fun hasPrefix(sorted: Array<String>, prefix: String): Boolean {
        var i = sorted.binarySearch(prefix)
        if (i < 0) i = -i - 1
        return i < sorted.size && sorted[i].startsWith(prefix)
    }
}

/** Loads assets/trackers.json once (Exodus Privacy database, ODbL v1.0). */
object TrackerDatabase {

    @Volatile private var cached: List<TrackerDef>? = null

    fun load(context: Context): List<TrackerDef> =
        cached ?: synchronized(this) { cached ?: parse(context).also { cached = it } }

    private fun parse(context: Context): List<TrackerDef> {
        val text = context.assets.open("trackers.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONObject(text).getJSONArray("trackers")
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val cats = o.getJSONArray("categories")
            val sigs = o.getJSONArray("signatures")
            TrackerDef(
                id = o.getInt("id"),
                name = o.getString("name"),
                categories = List(cats.length()) { cats.getString(it) },
                signatures = List(sigs.length()) { sigs.getString(it) },
                website = o.optString("website"),
            )
        }
    }
}