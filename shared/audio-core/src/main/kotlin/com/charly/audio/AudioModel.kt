package com.charly.audio

import java.net.URI
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

enum class EntryKind { LOCAL, RADIO, DEMO }

data class AudioEntry(val uri: String, val title: String, val kind: EntryKind, val favorite: Boolean = false) {
    val id: String get() = AudioPolicy.id(kind.name + ":" + uri)
}

/** Input policy only: a syntactically valid URL is not a verified playable stream. */
object AudioPolicy {
    const val MAX_LOCAL = 500
    const val MAX_RADIO = 100
    const val DEMO_URI = "asset:///aural-demo.wav"
    const val BIT_PERFECT_STATUS = "Bit-perfect: NO VERIFICADO"
    fun id(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun validRadio(input: String): Boolean {
        if (input.length !in 1..2048 || input.any { it.isWhitespace() || it.isISOControl() }) return false
        return try {
            val u = URI(input)
            u.scheme.equals("https", true) && !u.host.isNullOrBlank() && u.userInfo == null &&
                u.fragment == null && (u.port == -1 || u.port in 1..65535)
        } catch (_: Exception) { false }
    }
    fun validEntry(e: AudioEntry): Boolean {
        if (e.title.isBlank() || e.title.length > 160 || e.title.any { it.isISOControl() }) return false
        return when (e.kind) {
            EntryKind.RADIO -> validRadio(e.uri)
            EntryKind.DEMO -> e.uri == DEMO_URI
            EntryKind.LOCAL -> try {
                val u = URI(e.uri)
                e.uri.length <= 4096 && !e.uri.any { it.isISOControl() } && u.scheme == "content" && !u.authority.isNullOrBlank()
            } catch (_: Exception) { false }
        }
    }
    fun title(input: String): String = input.map { if (it.isISOControl()) ' ' else it }.joinToString("")
        .trim().take(160).ifBlank { "Audio sin título" }
    fun normalized(input: String): String = Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    fun matches(e: AudioEntry, query: String): Boolean = normalized(e.title).contains(normalized(query))
    fun clock(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    }
    /** Source format only; never describes the physical output or a DAC measurement. */
    fun sourceFormat(rate: Int, channels: Int, mime: String?): String {
        val hz = if (rate > 0) "$rate Hz" else "frecuencia desconocida"
        val ch = if (channels > 0) "$channels canales" else "canales desconocidos"
        return "Fuente: ${mime ?: "formato desconocido"} · $hz · $ch"
    }
    val demo = AudioEntry(DEMO_URI, "Prueba de sonido · tono suave original", EntryKind.DEMO)
}
