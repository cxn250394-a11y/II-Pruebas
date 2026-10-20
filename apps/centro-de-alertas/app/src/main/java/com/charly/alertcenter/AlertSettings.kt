package com.charly.alertcenter

import android.content.Context

/** Explicit opt-in per source: an enabled listener does not mean collect everything. */
class AlertSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("alert_preferences_v1", Context.MODE_PRIVATE)

    fun selectedPackages(): Set<String> =
        prefs.getStringSet("allowed_packages", emptySet())?.toSet() ?: emptySet()

    fun isSelected(packageId: String): Boolean = selectedPackages().contains(packageId)

    fun select(packageId: String, enabled: Boolean) {
        val next = selectedPackages().toMutableSet()
        if (enabled) next.add(packageId) else next.remove(packageId)
        prefs.edit().putStringSet("allowed_packages", next).apply()
    }

    fun customSources(): List<AlertSource> =
        (prefs.getStringSet("custom_sources", emptySet()) ?: emptySet())
            .mapNotNull { value ->
                val parts = value.split('|', limit = 2)
                if (parts.size != 2 || !AlertRules.validPackageId(parts[1])) return@mapNotNull null
                val category = AlertCategory.entries.firstOrNull { it.name == parts[0] }
                    ?: return@mapNotNull null
                AlertSource(parts[1], parts[1], category)
            }.sortedBy { it.packageId }

    fun sources(): List<AlertSource> = AlertRules.builtInSources + customSources()
        .filter { custom -> AlertRules.builtInSources.none { it.packageId == custom.packageId } }

    fun categoryFor(packageId: String): AlertCategory =
        sources().firstOrNull { it.packageId == packageId }?.category ?: AlertCategory.OTHER

    fun displayName(packageId: String): String =
        sources().firstOrNull { it.packageId == packageId }?.name ?: packageId

    fun addCustom(packageId: String, category: AlertCategory): Boolean {
        val trimmed = packageId.trim()
        if (!AlertRules.validPackageId(trimmed) || sources().any { it.packageId == trimmed }) return false
        val values = (prefs.getStringSet("custom_sources", emptySet()) ?: emptySet()).toMutableSet()
        values.add("${category.name}|$trimmed")
        prefs.edit().putStringSet("custom_sources", values).apply()
        return true
    }

    fun removeCustom(packageId: String) {
        val values = (prefs.getStringSet("custom_sources", emptySet()) ?: emptySet()).toMutableSet()
        values.removeAll { it.substringAfter('|', "") == packageId }
        prefs.edit().putStringSet("custom_sources", values).apply()
        select(packageId, false)
    }

    fun keywords(): String = prefs.getString("keywords", AlertRules.DEFAULT_KEYWORDS)
        ?: AlertRules.DEFAULT_KEYWORDS

    fun updateKeywords(input: String) {
        prefs.edit().putString("keywords", input.trim().take(2400)).apply()
    }
}
