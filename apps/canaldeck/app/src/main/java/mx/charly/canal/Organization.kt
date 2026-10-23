package mx.charly.canal

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Account-scoped preferences; titles never serve as identities. No chat content is stored here. */
class Organization(context: Context, private val accountId: Long) {
    private val p = context.applicationContext.getSharedPreferences("canaldeck-org-$accountId", Context.MODE_PRIVATE)
    private fun set(key: String) = p.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    /** Independent from favorites and from Telegram server-side pins. */
    fun important(id: Long) = id.toString() in set("important")
    fun toggleImportant(id: Long) {
        val current = set("important").toMutableSet()
        if (!current.add(id.toString())) current.remove(id.toString())
        p.edit().putStringSet("important", current).apply()
    }
    /** Only time measurements, never chat content or message identifiers. */
    fun recordLocalNoticeAge(serverSeconds: Long, deviceMillis: Long) {
        val age = DeckRules.observedAgeMillis(serverSeconds, deviceMillis) ?: return
        p.edit().putLong("notice-age-ms", age).putLong("notice-local-at", deviceMillis).apply()
    }
    fun lastLocalNoticeAgeMillis(): Long? = p.getLong("notice-age-ms", -1).takeIf { it >= 0 }
    fun lastLocalNoticeAt(): Long = p.getLong("notice-local-at", 0)
    fun favorite(id: Long) = id.toString() in set("favorites")
    fun toggleFavorite(id: Long) { val s = set("favorites").toMutableSet(); if (!s.add(id.toString())) s.remove(id.toString()); p.edit().putStringSet("favorites", s).apply() }
    /** Stable manual ordering, with legacy folders appended deterministically. */
    fun folders(): List<Pair<String, String>> {
        val ids=set("folders")
        val saved=p.getString("folders-order", "").orEmpty().split(',').filter { it in ids }.distinct()
        val rest=(ids-saved.toSet()).sortedBy { DeckRules.normalized(p.getString("name-$it", "").orEmpty()) }
        return (saved+rest).map { it to p.getString("name-$it", "Carpeta").orEmpty() }
    }
    fun moveFolder(id: String, delta: Int): Boolean {
        if (delta !in setOf(-1,1)) return false
        val sorted=folders().map { it.first }.toMutableList()
        val i=sorted.indexOf(id);val dest=i+delta
        if(i<0||dest !in sorted.indices)return false
        java.util.Collections.swap(sorted,i,dest)
        p.edit().putString("folders-order",sorted.joinToString(",")).apply()
        return true
    }
    fun createFolder(name: String): String? {
        val n = name.trim()
        if (!DeckRules.validFolder(n) || folders().any { DeckRules.normalized(it.second) == DeckRules.normalized(n) }) return null
        val id = UUID.randomUUID().toString()
        p.edit().putString("name-$id", n).putStringSet("folders", set("folders") + id)
            .putString("folders-order",(folders().map { it.first } + id).joinToString(",")).apply(); return id
    }
    fun renameFolder(id: String, name: String): Boolean {
        val n = name.trim()
        if (id !in set("folders") || !DeckRules.validFolder(n) || folders().any { it.first != id && DeckRules.normalized(it.second) == DeckRules.normalized(n) }) return false
        p.edit().putString("name-$id", n).apply(); return true
    }
    fun deleteFolder(id: String) {
        val e = p.edit().putStringSet("folders", set("folders") - id).remove("name-$id")
            .putString("folders-order",folders().map { it.first }.filter { it!=id }.joinToString(","))
        p.all.filter { it.key.startsWith("chat-folder-") && it.value == id }.keys.forEach { e.remove(it) }; e.apply()
    }
    fun folder(chat: Long) = p.getString("chat-folder-$chat", "").orEmpty().takeIf { it in set("folders") }.orEmpty()
    fun assign(chat: Long, folder: String) { if (folder.isBlank() || folder in set("folders")) p.edit().putString("chat-folder-$chat", folder).apply() }
    fun mode(chat: Long): NoticeMode = NoticeMode.entries.getOrElse(p.getInt("notice-$chat", 0)) { NoticeMode.OFF }
    fun mode(chat: Long, mode: NoticeMode) { p.edit().putInt("notice-$chat", mode.ordinal).apply() }
    fun words(chat: Long) = p.getString("words-$chat", "").orEmpty()
    fun words(chat: Long, value: String) { p.edit().putString("words-$chat", value.take(2400)).apply() }
    var screenshots: Boolean
        get() = p.getBoolean("screenshots", false)
        set(v) { p.edit().putBoolean("screenshots",v).apply() }
    var previews: Boolean
        get() = p.getBoolean("previews", false)
        set(v) { p.edit().putBoolean("previews", v).apply() }
    var quietEnabled: Boolean
        get() = p.getBoolean("quiet-enabled", false)
        set(v) { p.edit().putBoolean("quiet-enabled", v).apply() }
    fun quietStart() = p.getInt("quiet-start", 23 * 60)
    fun quietEnd() = p.getInt("quiet-end", 8 * 60)
    /** Portable settings only: no TDLib session, api_hash, phone, message text or media. */
    fun exportOrganization(): String {
        val data=JSONObject()
        val allowed=p.all.filterKeys { key ->
            key in setOf("favorites","important","folders","folders-order","screenshots","previews",
                "quiet-enabled","quiet-start","quiet-end") ||
                key.startsWith("name-") || key.startsWith("chat-folder-") ||
                key.startsWith("notice-") || key.startsWith("words-")
        }
        allowed.forEach { (key,value) ->
            when(value) {
                is String -> data.put(key,value)
                is Boolean -> data.put(key,value)
                is Int -> data.put(key,value)
                is Set<*> -> data.put(key,JSONArray(value.filterIsInstance<String>().sorted()))
            }
        }
        return JSONObject().put("schema",1).put("accountId",accountId.toString()).put("settings",data).toString()
    }
    /** Validate everything before writing. Never merge data belonging to a different account. */
    fun importOrganization(raw: String): Boolean {
        return try {
        if(raw.length > 300_000)return false
        val obj=JSONObject(raw)
        if(obj.optInt("schema") != 1 || obj.optString("accountId") != accountId.toString())return false
        val data=obj.getJSONObject("settings")
        if(data.length()>5000)return false
        val values=linkedMapOf<String,Any>()
        val iter=data.keys()
        val scalar=setOf("folders-order","screenshots","previews","quiet-enabled","quiet-start","quiet-end")
        val stringSets=setOf("favorites","important","folders")
        while(iter.hasNext()) {
            val k=iter.next()
            if(k !in scalar && k !in stringSets && !k.startsWith("name-") &&
                !k.startsWith("chat-folder-") && !k.startsWith("notice-") && !k.startsWith("words-"))return false
            if(k.length>120)return false
            val v=data.get(k)
            when {
                k in stringSets -> {
                    if(v !is JSONArray || v.length()>2000)return false
                    val set=mutableSetOf<String>()
                    for(i in 0 until v.length()) {
                        val el=v.getString(i)
                        if(el.length>150)return false
                        set+=el
                    }
                    values[k]=set
                }
                k == "screenshots" || k == "previews" || k=="quiet-enabled" -> {
                    if(v !is Boolean)return false
                    values[k]=v
                }
                k == "quiet-start" || k == "quiet-end" || k.startsWith("notice-") -> {
                    if(v !is Int)return false
                    if((k.startsWith("notice-") && v !in 0..2) || (!k.startsWith("notice-") && v !in 0..1439))return false
                    values[k]=v
                }
                else -> {
                    if(v !is String || v.length>2400)return false
                    values[k]=v
                }
            }
        }
        val folderIds=values["folders"] as? Set<*> ?: emptySet<String>()
        for((k,v) in values) {
            if(k.startsWith("chat-folder-") && v !in folderIds && v!="")return false
            if(k.startsWith("name-") && !DeckRules.validFolder(v as String))return false
        }
        val editor=p.edit()
        p.all.keys.filter { it in scalar || it in stringSets || it.startsWith("name-") ||
            it.startsWith("chat-folder-") || it.startsWith("notice-") || it.startsWith("words-") }
            .forEach(editor::remove)
        values.forEach { (key,v) -> when(v) {
            is String -> editor.putString(key,v)
            is Boolean -> editor.putBoolean(key,v)
            is Int -> editor.putInt(key,v)
            is Set<*> -> editor.putStringSet(key,v.filterIsInstance<String>().toSet())
        } }
        editor.commit()
        } catch (_: Exception) { false }
    }

    fun hours(start: String, end: String): Boolean {
        val a = DeckRules.clock(start) ?: return false; val b = DeckRules.clock(end) ?: return false
        if (a == b) return false
        p.edit().putInt("quiet-start", a).putInt("quiet-end", b).apply(); return true
    }
}
