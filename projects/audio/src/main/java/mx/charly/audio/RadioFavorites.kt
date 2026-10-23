package mx.charly.audio

import android.content.Context
import mx.charly.ui.LocalJson
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class FavoriteStation(val name: String, val url: String, val codec: String = "", val bitrate: Int = 0)

/** Local bookmarks only. Streams remain on the originating broadcaster's own server. */
class RadioFavorites(context: Context) {
    private val file = LocalJson(context.applicationContext, "radio-favorites.json")
    companion object {
        const val LIMIT = 300
        fun validStream(url: String): Boolean = try {
            if (url.length !in 10..2048) false
            else {
                val u = URI(url)
                u.scheme.equals("https", true) && !u.host.isNullOrBlank() && u.userInfo == null &&
                    u.port <= 65535 && u.rawFragment == null
            }
        } catch (_: Exception) { false }
    }
    @Synchronized fun all(): List<FavoriteStation> {
        val arr = file.read()
        return (0 until minOf(arr.length(), LIMIT)).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url")
            if (!validStream(url)) return@mapNotNull null
            FavoriteStation(o.optString("name").take(80), url, o.optString("codec").take(25), o.optInt("bitrate").coerceIn(0, 9999))
        }
    }
    @Synchronized fun add(station: FavoriteStation): Boolean {
        require(validStream(station.url)) { "Se requiere un stream HTTPS" }
        require(station.name.trim().isNotBlank()) { "La emisora necesita nombre" }
        val current = all()
        if (current.any { it.url == station.url } || current.size >= LIMIT) return false
        persist(current + station.copy(name = station.name.trim().take(80), codec = station.codec.take(25), bitrate = station.bitrate.coerceIn(0,9999)))
        return true
    }
    @Synchronized fun remove(url: String): Boolean {
        val current = all()
        if (current.none { it.url == url }) return false
        persist(current.filterNot { it.url == url })
        return true
    }
    private fun persist(items: List<FavoriteStation>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("name",it.name).put("url",it.url)
            .put("codec",it.codec).put("bitrate",it.bitrate)) }
        file.write(arr)
    }
}

object SleepTimerRules {
    val allowed = setOf(0, 15, 30, 45, 60, 90, 120)
    fun validMinutes(minutes: Int) = minutes in allowed
    fun deadline(elapsed: Long, minutes: Int): Long? {
        if (!validMinutes(minutes) || minutes == 0 || elapsed < 0) return null
        return elapsed + minutes * 60_000L
    }
}
