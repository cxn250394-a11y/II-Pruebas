package mx.charly.calendario;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class DateIcon {
    private DateIcon(){}
    // Disabling the launch alias can close an existing task, even while a document
    // picker covers it. Keep the alias stable through pause and recreation.
    private static int activities;
    private static boolean taskRetained, pending;
    public static synchronized void activityOpened(){activities++;taskRetained=true;}
    public static synchronized void activityDestroyed(Context context,boolean finishing){
        if(activities>0)activities--;
        // A non-finishing destroy can be a configuration change or system reclaim.
        // Keep deferring until an explicit finish or a fresh process.
        if(finishing && activities==0){
            taskRetained=false;
            if(pending)refresh(context.getApplicationContext());
        }
    }
    public static String alias(int day){
        if(day<1||day>31)throw new IllegalArgumentException("Día fuera de rango");
        return "mx.charly.calendario.Day"+(day<10?"0":"")+day;
    }
    public static synchronized void refresh(Context context){
        if(taskRetained){pending=true;schedule(context.getApplicationContext());return;}
        int today=LocalDate.now().getDayOfMonth();
        PackageManager pm=context.getPackageManager();
        try {
            if(Build.VERSION.SDK_INT>=33){
                List<PackageManager.ComponentEnabledSetting> settings=new ArrayList<>();
                for(int day=1;day<=31;day++)settings.add(new PackageManager.ComponentEnabledSetting(
                        new ComponentName(context,alias(day)),
                        day==today?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP));
                pm.setComponentEnabledSettings(settings);
            }else{
                // Enable the current day first to avoid temporarily removing every launcher entry.
                pm.setComponentEnabledSetting(new ComponentName(context,alias(today)),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
                for(int d=1;d<=31;d++)if(d!=today)
                    pm.setComponentEnabledSetting(new ComponentName(context,alias(d)),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            }
            context.getSharedPreferences("ui",Context.MODE_PRIVATE).edit().putInt("last_icon_day",today).apply();
            pending=false;
        }catch(Exception ignored){
            // Some third-party launchers disallow or cache alias changes; calendar functions remain usable.
        }
        schedule(context);
    }
    /** Schedule next update without disabling the active launcher entry during startup. */
    public static void scheduleOnly(Context context){schedule(context.getApplicationContext());}
    private static void schedule(Context context){
        try{
            AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            if(alarms==null)return;
            ZoneId zone=ZoneId.systemDefault();
            ZonedDateTime next=LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(4);
            Intent intent=new Intent(context,Receiver.class).setAction("mx.charly.calendario.UPDATE_DATE_ICON");
            PendingIntent pending=PendingIntent.getBroadcast(context,81,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pending);
        }catch(Exception ignored){/* OS battery policies may defer automatic updates. */}
    }
    public static final class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context context,Intent intent){refresh(context.getApplicationContext());}
    }
}
