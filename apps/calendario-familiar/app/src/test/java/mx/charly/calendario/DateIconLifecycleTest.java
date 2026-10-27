package mx.charly.calendario;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
    @Test public void legacyAliasSurvivesReceiverLaunchRecreationAndFinish() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        PackageManager pm=context.getPackageManager();
        // Simulate an older installation that left a non-default day enabled.
        ComponentName oldAlias=new ComponentName(context,DateIcon.alias(27));
        ComponentName defaultAlias=new ComponentName(context,DateIcon.alias(24));
        pm.setComponentEnabledSetting(defaultAlias,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(oldAlias,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
        int[] before=aliasStates(context);
        try {
            // The older version may still have a scheduled alarm after an update.
            new DateIcon.Receiver().onReceive(context,new Intent("mx.charly.calendario.UPDATE_DATE_ICON"));
            assertArrayEquals(before,aliasStates(context));
            assertEquals(R.drawable.calendar_generic,pm.getActivityInfo(oldAlias,PackageManager.GET_DISABLED_COMPONENTS).getIconResource());
            assertEquals(R.drawable.calendar_generic,pm.getActivityInfo(defaultAlias,PackageManager.GET_DISABLED_COMPONENTS).getIconResource());
            var ctl=Robolectric.buildActivity(MainActivity.class).create().start().resume();
            try {
                Configuration changed=new Configuration(context.getResources().getConfiguration());
                changed.orientation=changed.orientation==Configuration.ORIENTATION_LANDSCAPE
                    ?Configuration.ORIENTATION_PORTRAIT:Configuration.ORIENTATION_LANDSCAPE;
                ctl.configurationChange(changed);
                new DateIcon.Receiver().onReceive(context,new Intent("android.intent.action.BOOT_COMPLETED"));
                assertArrayEquals(before,aliasStates(context));
            } finally {
                ctl.get().finish();ctl.pause().stop().destroy();
            }
            assertArrayEquals(before,aliasStates(context));
        } finally {
            pm.setComponentEnabledSetting(defaultAlias,PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(oldAlias,PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,PackageManager.DONT_KILL_APP);
        }
    }
}
