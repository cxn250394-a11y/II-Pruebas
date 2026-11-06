package mx.charly.calendario;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Synthetic v1 SQLite fixture. This is not an APK-to-APK or file-picker test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BackupUpgradeTest {
    private static final String DATABASE = "calendario-familiar.db";
    private static final String DAY = "2026-11-06";
    private Context context;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DATABASE);
        createVersionOneDatabase();
    }

    /** Exact production v1 table/index definitions, independent of current onCreate. */
    private void createVersionOneDatabase() {
        try (SQLiteDatabase db = context.openOrCreateDatabase(DATABASE, Context.MODE_PRIVATE, null)) {
            db.execSQL("CREATE TABLE profiles(id INTEGER PRIMARY KEY, name TEXT NOT NULL, font_size INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE TABLE day_colors(profile_id INTEGER NOT NULL, day TEXT NOT NULL, color INTEGER NOT NULL, PRIMARY KEY(profile_id,day), FOREIGN KEY(profile_id) REFERENCES profiles(id))");
            db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL, name TEXT NOT NULL, short_name TEXT NOT NULL, color INTEGER NOT NULL, FOREIGN KEY(profile_id) REFERENCES profiles(id))");
            db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL, day TEXT NOT NULL, title TEXT NOT NULL, event_time TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', category_id INTEGER, FOREIGN KEY(profile_id) REFERENCES profiles(id), FOREIGN KEY(category_id) REFERENCES categories(id))");
            db.execSQL("CREATE INDEX events_by_day ON events(profile_id,day,event_time,id)");
            db.execSQL("INSERT INTO profiles(id,name,font_size) VALUES(1,'Yo',0),(2,'Mamá',2)");
            db.execSQL("INSERT INTO categories(id,profile_id,name,short_name,color) VALUES(101,1,'Categoría sintética Yo','QAY',?),(202,2,'Categoría sintética Mamá','QAM',?)",
                new Object[]{Palette.COLORS[1], Palette.COLORS[5]});
            db.execSQL("INSERT INTO day_colors(profile_id,day,color) VALUES(1,?,?),(2,?,?)",
                new Object[]{DAY, Palette.COLORS[3], DAY, Palette.COLORS[4]});
            db.execSQL("INSERT INTO events(id,profile_id,day,title,event_time,note,category_id) VALUES(1001,1,?,'Fixture Yo','09:30','Nota sintética uno',101),(2002,2,?,'Fixture Mamá','16:45','Nota sintética dos',202)",
                new Object[]{DAY, DAY});
            db.setVersion(1);
            assertEquals(1, db.getVersion());
            try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='paint_undo'", null)) {
                assertFalse("The legacy fixture must not contain the new table", cursor.moveToFirst());
            }
        }
    }

    private void assertLegacyData(CalStore store) {
        assertEquals(0, store.fontSize(1));
        assertEquals(2, store.fontSize(2));
        assertEquals(Palette.COLORS[3], store.dayColor(1, DAY));
        assertEquals(Palette.COLORS[4], store.dayColor(2, DAY));
        assertEquals(1, store.categories(1).size());
        assertEquals(1, store.categories(2).size());
        CalStore.Category one = store.categories(1).get(0);
        CalStore.Category two = store.categories(2).get(0);
        assertEquals(101L, one.id);
        assertEquals(202L, two.id);
        assertEquals(1, one.profile);
        assertEquals(2, two.profile);
        assertEquals("Categoría sintética Yo", one.name);
        assertEquals("Categoría sintética Mamá", two.name);
        assertEquals("QAY", one.shortName);
        assertEquals("QAM", two.shortName);
        assertEquals(Palette.COLORS[1], one.color);
        assertEquals(Palette.COLORS[5], two.color);
        assertEquals(1, store.events(1, DAY).size());
        assertEquals(1, store.events(2, DAY).size());
        CalStore.Event first = store.events(1, DAY).get(0);
        CalStore.Event second = store.events(2, DAY).get(0);
        assertEquals(1001L, first.id);
        assertEquals(2002L, second.id);
        assertEquals(101L, first.category);
        assertEquals(202L, second.category);
        assertEquals(DAY, first.date);
        assertEquals(DAY, second.date);
        assertEquals("Fixture Yo", first.title);
        assertEquals("Fixture Mamá", second.title);
        assertEquals("09:30", first.time);
        assertEquals("16:45", second.time);
        assertEquals("Nota sintética uno", first.note);
        assertEquals("Nota sintética dos", second.note);
        assertEquals(one.color, first.color);
        assertEquals(two.color, second.color);
        assertEquals(one.name, first.categoryName);
        assertEquals(two.name, second.categoryName);
        try (Cursor cursor = store.getReadableDatabase().rawQuery("PRAGMA foreign_key_check", null)) {
            assertFalse("Migration/import left broken references", cursor.moveToFirst());
        }
    }

    @Test public void migrationFromVersionOneKeepsBothProfilesAndSurvivesReopen() {
        try (CalStore store = new CalStore(context)) {
            assertEquals("Expected the real v1-to-v2 upgrade", 2, store.getWritableDatabase().getVersion());
            try (Cursor cursor = store.getReadableDatabase().rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='paint_undo'", null)) {
                assertTrue("The upgrade must add the undo table", cursor.moveToFirst());
            }
            assertLegacyData(store);
        }
        try (CalStore reopened = new CalStore(context)) {
            assertEquals(2, reopened.getReadableDatabase().getVersion());
            assertLegacyData(reopened);
        }
    }

    @Test public void cfamOneEncryptedRoundTripRestoresMigratedRecordsAndIds() throws Exception {
        char[] password = "clave-sintetica-upgrade".toCharArray();
        byte[] original = null, decrypted = null;
        try (CalStore store = new CalStore(context)) {
            assertEquals(2, store.getWritableDatabase().getVersion());
            assertLegacyData(store);
            original = Backup.exportData(store);
            byte[] encrypted = Backup.encrypt(original, password);
            assertArrayEquals("The portable backup format must remain CFAM1",
                "CFAM1".getBytes(StandardCharsets.US_ASCII), Arrays.copyOf(encrypted, 5));

            // Distinct sentinel values make a no-op import fail the comparison.
            store.saveEvent(1, 1001L, DAY, "Sentinel cambiado Yo", "01:00", "Sentinel uno", 101L);
            store.saveEvent(2, 2002L, DAY, "Sentinel cambiado Mamá", "02:00", "Sentinel dos", 202L);
            store.paint(1, DAY, Palette.COLORS[6]);
            store.paint(2, DAY, Palette.COLORS[7]);
            store.setFontSize(1, 2);
            store.setFontSize(2, 0);
            store.saveCategory(1, 101L, "Sentinel categoría", "CAMBIO", Palette.COLORS[8]);
            assertFalse(Arrays.equals(original, Backup.exportData(store)));

            decrypted = Backup.decrypt(encrypted, password);
            assertArrayEquals(original, decrypted);
            Backup.importData(store, decrypted);
            assertLegacyData(store);
            assertArrayEquals("All portable records must be restored exactly", original, Backup.exportData(store));
        } finally {
            Arrays.fill(password, '\0');
            if (original != null) Arrays.fill(original, (byte) 0);
            if (decrypted != null) Arrays.fill(decrypted, (byte) 0);
        }
        try (CalStore reopened = new CalStore(context)) {
            assertLegacyData(reopened);
        }
    }
    @Test public void unicodeWhitespaceTitleIsRejectedBeforeReplacingEitherAgenda()throws Exception{
        try(CalStore store=new CalStore(context)){
            byte[] before=Backup.exportData(store);
            org.json.JSONObject invalid=new org.json.JSONObject(new String(before,StandardCharsets.UTF_8));
            invalid.getJSONArray("events").getJSONObject(0).put("title"," ");
            try{
                Backup.importData(store,invalid.toString().getBytes(StandardCharsets.UTF_8));
                fail("Unicode whitespace title accepted");
            }catch(IllegalArgumentException expected){}
            assertArrayEquals(before,Backup.exportData(store));
            assertLegacyData(store);
        }
    }
}
