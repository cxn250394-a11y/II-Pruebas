package com.charly.aural

import android.content.Context
import com.charly.audio.*
import org.json.JSONArray
import org.json.JSONObject

/** Private references, not copies of audio. No account credentials, exports or telemetry. */
class LibraryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aural_library", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }
    fun networkAllowed(): Boolean = prefs.getBoolean("radio_consent", false)
    fun allowNetwork(value: Boolean) { prefs.edit().putBoolean("radio_consent", value).apply() }
    fun entries(): List<AudioEntry> = synchronized(lock) {
        val result = mutableListOf<AudioEntry>()
        val array = try { JSONArray(prefs.getString("entries", "[]")) } catch (_: Exception) { return@synchronized emptyList() }
        for (i in 0 until minOf(array.length(), AudioPolicy.MAX_LOCAL + AudioPolicy.MAX_RADIO)) {
            val o = array.optJSONObject(i) ?: continue
            val kind = EntryKind.entries.firstOrNull { it.name == o.optString("kind") } ?: continue
            val e = AudioEntry(o.optString("uri"), o.optString("title"), kind, o.optBoolean("favorite"))
            if (kind != EntryKind.DEMO && AudioPolicy.validEntry(e) && result.none { it.id == e.id }) result += e
        }
        result
    }
    fun add(e: AudioEntry): Boolean = synchronized(lock) {
        if (!AudioPolicy.validEntry(e) || e.kind == EntryKind.DEMO) return@synchronized false
        val old = entries()
        if (old.any { it.id == e.id }) return@synchronized false
        val limit = if (e.kind == EntryKind.LOCAL) AudioPolicy.MAX_LOCAL else AudioPolicy.MAX_RADIO
        if (old.count { it.kind == e.kind } >= limit) return@synchronized false
        save(old + e)
        true
    }
    fun remove(id: String) = synchronized(lock) { save(entries().filterNot { it.id == id }) }
    fun favorite(id: String) = synchronized(lock) { save(entries().map { if (it.id == id) it.copy(favorite = !it.favorite) else it }) }
    private fun save(items: List<AudioEntry>) {
        val a = JSONArray()
        items.forEach { e -> a.put(JSONObject().put("uri", e.uri).put("title", e.title).put("kind", e.kind.name).put("favorite", e.favorite)) }
        prefs.edit().putString("entries", a.toString()).apply()
    }
}
