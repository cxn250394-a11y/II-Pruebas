package mx.charly.canal

/**
 * Narrow text/caption filter for explicit retrospective-only posts. This is not OCR or a
 * semantic pick detector. Unknown text and captionless photos remain unclassified; they are
 * subject to the other source/author/permission gates. Result/balance mentions alone do not
 * reject a current pick, and a mixed post with an explicit new selection is preserved.
 */
object PickContentRules {
    private const val PICK = "(?:picks?|apuesta|jugada|pronostico|seleccion|parlay|combinada|ticket)"
    private const val SETTLED = "(?:ganad[oa]s?|perdid[oa]s?|liquidad[oa]s?|cobrad[oa]s?|fallad[oa]s?|acertad[oa]s?|cerrad[oa]s?|cancelad[oa]s?|anulad[oa]s?)"
    private const val RETROSPECTIVE = "(?:dia|semana|mes|resultados|ayer|hoy|diario|semanal|mensual|final|finales|picks|apuestas)"
    private val recapHeading = Regex("^(?:recap\\b|(?:resumen|recuento|resultados?|balance|cierre)(?:\\s*[:=+−-]|\\s*$|\\s+(?:(?:de|del)\\s+(?:(?:la|los|las)\\s+)?)?$RETROSPECTIVE\\b))")
    private val settledHeading = Regex("^(?:$PICK\\s*[:#-]?\\s+)?$SETTLED\\b")
    private val settledWord = Regex("\\b$SETTLED\\b")
    private val nextPick = Regex("(?:^|[.:;!?]\\s+)(?:(?:nuev[oa]|siguiente|proxim[oa])\\s+$PICK|$PICK\\s+(?:de|para)\\s+(?:hoy|manana))\\b")
    private val labelledPick = Regex("(?:^|[.;!?]\\s+)$PICK\\s*[:#-]\\s*\\S")
    private val selectionLabel = Regex("(?:^|[.;!?]\\s+)(?:(?:nuev[oa]|siguiente|proxim[oa])\\s+)?$PICK(?:\\s+(?:de|para)\\s+(?:hoy|manana))?\\s*[:#-]\\s*")
    private val forwardVerb = Regex("\\b(?:vamos|voy|iremos)\\s+(?:con|por)\\b")
    private val market = Regex("\\b(?:over|under|mas de|menos de|ambos anotan|resultado exacto|handicap|corners?|goles?|puntos?|remates?|yards?|yardas?|ml|moneyline)\\b")
    private val odds = Regex("(?:@\\s*[0-9]+(?:[.,][0-9]+)?|\\b(?:cuota|momio|odds)\\s*[:=]?\\s*[0-9]+(?:[.,][0-9]+)?)")

    fun isClearlyRecapOnly(content: String): Boolean = isClearlyRecapOnly(listOf(content))

    fun isClearlyRecapOnly(contents: List<String>): Boolean {
        val lines = contents.flatMap { it.lineSequence().flatMap { line -> line.split(Regex("(?<=[.;!?])\\s+")).asSequence() }.toList() }.map { raw ->
            raw to PickBridgeRules.normalized(raw).trimStart { !it.isLetterOrDigit() }
        }.filter { it.second.isNotBlank() }
        val firstRecap = lines.indexOfFirst { isRetrospectiveLine(it.second) }
        if (firstRecap < 0) return false
        val current = lines.withIndex().any { (index, entry) ->
            hasCurrentSelection(entry.first, entry.second) &&
                lines.drop(index + 1).takeWhile { !startsSelection(it.second) }.none {
                    settledHeading.containsMatchIn(it.second) || settledSelection(it.second)
                }
        }
        if (current) return false
        // A market/price preceding a later historical balance is not itself a recap.
        // A conclusive GANADA/LIQUIDADO status still prevents this weak override.
        val settled = lines.any { settledHeading.containsMatchIn(it.second) || settledSelection(it.second) }
        if (!settled && lines.take(firstRecap).any { (raw, line) ->
            market.containsMatchIn(line) && odds.containsMatchIn(line) &&
                !hasSettledStatus(line) && raw.none { it == '✅' || it == '❌' }
        }) return false
        return true
    }

    private fun isRetrospectiveLine(line: String) =
        recapHeading.containsMatchIn(line) || settledHeading.containsMatchIn(line) || settledSelection(line)

    private fun startsSelection(line: String) = nextPick.containsMatchIn(line) || selectionLabel.containsMatchIn(line)

    private fun settledSelection(line: String): Boolean {
        val label = selectionLabel.find(line)
        if (label != null && hasSettledStatus(line.substring(label.range.last + 1))) return true
        val prospective = nextPick.find(line) ?: return false
        return hasSettledStatus(line.substring(prospective.range.last + 1).trimStart(' ', ':', '#', '-'))
    }

    private fun hasSettledStatus(clause: String): Boolean {
        val text = clause.trimStart { !it.isLetterOrDigit() }.trimEnd { !it.isLetterOrDigit() }
        val status = settledWord.find(text) ?: return false
        if (status.range.first == 0) return true
        if (Regex("\\b(?:buscamos|buscando|queremos|vamos por)\\b").containsMatchIn(text)) return false
        return status.range.last == text.lastIndex
    }

    private fun hasCurrentSelection(raw: String, line: String): Boolean {
        val prospective = nextPick.find(line)
        if (prospective != null && !hasSettledStatus(line.substring(prospective.range.last + 1).trimStart(' ', ':', '#', '-'))) return true
        val verb = forwardVerb.find(line)
        if (verb != null && !Regex("\\bno$").containsMatchIn(line.substring(0, verb.range.first).trimEnd()) &&
            market.containsMatchIn(line.substring(verb.range.first)) && !hasSettledStatus(line.substring(verb.range.first))) return true
        val label = labelledPick.find(line) ?: return false
        val clause = line.substring(label.range.first)
        return !settledWord.containsMatchIn(clause) && raw.none { it == '✅' || it == '❌' }
    }
}
