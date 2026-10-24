package mx.charly.calendario;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CalStore extends SQLiteOpenHelper {
    public static final class Category {
        public final long id;
        public final int profile, color;
        public final String name, shortName;
        Category(long id,int p,String name,String shortName,int color) {
            this.id=id;profile=p;this.name=name;this.shortName=shortName;this.color=color;
        }
    }
    public static final class Event {
        public final long id, category;
        public final int color;
        public final String date, title, time, note, categoryName, shortName;
        Event(long id,long category,String date,String title,String time,String note,int color,String name,String shortName){
            this.id=id;this.category=category;this.date=date;this.title=title;this.time=time;this.note=note;
            this.color=color;categoryName=name;this.shortName=shortName;
        }
        public String preview() {
            String base=shortName.isEmpty() ? title : shortName+" · "+title;
            return base.trim();
        }
    }
    public static final class MonthData {
        public final Map<String,Integer> backgrounds=new HashMap<>();
        public final Map<String,List<Event>> events=new HashMap<>();
        public int color(String day) {return backgrounds.getOrDefault(day,Palette.DEFAULT_DAY);}
        public List<Event> events(String day) {return events.getOrDefault(day,Collections.emptyList());}
    }
    public CalStore(Context c){super(c.getApplicationContext(),"calendario-familiar.db",null,1);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE profiles(id INTEGER PRIMARY KEY, name TEXT NOT NULL, font_size INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE day_colors(profile_id INTEGER NOT NULL, day TEXT NOT NULL, color INTEGER NOT NULL, PRIMARY KEY(profile_id,day), FOREIGN KEY(profile_id) REFERENCES profiles(id))");
        db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL, name TEXT NOT NULL, short_name TEXT NOT NULL, color INTEGER NOT NULL, FOREIGN KEY(profile_id) REFERENCES profiles(id))");
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL, day TEXT NOT NULL, title TEXT NOT NULL, event_time TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', category_id INTEGER, FOREIGN KEY(profile_id) REFERENCES profiles(id), FOREIGN KEY(category_id) REFERENCES categories(id))");
        db.execSQL("CREATE INDEX events_by_day ON events(profile_id,day,event_time,id)");
        db.execSQL("INSERT INTO profiles(id,name,font_size) VALUES(1,'Yo',1),(2,'Mamá',2)");
        seed(db,1,"Oficina","Oficina",0xFF4FC8C6);
        seed(db,1,"Personal","Personal",0xFFA18BE3);
        seed(db,2,"IMSS 21","IMSS 21",0xFF64A8E6);
        seed(db,2,"ISSSTE Balbuena","ISSSTE",0xFFA18BE3);
    }
    private static void seed(SQLiteDatabase db,int profile,String name,String shortName,int color) {
        ContentValues v=new ContentValues();v.put("profile_id",profile);v.put("name",name);
        v.put("short_name",shortName);v.put("color",color);db.insertOrThrow("categories",null,v);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        throw new IllegalStateException("Missing database migration: "+oldVersion+"→"+newVersion);
    }
    public static void checkProfile(int id){if(id!=1&&id!=2)throw new IllegalArgumentException("Perfil inválido");}
    private static String day(String s){LocalDate.parse(s);return s;}
    public synchronized int fontSize(int profile) {
        checkProfile(profile);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT font_size FROM profiles WHERE id=?",new String[]{""+profile})){
            return c.moveToFirst()?c.getInt(0):1;
        }
    }
    public synchronized void setFontSize(int profile,int size) {
        checkProfile(profile);if(size<0||size>2)throw new IllegalArgumentException("Tamaño inválido");
        ContentValues v=new ContentValues();v.put("font_size",size);
        getWritableDatabase().update("profiles",v,"id=?",new String[]{""+profile});
    }
    public synchronized void paint(int profile,String date,int color){
        checkProfile(profile);day(date);
        if(!Palette.isAvailable(color))throw new IllegalArgumentException("Color no disponible");
        SQLiteDatabase db=getWritableDatabase();
        if(color==Palette.DEFAULT_DAY){db.delete("day_colors","profile_id=? AND day=?",new String[]{""+profile,date});return;}
        ContentValues v=new ContentValues();v.put("profile_id",profile);v.put("day",date);v.put("color",color);
        db.insertWithOnConflict("day_colors",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized MonthData month(int profile,YearMonth month){
        checkProfile(profile);
        String first=month.atDay(1).toString(),last=month.atEndOfMonth().toString();
        MonthData out=new MonthData();
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor c=db.rawQuery("SELECT day,color FROM day_colors WHERE profile_id=? AND day BETWEEN ? AND ?",new String[]{""+profile,first,last})){
            while(c.moveToNext())out.backgrounds.put(c.getString(0),c.getInt(1));
        }
        try(Cursor c=db.rawQuery("SELECT e.id,e.category_id,e.day,e.title,e.event_time,e.note,COALESCE(cat.color,?),COALESCE(cat.name,''),COALESCE(cat.short_name,'') FROM events e LEFT JOIN categories cat ON e.category_id=cat.id WHERE e.profile_id=? AND e.day BETWEEN ? AND ? ORDER BY e.event_time,e.id",
                new String[]{""+0xFF7363B2,""+profile,first,last})){
            while(c.moveToNext()){
                Event e=event(c);out.events.computeIfAbsent(e.date,k->new ArrayList<>()).add(e);
            }
        }
        return out;
    }
    private static Event event(Cursor c) {
        return new Event(c.getLong(0),c.isNull(1)?0:c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getInt(6),c.getString(7),c.getString(8));
    }
    public synchronized int dayColor(int profile,String date) {
        checkProfile(profile);day(date);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT color FROM day_colors WHERE profile_id=? AND day=?",new String[]{""+profile,date})){
            return c.moveToFirst()?c.getInt(0):Palette.DEFAULT_DAY;
        }
    }
    public synchronized List<Event> events(int profile,String date) {
        checkProfile(profile);day(date);
        ArrayList<Event> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.id,e.category_id,e.day,e.title,e.event_time,e.note,COALESCE(cat.color,?),COALESCE(cat.name,''),COALESCE(cat.short_name,'') FROM events e LEFT JOIN categories cat ON e.category_id=cat.id WHERE e.profile_id=? AND e.day=? ORDER BY e.event_time,e.id",
            new String[]{""+0xFF7363B2,""+profile,date})){
            while(c.moveToNext())out.add(event(c));
        }
        return out;
    }
    public synchronized List<Category> categories(int profile){
        checkProfile(profile);ArrayList<Category> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,profile_id,name,short_name,color FROM categories WHERE profile_id=? ORDER BY id",new String[]{""+profile})){
            while(c.moveToNext())out.add(new Category(c.getLong(0),c.getInt(1),c.getString(2),c.getString(3),c.getInt(4)));
        }
        return out;
    }
    public synchronized long saveCategory(int profile,long id,String name,String shortName,int color) {
        checkProfile(profile);
        name=name.trim();shortName=shortName.trim();
        if(name.isEmpty()||name.length()>46||shortName.isEmpty()||shortName.length()>16||!Palette.isAvailable(color))
            throw new IllegalArgumentException("Nombre, etiqueta corta o color inválido");
        ContentValues v=new ContentValues();v.put("name",name);v.put("short_name",shortName);v.put("color",color);
        SQLiteDatabase db=getWritableDatabase();
        if(id>0) {
            if(db.update("categories",v,"id=? AND profile_id=?",new String[]{""+id,""+profile})!=1)throw new IllegalArgumentException("Categoría ajena");
            return id;
        }
        v.put("profile_id",profile);
        return db.insertOrThrow("categories",null,v);
    }
    public synchronized long saveEvent(int profile,long id,String date,String title,String time,String note,long categoryId) {
        checkProfile(profile);day(date);
        title=title.trim();time=time.trim();note=note.trim();
        if(title.isEmpty()||title.length()>90||note.length()>500||(!time.isEmpty()&&!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")))
            throw new IllegalArgumentException("Revisa título, hora y nota");
        SQLiteDatabase db=getWritableDatabase();
        if(categoryId>0) {
            try(Cursor c=db.rawQuery("SELECT 1 FROM categories WHERE id=? AND profile_id=?",new String[]{""+categoryId,""+profile})){
                if(!c.moveToFirst())throw new IllegalArgumentException("Categoría no pertenece a este perfil");
            }
        }
        ContentValues v=new ContentValues();v.put("day",date);v.put("title",title);v.put("event_time",time);v.put("note",note);
        if(categoryId>0)v.put("category_id",categoryId);else v.putNull("category_id");
        if(id>0) {
            if(db.update("events",v,"id=? AND profile_id=?",new String[]{""+id,""+profile})!=1)throw new IllegalArgumentException("Evento ajeno");
            return id;
        }
        v.put("profile_id",profile);return db.insertOrThrow("events",null,v);
    }
    public synchronized void deleteEvent(int profile,long id) {
        checkProfile(profile);
        getWritableDatabase().delete("events","id=? AND profile_id=?",new String[]{""+id,""+profile});
    }
}
