package com.charly.alertcenter

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** No Android dependency: deterministic rules, easy to test without an emulator. */
enum class AlertCategory(val displayName: String) {
    TELEGRAM("Telegram"), SPORTS("Deportes"), OTHER("Otras")
}

data class AlertSource(val packageId: String, val name: String, val category: AlertCategory)

object AlertRules {
    const val DEFAULT_KEYWORDS = "gol, goal, córner, corner, tarjeta roja, red card, penal, penalty"

    val builtInSources = listOf(
        AlertSource("org.telegram.messenger", "Telegram", AlertCategory.TELEGRAM),
        AlertSource("org.telegram.messenger.web", "Telegram (otra versión)", AlertCategory.TELEGRAM),
        AlertSource("app.nicegram", "Nicegram", AlertCategory.TELEGRAM),
        AlertSource("com.sofascore.results", "Sofascore", AlertCategory.SPORTS),
        AlertSource("eu.livesport.FlashScore_com", "Flashscore", AlertCategory.SPORTS),
        AlertSource("mex.novibet.sports.betting", "Novibet México", AlertCategory.SPORTS),
        AlertSource("com.mobilefootie.wc2010", "FotMob", AlertCategory.SPORTS)
    )

    fun validPackageId(input: String): Boolean =
        input.length in 3..180 &&
            Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+").matches(input)

    fun normalized(input: String): String {
        val collapsed = input.trim().replace(Regex("\\s+"), " ")
        val stripped = Normalizer.normalize(collapsed, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return stripped.lowercase(Locale.ROOT)
    }

    fun terms(csv: String): List<String> = csv.split(',')
        .map { normalized(it) }
        .filter { it.isNotBlank() && it.length <= 60 }
        .distinct()
        .take(40)

    fun matchesQuery(title: String, body: String, query: String): Boolean {
        val q = normalized(query)
        return q.isEmpty() || normalized("$title $body").contains(q)
    }

    /** Enforce word/phrase boundaries so "gol" doesn't match "golpe". */
    fun hasPriorityWord(title: String, body: String, csv: String): Boolean {
        val message = normalized("$title $body")
        return terms(csv).any { word ->
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])")
                .containsMatchIn(message)
        }
    }

    fun fingerprint(packageId: String, androidUserId: Int, title: String, body: String, notificationKey: String = ""): String {
        val data = listOf(packageId, androidUserId.toString(), normalized(title), normalized(body), notificationKey)
            .joinToString("\u001f")
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** Best-effort only: Android may deliver secrets without recognizable markers. */
    fun isLikelySensitive(title: String, body: String): Boolean {
        val combined = normalized("$title $body")
        val markers = listOf(
            "verification code", "codigo de verificacion", "codigo de acceso",
            "one time password", "one-time password", "autenticacion de dos factores", "otp", "2fa"
        )
        return markers.any { combined.contains(it) }
    }

    fun protectSensitiveText(title: String, body: String): String =
        if (isLikelySensitive(title, body)) "[Contenido sensible oculto]" else body

    fun clip(input: String, max: Int): String = input.trim().take(max)
}
