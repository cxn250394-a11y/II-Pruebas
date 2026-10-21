package mx.charly.ui

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File

/** Atomic writes; corrupted input fails visibly instead of silently replacing user data. */
class LocalJson(context:Context,name:String){
    private val file=AtomicFile(File(context.filesDir,name))
    @Synchronized fun read():JSONArray { if(!file.baseFile.exists())return JSONArray();return file.openRead().use{JSONArray(it.bufferedReader().readText())} }
    @Synchronized fun write(data:JSONArray){ val out=file.startWrite();try{out.write(data.toString().toByteArray(Charsets.UTF_8));file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e} }
}
