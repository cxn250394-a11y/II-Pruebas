package mx.charly.canal

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class PickBridgeOrganizationTest {
    private val c: Context get() = RuntimeEnvironment.getApplication()

    @Test fun sourcesAreOffByDefault() {
        assertEquals(PickWatchMode.OFF, Organization(c, 901).pickMode(-10011))
    }

    @Test fun modeIsPerActualChatNotTitle() {
        val org = Organization(c, 902)
        org.pickMode(-10011, PickWatchMode.DIRECT)
        assertEquals(PickWatchMode.DIRECT, org.pickMode(-10011))
        assertEquals(PickWatchMode.OFF, org.pickMode(-10012))
    }

    @Test fun accountIsolation() {
        val a = Organization(c, 903)
        val b = Organization(c, 904)
        a.pickMode(-10011, PickWatchMode.RESELLER)
        assertEquals(PickWatchMode.OFF, b.pickMode(-10011))
        assertEquals(PickWatchMode.RESELLER, Organization(c, 903).pickMode(-10011))
    }

    @Test fun ownerCanDisableWithoutLosingOtherNoticeSettings() {
        val a = Organization(c, 905)
        a.mode(-10011, NoticeMode.KEYWORDS)
        a.pickMode(-10011, PickWatchMode.RESELLER)
        a.pickMode(-10011, PickWatchMode.OFF)
        assertEquals(PickWatchMode.OFF, a.pickMode(-10011))
        assertEquals(NoticeMode.KEYWORDS, a.mode(-10011))
    }
}
