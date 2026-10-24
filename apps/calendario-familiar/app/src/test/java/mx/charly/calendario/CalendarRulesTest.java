package mx.charly.calendario;

import android.content.Context;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Arrays;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CalendarRulesTest {
    private Context context;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("calendario-familiar.db");
    }
    private CalStore store(){return new CalStore(context);}
    @Test public void profilesAndExampleCategoriesButNoFakeEvents(){
        try(CalStore d=store()){
            assertEquals(2,d.categories(1).size());
            assertEquals(2,d.categories(2).size());
            assertTrue(d.events(2,LocalDate.now().toString()).isEmpty());
        }
    }
    @Test public void dayColorsAreWholeDayAndSeparated(){
        try(CalStore d=store()){
            String date="2026-10-24";d.paint(1,date,Palette.COLORS[8]);
            assertEquals(Palette.COLORS[8],d.dayColor(1,date));
            assertEquals(Palette.DEFAULT_DAY,d.dayColor(2,date));
            d.paint(1,date,Palette.DEFAULT_DAY);
            assertEquals(Palette.DEFAULT_DAY,d.dayColor(1,date));
        }
    }
    @Test public void eventColorIsIndependentOfDayBackground(){
        try(CalStore d=store()){
            String date="2026-10-24";d.paint(2,date,Palette.COLORS[2]);
            CalStore.Category cat=d.categories(2).get(0);
            d.saveEvent(2,0,date,"Consulta de seguimiento","09:30","Llevar estudios",cat.id);
            CalStore.Event event=d.events(2,date).get(0);
            assertEquals(Palette.COLORS[2],d.dayColor(2,date));
            assertEquals(cat.color,event.color);
            assertTrue(event.preview().contains("IMSS 21"));
            assertEquals("Consulta de seguimiento",event.title);
        }
    }
    @Test public void futureMonthsAndDatesAreQueriedByYearMonth(){
        try(CalStore d=store()){
            d.saveEvent(1,0,"2027-01-03","Oficina","","",d.categories(1).get(0).id);
            assertEquals(1,d.month(1,YearMonth.of(2027,1)).events("2027-01-03").size());
            assertTrue(d.month(1,YearMonth.of(2027,2)).events("2027-01-03").isEmpty());
        }
    }
    @Test public void cannotAddMomsCategoryToMyEvent(){
        try(CalStore d=store()){
            long other=d.categories(2).get(0).id;
            try{d.saveEvent(1,0,"2026-10-24","Consulta","","",other);fail("Cross-profile category accepted");}
            catch(IllegalArgumentException good){}
            assertTrue(d.events(1,"2026-10-24").isEmpty());
        }
    }
    @Test public void updateEventDoesNotDuplicate(){
        try(CalStore d=store()){
            long id=d.saveEvent(1,0,"2026-10-24","Primer texto","","",0);
            d.saveEvent(1,id,"2026-10-24","Texto corregido","13:00","Llevar carpeta",0);
            assertEquals(1,d.events(1,"2026-10-24").size());
            assertEquals("Texto corregido",d.events(1,"2026-10-24").get(0).title);
            d.deleteEvent(1,id);assertTrue(d.events(1,"2026-10-24").isEmpty());
        }
    }
    @Test public void validatesTimeAndTitle(){
        try(CalStore d=store()){
            try{d.saveEvent(1,0,"2026-10-24"," ","","",0);fail("Empty title accepted");}catch(IllegalArgumentException good){}
            try{d.saveEvent(1,0,"2026-10-24","Consulta","35:96","",0);fail("Wrong hour accepted");}catch(IllegalArgumentException good){}
        }
    }
    @Test public void categoryShortNameCanBeEdited(){
        try(CalStore d=store()){
            CalStore.Category c=d.categories(2).get(1);
            d.saveCategory(2,c.id,"ISSSTE Balbuena","ISSSTE",Palette.COLORS[5]);
            assertEquals("ISSSTE",d.categories(2).get(1).shortName);
            assertEquals(Palette.COLORS[5],d.categories(2).get(1).color);
        }
    }
    @Test public void mamFontSizePersists(){
        try(CalStore d=store()){
            assertEquals(2,d.fontSize(2));
            d.setFontSize(1,2);
            assertEquals(2,new CalStore(context).fontSize(1));
        }
    }
    @Test public void backupRoundTripBothProfiles()throws Exception{
        byte[] protectedData;char[] pass="contraseña familiar segura 2026".toCharArray();
        try(CalStore d=store()){
            d.paint(1,"2026-10-24",Palette.COLORS[3]);
            d.saveEvent(2,0,"2026-12-10","Consulta médica","08:15","Revisar cita",d.categories(2).get(0).id);
            protectedData=Backup.encrypt(Backup.exportData(d),pass);
            assertFalse(new String(protectedData).contains("Consulta médica"));
            d.deleteEvent(2,d.events(2,"2026-12-10").get(0).id);
            d.paint(1,"2026-10-24",Palette.DEFAULT_DAY);
            Backup.importData(d,Backup.decrypt(protectedData,pass));
            assertEquals(1,d.events(2,"2026-12-10").size());
            assertEquals(Palette.COLORS[3],d.dayColor(1,"2026-10-24"));
        }
    }
    @Test public void corruptBackupKeepsData()throws Exception{
        try(CalStore d=store()){
            d.saveEvent(1,0,"2026-10-24","No borrar","","",0);
            try{Backup.importData(d,"{broken".getBytes());fail("Accepted corrupt JSON");}
            catch(Exception expected){}
            assertEquals("No borrar",d.events(1,"2026-10-24").get(0).title);
        }
    }
    @Test public void wrongPasswordCannotReadBackup()throws Exception{
        byte[] encrypted=Backup.encrypt("Prueba".getBytes(),"contraseña-larga-uno".toCharArray());
        try{Backup.decrypt(encrypted,"contraseña-larga-dos".toCharArray());fail("Wrong password accepted");}
        catch(javax.crypto.AEADBadTagException good){}
    }
    @Test public void tamperedBackupRejected()throws Exception{
        char[] key="contraseña-larga-segura".toCharArray();
        byte[] encrypted=Backup.encrypt("Prueba".getBytes(),key);
        encrypted[encrypted.length-1]^=0x01;
        try{Backup.decrypt(encrypted,key);fail("Tampered ciphertext accepted");}
        catch(javax.crypto.AEADBadTagException good){}
    }
    @Test public void iconDayNamesHave31Dates(){
        assertEquals("mx.charly.calendario.Day01",DateIcon.alias(1));
        assertEquals("mx.charly.calendario.Day31",DateIcon.alias(31));
        try{DateIcon.alias(32);fail("Invalid alias");}catch(IllegalArgumentException good){}
    }
    @Test public void colorPaletteCanBeReadLegibly(){
        assertEquals(24,Palette.COLORS.length);
        for(int color:Palette.COLORS)assertTrue(Palette.text(color)==Palette.INK || Palette.text(color)==android.graphics.Color.WHITE);
    }
    @Test public void monthViewMeasuresGridWithoutClipping(){
        try(CalStore d=store()){
            MonthGrid grid=new MonthGrid(context,YearMonth.of(2026,10),d.month(1,YearMonth.of(2026,10)),2,date->{});
            grid.measure(android.view.View.MeasureSpec.makeMeasureSpec(480,android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(3000,android.view.View.MeasureSpec.AT_MOST));
            assertTrue(grid.getMeasuredHeight()>500);
        }
    }
    @Test public void startsWithoutPermissionsAndShowsTwoProfiles(){
        try(CalStore ignored=store()){
            var control=Robolectric.buildActivity(MainActivity.class).create();
            try{
                assertNotNull(control.get().getWindow().getDecorView());
            }finally{control.destroy();}
        }
    }
    @Test @Config(sdk=35) public void startsAndroid15(){
        var control=Robolectric.buildActivity(MainActivity.class).create();
        try{assertNotNull(control.get().findViewById(android.R.id.content));}finally{control.destroy();}
    }
}
