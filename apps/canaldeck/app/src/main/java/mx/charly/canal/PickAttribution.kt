package mx.charly.canal

/** Shared exact attribution for local notices and relay; a chat title never attests a tipster. */
internal object PickAttribution {
    data class Result(val name: String?, val invalid: Boolean)
    private val prefix = Regex("""^\s*(?:tipster|pronosticador|picks?\s+de|by)\s*[:#\-]\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Result {
        val names = PickBridgeRules.allowedTipsters.associateBy { PickBridgeRules.normalized(it) }
        val found = mutableSetOf<String>()
        var invalid = false
        text.lineSequence().forEach { line ->
            val explicit = prefix.matchEntire(line.trim())?.groupValues?.get(1)
            val name = names[PickBridgeRules.normalized(explicit ?: line)]
            if (explicit != null && name == null) invalid = true
            if (name != null) found.add(name)
        }
        return Result(found.singleOrNull(), invalid || found.size > 1)
    }
}
