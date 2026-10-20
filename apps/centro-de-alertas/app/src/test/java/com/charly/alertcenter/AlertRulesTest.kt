package com.charly.alertcenter

import org.junit.Assert.*
import org.junit.Test

class AlertRulesTest {
    @Test fun accentsAreNormalized() {
        assertTrue(AlertRules.matchesQuery("Córner 9", "", "corner"))
    }
    @Test fun boundariesPreventFalseGoals() {
        assertTrue(AlertRules.hasPriorityWord("GOL de Chivas", "", "gol"))
        assertFalse(AlertRules.hasPriorityWord("golpe", "", "gol"))
    }
    @Test fun mixedCaseAndPhrase() {
        assertTrue(AlertRules.hasPriorityWord("Tarjeta roja", "", "RED CARD, tarjeta ROJA"))
    }
    @Test fun fingerprintIsStableButRespectsProfile() {
        val a = AlertRules.fingerprint("org.telegram.messenger", 0, "Hey", "Hi")
        val b = AlertRules.fingerprint("org.telegram.messenger", 0, " hey ", "HI")
        val dual = AlertRules.fingerprint("org.telegram.messenger", 10, "Hey", "Hi")
        assertEquals(a, b)
        assertNotEquals(a, dual)
    }
    @Test fun protectsSensitiveCodes() {
        assertEquals("[Contenido sensible oculto]", AlertRules.protectSensitiveText("OTP", "123456"))
        assertEquals("2-1", AlertRules.protectSensitiveText("Partido", "2-1"))
    }
    @Test fun invalidPackageIdsAreRejected() {
        assertTrue(AlertRules.validPackageId("app.nicegram"))
        assertFalse(AlertRules.validPackageId("com.bad;rm -rf"))
        assertFalse(AlertRules.validPackageId(""))
    }
    @Test fun tooManyKeywordsAreCapped() {
        assertEquals(40, AlertRules.terms((1..60).joinToString(",") { "foo$it" }).size)
    }
}
