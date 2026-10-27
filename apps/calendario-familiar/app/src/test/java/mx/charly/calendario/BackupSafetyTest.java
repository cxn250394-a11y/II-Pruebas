package mx.charly.calendario;

import static org.junit.Assert.*;

import android.content.Context;
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
}
