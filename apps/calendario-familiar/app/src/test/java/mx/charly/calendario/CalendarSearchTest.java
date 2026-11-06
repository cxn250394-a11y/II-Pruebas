package mx.charly.calendario;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CalendarSearchTest {
    private Context context;
    private final LocalDate first=LocalDate.of(2026,11,1);
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("calendario-familiar.db");
    }
    private CalStore store(){return new CalStore(context);}
    @Test public void searchesTitleNoteCategoryAndShortLabelInOnlyOneProfile(){
        try(CalStore d=store()){
            long cat=d.saveCategory(1,0,"Laboratorio central","LAB42",Palette.COLORS[0]);
            long mine=d.saveEvent(1,0,"2026-11-01","Consulta familiar","09:30","Llevar carpeta azul",cat);
            long moms=d.saveEvent(2,0,"2026-11-01","Consulta familiar","","",0);
            for(String term:new String[]{"consulta","carpeta azul","laboratorio","lab42"}){
                List<CalStore.Event> found=d.search(1,term);
                assertEquals(1,found.size());assertEquals(mine,found.get(0).id);
            }
            assertEquals(moms,d.search(2,"consulta").get(0).id);
            assertTrue(d.search(2,"carpeta").isEmpty());
        }
    }
    @Test public void wildcardEscapeAndBackslashAreLiteral(){
        try(CalStore d=store()){
            long percent=d.saveEvent(1,0,"2026-11-01","100% listo","","",0);
            long underscore=d.saveEvent(1,0,"2026-11-01","Archivo_A","","",0);
            long slash=d.saveEvent(1,0,"2026-11-01","Ruta C:\\Papeles","","",0);
            long bang=d.saveEvent(1,0,"2026-11-01","Listo!","","",0);
            d.saveEvent(1,0,"2026-11-01","Otro archivo","","",0);
            String[] queries={"%","_","\\","!"};
            long[] ids={percent,underscore,slash,bang};
            for(int i=0;i<queries.length;i++){
                List<CalStore.Event> found=d.search(1,queries[i]);
                assertEquals(1,found.size());assertEquals(ids[i],found.get(0).id);
            }
            assertTrue(d.search(1,"' OR 1=1 --").isEmpty());
        }
    }
    @Test public void emptyQueriesDoNotReturnAllEventsAndResultsAreBoundedAndOrdered(){
        try(CalStore d=store()){
            for(int i=104;i>=0;i--)d.saveEvent(1,0,first.plusDays(i).toString(),"Agenda "+i,"","",0);
            assertTrue(d.search(1,"  ").isEmpty());assertTrue(d.search(1,null).isEmpty());
            List<CalStore.Event> found=d.search(1,"  agenda ");
            assertEquals(100,found.size());
            assertEquals(first.toString(),found.get(0).date);
            assertEquals(first.plusDays(99).toString(),found.get(99).date);
            try{d.search(3,"");fail("Invalid profile accepted");}catch(IllegalArgumentException good){}
        }
    }
    @Test public void sameDaySearchOrderIsAllDayThenTimeThenId(){
        try(CalStore d=store()){
            long late=d.saveEvent(1,0,"2026-11-01","Cita tarde","15:00","",0);
            long early=d.saveEvent(1,0,"2026-11-01","Cita temprano","09:00","",0);
            long all=d.saveEvent(1,0,"2026-11-01","Cita sin hora","","",0);
            long equal=d.saveEvent(1,0,"2026-11-01","Cita otra","09:00","",0);
            List<CalStore.Event> found=d.search(1,"cita");
            assertEquals(Arrays.asList(all,early,equal,late),Arrays.asList(found.get(0).id,found.get(1).id,found.get(2).id,found.get(3).id));
        }
    }
    @Test public void rangeCrossesYearsAndIncludesBothEndsWithProfileIsolation(){
        try(CalStore d=store()){
            d.paint(1,"2026-12-31",Palette.COLORS[0]);d.paint(1,"2027-01-01",Palette.COLORS[1]);
            d.saveEvent(1,0,"2026-12-31","Primera","","",0);
            d.saveEvent(1,0,"2027-01-01","Segunda","","",0);
            d.saveEvent(1,0,"2027-01-02","Fuera","","",0);
            d.saveEvent(2,0,"2027-01-01","Otro perfil","","",0);
            CalStore.MonthData data=d.range(1,LocalDate.of(2026,12,31),LocalDate.of(2027,1,1));
            assertEquals(2,data.backgrounds.size());assertEquals(2,data.events.size());
            assertEquals("Primera",data.events("2026-12-31").get(0).title);
            assertEquals("Segunda",data.events("2027-01-01").get(0).title);
            assertTrue(data.events("2027-01-02").isEmpty());
            try{d.range(1,first.plusDays(1),first);fail("Reversed range accepted");}catch(IllegalArgumentException good){}
        }
    }
    @Test public void batchPaintDeduplicatesAndKeepsOnlyChangedDates(){
        try(CalStore d=store()){
            d.paint(1,first.toString(),Palette.COLORS[0]);
            d.paint(2,first.toString(),Palette.COLORS[2]);
            LinkedHashMap<String,Integer> old=d.paintBatch(1,Arrays.asList(first,first.plusDays(1),first.plusDays(1)),Palette.COLORS[0]);
            assertEquals(1,old.size());assertEquals(Integer.valueOf(Palette.DEFAULT_DAY),old.get(first.plusDays(1).toString()));
            assertEquals(Palette.COLORS[2],d.dayColor(2,first.toString()));
            assertTrue(d.hasPaintUndo(1));assertFalse(d.hasPaintUndo(2));
            assertTrue(d.paintBatch(1,Arrays.asList(first),Palette.COLORS[0]).isEmpty());
            assertTrue(d.undoPaint(1));
            assertEquals(Palette.COLORS[0],d.dayColor(1,first.toString()));
            assertEquals(Palette.DEFAULT_DAY,d.dayColor(1,first.plusDays(1).toString()));
            assertFalse(d.undoPaint(1));
        }
    }
    @Test public void durableUndoSurvivesDatabaseCloseAndReopen(){
        try(CalStore d=store()){d.paintBatch(2,Arrays.asList(first,first.plusDays(1)),Palette.COLORS[3]);}
        try(CalStore d=store()){
            assertTrue(d.hasPaintUndo(2));assertTrue(d.undoPaint(2));
            assertEquals(Palette.DEFAULT_DAY,d.dayColor(2,first.toString()));
            assertEquals(Palette.DEFAULT_DAY,d.dayColor(2,first.plusDays(1).toString()));
            assertFalse(d.hasPaintUndo(2));
        }
    }
    @Test public void staleUndoNeverOverwritesAnyLaterChange(){
        try(CalStore d=store()){
            d.paintBatch(1,Arrays.asList(first,first.plusDays(1)),Palette.COLORS[3]);
            d.paint(1,first.plusDays(1).toString(),Palette.COLORS[4]);
            try{d.undoPaint(1);fail("Stale undo accepted");}catch(IllegalStateException good){}
            assertEquals(Palette.COLORS[3],d.dayColor(1,first.toString()));
            assertEquals(Palette.COLORS[4],d.dayColor(1,first.plusDays(1).toString()));
            assertTrue(d.hasPaintUndo(1));
        }
    }
    @Test public void batchFailureRollsBackDaysAndPreviousUndo(){
        try(CalStore d=store()){
            LocalDate earlier=first.plusDays(9);
            d.paintBatch(1,Arrays.asList(earlier),Palette.COLORS[5]);
            SQLiteDatabase db=d.getWritableDatabase();
            db.execSQL("CREATE TRIGGER fail_batch BEFORE INSERT ON day_colors WHEN NEW.day='2026-11-02' BEGIN SELECT RAISE(ABORT,'fixture'); END");
            try{d.paintBatch(1,Arrays.asList(first,first.plusDays(1)),Palette.COLORS[3]);fail("Fixture did not abort");}catch(RuntimeException good){}
            assertEquals(Palette.DEFAULT_DAY,d.dayColor(1,first.toString()));
            assertEquals(Palette.DEFAULT_DAY,d.dayColor(1,first.plusDays(1).toString()));
            db.execSQL("DROP TRIGGER fail_batch");
            assertTrue(d.undoPaint(1));assertEquals(Palette.DEFAULT_DAY,d.dayColor(1,earlier.toString()));
        }
    }
    @Test public void allBatchValidationHappensBeforeAnyWrite(){
        try(CalStore d=store()){
            try{d.paintBatch(1,Arrays.asList(first,null),Palette.COLORS[3]);fail("Null date accepted");}catch(IllegalArgumentException good){}
            ArrayList<LocalDate> tooMany=new ArrayList<>();
            for(int i=0;i<367;i++)tooMany.add(first.plusDays(i));
            try{d.paintBatch(1,tooMany,Palette.COLORS[3]);fail("Oversized batch accepted");}catch(IllegalArgumentException good){}
            LinkedHashMap<String,Integer> bad=new LinkedHashMap<>();
            bad.put(first.toString(),Palette.COLORS[0]);bad.put("invalid",Palette.COLORS[1]);
            try{d.restorePaint(1,bad);fail("Bad restore accepted");}catch(java.time.format.DateTimeParseException good){}
            assertEquals(Palette.DEFAULT_DAY,d.dayColor(1,first.toString()));assertFalse(d.hasPaintUndo(1));
        }
    }
    @Test public void equivalentVersionOneSchemaUpgradesWithoutLosingEventsColorsOrFonts(){
        // The existing v1 schema is exactly v2 minus paint_undo; make that fixture on disk.
        try(CalStore d=store()){
            d.saveEvent(2,0,first.toString(),"Conservar","09:00","Nota anterior",d.categories(2).get(0).id);
            d.paint(2,first.toString(),Palette.COLORS[1]);d.setFontSize(1,2);
            d.getWritableDatabase().execSQL("DROP TABLE paint_undo");
            d.getWritableDatabase().setVersion(1);
        }
        try(CalStore d=store()){
            assertEquals(2,d.getReadableDatabase().getVersion());
            assertEquals("Nota anterior",d.events(2,first.toString()).get(0).note);
            assertEquals(Palette.COLORS[1],d.dayColor(2,first.toString()));assertEquals(2,d.fontSize(1));
            assertEquals(2,d.categories(2).size());
            d.paintBatch(2,Arrays.asList(first),Palette.COLORS[2]);assertTrue(d.undoPaint(2));
            assertEquals(Palette.COLORS[1],d.dayColor(2,first.toString()));
        }
    }
}
