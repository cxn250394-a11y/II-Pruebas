package mx.charly.calendario;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputFilter;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG=0xFFF8F7FA,INK=0xFF243047,PURPLE=0xFF7260A8,SOFT=0xFFEDE9F4,BORDER=0xFFE7E3ED,WHITE=Color.WHITE;
    private CalStore store;
    private int profile=1,brush=Palette.COLORS[0];
    private boolean painting=false;
    private YearMonth currentMonth=YearMonth.now();
    private LocalDate selected=LocalDate.now();
    private String page="month";
    private LinearLayout root,body;
    private int readingSize=1;
    private String searchQuery="";
    private Bundle eventDraft;
    private AlertDialog eventDialog;
    private EditText draftTitle,draftTime,draftNote;
    private Spinner draftCategory;
    private LocalDate draftDate;
    private long draftId;
    private int draftProfile;

    private char[] backupPassword;
    private static final int EXPORT=101,IMPORT=102;
    private final Locale locale=new Locale("es","MX");
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        store=new CalStore(this);
        profile=getPreferences(MODE_PRIVATE).getInt("active_profile",1);
        if(profile!=1 && profile!=2)profile=1;
        loadNavigation();
        if(state!=null){
            profile=state.getInt("profile",profile);
            page=state.getString("page","month");
            currentMonth=YearMonth.parse(state.getString("month",YearMonth.now().toString()));
            selected=LocalDate.parse(state.getString("selected",LocalDate.now().toString()));
            brush=state.getInt("brush",Palette.COLORS[0]);painting=state.getBoolean("painting",false);
            searchQuery=state.getString("search","");eventDraft=state.getBundle("event_draft");
        }
        render();
        if(eventDraft!=null)restoreEventDraft();
    }
    @Override protected void onPause(){
        saveNavigation();super.onPause();
    }
    private void saveNavigation(){
        getPreferences(MODE_PRIVATE).edit().putInt("active_profile",profile)
            .putString("profile_"+profile+"_page",page)
            .putString("profile_"+profile+"_month",currentMonth.toString())
            .putString("profile_"+profile+"_selected",selected.toString()).apply();
    }
    private void loadNavigation(){
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        try{
            page=prefs.getString("profile_"+profile+"_page","month");
            currentMonth=YearMonth.parse(prefs.getString("profile_"+profile+"_month",YearMonth.now().toString()));
            selected=LocalDate.parse(prefs.getString("profile_"+profile+"_selected",LocalDate.now().toString()));
        }catch(RuntimeException invalid){page="month";currentMonth=YearMonth.now();selected=LocalDate.now();}
    }
    @Override protected void onDestroy(){
        if(eventDialog!=null)eventDialog.dismiss();
        if(backupPassword!=null)Arrays.fill(backupPassword,'\0');
        if(store!=null)store.close();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putInt("profile",profile);out.putString("page",page);
        out.putString("month",currentMonth.toString());out.putString("selected",selected.toString());
        out.putInt("brush",brush);out.putBoolean("painting",painting);out.putString("search",searchQuery);
        if(eventDialog!=null&&eventDialog.isShowing()){
            Bundle draft=new Bundle();draft.putString("title",draftTitle.getText().toString());
            draft.putString("time",draftTime.getText().toString());draft.putString("note",draftNote.getText().toString());
            draft.putInt("category",draftCategory.getSelectedItemPosition());draft.putInt("profile",draftProfile);
            draft.putLong("id",draftId);draft.putString("date",draftDate.toString());out.putBundle("event_draft",draft);
        }
        super.onSaveInstanceState(out);
    }
    private int d(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private float scale(){return readingSize==2?1.27f:readingSize==1?1.13f:1f;}
    private GradientDrawable shape(int fill,int radius,int stroke){
        GradientDrawable x=new GradientDrawable();x.setColor(fill);x.setCornerRadius(d(radius));
        if(stroke!=0)x.setStroke(d(1),stroke);
        return x;
    }
    private TextView label(String text,int sp,boolean bold,int color){
        TextView v=new TextView(this);v.setText(text);v.setTextSize(sp*scale());v.setTextColor(color);
        v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(d(3),d(5),d(3),d(5));v.setLineSpacing(d(2),1.02f);
        return v;
    }
    private void add(LinearLayout parent,View v,int marginTop){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin=d(marginTop);parent.addView(v,p);
    }
    private TextView btn(String text,int background,int textColor,Runnable action){
        TextView b=label(text,15,true,textColor);
        b.setGravity(Gravity.CENTER);b.setMinHeight(d(49));b.setPadding(d(11),d(10),d(11),d(10));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227260A8),shape(background,14,background==SOFT?BORDER:0),null));
        b.setClickable(true);b.setFocusable(true);
        buttonRole(b);b.setOnClickListener(v->action.run());return b;
    }
    private void buttonRole(View view){
        view.setClickable(true);view.setFocusable(true);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());
            }
        });
    }
    private void heading(TextView view){view.setAccessibilityHeading(true);}
    private TextView primary(String text,Runnable action){return btn(text,PURPLE,WHITE,action);}
    private TextView secondary(String text,Runnable action){return btn(text,SOFT,INK,action);}
    private LinearLayout column(){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;
    }
    private LinearLayout row(){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private void weighted(LinearLayout parent,View v,int margin){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);p.rightMargin=d(margin);parent.addView(v,p);
    }
    private LinearLayout card(LinearLayout parent,int background){
        LinearLayout c=column();c.setPadding(d(13),d(13),d(13),d(13));c.setBackground(shape(background,16,background==WHITE?BORDER:0));add(parent,c,10);return c;
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String monthTitle(){
        String text=currentMonth.atDay(1).format(DateTimeFormatter.ofPattern("LLLL 'de' uuuu",locale));
        return text.substring(0,1).toUpperCase(locale)+text.substring(1);
    }
    private String dateTitle(LocalDate date){
        String text=date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",locale));
        return text.substring(0,1).toUpperCase(locale)+text.substring(1);
    }
    private void render(){
        readingSize=store.fontSize(profile);
        root=column();root.setBackgroundColor(BG);
        LinearLayout top=column();top.setPadding(d(16),d(11),d(16),d(6));
        TextView brand=label("Calendario Familiar",23,true,INK);heading(brand);top.addView(brand);
        top.addView(label("Agenda de "+(profile==1?"Yo":"Mamá")+" · guardado en este dispositivo",12,false,0xFF586376));
        add(root,top,0);
        LinearLayout profiles=row();
        weighted(profiles,btn("Yo",profile==1?PURPLE:SOFT,profile==1?WHITE:INK,()->switchProfile(1)),6);
        weighted(profiles,btn("Mamá",profile==2?PURPLE:SOFT,profile==2?WHITE:INK,()->switchProfile(2)),0);
        profiles.setPadding(d(16),0,d(10),d(8));add(root,profiles,0);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        body=column();body.setPadding(d(13),d(5),d(13),d(24));
        scroll.addView(body,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        LinearLayout tabs=row();tabs.setPadding(d(12),d(7),d(12),d(9));tabs.setBackgroundColor(WHITE);
        String[] screens={"month","week","day","settings"},names={"Mes","Semana","Día","Ajustes"};
        for(int i=0;i<screens.length;i++){
            final String target=screens[i];TextView tab=btn(names[i],target.equals(page)?PURPLE:WHITE,target.equals(page)?WHITE:INK,()->{
                if("month".equals(page)&&("day".equals(target)||"week".equals(target))&&!YearMonth.from(selected).equals(currentMonth))selected=currentMonth.atDay(1);
                if("month".equals(target)&&("day".equals(page)||"week".equals(page)))currentMonth=YearMonth.from(selected);
                painting=false;page=target;render();
            });tab.setTag("nav-"+target);tab.setSelected(target.equals(page));weighted(tabs,tab,i==3?0:3);
        }
        add(root,tabs,0);setContentView(root);
        switch(page){
            case "day":drawDay();break;
            case "week":drawWeek();break;
            case "categories":drawCategories();break;
            case "settings":drawSettings();break;
            case "search":drawSearch();break;
            default:page="month";drawMonth();
        }
    }
    private void switchProfile(int p){
        if(profile==p)return;
        saveNavigation();profile=p;painting=false;searchQuery="";loadNavigation();
        getPreferences(MODE_PRIVATE).edit().putInt("active_profile",p).apply();render();
    }
    private void drawMonth(){
        LinearLayout nav=row();
        TextView previousMonth=secondary("‹",()->{currentMonth=currentMonth.minusMonths(1);render();});
        previousMonth.setContentDescription("Mes anterior");weighted(nav,previousMonth,4);
        TextView title=label(monthTitle(),20,true,INK);
        title.setGravity(Gravity.CENTER);title.setMinHeight(d(48));buttonRole(title);title.setContentDescription("Elegir fecha. "+monthTitle());
        title.setOnClickListener(v->chooseDate(currentMonth.atDay(1),date->{selected=date;currentMonth=YearMonth.from(date);render();}));nav.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,3f));
        TextView nextMonth=secondary("›",()->{currentMonth=currentMonth.plusMonths(1);render();});
        nextMonth.setContentDescription("Mes siguiente");weighted(nav,nextMonth,0);
        add(body,nav,3);
        LinearLayout controls=row();
        weighted(controls,secondary("Hoy",()->{currentMonth=YearMonth.now();selected=LocalDate.now();painting=false;render();}),5);
        weighted(controls,secondary("Colores de eventos",()->{page="categories";painting=false;render();}),0);
        add(body,controls,6);
        LinearLayout tools=row();weighted(tools,secondary("Buscar citas",()->{page="search";painting=false;render();}),5);
        weighted(tools,secondary("Pintar rango",()->paintRange()),0);add(body,tools,6);
        add(body,secondary("Ver semana · letras más grandes",()->{
            selected=YearMonth.from(selected).equals(currentMonth)?selected:currentMonth.atDay(1);
            page="week";painting=false;render();
        }),7);
        if(painting){
            LinearLayout hint=card(body,0xFFECE8F5);
            hint.addView(label("Pintar · "+nameColor(brush),17,true,PURPLE));
            hint.addView(label("Cada toque colorea el día completo. Los eventos no cambian de color.",13,false,INK));
            LinearLayout actions=row();
            weighted(actions,secondary("Cambiar color",()->pickColor(c->{brush=c;render();})),5);
            weighted(actions,secondary("Terminar",()->{painting=false;render();}),0);
            add(hint,actions,4);
            add(hint,secondary("Deshacer último día pintado",()->undoLastPaint()),5);
        }else{
            add(body,primary("Pintar varios días",()->pickColor(c->{brush=c;painting=true;render();})),8);
        }
        LinearLayout weekdays=row();
        for(String name:new String[]{"DOM","LUN","MAR","MIÉ","JUE","VIE","SÁB"}){
            TextView day=label(name,11,true,0xFF6B657A);day.setGravity(Gravity.CENTER);
            weekdays.addView(day,new LinearLayout.LayoutParams(0,d(38),1f));
        }
        add(body,weekdays,9);
        CalStore.MonthData monthData=store.month(profile,currentMonth);
        MonthGrid grid=new MonthGrid(this,currentMonth,monthData,readingSize,date->{
            if(painting){
                paintDay(date,brush);
                monthData.backgrounds.put(date.toString(),brush);
                gridInvalidateByDate(monthData,date,brush);
            }else{
                selected=date;page="day";render();
            }
        });
        grid.setSelectedDate(selected);grid.setPainting(painting);
        grid.setTag("month-grid");
        add(body,grid,0);
        if(store.hasPaintUndo(profile))add(body,secondary("Deshacer último color o rango",()->undoLastPaint()),8);
        // Helps everyone, especially the large-font profile, find full event names.
        LinearLayout expl=card(body,WHITE);
        expl.addView(label("Toca un día para ver todas sus citas",16,true,INK));
        expl.addView(label("Las tarjetas muestran una parte del nombre; en el detalle del día verás texto más grande y completo.",13,false,0xFF5B667A));
        add(body,secondary("Tamaño de letra y respaldo",()->{page="settings";painting=false;render();}),10);
    }
    private void paintDay(LocalDate date,int color){
        store.paintBatch(profile,java.util.Collections.singletonList(date),color);
    }
    private void undoLastPaint(){
        try{
            if(!store.undoPaint(profile)){toast("Todavía no hay un color que deshacer");return;}
            render();toast("Se restauraron los colores anteriores");
        }catch(IllegalStateException stale){toast("Los colores cambiaron después; no se sobrescribieron");}
    }
    /** Called after a paint operation; redraw the month without resetting scroll position. */
    private void gridInvalidateByDate(CalStore.MonthData data,LocalDate date,int color){
        View v=body.findViewWithTag("month-grid");if(v instanceof MonthGrid)((MonthGrid)v).notifyDataChanged();
    }
    /** Larger native view: 7 individual accessible day cards, unlike the small month grid. */
    private void drawWeek(){
        add(body,secondary("‹ Volver al mes",()->{
            page="month";currentMonth=YearMonth.from(selected);render();
        }),3);
        LinearLayout nav=row();
        weighted(nav,secondary("‹ Semana anterior",()->{selected=selected.minusWeeks(1);render();}),5);
        weighted(nav,secondary("Semana siguiente ›",()->{selected=selected.plusWeeks(1);render();}),0);
        add(body,nav,9);
        add(body,label("Semana del "+dateTitle(WeekRules.sunday(selected)),19,true,INK),12);
        add(body,label("Vista grande. Toca un día para ver o editar sus citas.",15,false,0xFF566374),3);
        add(body,secondary("Esta semana",()->{selected=LocalDate.now();currentMonth=YearMonth.from(selected);render();}),6);
        CalStore.MonthData weekData=store.range(profile,WeekRules.sunday(selected),WeekRules.sunday(selected).plusDays(6));
        for(LocalDate date:WeekRules.days(selected)){
            int dayColor=weekData.color(date.toString());
            int ink=Palette.text(dayColor);
            List<CalStore.Event> events=weekData.events(date.toString());
            LinearLayout dayCard=card(body,dayColor);
            dayCard.setMinimumHeight(d(78));
            dayCard.addView(label(dateTitle(date)+(date.equals(LocalDate.now())?" · Hoy":""),21,true,ink));
            if(events.isEmpty())dayCard.addView(label("Sin eventos",15,false,ink));
            else for(CalStore.Event event:events){
                LinearLayout tile=column();
                tile.setPadding(d(13),d(8),d(13),d(8));
                tile.setBackground(shape(event.color,11,0));
                tile.setMinimumHeight(d(57));
                tile.addView(label(WeekRules.eventLine(event),17,true,Palette.text(event.color)));
                if(!event.note.isEmpty())
                    tile.addView(label(event.note,14,false,Palette.text(event.color)));
                add(dayCard,tile,6);
            }
            dayCard.addView(label("Toca para abrir este día",12,false,ink));
            dayCard.setClickable(true);dayCard.setFocusable(true);
            dayCard.setContentDescription(WeekRules.accessibleDay(dateTitle(date),events));
            dayCard.setOnClickListener(v->{selected=date;page="day";render();});
        }
        add(body,secondary("Volver a la vista mensual",()->{
            currentMonth=YearMonth.from(selected);page="month";render();
        }),13);
    }
    private void drawDay(){
        add(body,secondary("‹ Volver al mes",()->{page="month";currentMonth=YearMonth.from(selected);render();}),3);
        LinearLayout days=row();weighted(days,secondary("‹ Día anterior",()->{selected=selected.minusDays(1);currentMonth=YearMonth.from(selected);render();}),5);
        weighted(days,secondary("Día siguiente ›",()->{selected=selected.plusDays(1);currentMonth=YearMonth.from(selected);render();}),0);add(body,days,7);
        add(body,secondary("Elegir fecha",()->chooseDate(selected,date->{selected=date;currentMonth=YearMonth.from(selected);render();})),6);
        LinearLayout day=card(body,store.dayColor(profile,selected.toString()));
        int fg=Palette.text(store.dayColor(profile,selected.toString()));
        day.addView(label(dateTitle(selected),23,true,fg));
        day.addView(label(selected.format(DateTimeFormatter.ofPattern("uuuu-MM-dd")),13,false,fg));
        LinearLayout actions=row();
        weighted(actions,secondary("Pintar día",()->pickColor(c->{paintDay(selected,c);render();})),5);
        weighted(actions,secondary("Quitar color",()->{paintDay(selected,Palette.DEFAULT_DAY);render();}),0);
        add(body,actions,10);
        if(store.hasPaintUndo(profile))
            add(body,secondary("Deshacer último color",()->undoLastPaint()),6);
        add(body,primary("+ Añadir evento o cita",()->editEvent(null)),9);
        List<CalStore.Event> events=store.events(profile,selected.toString());
        add(body,label("Eventos de este día ("+events.size()+")",19,true,INK),17);
        if(events.isEmpty()){
            LinearLayout empty=card(body,WHITE);
            empty.addView(label("Sin eventos. Puedes colorear el día aunque no tenga citas.",17,false,0xFF566273));
        }
        for(CalStore.Event e:events){
            LinearLayout c=card(body,WHITE);
            LinearLayout contents=row();
            View swatch=new View(this);swatch.setBackground(shape(e.color,6,0));
            contents.addView(swatch,new LinearLayout.LayoutParams(d(9),ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout text=column();text.setPadding(d(12),0,0,0);
            if(!e.categoryName.isEmpty())text.addView(label(e.categoryName,14,true,0xFF7362A9));
            text.addView(label(e.title,19,true,INK));
            if(!e.time.isEmpty())text.addView(label("Hora: "+e.time,16,true,0xFF535F72));
            if(!e.note.isEmpty())text.addView(label(e.note,16,false,0xFF535F72));
            text.addView(label("Toca para editar",12,false,0xFF6D7682));
            contents.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            c.addView(contents,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            buttonRole(c);c.setOnClickListener(v->editEvent(e));
            c.setContentDescription(e.categoryName+", "+e.title+(e.time.isEmpty()?"":", hora "+e.time)+". Toca para editar");
        }
        add(body,secondary("Administrar colores de eventos",()->{page="categories";render();}),15);
    }
    private void drawCategories(){
        add(body,secondary("‹ Volver al calendario",()->{page="month";render();}),3);
        add(body,label("Colores y etiquetas de eventos",22,true,INK),14);
        add(body,label("Cada lugar o tipo de cita tiene su color propio. La etiqueta corta se muestra dentro del día; el nombre completo aparece al tocarlo.",15,false,0xFF596579),4);
        add(body,primary("+ Nueva categoría",()->editCategory(null)),12);
        for(CalStore.Category c:store.categories(profile)){
            LinearLayout card=card(body,WHITE);
            LinearLayout row=row();
            TextView chip=label(c.shortName,15,true,Palette.text(c.color));chip.setGravity(Gravity.CENTER);
            chip.setBackground(shape(c.color,11,0));chip.setMinWidth(d(95));
            chip.setPadding(d(10),d(13),d(10),d(13));
            row.addView(chip,new LinearLayout.LayoutParams(d(125),ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout right=column();right.setPadding(d(12),0,0,0);
            right.addView(label(c.name,17,true,INK));
            right.addView(label("Toca para cambiar nombre o color",12,false,0xFF667080));
            row.addView(right,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            card.addView(row);
            buttonRole(card);card.setContentDescription(c.name+". Color "+nameColor(c.color)+". Editar categoría");
            card.setOnClickListener(v->editCategory(c));
        }
    }
    private void drawSettings(){
        add(body,secondary("‹ Volver al calendario",()->{page="month";render();}),3);
        add(body,label("Lectura y privacidad",22,true,INK),15);
        LinearLayout fontCard=card(body,WHITE);
        fontCard.addView(label("Tamaño de letra de "+(profile==1?"Yo":"Mamá"),18,true,INK));
        fontCard.addView(label("La letra del detalle diario y los botones aumenta sin reducir los días a marcas diminutas.",15,false,0xFF5B6576));
        for(int i=0;i<3;i++){
            final int value=i;String[] names={"Normal","Grande","Muy grande"};
            TextView option=secondary(names[i]+(store.fontSize(profile)==i?" ✓":""),()->{store.setFontSize(profile,value);render();});
            add(fontCard,option,6);
        }
        LinearLayout copy=card(body,WHITE);
        copy.addView(label("Respaldos de los dos calendarios",18,true,INK));
        copy.addView(label("Para cambiar de teléfono o conservar tus datos. El archivo incluye ambas agendas y se protege con tu contraseña.",15,false,0xFF5B6576));
        add(copy,primary("Exportar respaldo protegido",()->backupPrompt(true)),9);
        add(copy,secondary("Restaurar respaldo",()->backupPrompt(false)),7);
        LinearLayout about=card(body,WHITE);
        about.addView(label("Icono del calendario",18,true,INK));
        about.addView(label("El icono es fijo y no muestra la fecha. La actualización automática del número está pendiente de reparación.",14,false,0xFF5B6576));
        add(body,label("Sin anuncios, sin Internet y sin lectura de otros calendarios. Los datos se guardan en este dispositivo.",13,false,0xFF697486),14);
    }
    private void pickColor(java.util.function.Consumer<Integer> chosen){
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=column();content.setPadding(d(13),d(7),d(13),d(18));
        scroll.addView(content);
        final AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Elige un color").setView(scroll).setNegativeButton("Cancelar",null).create();
        for(int group=0;group<3;group++){
            add(content,label(Palette.GROUPS[group],18,true,INK),group==0?4:13);
            GridLayout grid=new GridLayout(this);grid.setColumnCount(3);
            int screen=getResources().getDisplayMetrics().widthPixels;
            int cell=(screen-d(66))/3;
            for(int i=group*8;i<(group+1)*8;i++){
                final int color=Palette.COLORS[i];
                TextView b=label(Palette.NAMES[i],12,true,Palette.text(color));
                b.setBackground(shape(color,12,0));b.setGravity(Gravity.CENTER);
                b.setMinHeight(d(66));b.setPadding(d(4),d(7),d(4),d(7));
                GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=cell-d(5);p.height=ViewGroup.LayoutParams.WRAP_CONTENT;
                p.setMargins(d(2),d(3),d(3),d(3));grid.addView(b,p);
                buttonRole(b);b.setContentDescription(Palette.NAMES[i]);
                b.setOnClickListener(v->{dlg.dismiss();chosen.accept(color);});
            }
            add(content,grid,5);
        }
        dlg.show();
    }
    private EditText input(String hint,String initial){
        EditText field=new EditText(this);field.setSingleLine(true);field.setTextSize(17*scale());
        field.setTextColor(INK);field.setHintTextColor(0xFF697287);field.setHint(hint);
        field.setText(initial);field.setSelectAllOnFocus(false);
        return field;
    }
    private void editEvent(CalStore.Event existing){
        List<CalStore.Category> categories=store.categories(profile);
        draftId=existing==null?0:existing.id;draftProfile=profile;draftDate=existing==null?selected:LocalDate.parse(existing.date);
        LinearLayout form=column();form.setPadding(d(18),0,d(18),d(12));
        EditText title=input("Ej. Consulta, análisis, oficina",existing==null?"":existing.title);
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(90)});draftTitle=title;
        TextView dateButton=secondary("Fecha: "+dateTitle(draftDate),()->{});
        dateButton.setTag("event-date-button");dateButton.setOnClickListener(v->chooseDate(draftDate,date->{draftDate=date;dateButton.setText("Fecha: "+dateTitle(date));}));
        add(form,dateButton,5);
        add(form,label("Nombre del evento",16,true,INK),5);add(form,title,0);
        add(form,label("Lugar o tipo (su color)",16,true,INK),11);
        ArrayList<String> names=new ArrayList<>();names.add("Sin categoría");
        for(CalStore.Category c:categories)names.add(c.name);
        Spinner spinner=new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        if(existing!=null)for(int i=0;i<categories.size();i++)if(categories.get(i).id==existing.category)spinner.setSelection(i+1);
        spinner.setMinimumHeight(d(48));draftCategory=spinner;add(form,spinner,5);
        EditText time=input("Hora opcional, ej. 09:30",existing==null?"":existing.time);
        time.setInputType(InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_TIME);
        draftTime=time;add(form,label("Hora (opcional)",16,true,INK),10);add(form,time,0);
        add(form,secondary("Elegir hora",()->{
            java.time.LocalTime initial;try{initial=java.time.LocalTime.parse(time.getText().toString());}catch(Exception invalid){initial=java.time.LocalTime.of(9,0);}
            new TimePickerDialog(this,(picker,h,m)->time.setText(String.format(Locale.ROOT,"%02d:%02d",h,m)),initial.getHour(),initial.getMinute(),true).show();
        }),4);
        EditText notes=input("Detalles importantes (opcional)",existing==null?"":existing.note);
        notes.setSingleLine(false);notes.setMinLines(2);notes.setMaxLines(4);
        notes.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});draftNote=notes;
        add(form,label("Notas (opcional)",16,true,INK),10);add(form,notes,0);
        ScrollView sc=new ScrollView(this);sc.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"Añadir evento":"Editar evento")
            .setView(sc).setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null)
            .setNeutralButton(existing==null?"":"Eliminar",null).create();
        eventDialog=dialog;
        dialog.setOnDismissListener(x->{if(eventDialog==dialog)eventDialog=null;});
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{
                    int index=spinner.getSelectedItemPosition();
                    long category=index==0?0:categories.get(index-1).id;
                    store.saveEvent(draftProfile,draftId,draftDate.toString(),title.getText().toString(),
                        time.getText().toString(),notes.getText().toString(),category);
                    selected=draftDate;currentMonth=YearMonth.from(selected);dialog.dismiss();render();
                }catch(Exception ex){toast("Revisa el título y la hora (HH:mm). "+ex.getMessage());}
            });
            if(existing!=null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->
                new AlertDialog.Builder(this).setTitle("¿Eliminar este evento?").setMessage(existing.title)
                .setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,i)->{
                    store.deleteEvent(profile,existing.id);dialog.dismiss();render();
                }).show());
            else dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
        });
        dialog.show();
    }
    private void editCategory(CalStore.Category existing){
        final int[] color={existing==null?Palette.COLORS[1]:existing.color};
        LinearLayout form=column();form.setPadding(d(18),0,d(18),d(12));
        EditText name=input("Ej. ISSSTE Balbuena",existing==null?"":existing.name);
        EditText shortName=input("Ej. ISSSTE",existing==null?"":existing.shortName);
        add(form,label("Nombre completo",16,true,INK),2);add(form,name,0);
        add(form,label("Nombre corto visible en el mes",16,true,INK),8);add(form,shortName,0);
        TextView colorButton=btn("Seleccionar color",color[0],Palette.text(color[0]),()->pickColor(c->{
            color[0]=c;colorButtonUpdate(form,c);
        }));
        colorButton.setTag("category-color-button");add(form,colorButton,12);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"Nueva categoría":"Editar categoría")
            .setView(scroll).setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                store.saveCategory(profile,existing==null?0:existing.id,name.getText().toString(),shortName.getText().toString(),color[0]);
                dialog.dismiss();render();
            }catch(Exception ex){toast("Revisa nombres y color: "+ex.getMessage());}
        }));
        dialog.show();
    }
    private void colorButtonUpdate(LinearLayout form,int color){
        View v=form.findViewWithTag("category-color-button");
        if(v instanceof TextView){
            TextView text=(TextView)v;text.setText("Color seleccionado: "+nameColor(color));
            text.setTextColor(Palette.text(color));text.setBackground(shape(color,12,0));
        }
    }
    private String nameColor(int color){
        for(int i=0;i<Palette.COLORS.length;i++)if(Palette.COLORS[i]==color)return Palette.NAMES[i];
        return "Personalizado";
    }
    private void backupPrompt(boolean exporting){
        EditText password=input("Contraseña (mínimo 12 caracteres)","");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSaveEnabled(false);password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        LinearLayout form=column();form.setPadding(d(16),0,d(16),d(8));
        add(form,label(exporting?"Crea una contraseña para proteger las dos agendas. Guárdala en un lugar seguro.":"Escribe la contraseña que utilizaste al exportar. El respaldo sustituirá las dos agendas actuales.",16,false,INK),0);
        add(form,password,8);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(exporting?"Exportar respaldo cifrado":"Restaurar calendario")
            .setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Elegir archivo",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            char[] pass=password.getText().toString().toCharArray();
            password.getText().clear();
            if(pass.length<12){Arrays.fill(pass,'\0');toast("Se necesitan al menos 12 caracteres");return;}
            if(backupPassword!=null)Arrays.fill(backupPassword,'\0');
            backupPassword=pass;
            try{
                Intent intent=new Intent(exporting?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(exporting?"application/octet-stream":"*/*");
                if(exporting)intent.putExtra(Intent.EXTRA_TITLE,"calendario-familiar-"+LocalDate.now()+".cfam");
                startActivityForResult(intent,exporting?EXPORT:IMPORT);
                dialog.dismiss();
            }catch(Exception ex){Arrays.fill(pass,'\0');backupPassword=null;toast("No se pudo abrir el selector de archivos");}
        }));
        dialog.show();
    }
    @Override @Deprecated protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=EXPORT&&request!=IMPORT)return;
        char[] pass=backupPassword;backupPassword=null;
        try{
            if(result!=RESULT_OK||data==null||data.getData()==null)return;
            if(pass==null){toast("La pantalla se recreó. Vuelve a elegir el respaldo y escribe la contraseña.");return;}
            if(request==EXPORT){
                byte[] raw=Backup.exportData(store);
                byte[] encrypted=Backup.encrypt(raw,pass);
                try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){
                    if(out==null)throw new java.io.IOException("Archivo no disponible");
                    out.write(encrypted);
                }finally{Arrays.fill(raw,(byte)0);}
                toast("Respaldo cifrado guardado. Conserva la contraseña.");
            }else{
                byte[] payload;
                try(java.io.InputStream in=getContentResolver().openInputStream(data.getData())){
                    if(in==null)throw new java.io.IOException("Archivo no disponible");
                    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                    byte[] part=new byte[4096];int n;
                    while((n=in.read(part))>=0){bytes.write(part,0,n);if(bytes.size()>2_100_000)throw new java.io.IOException("Archivo excesivo");}
                    payload=bytes.toByteArray();
                }
                byte[] raw=Backup.decrypt(payload,pass);
                try{Backup.importData(store,raw);}finally{Arrays.fill(raw,(byte)0);}
                page="month";painting=false;render();toast("Restauradas las dos agendas");
            }
        }catch(Exception e){toast("No se pudo usar el respaldo. Verifica archivo y contraseña; los datos existentes se conservaron si falló la validación.");}
        finally{if(pass!=null)Arrays.fill(pass,'\0');}
    }

    private void chooseDate(LocalDate initial,java.util.function.Consumer<LocalDate> chosen){
        new DatePickerDialog(this,(picker,year,month,day)->chosen.accept(LocalDate.of(year,month+1,day)),
            initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();
    }
    private void restoreEventDraft(){
        Bundle saved=eventDraft;eventDraft=null;
        if(saved.getInt("profile")!=profile)return;
        CalStore.Event original=null;long id=saved.getLong("id");
        if(id>0){
            for(CalStore.Event event:store.events(profile,selected.toString()))if(event.id==id){original=event;break;}
            if(original==null){toast("El evento ya no está disponible");return;}
        }
        editEvent(original);
        draftTitle.setText(saved.getString("title",""));draftTime.setText(saved.getString("time",""));
        draftNote.setText(saved.getString("note",""));draftDate=LocalDate.parse(saved.getString("date",selected.toString()));
        draftCategory.setSelection(Math.min(saved.getInt("category"),draftCategory.getCount()-1));
        TextView dateButton=eventDialog.getWindow().getDecorView().findViewWithTag("event-date-button");
        if(dateButton!=null)dateButton.setText("Fecha: "+dateTitle(draftDate));
    }
    private void paintRange(){
        final LocalDate[] dates={selected,selected};
        final int[] color={brush};
        LinearLayout form=column();form.setPadding(d(18),d(5),d(18),d(12));
        form.addView(label("Colorea un rango de hasta 366 días de "+(profile==1?"Yo":"Mamá")+". Las citas conservan su color.",15,false,INK));
        TextView first=secondary("Desde: "+dates[0],()->{}),last=secondary("Hasta: "+dates[1],()->{});
        first.setOnClickListener(v->chooseDate(dates[0],date->{dates[0]=date;first.setText("Desde: "+date);}));
        last.setOnClickListener(v->chooseDate(dates[1],date->{dates[1]=date;last.setText("Hasta: "+date);}));
        add(form,first,7);add(form,last,7);
        TextView choice=btn("Color: "+nameColor(color[0]),color[0],Palette.text(color[0]),()->{});
        choice.setOnClickListener(v->pickColor(chosen->{color[0]=chosen;choice.setText("Color: "+nameColor(chosen));
            choice.setTextColor(Palette.text(chosen));choice.setBackground(shape(chosen,14,0));}));
        add(form,choice,7);
        ScrollView sc=new ScrollView(this);sc.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Pintar un rango")
            .setView(sc).setNegativeButton("Cancelar",null).setPositiveButton("Revisar",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            long count=java.time.temporal.ChronoUnit.DAYS.between(dates[0],dates[1])+1;
            if(count<1||count>366){toast("Elige un rango en orden de 1 a 366 días");return;}
            new AlertDialog.Builder(this).setTitle("Confirmar "+count+" días")
                .setMessage("Agenda de "+(profile==1?"Yo":"Mamá")+" · "+dates[0]+" a "+dates[1]+
                    "\nColor: "+nameColor(color[0])+". Podrás deshacer este lote completo.")
                .setNegativeButton("Volver",null).setPositiveButton("Pintar",(confirm,which)->{
                    List<LocalDate> batch=new ArrayList<>();for(LocalDate day=dates[0];!day.isAfter(dates[1]);day=day.plusDays(1))batch.add(day);
                    store.paintBatch(profile,batch,color[0]);brush=color[0];selected=dates[0];currentMonth=YearMonth.from(selected);
                    page="month";painting=false;dialog.dismiss();render();toast("Rango guardado");
                }).show();
        }));dialog.show();
    }
    private void drawSearch(){
        add(body,secondary("‹ Volver al calendario",()->{page="month";render();}),3);
        TextView title=label("Buscar en "+(profile==1?"Yo":"Mamá"),22,true,INK);heading(title);add(body,title,12);
        add(body,label("Busca por nombre, lugar o notas. Solo se consulta esta agenda en el dispositivo.",14,false,0xFF586376),3);
        EditText query=input("Ej. consulta o llevar estudios",searchQuery);
        query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});add(body,query,8);
        add(body,primary("Buscar",()->{searchQuery=query.getText().toString().trim();render();}),7);
        if(searchQuery.isEmpty()){add(body,label("Escribe lo que necesitas encontrar.",16,false,INK),12);return;}
        List<CalStore.Event> results=store.search(profile,searchQuery);
        add(body,label(results.size()==100?"Primeras 100 citas. Afina tu búsqueda.":results.size()+" citas encontradas",16,true,INK),12);
        if(results.isEmpty())add(body,label("No hay coincidencias en esta agenda.",16,false,0xFF586376),8);
        for(CalStore.Event event:results){
            LinearLayout result=card(body,WHITE);
            result.addView(label(dateTitle(LocalDate.parse(event.date))+" · "+LocalDate.parse(event.date).getYear(),14,true,PURPLE));
            result.addView(label(WeekRules.eventLine(event),17,true,INK));
            if(!event.note.isEmpty())result.addView(label(event.note,14,false,0xFF586376));
            buttonRole(result);result.setContentDescription(event.date+". "+WeekRules.eventLine(event)+". "+event.note+". Abrir día");
            result.setOnClickListener(v->{selected=LocalDate.parse(event.date);currentMonth=YearMonth.from(selected);page="day";render();});
        }
    }

    @Override @Deprecated public void onBackPressed(){
        if(painting){painting=false;render();return;}
        if(!"month".equals(page)){
            if("day".equals(page)||"week".equals(page))currentMonth=YearMonth.from(selected);
            page="month";painting=false;render();return;
        }
        super.onBackPressed();
    }
}
