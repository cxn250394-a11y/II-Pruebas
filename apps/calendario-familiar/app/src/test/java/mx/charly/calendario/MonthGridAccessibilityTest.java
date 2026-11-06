package mx.charly.calendario;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public final class MonthGridAccessibilityTest {
    private MonthGrid grid(YearMonth month,CalStore.MonthData data,List<LocalDate> opened){
        MonthGrid view=new MonthGrid(RuntimeEnvironment.getApplication(),month,data,2,opened::add);
        view.measure(View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());
        return view;
    }
    private Rect bounds(MonthGrid grid,int day){
        Rect rect=new Rect();
        grid.getAccessibilityNodeProvider().createAccessibilityNodeInfo(day).getBoundsInParent(rect);
        return rect;
    }
    private void touch(MonthGrid grid,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(0,1,action,x,y,0);
        try{grid.onTouchEvent(event);}finally{event.recycle();}
    }
    @Test public void virtualTreeHasEveryRealDateIncludingLeapDay(){
        MonthGrid grid=grid(YearMonth.of(2028,2),new CalStore.MonthData(),new ArrayList<>());
        AccessibilityNodeProvider provider=grid.getAccessibilityNodeProvider();
        AccessibilityNodeInfo host=provider.createAccessibilityNodeInfo(AccessibilityNodeProvider.HOST_VIEW_ID);
        assertEquals(29,host.getChildCount());
        assertEquals(7,host.getCollectionInfo().getColumnCount());
        assertNotNull(provider.createAccessibilityNodeInfo(29));
        assertNull(provider.createAccessibilityNodeInfo(0));assertNull(provider.createAccessibilityNodeInfo(30));
        assertFalse(provider.performAction(30,AccessibilityNodeInfo.ACTION_CLICK,null));
    }
    @Test public void descriptionIncludesFullEventNoteDateAndBackgroundColor(){
        CalStore.MonthData data=new CalStore.MonthData();
        data.backgrounds.put("2027-01-14",Palette.COLORS[0]);
        data.events.put("2027-01-14",List.of(new CalStore.Event(1,1,"2027-01-14",
            "Consulta de seguimiento completa","09:30","Llevar estudios",Palette.COLORS[1],"Hospital Central","Hospital")));
        MonthGrid grid=grid(YearMonth.of(2027,1),data,new ArrayList<>());
        String description=grid.getAccessibilityNodeProvider().createAccessibilityNodeInfo(14).getContentDescription().toString();
        assertTrue(description.contains("14 de enero de 2027"));
        assertTrue(description.contains("Durazno suave"));
        assertTrue(description.contains("09:30"));assertTrue(description.contains("Hospital Central"));
        assertTrue(description.contains("Consulta de seguimiento completa"));assertTrue(description.contains("Nota: Llevar estudios"));
        assertTrue(description.endsWith("Abrir día."));
    }
    @Test public void virtualClickSelectsExactlyRequestedDateAndPaintingChangesAction(){
        List<LocalDate> opened=new ArrayList<>();
        MonthGrid grid=grid(YearMonth.of(2027,1),new CalStore.MonthData(),opened);
        grid.setSelectedDate(LocalDate.of(2027,1,6));
        AccessibilityNodeProvider provider=grid.getAccessibilityNodeProvider();
        assertTrue(provider.createAccessibilityNodeInfo(6).isSelected());
        grid.setPainting(true);
        assertTrue(provider.createAccessibilityNodeInfo(14).getContentDescription().toString().endsWith("Pintar día."));
        assertTrue(provider.performAction(14,AccessibilityNodeInfo.ACTION_CLICK,null));
        assertEquals(List.of(LocalDate.of(2027,1,14)),opened);
        assertEquals(LocalDate.of(2027,1,14),grid.getSelectedDate());
    }
    @Test public void accessibilityFocusMovesAndClearsWithoutOpeningDate(){
        List<LocalDate> opened=new ArrayList<>();
        MonthGrid grid=grid(YearMonth.of(2027,1),new CalStore.MonthData(),opened);
        AccessibilityNodeProvider provider=grid.getAccessibilityNodeProvider();
        assertTrue(provider.performAction(8,AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null));
        assertTrue(provider.createAccessibilityNodeInfo(8).isAccessibilityFocused());
        assertTrue(provider.performAction(9,AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null));
        assertFalse(provider.createAccessibilityNodeInfo(8).isAccessibilityFocused());
        assertNotNull(provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY));
        assertTrue(provider.performAction(9,AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,null));
        assertNull(provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY));assertTrue(opened.isEmpty());
    }
    @Test public void keyboardMovesByDayAndWeekThenEnterOpensSelection(){
        List<LocalDate> opened=new ArrayList<>();
        MonthGrid grid=grid(YearMonth.of(2027,1),new CalStore.MonthData(),opened);
        grid.setSelectedDate(LocalDate.of(2027,1,10));
        assertTrue(grid.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN)));
        assertTrue(grid.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertEquals(LocalDate.of(2027,1,18),grid.getSelectedDate());
        assertTrue(grid.onKeyDown(KeyEvent.KEYCODE_ENTER,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER)));
        assertEquals(List.of(LocalDate.of(2027,1,18)),opened);
    }
    @Test public void cancelledAndScrolledGesturesDoNotOpenOrPaintDate(){
        List<LocalDate> opened=new ArrayList<>();
        MonthGrid grid=grid(YearMonth.of(2027,1),new CalStore.MonthData(),opened);
        Rect cell=bounds(grid,14);float x=cell.exactCenterX(),y=cell.exactCenterY();
        touch(grid,MotionEvent.ACTION_DOWN,x,y);touch(grid,MotionEvent.ACTION_CANCEL,x,y);touch(grid,MotionEvent.ACTION_UP,x,y);
        touch(grid,MotionEvent.ACTION_DOWN,x,y);touch(grid,MotionEvent.ACTION_MOVE,x,y+90);touch(grid,MotionEvent.ACTION_UP,x,y);
        touch(grid,MotionEvent.ACTION_DOWN,-10,y);touch(grid,MotionEvent.ACTION_UP,-10,y);
        assertTrue(opened.isEmpty());
        touch(grid,MotionEvent.ACTION_DOWN,x,y);touch(grid,MotionEvent.ACTION_UP,x,y);
        assertEquals(List.of(LocalDate.of(2027,1,14)),opened);
    }
    @Test public void todayIsAnnouncedAndTextSearchFindsIndividualDates(){
        LocalDate today=LocalDate.now();
        MonthGrid grid=grid(YearMonth.from(today),new CalStore.MonthData(),new ArrayList<>());
        AccessibilityNodeProvider provider=grid.getAccessibilityNodeProvider();
        assertTrue(provider.createAccessibilityNodeInfo(today.getDayOfMonth()).getContentDescription().toString().contains(". Hoy"));
        assertEquals(1,provider.findAccessibilityNodeInfosByText(". Hoy",AccessibilityNodeProvider.HOST_VIEW_ID).size());
    }
    @Test public void largerSystemFontKeepsSevenColumnsAndIncreasesRowHeight(){
        Context context=RuntimeEnvironment.getApplication();
        Configuration original=new Configuration(context.getResources().getConfiguration());
        try{
            Configuration config=new Configuration(original);config.fontScale=1f;
            context.getResources().updateConfiguration(config,context.getResources().getDisplayMetrics());
            MonthGrid normal=grid(YearMonth.of(2027,1),new CalStore.MonthData(),new ArrayList<>());
            config=new Configuration(original);config.fontScale=2f;
            context.getResources().updateConfiguration(config,context.getResources().getDisplayMetrics());
            MonthGrid large=grid(YearMonth.of(2027,1),new CalStore.MonthData(),new ArrayList<>());
            assertEquals(normal.getMeasuredHeight()*2d,large.getMeasuredHeight(),1d);
            assertEquals(7,large.getAccessibilityNodeProvider().createAccessibilityNodeInfo(-1).getCollectionInfo().getColumnCount());
        }finally{context.getResources().updateConfiguration(original,context.getResources().getDisplayMetrics());}
    }
}
