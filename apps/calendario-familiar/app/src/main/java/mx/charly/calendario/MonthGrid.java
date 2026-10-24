package mx.charly.calendario;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class MonthGrid extends View {
    public interface OnDateTap {void tap(LocalDate date);}
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint ink=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private YearMonth month;
    private CalStore.MonthData data;
    private int fontSize=1;
    private OnDateTap listener;
    private int leading,rows;
    private float cellWidth,cellHeight;
    private float startX,startY;
    public MonthGrid(Context context,YearMonth month,CalStore.MonthData data,int fontSize,OnDateTap callback){
        super(context);this.month=month;this.data=data;this.fontSize=fontSize;this.listener=callback;
        setClickable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Calendario mensual. Toca una fecha para ver sus eventos");
        recalc();
    }
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private void recalc(){
        leading=month.atDay(1).getDayOfWeek().getValue()%7;
        rows=(leading+month.lengthOfMonth()+6)/7;
        cellHeight=dp(fontSize==2?154:fontSize==1?144:135);
    }
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        int width=MeasureSpec.getSize(widthMeasureSpec);
        cellWidth=width/7f;
        setMeasuredDimension(width,Math.round(rows*cellHeight));
    }
    private void fill(Canvas c,int color,float l,float t,float r,float b,float radius){
        p.setColor(color);p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(l,t,r,b),radius,radius,p);
    }
    private void stroke(Canvas c,int color,float l,float t,float r,float b,float width){
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);
        c.drawRoundRect(new RectF(l,t,r,b),dp(9),dp(9),p);p.setStyle(Paint.Style.FILL);
    }
    private void text(Canvas canvas,String value,float x,float baseline,float size,int color,boolean bold){
        ink.setColor(color);ink.setTextSize(dp(size));ink.setTypeface(bold?android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD):android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));
        canvas.drawText(value,x,baseline,ink);
    }
    private String fit(String s,float width,float size,boolean bold){
        ink.setTextSize(dp(size));ink.setTypeface(android.graphics.Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));
        return TextUtils.ellipsize(s,ink,Math.max(1,width),TextUtils.TruncateAt.END).toString();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        LocalDate today=LocalDate.now();
        for(int day=1;day<=month.lengthOfMonth();day++){
            int slot=leading+day-1;
            float l=(slot%7)*cellWidth+dp(1.5f),top=(slot/7)*cellHeight+dp(1.5f);
            float r=l+cellWidth-dp(3),bot=top+cellHeight-dp(3);
            LocalDate date=month.atDay(day);
            int color=data.color(date.toString());
            fill(canvas,color,l,top,r,bot,dp(8));
            boolean current=today.equals(date);
            if(current)stroke(canvas,0xFF5C4A95,l+dp(1),top+dp(1),r-dp(1),bot-dp(1),dp(2.8f));
            int dayInk=Palette.text(color);
            float dateSize=fontSize==2?20:fontSize==1?19:17;
            text(canvas,String.valueOf(day),l+dp(6),top+dp(23),dateSize,dayInk,true);
            List<CalStore.Event> items=data.events(date.toString());
            float y=top+dp(32);
            int max=Math.min(items.size(),2);
            for(int j=0;j<max;j++){
                CalStore.Event e=items.get(j);
                float chipHeight=dp(fontSize==2?46:fontSize==1?42:39);
                float left=l+dp(2),right=r-dp(2);
                fill(canvas,e.color,left,y,right,y+chipHeight,dp(6));
                int fg=Palette.text(e.color);
                float txtSize=fontSize==2?12:fontSize==1?11:10.5f;
                String shortTitle=e.shortName.isEmpty()?e.title:e.shortName;
                text(canvas,fit(shortTitle,right-left-dp(6),txtSize,true),left+dp(3),y+dp(16),txtSize,fg,true);
                float bodySize=fontSize==2?11.5f:fontSize==1?10.5f:10;
                if(!e.shortName.isEmpty())
                    text(canvas,fit(e.title,right-left-dp(6),bodySize,false),left+dp(3),y+dp(32),bodySize,fg,false);
                y+=chipHeight+dp(3);
            }
            if(items.size()>2)text(canvas,"+"+(items.size()-2)+" más",l+dp(4),Math.min(bot-dp(6),y+dp(12)),fontSize==2?12:11,dayInk,true);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){
            if(Math.abs(e.getX()-startX)>dp(18)||Math.abs(e.getY()-startY)>dp(18))return true;
            cellWidth=getWidth()/7f;
            int col=Math.min(6,Math.max(0,(int)(e.getX()/cellWidth)));
            int row=Math.max(0,(int)(e.getY()/cellHeight));
            int day=row*7+col-leading+1;
            if(day>=1 && day<=month.lengthOfMonth() && listener!=null){
                performClick();listener.tap(month.atDay(day));return true;
            }
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
