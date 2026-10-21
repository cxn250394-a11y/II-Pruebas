package com.charly.alertaslab

import android.content.Context
import com.charly.alertcenter.AlertCategory
import com.charly.alertcenter.AlertRules
import com.charly.alertcenter.AlertSource
import com.charly.alertcenter.LabEngine

class LabPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lab_settings", Context.MODE_PRIVATE)
    fun selected(): Set<String> = prefs.getStringSet("sources", emptySet())?.toSet() ?: emptySet()
    fun paused(): Boolean = prefs.getBoolean("paused", false)
    fun permits(source: String): Boolean = !paused() && source in selected()
    fun pause(value: Boolean) = synchronized(LabStore.writeLock) { prefs.edit().putBoolean("paused", value).apply() }
    fun select(source: String, value: Boolean) = synchronized(LabStore.writeLock) {
        val ids = selected().toMutableSet()
        if (value) ids.add(source) else ids.remove(source)
        prefs.edit().putStringSet("sources", ids).apply()
    }
    fun sources(): List<AlertSource> = AlertRules.builtInSources +
        (prefs.getStringSet("custom", emptySet()) ?: emptySet()).mapNotNull {
            val p = it.split('|', limit = 2)
            val c = AlertCategory.entries.firstOrNull { c -> c.name == p.firstOrNull() }
            if (p.size == 2 && c != null && AlertRules.validPackageId(p[1])) AlertSource(p[1], p[1], c) else null
        }.filter { custom -> AlertRules.builtInSources.none { it.packageId == custom.packageId } }.sortedBy { it.packageId }
    fun category(source: String): AlertCategory = sources().firstOrNull { it.packageId == source }?.category ?: AlertCategory.OTHER
    fun name(source: String): String = sources().firstOrNull { it.packageId == source }?.name ?: source
    fun addSource(input: String, category: AlertCategory): Boolean {
        val source = input.trim()
        if (!AlertRules.validPackageId(source) || sources().any { it.packageId == source }) return false
        val set = (prefs.getStringSet("custom", emptySet()) ?: emptySet()).toMutableSet()
        set.add("${category.name}|$source")
        prefs.edit().putStringSet("custom", set).apply()
        return true
    }
    fun words(): String = prefs.getString("words", AlertRules.DEFAULT_KEYWORDS) ?: AlertRules.DEFAULT_KEYWORDS
    fun words(input: String) { prefs.edit().putString("words", input.take(2400)).apply() }
    fun folders(): List<String> = (prefs.getStringSet("folders", setOf("Importantes", "Deportes", "Lectura")) ?: emptySet()).sorted()
    fun addFolder(input: String): Boolean {
        val name = input.trim()
        if (!LabEngine.validFolder(name) || folders().any { it.equals(name, ignoreCase = true) }) return false
        prefs.edit().putStringSet("folders", (folders() + name).toSet()).apply()
        return true
    }
    fun folder(key: String): String = prefs.getString("folder_$key", "").orEmpty()
    fun assign(key: String, folder: String) {
        if (folder.isNotEmpty() && folder !in folders()) return
        prefs.edit().putString("folder_$key", folder).apply()
    }
    fun dropped(): Int = prefs.getInt("dropped", 0)
    @Synchronized fun recordDropped() { prefs.edit().putInt("dropped", dropped() + 1).apply() }
    /** Only group assignments for currently retained history are kept. */
    fun pruneAssignments(keys: Set<String>) {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("folder_") && it.removePrefix("folder_") !in keys }.forEach(edit::remove)
        edit.apply()
    }
}
