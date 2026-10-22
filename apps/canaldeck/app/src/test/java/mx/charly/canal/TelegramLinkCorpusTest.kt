package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

/** Identical synthetic corpus is exercised by Info #33; URL syntax is not provenance. */
class TelegramLinkCorpusTest {
    @Test fun commonTelegramMessageCorpusMatchesAndPreservesAcceptedBytes() {
        val rows = requireNotNull(javaClass.getResourceAsStream("/telegram-message-links.tsv"))
            .bufferedReader(Charsets.UTF_8).use { it.readLines() }
        assertEquals(80, rows.size)
        rows.forEach { row ->
            val (expected, url) = row.split('\t', limit = 2)
            val result = PickBridgeRules.validTelegramLink(url)
            if (expected == "ACCEPT") assertEquals(url, url, result)
            else { assertEquals("REJECT", expected); assertNull(url, result) }
        }
    }
}
