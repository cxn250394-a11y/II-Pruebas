package mx.charly.calendario;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Export/import the TWO local profiles together; never sends data over the network. */
public final class Backup {
    private Backup(){}
    private static final byte[] MAGIC = new byte[]{'C','F','A','M','1'};
    private static final int ITERATIONS = 170_000;
    private static SecretKeySpec key(char[] password,byte[] salt)throws Exception{
        if(password.length<12)throw new IllegalArgumentException("Contraseña de al menos 12 caracteres");
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        try{return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(),"AES");}
        finally{spec.clearPassword();}
    }
    public static byte[] encrypt(byte[] input,char[] password)throws Exception {
        if(input.length<=0||input.length>2_000_000)throw new IllegalArgumentException("Respaldo demasiado grande");
        byte[] salt=new byte[16],iv=new byte[12];SecureRandom rng=new SecureRandom();rng.nextBytes(salt);rng.nextBytes(iv);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));c.updateAAD(MAGIC);
        byte[] ciphertext=c.doFinal(input),out=new byte[MAGIC.length+salt.length+iv.length+ciphertext.length];
        System.arraycopy(MAGIC,0,out,0,MAGIC.length);
        System.arraycopy(salt,0,out,MAGIC.length,salt.length);
        System.arraycopy(iv,0,out,MAGIC.length+salt.length,iv.length);
        System.arraycopy(ciphertext,0,out,MAGIC.length+salt.length+iv.length,ciphertext.length);
        return out;
    }
    public static byte[] decrypt(byte[] payload,char[] password)throws Exception{
        if(payload.length<MAGIC.length+16+12+16||payload.length>2_100_000)throw new IllegalArgumentException("Archivo no compatible");
        for(int i=0;i<MAGIC.length;i++)if(payload[i]!=MAGIC[i])throw new IllegalArgumentException("Archivo no compatible");
        int idx=MAGIC.length;
        byte[] salt=java.util.Arrays.copyOfRange(payload,idx,idx+16);idx+=16;
        byte[] iv=java.util.Arrays.copyOfRange(payload,idx,idx+12);idx+=12;
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));c.updateAAD(MAGIC);
        byte[] clear=c.doFinal(payload,idx,payload.length-idx);
        if(clear.length>2_000_000)throw new IllegalArgumentException("Archivo demasiado grande");
        return clear;
    }
    public static byte[] exportData(CalStore store)throws Exception{
        JSONObject json=new JSONObject();json.put("version",1);
        JSONArray cat=new JSONArray(),days=new JSONArray(),events=new JSONArray(),fonts=new JSONArray();
        SQLiteDatabase db=store.getReadableDatabase();
        try(Cursor c=db.rawQuery("SELECT id,profile_id,name,short_name,color FROM categories ORDER BY id",null)){
            while(c.moveToNext())cat.put(new JSONObject().put("id",c.getLong(0)).put("profile",c.getInt(1))
                .put("name",c.getString(2)).put("short",c.getString(3)).put("color",c.getInt(4)));
        }
        try(Cursor c=db.rawQuery("SELECT profile_id,day,color FROM day_colors ORDER BY profile_id,day",null)){
            while(c.moveToNext())days.put(new JSONObject().put("profile",c.getInt(0)).put("date",c.getString(1)).put("color",c.getInt(2)));
        }
        try(Cursor c=db.rawQuery("SELECT id,profile_id,day,title,event_time,note,category_id FROM events ORDER BY id",null)){
            while(c.moveToNext())events.put(new JSONObject().put("id",c.getLong(0)).put("profile",c.getInt(1))
                .put("date",c.getString(2)).put("title",c.getString(3)).put("time",c.getString(4))
                .put("note",c.getString(5)).put("category",c.isNull(6)?JSONObject.NULL:c.getLong(6)));
        }
        try(Cursor c=db.rawQuery("SELECT id,font_size FROM profiles ORDER BY id",null)){
            while(c.moveToNext())fonts.put(new JSONObject().put("profile",c.getInt(0)).put("size",c.getInt(1)));
        }
        json.put("categories",cat).put("days",days).put("events",events).put("fonts",fonts);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
    /** Validates every record BEFORE opening any destructive transaction. */
    public static void importData(CalStore store,byte[] raw)throws Exception{
        if(raw.length<=0||raw.length>2_000_000)throw new IllegalArgumentException("Datos de respaldo inválidos");
        JSONObject root=new JSONObject(new String(raw,StandardCharsets.UTF_8));
        if(root.getInt("version")!=1)throw new IllegalArgumentException("Versión de respaldo desconocida");
        JSONArray cats=root.getJSONArray("categories"),days=root.getJSONArray("days"),
            events=root.getJSONArray("events"),fonts=root.getJSONArray("fonts");
        if(cats.length()>1000||days.length()>7000||events.length()>12000||fonts.length()>2)throw new IllegalArgumentException("Demasiados registros");
        Set<Long> catIds=new HashSet<>(),eventIds=new HashSet<>();
        Map<Long,Integer> categoryOwner=new HashMap<>();
        Set<String> uniqueDays=new HashSet<>();
        for(int i=0;i<cats.length();i++){
            JSONObject o=cats.getJSONObject(i);long id=o.getLong("id");int profile=o.getInt("profile"),color=o.getInt("color");
            CalStore.checkProfile(profile);
            if(id<=0||!catIds.add(id)||!Palette.isAvailable(color))throw new IllegalArgumentException("Categoría duplicada o inválida");
            String name=o.getString("name"),shortName=o.getString("short");
            if(name.isBlank()||name.length()>46||shortName.isBlank()||shortName.length()>16)throw new IllegalArgumentException("Nombre de categoría inválido");
            categoryOwner.put(id,profile);
        }
        for(int i=0;i<days.length();i++){
            JSONObject o=days.getJSONObject(i);int profile=o.getInt("profile");CalStore.checkProfile(profile);
            String date=o.getString("date");LocalDate.parse(date);
            if(!uniqueDays.add(profile+"/"+date)||!Palette.isAvailable(o.getInt("color")))throw new IllegalArgumentException("Fecha duplicada o color inválido");
        }
        for(int i=0;i<events.length();i++){
            JSONObject o=events.getJSONObject(i);long id=o.getLong("id");int profile=o.getInt("profile");
            CalStore.checkProfile(profile);LocalDate.parse(o.getString("date"));
            if(id<=0||!eventIds.add(id))throw new IllegalArgumentException("Evento duplicado");
            String title=o.getString("title"),time=o.getString("time"),note=o.getString("note");
            if(title.isBlank()||title.length()>90||note.length()>500||
                (!time.isEmpty()&&!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")))throw new IllegalArgumentException("Evento inválido");
            if(!o.isNull("category") && !Integer.valueOf(profile).equals(categoryOwner.get(o.getLong("category"))))
                throw new IllegalArgumentException("Categoría de otro perfil o ausente");
        }
        if(fonts.length()!=2)throw new IllegalArgumentException("Se requiere un tamaño de letra por perfil");
        Set<Integer> fontProfiles=new HashSet<>();
        for(int i=0;i<fonts.length();i++){
            JSONObject o=fonts.getJSONObject(i);int profile=o.getInt("profile");CalStore.checkProfile(profile);
            if(!fontProfiles.add(profile))throw new IllegalArgumentException("Perfil de letra duplicado");
            if(o.getInt("size")<0||o.getInt("size")>2)throw new IllegalArgumentException("Letra inválida");
        }
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            db.delete("events",null,null);db.delete("day_colors",null,null);db.delete("categories",null,null);
            for(int i=0;i<cats.length();i++){
                JSONObject o=cats.getJSONObject(i);ContentValues v=new ContentValues();
                v.put("id",o.getLong("id"));v.put("profile_id",o.getInt("profile"));v.put("name",o.getString("name"));
                v.put("short_name",o.getString("short"));v.put("color",o.getInt("color"));db.insertOrThrow("categories",null,v);
            }
            for(int i=0;i<days.length();i++){
                JSONObject o=days.getJSONObject(i);ContentValues v=new ContentValues();
                v.put("profile_id",o.getInt("profile"));v.put("day",o.getString("date"));v.put("color",o.getInt("color"));
                db.insertOrThrow("day_colors",null,v);
            }
            for(int i=0;i<events.length();i++){
                JSONObject o=events.getJSONObject(i);ContentValues v=new ContentValues();
                v.put("id",o.getLong("id"));v.put("profile_id",o.getInt("profile"));v.put("day",o.getString("date"));
                v.put("title",o.getString("title"));v.put("event_time",o.getString("time"));v.put("note",o.getString("note"));
                if(o.isNull("category"))v.putNull("category_id");else v.put("category_id",o.getLong("category"));
                db.insertOrThrow("events",null,v);
            }
            for(int i=0;i<fonts.length();i++){
                JSONObject o=fonts.getJSONObject(i);ContentValues v=new ContentValues();v.put("font_size",o.getInt("size"));
                if(db.update("profiles",v,"id=?",new String[]{""+o.getInt("profile")})!=1)
                    throw new IllegalStateException("No se pudo restaurar el perfil");
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
