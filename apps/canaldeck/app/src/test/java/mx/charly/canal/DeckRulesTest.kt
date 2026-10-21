package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class DeckRulesTest {
    private val rows = listOf(ChatRow(1,"Fútbol","Nuevo córner",1,3,"channel"), ChatRow(2,"Trabajo","Hola",3,0,"group"), ChatRow(3,"Ana","Mensaje",2,2,"private",true,"a"))
    @Test fun accentSearch() { assertEquals(1L, DeckRules.filter(rows,"futbol").single().id) }
    @Test fun bodySearch() { assertEquals(1L, DeckRules.filter(rows,"corner").single().id) }
    @Test fun favoriteFirst() { assertEquals(listOf(3L,2L,1L),DeckRules.filter(rows).map { it.id }) }
    @Test fun unreadOnly() { assertEquals(2,DeckRules.filter(rows,kind="unread").size) }
    @Test fun channelsOnly() { assertEquals(1L,DeckRules.filter(rows,kind="channel").single().id) }
    @Test fun groupsOnly() { assertEquals(2L,DeckRules.filter(rows,kind="group").single().id) }
    @Test fun privateOnly() { assertEquals(3L,DeckRules.filter(rows,kind="private").single().id) }
    @Test fun favoritesOnly() { assertEquals(3L,DeckRules.filter(rows,kind="favorite").single().id) }
    @Test fun folderIsId() { assertEquals(3L,DeckRules.filter(rows,folder="a").single().id) }
    @Test fun noFolderSeparate() { assertEquals(2,DeckRules.filter(rows,folder="").size) }
    @Test fun sortUnread() { assertEquals(listOf(1L,3L,2L),DeckRules.filter(rows,sort=2).map { it.id }) }
    @Test fun alphabetical() { assertEquals(listOf(3L,1L,2L),DeckRules.filter(rows,sort=1).map { it.id }) }
    @Test fun homonymsKeepIds() { assertEquals(2,DeckRules.filter(listOf(rows[0],rows[0].copy(id=9))).size) }
    @Test fun validNames() { assertTrue(DeckRules.validFolder(" Lectura ")); assertFalse(DeckRules.validFolder(" ")); assertFalse(DeckRules.validFolder("x".repeat(41))); assertFalse(DeckRules.validFolder("a\nb")) }
    @Test fun textLimits() { assertTrue(DeckRules.validMessage("hola")); assertFalse(DeckRules.validMessage("\n ")); assertTrue(DeckRules.validMessage("a".repeat(4096))); assertFalse(DeckRules.validMessage("a".repeat(4097))) }
    @Test fun midnightQuiet() { assertTrue(DeckRules.quiet(23*60,23*60,8*60)); assertTrue(DeckRules.quiet(0,23*60,8*60)); assertFalse(DeckRules.quiet(8*60,23*60,8*60)) }
    @Test fun dayQuiet() { assertTrue(DeckRules.quiet(61,60,120)); assertFalse(DeckRules.quiet(120,60,120)); assertFalse(DeckRules.quiet(10,60,120)) }
    @Test fun equalHoursNotAllDay() { assertFalse(DeckRules.quiet(10,10,10)) }
    @Test fun invalidClock() { listOf("24:00","22:60","1:30","abc","-1:10").forEach { assertNull(DeckRules.clock(it)) }; assertEquals(1439,DeckRules.clock("23:59")) }
    @Test fun keywordBoundaries() { assertTrue(DeckRules.keyword("¡GOl!","gol")); assertFalse(DeckRules.keyword("golpe","gol")); assertTrue(DeckRules.keyword("CÓRNER local","corner")); assertFalse(DeckRules.keyword("x","")) }
    @Test fun notificationOff() { assertFalse(DeckRules.shouldNotify(NoticeMode.OFF,"gol","gol",false,false,false)) }
    @Test fun notificationSelected() { assertTrue(DeckRules.shouldNotify(NoticeMode.ALL,"hola","",false,false,false)) }
    @Test fun keywordsOnly() { assertFalse(DeckRules.shouldNotify(NoticeMode.KEYWORDS,"hola","gol",false,false,false)); assertTrue(DeckRules.shouldNotify(NoticeMode.KEYWORDS,"gol","gol",false,false,false)) }
    @Test fun noOwnMessages() { assertFalse(DeckRules.shouldNotify(NoticeMode.ALL,"hola","",true,false,false)) }
    @Test fun noVisibleChatAlerts() { assertFalse(DeckRules.shouldNotify(NoticeMode.ALL,"hola","",false,true,false)) }
    @Test fun quietSuppressesAlert() { assertFalse(DeckRules.shouldNotify(NoticeMode.ALL,"hola","",false,false,true)) }
    @Test fun noDuplicate() { val b=NoticeBook(); assertTrue(b.accept(1,1)); assertFalse(b.accept(1,1)); assertTrue(b.accept(2,1)); assertTrue(b.accept(1,2)) }
    @Test fun boundedDedup() { val b=NoticeBook(2); b.accept(1,1); b.accept(1,2); b.accept(1,3); assertEquals(2,b.size()); assertTrue(b.accept(1,1)) }
    @Test fun repeatedTextIsNotIdentifier() { val b=NoticeBook(); assertTrue(b.accept(1,99)); assertTrue(b.accept(1,100)) }
    @Test fun scanMoreThanFirstPage() { val rs=(1L..1000L).map { ChatRow(it,"Canal $it","",it,0,"channel") }; assertEquals(970L,DeckRules.filter(rs,"Canal 970").single().id) }
}
