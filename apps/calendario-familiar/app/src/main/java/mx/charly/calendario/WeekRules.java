package mx.charly.calendario;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Seven day inclusive week (Sunday to Saturday); independent of locale and app clock. */
public final class WeekRules {
    private WeekRules(){}
    public static LocalDate sunday(LocalDate reference) {
        if(reference==null)throw new IllegalArgumentException("Fecha requerida");
        return reference.minusDays(reference.getDayOfWeek().getValue()%7);
    }
    public static List<LocalDate> days(LocalDate reference) {
        LocalDate first=sunday(reference);
        ArrayList<LocalDate> dates=new ArrayList<>(7);
        for(int i=0;i<7;i++)dates.add(first.plusDays(i));
        return Collections.unmodifiableList(dates);
    }
    public static String eventLine(CalStore.Event e) {
        if(e==null)throw new IllegalArgumentException("Evento requerido");
        String category=e.shortName.isEmpty()?"":e.shortName+" · ";
        String hour=e.time.isEmpty()?"":e.time+" · ";
        return hour+category+e.title;
    }
}
