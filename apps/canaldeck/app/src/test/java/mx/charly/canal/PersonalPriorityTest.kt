package mx.charly.canal
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class PriorityRulesTest {
    private val rows = listOf(
        ChatRow(1,"Sports","Goal",10,0,"channel",favorite=false,important=false),
        ChatRow(2,"Music","New",8,2,"channel",favorite=true,important=false),
        ChatRow(3,"Urgent","News",1,0,"channel",favorite=false,important=true),
        ChatRow(4,"Urgent unread","Ping",9,3,"group",favorite=false,important=true)
    )
    @Test fun priorityFirstAndFavoriteSecond() = assertEquals(listOf(4L,3L,2L,1L),DeckRules.filter(rows).map{it.id})
    @Test fun onlyPriority() = assertEquals(listOf(4L,3L),DeckRules.filter(rows,kind="important").map{it.id})
    @Test fun importantUnread() = assertEquals(4L,DeckRules.filter(rows,kind="important-unread").single().id)
    @Test fun combinedKindAndFolderDoNotLeak() = assertEquals(0,DeckRules.filter(rows,folder="not-a-folder",kind="important").size)
    @Test fun explicitUnreadSortStillWorks() = assertEquals(listOf(4L,2L,1L,3L),DeckRules.filter(rows,sort=2).map{it.id})
    @Test fun messageAgePositive() = assertEquals(1500L,DeckRules.observedAgeMillis(100,101500))
    @Test fun futureDateRejected() = assertNull(DeckRules.observedAgeMillis(200,100_000))
    @Test fun staleMessagesRejected() = assertNull(DeckRules.observedAgeMillis(1,86_403_000))
    @Test fun invalidTimestampRejected() = assertNull(DeckRules.observedAgeMillis(0,100_000))
    @Test fun boundary24HoursAccepted() = assertEquals(86_400_000L,DeckRules.observedAgeMillis(1,86_401_000))
    @Test fun invalidOverflowRejected() = assertNull(DeckRules.observedAgeMillis(Long.MAX_VALUE,Long.MAX_VALUE))
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PersonalOrganizationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Test fun disabledByDefaultAndAccountScoped() {
        val a=Organization(context,-7777771);val b=Organization(context,-7777772)
        assertFalse(a.important(18));a.toggleImportant(18);assertTrue(a.important(18));assertFalse(b.important(18))
        a.toggleImportant(18);assertFalse(a.important(18))
    }
    @Test fun importantAndFavoriteIndependent() {
        val a=Organization(context,-7777773);a.toggleImportant(21)
        assertFalse(a.favorite(21));a.toggleFavorite(21);assertTrue(a.important(21))
    }
    @Test fun noticeAgePersistsWithoutMessages() {
        val a=Organization(context,-7777774);a.recordLocalNoticeAge(100,103_000)
        assertEquals(3_000L,Organization(context,-7777774).lastLocalNoticeAgeMillis())
        assertEquals(103_000L,Organization(context,-7777774).lastLocalNoticeAt())
        a.recordLocalNoticeAge(200,100_000)
        assertEquals(3_000L,a.lastLocalNoticeAgeMillis())
    }
    @Test fun priorityNotificationChannelIsDistinct() {
        assertEquals("canaldeck-42",ConnectionService.channel(42))
        assertEquals("canaldeck-important-42",ConnectionService.channel(42,true))
    }
}
