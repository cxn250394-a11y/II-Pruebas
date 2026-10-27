package mx.charly.calendario;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Legacy launcher component names are retained, but all use the same static icon. */
public final class DateIcon {
    private DateIcon(){}
    public static String alias(int day){
        if(day<1||day>31)throw new IllegalArgumentException("Día fuera de rango");
        return "mx.charly.calendario.Day"+(day<10?"0":"")+day;
    }
    /** An alarm already scheduled by an older version may still arrive. Do nothing. */
    public static final class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context context,Intent intent){ /* no component mutations */ }
    }
}
