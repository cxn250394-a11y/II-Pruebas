package mx.charly.calendario;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Safety checks use synthetic data only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BackupSafetyTest {
    private Context context;
    private final char[] password = "clave-sintetica-de-prueba".toCharArray();

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("calendario-familiar.db");
    }

    @Test public void truncatedEncryptedFilesCannotRestoreOrChangeEitherProfile() throws Exception {
        try (CalStore store = new CalStore(context)) {
            store.saveEvent(1, 0, "2026-11-01", "Evento uno", "09:30", "Nota uno", store.categories(1).get(0).id);
            store.saveEvent(2, 0, "2026-11-02", "Evento dos", "10:30", "Nota dos", store.categories(2).get(0).id);
            byte[] encrypted = Backup.encrypt(Backup.exportData(store), password);
            for (int length : new int[]{0, 4, 32, encrypted.length - 1}) {
                try {
                    byte[] clear = Backup.decrypt(Arrays.copyOf(encrypted, length), password);
                    Backup.importData(store, clear);
                    fail("Se aceptó un respaldo truncado de longitud " + length);
                } catch (IllegalArgumentException | javax.crypto.AEADBadTagException expected) {
                    // A malformed header or failed authentication must prevent import.
                }
                assertEquals("Evento uno", store.events(1, "2026-11-01").get(0).title);
                assertEquals("Evento dos", store.events(2, "2026-11-02").get(0).title);
            }
        }
    }

    @Test public void authenticatedBackupWithCrossProfileCategoryDoesNotOverwriteData() throws Exception {
        try (CalStore store = new CalStore(context)) {
            store.saveEvent(1, 0, "2026-11-01", "Se conserva uno", "09:30", "Nota uno", store.categories(1).get(0).id);
            store.saveEvent(2, 0, "2026-11-02", "Se conserva dos", "10:30", "Nota dos", store.categories(2).get(0).id);
            store.paint(1, "2026-11-01", Palette.COLORS[3]);
            store.paint(2, "2026-11-02", Palette.COLORS[4]);

            JSONObject document = new JSONObject(new String(Backup.exportData(store), StandardCharsets.UTF_8));
            JSONArray events = document.getJSONArray("events");
            JSONObject first = events.getJSONObject(0);
            assertEquals(1, first.getInt("profile"));
            first.put("category", store.categories(2).get(0).id);
            // This payload has a valid authentication tag but violates profile isolation.
            byte[] protectedData = Backup.encrypt(document.toString().getBytes(StandardCharsets.UTF_8), password);
            try {
                Backup.importData(store, Backup.decrypt(protectedData, password));
                fail("Se aceptó una categoría de otro perfil");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("otro perfil"));
            }
            assertEquals("Se conserva uno", store.events(1, "2026-11-01").get(0).title);
            assertEquals("Se conserva dos", store.events(2, "2026-11-02").get(0).title);
            assertEquals(Palette.COLORS[3], store.dayColor(1, "2026-11-01"));
            assertEquals(Palette.COLORS[4], store.dayColor(2, "2026-11-02"));
        }
    }

    @Test public void incompleteOrDuplicateFontsCannotOverwriteEitherProfile() throws Exception {
        try (CalStore store = new CalStore(context)) {
            store.saveEvent(1, 0, "2026-11-01", "Conservar uno", "09:30", "Nota uno", store.categories(1).get(0).id);
            store.saveEvent(2, 0, "2026-11-02", "Conservar dos", "10:30", "Nota dos", store.categories(2).get(0).id);
            store.paint(1, "2026-11-01", Palette.COLORS[3]);
            store.paint(2, "2026-11-02", Palette.COLORS[4]);
            store.setFontSize(1, 2);
            store.setFontSize(2, 0);
            byte[] before = Backup.exportData(store);
            JSONArray[] badFonts = {
                new JSONArray(),
                new JSONArray().put(new JSONObject().put("profile", 1).put("size", 0)),
                new JSONArray().put(new JSONObject().put("profile", 1).put("size", 0))
                    .put(new JSONObject().put("profile", 1).put("size", 1)),
                new JSONArray().put(new JSONObject().put("profile", 2).put("size", 0))
                    .put(new JSONObject().put("profile", 2).put("size", 1))
            };
            for (JSONArray fonts : badFonts) {
                JSONObject document = new JSONObject(new String(before, StandardCharsets.UTF_8));
                document.put("fonts", fonts);
                // A valid encrypted payload can still contain an invalid backup schema.
                byte[] encrypted = Backup.encrypt(document.toString().getBytes(StandardCharsets.UTF_8), password);
                try {
                    Backup.importData(store, Backup.decrypt(encrypted, password));
                    fail("Se aceptaron fuentes incompletas o repetidas: " + fonts);
                } catch (IllegalArgumentException expected) {
                    assertArrayEquals("La restauración inválida cambió datos", before, Backup.exportData(store));
                }
            }
        }
    }

    @Test public void sqliteFailureDuringFontRestoreRollsBackAllRecords() throws Exception {
        try (CalStore store = new CalStore(context)) {
            store.saveEvent(1, 0, "2026-11-01", "Original", "09:30", "Nota", store.categories(1).get(0).id);
            store.saveEvent(2, 0, "2026-11-02", "Otro original", "10:30", "Nota dos", store.categories(2).get(0).id);
            store.paint(1, "2026-11-01", Palette.COLORS[3]);
            store.setFontSize(1, 2);
            byte[] before = Backup.exportData(store);
            JSONObject replacement = new JSONObject(new String(before, StandardCharsets.UTF_8));
            replacement.getJSONArray("events").getJSONObject(0).put("title", "Cambio que debe revertirse");
            replacement.getJSONArray("fonts").getJSONObject(0).put("size", 0);
            store.getWritableDatabase().execSQL("CREATE TRIGGER fail_second_font BEFORE UPDATE OF font_size ON profiles " +
                "WHEN OLD.id=2 BEGIN SELECT RAISE(ABORT, 'simulated restore failure'); END");
            try {
                Backup.importData(store, replacement.toString().getBytes(StandardCharsets.UTF_8));
                fail("Se aceptó un fallo durante la transacción");
            } catch (SQLiteException expected) {
                assertArrayEquals("La transacción no revirtió los cambios", before, Backup.exportData(store));
            }
        }
    }
}
