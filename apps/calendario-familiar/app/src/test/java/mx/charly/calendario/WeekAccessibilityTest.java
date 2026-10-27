package mx.charly.calendario;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class WeekAccessibilityTest {
    private Context context;
    @Before public void reset(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("calendario-familiar.db");
        context.getSharedPreferences("ui",Context.MODE_PRIVATE).edit().clear().commit();
    }
    @Test public void startOfWeekIsSundayEvenAcrossYear(){
        assertEquals(LocalDate.parse("2025-12-28"),WeekRules.sunday(LocalDate.parse("2026-01-01")));
        assertEquals(LocalDate.parse("2026-10-25"),WeekRules.sunday(LocalDate.parse("2026-10-27")));
    }
    @Test public void weekHasSevenConsecutiveDays(){
        List<LocalDate> days=WeekRules.days(LocalDate.parse("2026-10-31"));
        assertEquals(7,days.size());
        assertEquals(LocalDate.parse("2026-10-25"),days.get(0));
        assertEquals(LocalDate.parse("2026-10-31"),days.get(6));
        for(int i=1;i<7;i++)assertEquals(days.get(i-1).plusDays(1),days.get(i));
    }
    @Test public void weekCrossesYear(){
        List<LocalDate> dates=WeekRules.days(LocalDate.parse("2027-01-01"));
        assertEquals(LocalDate.parse("2026-12-27"),dates.get(0));
        assertEquals(LocalDate.parse("2027-01-02"),dates.get(6));
    }
    @Test public void eventLineIncludesActualTextAndTime(){
        CalStore.Event e=new CalStore.Event(1,1,"2026-10-27","Consulta de seguimiento","09:30","Notas",Palette.COLORS[1],"ISSSTE Balbuena","ISSSTE");
        assertEquals("09:30 · ISSSTE · Consulta de seguimiento",WeekRules.eventLine(e));
    }
    @Test public void eventLineWithoutCategoryOrTimeIsPlainTitle(){
        CalStore.Event e=new CalStore.Event(1,0,"2026-10-27","Trabajo","","",Palette.COLORS[3],"","");
        assertEquals("Trabajo",WeekRules.eventLine(e));
    }
    @Test public void localMonthContainsMomsEventsWithoutMixingPersonal(){
        try(CalStore db=new CalStore(context)){
            db.saveEvent(2,0,"2026-10-27","Estudios","08:00","Recordatorio",db.categories(2).get(0).id);
            assertEquals(1,db.events(2,"2026-10-27").size());
            assertTrue(db.events(1,"2026-10-27").isEmpty());
        }
    }
    private static TextView find(View root,String needle){
        if(root instanceof TextView && ((TextView)root).getText().toString().contains(needle))return (TextView)root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                TextView nested=find(group.getChildAt(i),needle);
                if(nested!=null)return nested;
            }
        }
        return null;
    }
    @Test public void weeklyAccessibleScreenCanBeReachedWithoutAccount(){
        var ctl=Robolectric.buildActivity(MainActivity.class).create();
        try{
            View root=ctl.get().getWindow().getDecorView();
            TextView button=find(root,"Ver semana");
            assertNotNull(button);button.performClick();
            View week=ctl.get().getWindow().getDecorView();
            assertNotNull(find(week,"Semana anterior"));
            assertNotNull(find(week,"Semana siguiente"));
            assertNotNull(find(week,"Vista grande"));
            assertNotNull(find(week,"Volver a la vista mensual"));
        }finally{ctl.destroy();}
    }
    @Test public void paintUndoRestoresPriorColorAndLeavesOtherProfile(){
        var ctl=Robolectric.buildActivity(MainActivity.class).create();
        try{
            MainActivity activity=ctl.get();
            LocalDate date=LocalDate.parse("2026-10-27");
            CalStore db=new CalStore(activity);
            db.paint(1,date.toString(),Palette.COLORS[2]);
            Method paint=MainActivity.class.getDeclaredMethod("paintDay",LocalDate.class,int.class);
            Method undo=MainActivity.class.getDeclaredMethod("undoLastPaint");
            paint.setAccessible(true);undo.setAccessible(true);
            paint.invoke(activity,date,Palette.COLORS[8]);
            assertEquals(Palette.COLORS[8],db.dayColor(1,date.toString()));
            undo.invoke(activity);
            assertEquals(Palette.COLORS[2],db.dayColor(1,date.toString()));
            assertEquals(Palette.DEFAULT_DAY,db.dayColor(2,date.toString()));
            db.close();
        }catch(Exception err){throw new AssertionError(err);}
        finally{ctl.destroy();}
    }
    @Test public void previewPackageIsolatedFromPreviousInstallation(){
        assertEquals("mx.charly.calendario.terra",context.getPackageName());
    }
}
