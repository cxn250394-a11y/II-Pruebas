package mx.charly.calendario;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import java.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class DateIconLifecycleTest {
    private int[] aliasStates(Context context) {
        int[] states=new int[31];
        for(int i=1;i<=31;i++)states[i-1]=context.getPackageManager().getComponentEnabledSetting(
            new ComponentName(context,DateIcon.alias(i)));
        return states;
    }
    @Test public void receiverAndRecreationKeepAliasesStableUntilExplicitFinish() {
        Context context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("ui",Context.MODE_PRIVATE).edit().clear().commit();
        var ctl=Robolectric.buildActivity(MainActivity.class).create().start().resume();
        int[] initial=aliasStates(context);
        try {
            new DateIcon.Receiver().onReceive(context,new Intent("mx.charly.calendario.UPDATE_DATE_ICON"));
            assertArrayEquals(initial,aliasStates(context));
            Configuration changed=new Configuration(context.getResources().getConfiguration());
            changed.orientation=changed.orientation==Configuration.ORIENTATION_LANDSCAPE
                ?Configuration.ORIENTATION_PORTRAIT:Configuration.ORIENTATION_LANDSCAPE;
            ctl.configurationChange(changed);
            new DateIcon.Receiver().onReceive(context,new Intent("mx.charly.calendario.UPDATE_DATE_ICON"));
            assertArrayEquals(initial,aliasStates(context));
            // A document picker or another app can pause and stop the calendar
            // without closing its task.
            ctl.pause().stop();
            new DateIcon.Receiver().onReceive(context,new Intent("mx.charly.calendario.UPDATE_DATE_ICON"));
            assertArrayEquals(initial,aliasStates(context));
            ctl.start().resume();
        } finally {
            ctl.get().finish();
            ctl.pause().stop().destroy();
        }
        int today=LocalDate.now().getDayOfMonth();
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            context.getPackageManager().getComponentEnabledSetting(new ComponentName(context,DateIcon.alias(today))));
        assertEquals(today,context.getSharedPreferences("ui",Context.MODE_PRIVATE).getInt("last_icon_day",-1));
    }
}
