package mx.charly.canal

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Bundled on-device Latin OCR. Explicit user action; no HTTP/cloud requests, no automatic forwarding.
 * Output is evidence to inspect, never a verified wager, odds feed or betting instruction.
 */
object LocalBetText {
    fun read(bitmap: Bitmap, callback: (String?, String?) -> Unit) {
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            recognizer.process(InputImage.fromBitmap(bitmap,0))
                .addOnSuccessListener { result -> callback(result.text.take(12_000),null) }
                .addOnFailureListener { callback(null,"No se pudo leer la imagen en el teléfono") }
                .addOnCompleteListener { recognizer.close() }
        } catch (_: Exception) { recognizer.close();callback(null,"No se pudo iniciar el lector local") }
    }
    /** Textual flags only; market, match, book and odds must be confirmed by the user. */
    fun labels(text: String): List<String> {
        val t=DeckRules.normalized(text)
        val matches=listOf(
            "Goles" to Regex("\\b(gol(es)?|goal(s)?|ambos anotan|btts|over|under)\\b"),
            "Córners" to Regex("\\b(corner(s)?|saques de esquina|esquinas)\\b"),
            "Tarjetas" to Regex("\\b(tarjeta(s)?|amarilla(s)?|roja(s)?)\\b"),
            "Tiros" to Regex("\\b(tiro(s)?|shot(s)?|remate(s)?)\\b"),
            "Cuota o momio (no verificado)" to Regex("\\b(cuota|momio|odds|odd)\\b")
        ).filter { it.second.containsMatchIn(t) }.map { it.first }
        return matches
    }
}
