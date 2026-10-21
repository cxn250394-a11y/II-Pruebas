package com.charly.alertcenter

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class LabEngineTest(private val description: String, private val check: () -> Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = LabCases.all.map { arrayOf(it.first, it.second) }
    }
    @Test fun contract() { assertTrue(description, check()) }
}
