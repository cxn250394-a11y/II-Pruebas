package mx.charly.canal

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import dev.g000sha256.tdl.dto.*
import mx.charly.ui.Screen

class CanalActivity:Screen(){
 private val prefs by lazy{getSharedPreferences("organization",MODE_PRIVATE)}
 private var view="home";private var statusText:TextView?=null;private var list:LinearLayout?=null
 private var query="";private var mode=0;private var folder="Todas";private var activeChat=0L;private var history=emptyList<Message>();private var limit=100
 private var observed:TelegramSession?=null
 private val changed:()->Unit={runOnUiThread{if(!isDestroyed){statusText?.text=TelegramSession.current?.status?:"Desconectado";if(view=="list")renderChats();else if(view=="login"&&TelegramSession.current?.ready==true)chats()}}}
 override fun onCreate(state:Bundle?){super.onCreate(state);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);home()}
 override fun onResume(){super.onResume();observe();val id=intent.getLongExtra("chat",0);if(id!=0L&&TelegramSession.current?.ready==true){intent.removeExtra("chat");openChat(id)}}
 override fun onPause(){observed?.observers?.remove(changed);observed=null;super.onPause()}
 private fun observe(){val s=TelegramSession.current;if(observed!==s){observed?.observers?.remove(changed);observed=s;s?.observers?.add(changed)}}
 private fun prefsSet(key:String)=prefs.getStringSet(key,emptySet())?.toMutableSet()?:mutableSetOf()
 private fun toggle(key:String,id:Long){val set=prefsSet(key);if(!set.add(id.toString()))set.remove(id.toString());prefs.edit().putStringSet(key,set).apply()}
 private fun home(){view="home";layout("CanalDeck","Cliente no oficial de Telegram · TDLib")
  card("Tu cuenta, tus canales","Esta app sí incorpora TDLib. Se conecta solo después de que tú configures tu identidad de desarrollador e inicies sesión. No copia sesiones de Telegram ni de Nicegram.")
  statusText=label(TelegramSession.current?.status?:"Sin conexión",15f,accent)
  button("Abrir mis canales",true){if(TelegramSession.current?.ready==true)chats() else login()}
  button("Configurar api_id / api_hash"){credentials()};button("Conectar / iniciar sesión"){connect()}
  button("Desconectar esta aplicación"){stopService(Intent(this,ConnectionService::class.java));home()}
  button("Cerrar sesión de CanalDeck"){confirm("Cerrar sesión","Cierra únicamente la sesión de este cliente en Telegram. No cierra Telegram ni Nicegram."){TelegramSession.current?.logout();login()}}
  label("Alpha de lectura de canales: texto, fotos bajo demanda, carpetas locales y avisos por canal. Aún no incorpora llamadas, envío de mensajes, reproducción de video ni múltiples cuentas. No sustituye todavía a Nicegram.",13f,soft)
  button("Crear identidad de aplicación en Telegram"){website("https://my.telegram.org/apps")}
 }
 private fun credentials(){view="credentials";layout("Credenciales de aplicación","Se guardan cifradas en este teléfono, nunca en GitHub")
  label("Son api_id y api_hash de tu aplicación en my.telegram.org. NO son tu teléfono, código de acceso ni contraseña. No los pegues en el chat.",13f,soft)
  val id=field("api_id",numeric=true);val hash=field("api_hash",password=true)
  button("Guardar de forma local",true){val a=id.text.toString().trim();val b=hash.text.toString().trim();if(!Vault.validApi(a,b)){toast("api_id debe ser un número positivo y api_hash 32 caracteres hexadecimales");return@button}
   try{Vault(this).put("api-id",a);Vault(this).put("api-hash",b);hash.text.clear();home()}catch(_:Exception){toast("No se pudieron guardar de forma cifrada")}
  };button("Cancelar"){home()}
 }
 private fun connect(){try{val v=Vault(this);if(!Vault.validApi(v.get("api-id").orEmpty(),v.get("api-hash").orEmpty())){credentials();return}}catch(_:Exception){toast("No se pudo abrir el almacén cifrado");return}
  confirm("Conectar con Telegram","El código y contraseña se introducen solo aquí y se envían a Telegram mediante TDLib. Se guardará una sesión local. La conexión usa un servicio visible y se puede detener en cualquier momento."){
   try{startForegroundService(Intent(this,ConnectionService::class.java));android.os.Handler(mainLooper).postDelayed({if(!isDestroyed){observe();login()}},600)}catch(_:Exception){toast("Android no permitió iniciar la conexión")}
  }
 }
 private fun login(){view="login";layout("Acceso a Telegram","No se envían credenciales a un servidor nuestro")
  val s=TelegramSession.current;if(s==null){label("Primero configura las credenciales e inicia la conexión.");button("Conectar",true){connect()};button("Volver"){home()};return}
  observe();statusText=label(s.status,16f,accent)
  val input=field("Teléfono, código o contraseña según el estado",password=true)
  button("Enviar al paso actual",true){val value=input.text.toString();input.text.clear();s.authenticate(value)}
  button("Ver estado / continuar"){if(s.ready)chats() else login()};button("Volver"){home()}
  label("Usa teléfono internacional (+52...). Si Telegram solicita un estado no implementado, como correo o aprobación adicional, aparecerá su nombre; no se omite esa protección. Los códigos no se guardan.",13f,soft)
 }
 private fun chats(){view="list";layout("Mis canales y chats","Organización local por ID real de Telegram")
  observe();button("Inicio"){home()};statusText=label(TelegramSession.current?.status?:"Sin conexión",12f,accent)
  menu(listOf("Todo","Solo canales","Favoritos","Con avisos"),mode){mode=it;renderChats()}
  val folders=listOf("Todas")+prefsSet("folders").sorted();menu(folders,folders.indexOf(folder).coerceAtLeast(0)){folder=folders[it];renderChats()}
  val search=field("Buscar título o último mensaje",query);search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun afterTextChanged(s:android.text.Editable?){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){query=s.toString();renderChats()}})
  button("Crear carpeta"){prompt("Carpeta local"){s->val name=s.trim();if(name.length in 1..40&&name!="Todas"){val set=prefsSet("folders");set.add(name);prefs.edit().putStringSet("folders",set).apply();chats()}else toast("Nombre inválido")}}
  button("Cargar / actualizar lista"){TelegramSession.current?.refresh(limit)}
  list=column();body.addView(list);renderChats()
  button("Cargar más (hasta 1,000)"){limit=(limit+100).coerceAtMost(1000);TelegramSession.current?.refresh(limit)}
 }
 private fun renderChats(){val holder=list?:return;if(view!="list")return;holder.removeAllViews()
  val fav=prefsSet("favorite");val alerts=prefsSet("notify");val rows=TelegramSession.current?.chats?.values.orEmpty().filter{c->
   val channel=(c.type as? ChatTypeSupergroup)?.isChannel==true
   (when(mode){1->channel;2->c.id.toString() in fav;3->c.id.toString() in alerts;else->true})&&
    (folder=="Todas"||prefs.getString("folder_${c.id}","")==folder)&&
    (query.isBlank()||(c.title+" "+TelegramSession.preview(c.lastMessage?.content)).contains(query,true))
  }.sortedWith(compareByDescending<Chat>{it.id.toString() in fav}.thenByDescending{it.lastMessage?.date?:0})
  holder.addView(text("${rows.size} conversaciones cargadas · favoritos primero",12f,accent))
  rows.forEach{c->holder.addView(Button(this).apply{text=(if(c.id.toString() in fav)"★ " else "")+c.title+"  (${c.unreadCount})\n"+TelegramSession.preview(c.lastMessage?.content).take(140);isAllCaps=false;setTextColor(android.graphics.Color.WHITE);minHeight=dp(80);backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(23,36,53));setOnClickListener{openChat(c.id)};setOnLongClickListener{organize(c.id);true}})}
 }
 private fun organize(id:Long){val choices=arrayOf("Favorito: alternar","Avisos de este canal: alternar","Mover a carpeta")
  AlertDialog.Builder(this).setTitle("Organizar conversación").setItems(choices){_,i->when(i){0->{toggle("favorite",id);renderChats()};1->{toggle("notify",id);if(Build.VERSION.SDK_INT>=33&&id.toString() in prefsSet("notify"))requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),8);renderChats()};else->{val fs=listOf("Sin carpeta")+prefsSet("folders").sorted();AlertDialog.Builder(this).setTitle("Carpeta local").setItems(fs.toTypedArray()){_,n->prefs.edit().putString("folder_$id",if(n==0)"" else fs[n]).apply();renderChats()}.show()}}}.show()
 }
 private fun openChat(id:Long){activeChat=id;history=emptyList();view="history";historyScreen("Cargando historial…");TelegramSession.current?.history(id){messages,error->if(!isDestroyed&&activeChat==id&&view=="history"){if(messages!=null)history=messages;historyScreen(error)}}}
 private fun historyScreen(error:String?=null){layout(TelegramSession.current?.chats?.get(activeChat)?.title?:"Conversación","Lectura directa de Telegram · últimos mensajes primero")
  button("Volver a canales"){chats()};button("Favorito / avisos / carpeta"){organize(activeChat)}
  if(error!=null)label(error,14f,soft)
  history.distinctBy{it.id}.sortedByDescending{it.id}.forEach{m->val time=java.text.SimpleDateFormat("dd/MM HH:mm",java.util.Locale("es","MX")).format(java.util.Date(m.date.toLong()*1000));card(TelegramSession.preview(m.content).ifBlank{"[Contenido no representado]"},time){val photo=m.content as? MessagePhoto;if(photo!=null){val file=photo.photo.sizes.lastOrNull()?.photo;if(file==null||file.size>20_000_000)toast("Foto no disponible o mayor a 20 MB")else TelegramSession.current?.photo(file.id){path->if(path==null)toast("No se pudo descargar")else displayPhoto(path)}}}}
  button("Cargar mensajes anteriores"){val before=history.minOfOrNull{it.id}?:0;TelegramSession.current?.history(activeChat,before){m,e->if(!isDestroyed&&view=="history"){if(m!=null)history=(history+m).distinctBy{it.id};historyScreen(e)}}}
  label("No marca mensajes como leídos ni envía nada. Las carpetas y favoritos se quedan en este cliente.",12f,soft)
 }
 private fun displayPhoto(path:String){if(isDestroyed)return
  try{val file=java.io.File(path);require(file.canonicalPath.startsWith(filesDir.canonicalPath+"/"));val opt=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(path,opt);var sample=1;while(opt.outWidth/sample>1400||opt.outHeight/sample>1400)sample*=2;opt.inJustDecodeBounds=false;opt.inSampleSize=sample
   val bitmap=android.graphics.BitmapFactory.decodeFile(path,opt)?:error("Foto inválida");val image=ImageView(this).apply{setImageBitmap(bitmap);adjustViewBounds=true}
   AlertDialog.Builder(this).setView(image).setPositiveButton("Cerrar",null).show()
  }catch(_:Exception){toast("No se pudo representar la foto")}
 }
}
