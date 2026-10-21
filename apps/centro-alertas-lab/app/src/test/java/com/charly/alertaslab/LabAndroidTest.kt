package com.charly.alertaslab

import android.app.Application
import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.charly.alertcenter.AlertCategory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Android JVM simulation only; these are not POCO or emulator acceptance tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LabAndroidTest {
    private val context: Context get() = RuntimeEnvironment.getApplication<Application>()
    private val now = 10_000_000_000L
    @Before fun clean() {
        context.deleteDatabase("alerts_lab.db")
        context.getSharedPreferences("lab_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun add(db: LabStore, posted: Long = now, message: Long = 0, occurrence: Int = 0,
                    source: String = "org.telegram.messenger", profile: Int = 0,
                    title: String = "Canal", body: String = "hola", received: Long = now) =
        db.insert(source, profile, "key", title, body, posted, received, message, occurrence)
    private fun builder() = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_info)

    @Test fun defaultConsentOff() { val p = LabPrefs(context); assertTrue(p.selected().isEmpty()); assertFalse(p.permits("org.telegram.messenger")) }
    @Test fun consentAndPause() { val p = LabPrefs(context); p.select("org.telegram.messenger", true); assertTrue(p.permits("org.telegram.messenger")); p.pause(true); assertFalse(p.permits("org.telegram.messenger")) }
    @Test fun customSourceDoesNotOptIn() { val p = LabPrefs(context); assertTrue(p.addSource("com.example.sports", AlertCategory.SPORTS)); assertFalse(p.permits("com.example.sports")); assertEquals(AlertCategory.SPORTS, p.category("com.example.sports")) }
    @Test fun invalidCustomSourceRejected() { assertFalse(LabPrefs(context).addSource("not valid", AlertCategory.SPORTS)) }
    @Test fun folderPersisted() { val p = LabPrefs(context); assertTrue(p.addFolder("Favoritos míos")); p.assign("key", "Favoritos míos"); assertEquals("Favoritos míos", LabPrefs(context).folder("key")) }
    @Test fun folderDuplicateRejected() { val p = LabPrefs(context); assertTrue(p.addFolder("Amigos")); assertFalse(p.addFolder("amigos")) }
    @Test fun noUnknownFolderAssignment() { val p = LabPrefs(context); p.assign("key", "desconocida"); assertEquals("", p.folder("key")) }
    @Test fun staleAssignmentsCanBePruned() { val p = LabPrefs(context); p.assign("old", "Importantes"); p.pruneAssignments(emptySet()); assertEquals("", p.folder("old")) }
    @Test fun sameEventStoredOnce() = LabStore(context).use { d -> assertTrue(add(d)); assertFalse(add(d)); assertEquals(1, d.all(now).size) }
    @Test fun repeatedTextAtNewTimestampKept() = LabStore(context).use { d -> add(d); add(d, posted=now+1); assertEquals(2, d.all(now+1).size) }
    @Test fun messagingHistoryUpdateNotDuplicated() = LabStore(context).use { d -> add(d, message=100); assertFalse(add(d, posted=now+1, message=100)); assertEquals(1,d.all(now).size) }
    @Test fun identicalMessagesSameTimestampDifferentOccurrenceKept() = LabStore(context).use { d -> add(d,message=100,occurrence=0); add(d,message=100,occurrence=1); assertEquals(2,d.all(now).size) }
    @Test fun profilesAreSeparate() = LabStore(context).use { d -> add(d); add(d, profile=999); assertEquals(2,d.all(now).size) }
    @Test fun clientsAreSeparate() = LabStore(context).use { d -> add(d); add(d, source="app.nicegram"); assertEquals(2,d.all(now).size) }
    @Test fun OTPNotStoredInTitleOrBody() = LabStore(context).use { d -> add(d,title="Verification code: 123456",body="Secret 123456"); val a=d.all(now).single(); assertFalse((a.title+a.body).contains("123456")); assertTrue(a.body.contains("oculto")) }
    @Test fun blankContentIgnored() = LabStore(context).use { d -> assertFalse(add(d,title="",body="")); assertTrue(d.all(now).isEmpty()) }
    @Test fun longContentClipped() = LabStore(context).use { d -> add(d,title="a".repeat(900),body="b".repeat(6000)); val a=d.all(now).single(); assertEquals(300,a.title.length); assertEquals(1800,a.body.length) }
    @Test fun localReadAndFavoritePersist() = LabStore(context).use { d -> add(d); val id=d.all(now).single().id; d.markRead(id); d.favorite(id,true); val a=d.all(now).single(); assertTrue(a.read); assertTrue(a.favorite) }
    @Test fun demoIdCannotChangeRealRow() = LabStore(context).use { d -> add(d); d.markRead(-1); d.favorite(-1,true); val a=d.all(now).single(); assertFalse(a.read); assertFalse(a.favorite) }
    @Test fun clearOnlyLabDatabase() = LabStore(context).use { d -> add(d); d.clear(); assertTrue(d.all(now).isEmpty()) }
    @Test fun retentionBoundary() = LabStore(context).use { d -> add(d,posted=1,received=now-LabStore.KEEP_MS); assertEquals(1,d.all(now).size); d.purge(now+1); assertTrue(d.all(now+1).isEmpty()) }
    @Test fun rowCeiling() = LabStore(context).use { d ->
        val db=d.writableDatabase; db.beginTransaction()
        try {
            val s=db.compileStatement("INSERT INTO alerts(event_key,source,profile,title,body,posted,received) VALUES(?, 's', 0, 't', 'b', ?, ?)")
            for (i in 0..2004) { s.bindString(1,"seed$i"); s.bindLong(2,now+i); s.bindLong(3,now+i); s.executeInsert() }
            s.close(); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        d.purge(now+2004); assertEquals(2000,d.all(now+2004).size)
        add(d,posted=now+2005,received=now+2005); assertEquals(2000,d.all(now+2005).size)
    }
    @Test fun plainNotificationExtracted() { val p=NotificationParts.extract(context, builder().setContentTitle("T").setContentText("B").build()).single(); assertEquals("T",p.title); assertEquals("B",p.body) }
    @Test fun bigTextPreferred() { val n=builder().setContentTitle("T").setContentText("short").setStyle(Notification.BigTextStyle().bigText("long full text")).build(); assertEquals("long full text",NotificationParts.extract(context, n).single().body) }
    @Test fun inboxLinesKept() { val n=builder().setContentTitle("T").setContentText("summary").setStyle(Notification.InboxStyle().addLine("one").addLine("two")).build(); assertEquals("one\ntwo",NotificationParts.extract(context, n).single().body) }
    @Test fun messagingPartsAndStableOccurrence() {
        val sender=Person.Builder().setName("Sender").build()
        val style=Notification.MessagingStyle(Person.Builder().setName("Me").build()).setConversationTitle("Group").setGroupConversation(true)
            .addMessage("one",100L,sender).addMessage("one",100L,sender)
        val parts=NotificationParts.extract(context, builder().setStyle(style).build())
        assertEquals(2,parts.size); assertEquals("Group",parts[0].title)
        assertEquals(listOf(0,1),parts.map { it.occurrence }); assertEquals("Sender: one",parts[0].body)
    }
    @Test fun historicMessagesNotImported() {
        val sender=Person.Builder().setName("Sender").build()
        val style=Notification.MessagingStyle(Person.Builder().setName("Me").build())
            .addHistoricMessage(Notification.MessagingStyle.Message("history",1L,sender)).addMessage("current",2L,sender)
        val parts=NotificationParts.extract(context, builder().setStyle(style).build())
        assertEquals(1,parts.size); assertTrue(parts.single().body.contains("current"))
    }
    @Test fun separateApplicationAndNoRuntimePermissions() {
        assertEquals("com.charly.alertaslab",context.packageName)
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.isNullOrEmpty())
    }
    @Test fun listenerRequiresSystemBindPermission() {
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SERVICES)
        assertEquals(1,info.services.size)
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",info.services.single().permission)
    }
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Test fun welcomeAndDemoNeedNoPermissionOrPersistentData() {
        val c=Robolectric.buildActivity(LabActivity::class.java).create()
        try {
            val a=c.get(); val root=a.window.decorView
            assertTrue(views(root).filterIsInstance<TextView>().any { it.text.toString().contains("Paso 1 pendiente") })
            views(root).filterIsInstance<Button>().first { it.text.toString().contains("Probar con ejemplos") }.performClick()
            assertTrue(views(root).filterIsInstance<Button>().any { it.text.toString().contains("DEMO") })
            assertEquals(7,views(root).filterIsInstance<ListView>().single().adapter.count)
            assertTrue(LabPrefs(context).selected().isEmpty())
            LabStore(context).use { assertTrue(it.all().isEmpty()) }
        } finally { c.destroy() }
    }
    @Test @Config(sdk = [35]) fun android35StartupSmoke() {
        val c=Robolectric.buildActivity(LabActivity::class.java).create()
        try { assertNotNull(c.get().findViewById<View>(android.R.id.content)) } finally { c.destroy() }
    }
}
