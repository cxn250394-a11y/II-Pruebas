package mx.charly.calendario;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.YearMonth;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Regression checks for navigation context and configuration changes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class CalendarExperienceTest {
    @Before public void reset() {
        Context context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("calendario-familiar.db");
        context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("ui",Context.MODE_PRIVATE).edit().clear().commit();
    }
    private static Object read(MainActivity activity,String name)throws Exception {
        Field field=MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }
    private static void write(MainActivity activity,String name,Object value)throws Exception {
        Field field=MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity,value);
    }
    private static void render(MainActivity activity)throws Exception {
        Method method=MainActivity.class.getDeclaredMethod("render");
        method.setAccessible(true);method.invoke(activity);
    }
    private static void profile(MainActivity activity,int value)throws Exception {
        Method method=MainActivity.class.getDeclaredMethod("switchProfile",int.class);
        method.setAccessible(true);method.invoke(activity,value);
    }
    private static View screen(MainActivity activity) {
        return activity.getWindow().getDecorView();
    }
    private static TextView text(View root,String value) {
        if(root instanceof TextView && ((TextView)root).getText().toString().contains(value))
            return (TextView)root;
        if(root instanceof ViewGroup) {
            ViewGroup children=(ViewGroup)root;
            for(int i=0;i<children.getChildCount();i++) {
                TextView found=text(children.getChildAt(i),value);
                if(found!=null)return found;
            }
        }
        return null;
    }
    @Test public void primaryNavigationHasAccessibleButtonsAndKeepsTheSelectedDay()throws Exception {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try {
            MainActivity activity=controller.get();
            LocalDate chosen=LocalDate.of(2027,2,13);
            write(activity,"selected",chosen);
            write(activity,"currentMonth",YearMonth.from(chosen));
            render(activity);
            View day=screen(activity).findViewWithTag("nav-day");
            assertNotNull(day);assertTrue(day.isClickable());assertTrue(day.isFocusable());
            AccessibilityNodeInfo node=day.createAccessibilityNodeInfo();
            assertEquals("android.widget.Button",node.getClassName().toString());
            assertTrue(day.performClick());
            assertEquals("day",read(activity,"page"));
            assertEquals(chosen,read(activity,"selected"));
            View month=screen(activity).findViewWithTag("nav-month");
            assertNotNull(month);month.performClick();
            assertEquals("month",read(activity,"page"));
            assertEquals(YearMonth.of(2027,2),read(activity,"currentMonth"));
        } finally {controller.destroy();}
    }
    @Test public void previousAndNextDayCrossYearWithoutDroppingDate()throws Exception {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try {
            MainActivity activity=controller.get();
            write(activity,"page","day");
            write(activity,"selected",LocalDate.of(2027,1,1));
            write(activity,"currentMonth",YearMonth.of(2027,1));
            render(activity);
            TextView previous=text(screen(activity),"Día anterior");
            assertNotNull(previous);previous.performClick();
            assertEquals(LocalDate.of(2026,12,31),read(activity,"selected"));
            TextView next=text(screen(activity),"Día siguiente");
            assertNotNull(next);next.performClick();
            assertEquals(LocalDate.of(2027,1,1),read(activity,"selected"));
        } finally {controller.destroy();}
    }
    @Test public void eachProfileRestoresItsOwnDateAndView()throws Exception {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try {
            MainActivity activity=controller.get();
            LocalDate mine=LocalDate.of(2027,3,20),moms=LocalDate.of(2028,2,29);
            write(activity,"selected",mine);write(activity,"currentMonth",YearMonth.from(mine));
            write(activity,"page","day");render(activity);
            profile(activity,2);
            assertEquals(2,read(activity,"profile"));
            write(activity,"selected",moms);write(activity,"currentMonth",YearMonth.from(moms));
            write(activity,"page","week");render(activity);
            profile(activity,1);
            assertEquals(mine,read(activity,"selected"));
            assertEquals(YearMonth.of(2027,3),read(activity,"currentMonth"));
            assertEquals("day",read(activity,"page"));
            profile(activity,2);
            assertEquals(moms,read(activity,"selected"));
            assertEquals(YearMonth.of(2028,2),read(activity,"currentMonth"));
            assertEquals("week",read(activity,"page"));
        } finally {controller.destroy();}
    }
    @Test public void savedInstanceRestoresPaintingColorAndNavigation()throws Exception {
        Bundle state=new Bundle();
        var first=Robolectric.buildActivity(MainActivity.class).create();
        try {
            MainActivity activity=first.get();
            write(activity,"page","month");
            write(activity,"selected",LocalDate.of(2027,5,12));
            write(activity,"currentMonth",YearMonth.of(2027,5));
            write(activity,"painting",true);
            write(activity,"brush",Palette.COLORS[19]);
            first.saveInstanceState(state);
        } finally {first.destroy();}
        var next=Robolectric.buildActivity(MainActivity.class).create(state);
        try {
            assertEquals("month",read(next.get(),"page"));
            assertEquals(LocalDate.of(2027,5,12),read(next.get(),"selected"));
            assertEquals(YearMonth.of(2027,5),read(next.get(),"currentMonth"));
            assertEquals(true,read(next.get(),"painting"));
            assertEquals(Palette.COLORS[19],read(next.get(),"brush"));
        } finally {next.destroy();}
    }
    @Test public void selectedWeekSurvivesActivityRestart()throws Exception {
        var first=Robolectric.buildActivity(MainActivity.class).create().start().resume();
        MainActivity activity=first.get();
        write(activity,"page","week");
        write(activity,"selected",LocalDate.of(2029,6,17));
        write(activity,"currentMonth",YearMonth.of(2029,6));
        render(activity);
        first.pause().stop().destroy();
        var second=Robolectric.buildActivity(MainActivity.class).create();
        try {
            assertEquals("week",read(second.get(),"page"));
            assertEquals(LocalDate.of(2029,6,17),read(second.get(),"selected"));
            assertEquals(YearMonth.of(2029,6),read(second.get(),"currentMonth"));
        } finally {second.destroy();}
    }
    @Test @Config(sdk=35) public void android15NavigationReachesDay()throws Exception {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try {
            View day=screen(controller.get()).findViewWithTag("nav-day");
            assertNotNull(day);day.performClick();
            assertEquals("day",read(controller.get(),"page"));
            assertNotNull(text(screen(controller.get()),"Eventos de este día"));
        } finally {controller.destroy();}
    }
    @Test public void settingsDoesNotDiscardTheVisibleFutureMonth()throws Exception{
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try{
            MainActivity activity=controller.get();
            write(activity,"selected",LocalDate.of(2026,11,6));
            write(activity,"currentMonth",YearMonth.of(2029,8));write(activity,"page","month");render(activity);
            screen(activity).findViewWithTag("nav-settings").performClick();
            screen(activity).findViewWithTag("nav-month").performClick();
            assertEquals(YearMonth.of(2029,8),read(activity,"currentMonth"));
            screen(activity).findViewWithTag("nav-week").performClick();
            assertEquals(LocalDate.of(2029,8,1),read(activity,"selected"));
        }finally{controller.destroy();}
    }
    @Test public void backFromDayReturnsToTheMonthOfThatDay()throws Exception{
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try{
            MainActivity activity=controller.get();
            write(activity,"page","day");write(activity,"selected",LocalDate.of(2028,1,1));
            write(activity,"currentMonth",YearMonth.of(2027,12));render(activity);
            activity.onBackPressed();
            assertEquals("month",read(activity,"page"));
            assertEquals(YearMonth.of(2028,1),read(activity,"currentMonth"));
        }finally{controller.destroy();}
    }
    @Test public void unsavedAppointmentDraftSurvivesRecreationWithoutInsertingARecord()throws Exception{
        Bundle state=new Bundle();
        var first=Robolectric.buildActivity(MainActivity.class).create();
        LocalDate date=LocalDate.of(2028,6,21);
        try{
            MainActivity activity=first.get();
            Method edit=MainActivity.class.getDeclaredMethod("editEvent",CalStore.Event.class);
            edit.setAccessible(true);edit.invoke(activity,new Object[]{null});
            ((android.widget.EditText)read(activity,"draftTitle")).setText("Consulta pendiente");
            ((android.widget.EditText)read(activity,"draftTime")).setText("11:45");
            ((android.widget.EditText)read(activity,"draftNote")).setText("Llevar documentos");
            write(activity,"draftDate",date);
            first.saveInstanceState(state);
        }finally{first.destroy();}
        var next=Robolectric.buildActivity(MainActivity.class).create(state);
        try{
            MainActivity activity=next.get();
            assertEquals("Consulta pendiente",((android.widget.EditText)read(activity,"draftTitle")).getText().toString());
            assertEquals("11:45",((android.widget.EditText)read(activity,"draftTime")).getText().toString());
            assertEquals("Llevar documentos",((android.widget.EditText)read(activity,"draftNote")).getText().toString());
            assertEquals(date,read(activity,"draftDate"));
            try(CalStore db=new CalStore(activity)){assertTrue(db.events(1,date.toString()).isEmpty());}
        }finally{next.destroy();}
    }
}
