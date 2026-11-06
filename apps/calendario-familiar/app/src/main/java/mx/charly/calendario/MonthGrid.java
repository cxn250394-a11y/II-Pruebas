package mx.charly.calendario;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact visual month with one native accessibility node for every actual date. */
public final class MonthGrid extends View {
    public interface OnDateTap {void tap(LocalDate date);}
    private static final int NONE=-1;
    private static final Typeface NORMAL=Typeface.create("sans-serif",Typeface.NORMAL);
    private static final Typeface BOLD=Typeface.create("sans-serif-medium",Typeface.BOLD);
    private static final DateTimeFormatter SPOKEN=DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu",new Locale("es","MX"));
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint ink=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rounded=new RectF();
    private final YearMonth month;
    private final CalStore.MonthData data;
    private final int fontSize,leading,rows,touchSlop;
    private final OnDateTap listener;
    private final AccessibilityNodeProvider provider=new DayProvider();
    private float cellWidth,cellHeight,startX,startY;
    private int selectedDay=1,accessibilityDay=NONE,hoveredDay=NONE,pressedDay=NONE;
    private boolean cancelled,painting;
    public MonthGrid(Context context,YearMonth month,CalStore.MonthData data,int fontSize,OnDateTap callback){
        super(context);this.month=month;this.data=data;this.fontSize=fontSize;listener=callback;
        leading=month.atDay(1).getDayOfWeek().getValue()%7;
        rows=(leading+month.lengthOfMonth()+6)/7;
        touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
        if(YearMonth.now().equals(month))selectedDay=LocalDate.now().getDayOfMonth();
        setClickable(true);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Calendario mensual. Toca una fecha para ver sus eventos");
    }
    public void setSelectedDate(LocalDate date){
        int day=date!=null&&YearMonth.from(date).equals(month)?date.getDayOfMonth():1;
        if(selectedDay!=day){selectedDay=day;invalidate();}
    }
    public LocalDate getSelectedDate(){return month.atDay(selectedDay);}
    public void setPainting(boolean value){painting=value;notifyDataChanged();}
    public void notifyDataChanged(){invalidate();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private float fontScale(){return getResources().getConfiguration().fontScale;}
    private float unit(float n){return dp(n)*Math.max(1f,fontScale());}
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        int width=MeasureSpec.getSize(widthMeasureSpec);
        cellWidth=width/7f;
        cellHeight=unit(fontSize==2?154:fontSize==1?144:135);
        setMeasuredDimension(width,Math.round(rows*cellHeight));
    }
    private void fill(Canvas c,int color,float l,float t,float r,float b,float radius){
        p.setColor(color);p.setStyle(Paint.Style.FILL);rounded.set(l,t,r,b);
        c.drawRoundRect(rounded,radius,radius,p);
    }
    private void stroke(Canvas c,int color,float l,float t,float r,float b,float width){
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);
        rounded.set(l,t,r,b);c.drawRoundRect(rounded,dp(8),dp(8),p);p.setStyle(Paint.Style.FILL);
    }
    private void text(Canvas canvas,String value,float x,float baseline,float size,int color,boolean bold){
        ink.setColor(color);ink.setTextSize(dp(size)*fontScale());ink.setTypeface(bold?BOLD:NORMAL);
        canvas.drawText(value,x,baseline,ink);
    }
    private String fit(String s,float width,float size,boolean bold){
        ink.setTextSize(dp(size)*fontScale());ink.setTypeface(bold?BOLD:NORMAL);
        return TextUtils.ellipsize(s,ink,Math.max(1,width),TextUtils.TruncateAt.END).toString();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);LocalDate today=LocalDate.now();
        for(int day=1;day<=month.lengthOfMonth();day++){
            int slot=leading+day-1;
            float l=(slot%7)*cellWidth+dp(1.5f),top=(slot/7)*cellHeight+dp(1.5f);
            float r=l+cellWidth-dp(3),bot=top+cellHeight-dp(3);
            LocalDate date=month.atDay(day);int color=data.color(date.toString());
            fill(canvas,color,l,top,r,bot,dp(8));
            if(today.equals(date))stroke(canvas,0xFF5C4A95,l+dp(1),top+dp(1),r-dp(1),bot-dp(1),dp(2.8f));
            if(day==selectedDay||day==accessibilityDay)
                stroke(canvas,Palette.text(color),l+dp(4),top+dp(4),r-dp(4),bot-dp(4),dp(1));
            int dayInk=Palette.text(color);
            float dateSize=fontSize==2?20:fontSize==1?19:17;
            text(canvas,fit(String.valueOf(day),r-l-dp(10),dateSize,true),l+dp(6),top+unit(23),dateSize,dayInk,true);
            List<CalStore.Event> items=data.events(date.toString());float y=top+unit(32);
            for(int j=0;j<Math.min(items.size(),2);j++){
                CalStore.Event e=items.get(j);
                float chipHeight=unit(fontSize==2?46:fontSize==1?42:39),left=l+dp(2),right=r-dp(2);
                fill(canvas,e.color,left,y,right,y+chipHeight,dp(6));int fg=Palette.text(e.color);
                float txtSize=fontSize==2?12:fontSize==1?11:10.5f;
                String first=e.shortName.isEmpty()?e.title:e.shortName;
                text(canvas,fit(first,right-left-dp(6),txtSize,true),left+dp(3),y+unit(16),txtSize,fg,true);
                float bodySize=fontSize==2?11.5f:fontSize==1?10.5f:10;
                String second=e.shortName.isEmpty()?e.time:e.title;
                text(canvas,fit(second,right-left-dp(6),bodySize,false),left+dp(3),y+unit(32),bodySize,fg,false);
                y+=chipHeight+unit(3);
            }
            if(items.size()>2)text(canvas,fit("+"+(items.size()-2)+" más",r-l-dp(8),11,true),
                l+dp(4),Math.min(bot-dp(6),y+unit(12)),11,dayInk,true);
        }
    }
    private int dayAt(float x,float y){
        if(cellWidth<=0||cellHeight<=0||x<0||y<0||x>=getWidth()||y>=getHeight())return NONE;
        int day=(int)(y/cellHeight)*7+(int)(x/cellWidth)-leading+1;
        return valid(day)?day:NONE;
    }
    private boolean valid(int day){return day>=1&&day<=month.lengthOfMonth();}
    private Rect bounds(int day){
        int slot=leading+day-1;
        return new Rect(Math.round((slot%7)*cellWidth),Math.round((slot/7)*cellHeight),
            Math.round((slot%7+1)*cellWidth),Math.round((slot/7+1)*cellHeight));
    }
    private boolean activate(int day){
        if(!isEnabled()||!valid(day)||listener==null)return false;
        selectedDay=day;invalidate();super.performClick();
        event(day,AccessibilityEvent.TYPE_VIEW_CLICKED);listener.tap(month.atDay(day));return true;
    }
    @Override public boolean performClick(){return activate(selectedDay);}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!isEnabled())return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                pressedDay=dayAt(e.getX(),e.getY());cancelled=false;startX=e.getX();startY=e.getY();
                setPressed(pressedDay!=NONE);return pressedDay!=NONE;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(e.getX()-startX)>touchSlop||Math.abs(e.getY()-startY)>touchSlop){cancelled=true;setPressed(false);}
                return pressedDay!=NONE;
            case MotionEvent.ACTION_CANCEL:
                cancelled=true;pressedDay=NONE;setPressed(false);return true;
            case MotionEvent.ACTION_UP:
                int day=dayAt(e.getX(),e.getY()),start=pressedDay;pressedDay=NONE;setPressed(false);
                if(!cancelled&&day==start&&valid(day)&&Math.abs(e.getX()-startX)<=touchSlop&&Math.abs(e.getY()-startY)<=touchSlop)activate(day);
                return start!=NONE;
            default:return super.onTouchEvent(e);
        }
    }
    private void focusDay(int day){
        selectedDay=day;invalidate();requestRectangleOnScreen(bounds(day),false);
        event(day,AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }
    @Override public boolean onKeyDown(int keyCode,KeyEvent e){
        int next=selectedDay;
        switch(keyCode){
            case KeyEvent.KEYCODE_DPAD_LEFT:next--;break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:next++;break;
            case KeyEvent.KEYCODE_DPAD_UP:next-=7;break;
            case KeyEvent.KEYCODE_DPAD_DOWN:next+=7;break;
            case KeyEvent.KEYCODE_MOVE_HOME:next=1;break;
            case KeyEvent.KEYCODE_MOVE_END:next=month.lengthOfMonth();break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:return e.getRepeatCount()==0&&performClick();
            default:return super.onKeyDown(keyCode,e);
        }
        if(!valid(next))return super.onKeyDown(keyCode,e);
        focusDay(next);return true;
    }
    @Override protected void onFocusChanged(boolean gain,int direction,Rect previous){
        super.onFocusChanged(gain,direction,previous);invalidate();
        if(gain)event(selectedDay,AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }
    private String description(int day){
        LocalDate date=month.atDay(day);StringBuilder text=new StringBuilder(date.format(SPOKEN));
        if(date.equals(LocalDate.now()))text.append(". Hoy");
        int color=data.color(date.toString());String name="Sin color";
        for(int i=0;i<Palette.COLORS.length;i++)if(color==Palette.COLORS[i]){name=Palette.NAMES[i];break;}
        text.append(". ").append(name);
        List<CalStore.Event> events=data.events(date.toString());
        if(events.isEmpty())text.append(". Sin eventos");
        for(CalStore.Event e:events){
            text.append(". ").append(WeekRules.eventLine(e));
            if(!e.note.isEmpty())text.append(". Nota: ").append(e.note);
        }
        return text.append(painting?". Pintar día.":". Abrir día.").toString();
    }
    private void event(int day,int type){
        AccessibilityManager manager=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(!valid(day)||getParent()==null||manager==null||!manager.isEnabled())return;
        AccessibilityEvent event=AccessibilityEvent.obtain(type);
        event.setPackageName(getContext().getPackageName());event.setClassName("android.widget.Button");
        event.setSource(this,day);event.setEnabled(isEnabled());event.getText().add(description(day));
        getParent().requestSendAccessibilityEvent(this,event);
    }
    @Override public boolean dispatchHoverEvent(MotionEvent e){
        AccessibilityManager manager=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(manager!=null&&manager.isEnabled()&&manager.isTouchExplorationEnabled()){
            int day=e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?NONE:dayAt(e.getX(),e.getY());
            if(day!=hoveredDay){int old=hoveredDay;hoveredDay=day;event(day,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);event(old,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);}
            if(day!=NONE||e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT)return true;
        }
        return super.dispatchHoverEvent(e);
    }
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return provider;}
    private final class DayProvider extends AccessibilityNodeProvider {
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int day){
            if(day==HOST_VIEW_ID){
                AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain(MonthGrid.this);
                onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.GridView");
                info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(rows,7,false));
                for(int i=1;i<=month.lengthOfMonth();i++)info.addChild(MonthGrid.this,i);
                return info;
            }
            if(!valid(day))return null;
            AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain();
            info.setSource(MonthGrid.this,day);info.setParent(MonthGrid.this);
            info.setPackageName(getContext().getPackageName());info.setClassName("android.widget.Button");
            info.setContentDescription(description(day));info.setEnabled(isEnabled());info.setClickable(true);info.setFocusable(true);
            info.setSelected(selectedDay==day);info.setFocused(isFocused()&&selectedDay==day);info.setAccessibilityFocused(accessibilityDay==day);
            Rect local=bounds(day),screen=new Rect(local),visible=new Rect();int[] location=new int[2];
            getLocationOnScreen(location);screen.offset(location[0],location[1]);
            info.setBoundsInParent(local);info.setBoundsInScreen(screen);
            info.setVisibleToUser(isShown()&&getLocalVisibleRect(visible)&&Rect.intersects(visible,local));
            int slot=leading+day-1;
            info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(slot/7,1,slot%7,1,false,selectedDay==day));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,painting?"Pintar día":"Abrir día"));
            info.addAction(selectedDay==day&&isFocused()?AccessibilityNodeInfo.ACTION_CLEAR_FOCUS:AccessibilityNodeInfo.ACTION_FOCUS);
            info.addAction(accessibilityDay==day?AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN);
            return info;
        }
        @Override public boolean performAction(int day,int action,Bundle arguments){
            if(day==HOST_VIEW_ID)return MonthGrid.this.performAccessibilityAction(action,arguments);
            if(!valid(day)||!isEnabled())return false;
            if(action==AccessibilityNodeInfo.ACTION_CLICK)return activate(day);
            if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){
                if(accessibilityDay==day)return false;
                int old=accessibilityDay;accessibilityDay=day;invalidate();
                event(old,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                requestRectangleOnScreen(bounds(day),false);event(day,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);return true;
            }
            if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS&&accessibilityDay==day){
                accessibilityDay=NONE;invalidate();event(day,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);return true;
            }
            if(action==AccessibilityNodeInfo.ACTION_FOCUS){
                if(!requestFocus())return false;
                focusDay(day);return true;
            }
            if(action==AccessibilityNodeInfo.ACTION_CLEAR_FOCUS&&selectedDay==day){clearFocus();return true;}
            if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId()){
                requestRectangleOnScreen(bounds(day),false);return true;
            }
            return false;
        }
        @Override public AccessibilityNodeInfo findFocus(int focus){
            int day=focus==AccessibilityNodeInfo.FOCUS_ACCESSIBILITY?accessibilityDay:isFocused()?selectedDay:NONE;
            return valid(day)?createAccessibilityNodeInfo(day):null;
        }
        @Override public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text,int root){
            ArrayList<AccessibilityNodeInfo> result=new ArrayList<>();
            if(text==null)return result;
            String query=text.toLowerCase(new Locale("es","MX"));
            for(int day=1;day<=month.lengthOfMonth();day++)
                if((root==HOST_VIEW_ID||root==day)&&description(day).toLowerCase(new Locale("es","MX")).contains(query))
                    result.add(createAccessibilityNodeInfo(day));
            return result;
        }
    }
}
