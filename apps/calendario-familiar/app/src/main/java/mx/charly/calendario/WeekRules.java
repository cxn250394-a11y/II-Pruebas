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
        String category=e.categoryName.isEmpty()?"":e.categoryName+" · ";
        String hour=e.time.isEmpty()?"":e.time+" · ";
        return hour+category+e.title;
    }
    /** Complete spoken description, including the note hidden by a grouped card's label. */
    public static String accessibleDay(String date, List<CalStore.Event> events) {
        StringBuilder text=new StringBuilder(date);
        if(events.isEmpty())text.append(". Sin eventos");
        for(CalStore.Event event:events){
            text.append(". ").append(eventLine(event));
            if(!event.note.isEmpty())text.append(". Nota: ").append(event.note);
        }
        return text.append(". Abrir día.").toString();
    }
}
