package mx.charly.canal

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import dev.g000sha256.tdl.dto.*
import mx.charly.ui.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native client UI. Demo cannot initiate requests, send messages or persist organization. */
class CanalActivity : Screen() {
    private var screen = "home"
    private var demo = false
    private var kind = "all"
    private var folder = "*"
    private var query = ""
    private var sort = 0
    private var limit = 200
    private var activeChat = 0L
    private var history = emptyList<Message>()
    private var rows = emptyList<ChatRow>()
    private var statusLabel: TextView? = null
    private var countLabel: TextView? = null
    private var chatsList: ListView? = null
    private var messagesList: ListView? = null
    private var composer: EditText? = null
    private var replyLabel: TextView? = null
    private var sendButton: Button? = null
    private var authButton: Button? = null
    private var reply: Message? = null
    private var historyError: TextView? = null
    private var historyTicket = 0
    private var searchHistory = false
    private var foreground = false
    private var observed: TelegramSession? = null
    private var lastAuthType = ""
    private var lastAccount = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val drafts = mutableMapOf<Pair<Long,Long>,String>()
    private var mediaPlayer: MediaPlayer? = null
    private var mediaDialog: AlertDialog? = null
    private var protectedMedia = false
    private var backupPassword: CharArray? = null
    private var backupMode = ""
    private val exportCode = 551
    private val importCode = 552
    private val uiChange = Runnable { refreshState() }
    private val readVisible = Runnable { acknowledgeVisible() }
    private val changed: () -> Unit = { handler.removeCallbacks(uiChange); handler.postDelayed(uiChange,120) }
    private val messagesChanged: (Long) -> Unit = { id ->
        if (foreground && screen == "history" && activeChat == id && !searchHistory) {
            val s=TelegramSession.current
            if(s!=null){history=(history+s.recent.filterKeys{it.first==id}.values).associateBy{it.id}.values.filter{(id to it.id) !in s.removed}.sortedBy{it.id}.takeLast(240);refreshMessages()}
        }
    }
    private val org: Organization get() = Organization(this,TelegramSession.current?.accountId ?: 0)

    override fun onCreate(state: Bundle?) { super.onCreate(state);home() }
    override fun onResume() { super.onResume();foreground=true;observe();if(screen=="history")TelegramSession.current?.showChat(activeChat);refreshState();consumeIntent(intent) }
    override fun onPause() {
        foreground=false;saveDraft();handler.removeCallbacks(readVisible);observed?.showChat(0)
        observed?.observers?.remove(changed);observed?.messageObservers?.remove(messagesChanged);observed=null
        mediaDialog?.dismiss();mediaDialog=null;mediaPlayer?.release();mediaPlayer=null
        super.onPause()
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null);super.onDestroy() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);consumeIntent(intent) }
    @Deprecated("Native Activity compatibility")
    override fun onBackPressed() { if(screen=="history")chats();else if(screen!="home")home();else super.onBackPressed() }
    private fun consumeIntent(i: Intent) {
        val id=i.getLongExtra("chat",0)
        val s=TelegramSession.current
        if(id!=0L && s?.ready==true){i.removeExtra("chat");if(id in s.chats){demo=false;openChat(id)}else toast("Esta conversación todavía no está cargada")}
    }
    private fun observe() {
        val s=TelegramSession.current
        if(observed!==s){observed?.observers?.remove(changed);observed?.messageObservers?.remove(messagesChanged);observed=s;s?.observers?.add(changed);s?.messageObservers?.add(messagesChanged)}
    }
    private fun refreshState() {
        if(isDestroyed||!foreground)return
        observe();secure()
        val s=TelegramSession.current
        statusLabel?.text=if(s==null)ConnectionService.lastError ?: "Desconectado" else if(s.ready) "${s.accountName} · ${s.network}" else s.status
        authButton?.isEnabled=s?.authBusy!=true
        if(s?.ready==true && lastAccount!=s.accountId){lastAccount=s.accountId;folder="*";query="";drafts.clear()}
        when(screen){
            "login" -> if(s?.ready==true)chats() else if(lastAuthType!=s?.state?.javaClass?.simpleName.orEmpty())login()
            "list" -> refreshChats()
            "history" -> if(s?.ready!=true){history=emptyList();drafts.clear();home()}
        }
    }
    private fun secure() {
        val current = TelegramSession.current
        val chat=current?.chats?.get(activeChat)
        val protectedChat=screen=="history" && (chat?.hasProtectedContent==true || chat?.type is ChatTypeSecret)
        val sensitive=screen=="credentials" || screen=="login" || protectedMedia || protectedChat
        val allow= !sensitive && (demo || current==null || org.screenshots)
        allowDialogScreenshots=allow
        if(allow) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun saveDraft(){val c=composer;if(screen=="history"&&c!=null)drafts[(TelegramSession.current?.accountId?:0) to activeChat]=c.text.toString()}
    private fun page(name:String,title:String,subtitle:String){
        saveDraft();if(screen=="history"&&name!="history")TelegramSession.current?.showChat(0)
        screen=name;statusLabel=null;countLabel=null;chatsList=null;messagesList=null;composer=null;authButton=null
        layout(title,subtitle);secure()
    }
    private fun linearPage(name:String,title:String,subtitle:String){page(name,title,subtitle);root.removeViewAt(1);body=column().apply{setPadding(dp(16),0,dp(16),dp(8))};root.addView(body,LinearLayout.LayoutParams(-1,0,1f))}
    private fun nav(){
        val n=row()
        listOf("Chats","Canales","Carpetas","Ajustes").forEachIndexed{i,t->n.addView(Button(this).apply{
            text=t;isAllCaps=false;textSize=12f;minHeight=dp(50);setPadding(0,0,0,0);setTextColor(Color.WHITE)
            backgroundTintList=ColorStateList.valueOf(Color.rgb(25,39,57));setOnClickListener{when(i){0->{kind="all";chats()};1->{kind="channel";chats()};2->folders();else->settings()}}
        },LinearLayout.LayoutParams(0,-2,1f))};root.addView(n)
    }
    private fun home(){
        page("home","CanalDeck","Tu Telegram y tus canales · Personal 0.3.2")
        card("Tus canales, sin perder lo nuevo","Cliente propio con TDLib. Una cuenta real, carpetas locales, favoritos, lectura de canales y mensajes de texto. No es la aplicación Nicegram ni una app oficial de Telegram.")
        statusLabel=label(TelegramSession.current?.let{if(it.ready)"${it.accountName} · ${it.network}" else it.status} ?: "Sin sesión iniciada",15f,accent)
        button(if(TelegramSession.current?.ready==true)"Abrir mis conversaciones" else "Empezar · conectar mi cuenta",true){demo=false;if(TelegramSession.current?.ready==true)chats()else connect()}
        button("Ver diseño con ejemplos — sin iniciar sesión"){demo=true;kind="all";query="";folder="*";chats()}
        button("Ayuda para activar mi cuenta"){guide()}
        button("Identidad de aplicación / configuración"){demo=false;credentials()}
        label("Primera vez: crea tu identidad gratuita de aplicación en Telegram y pégala solo aquí. Después introduces tu teléfono, código y, si aplica, contraseña. No necesitas enviarlos a este chat.",13f,soft)
        label("Esta beta no incluye llamadas, varias cuentas simultáneas, stickers animados, envío de adjuntos ni chats secretos. Conserva Telegram/Nicegram mientras pruebas.",12f,soft)
        if(TelegramSession.current!=null)button("Ajustes y desconexión"){demo=false;settings()}
    }
    private fun guide(){
        page("guide","Activar CanalDeck","Tres pasos · una sola vez")
        card("1 · Registrar tu aplicación","Abre my.telegram.org → API development tools. Usa App title: CanalDeck; Short name: canaldeckpersonal; Platform: Android. Si ya tienes una aplicación propia registrada, usa esa identidad.")
        button("Abrir página oficial de Telegram",true){website("https://my.telegram.org/apps")}
        card("2 · Guardar api_id y api_hash aquí","Son la identidad de tu aplicación, no el código de inicio de sesión. Se almacenan cifradas con Android Keystore. No uses identidades publicadas por terceros ni pegues estos datos en el chat.")
        card("3 · Iniciar sesión con tu cuenta","CanalDeck te pide teléfono con prefijo internacional, el código que indique Telegram y tu contraseña de verificación en dos pasos si está activa. La sesión de Telegram oficial no se borra.")
        button("Ya tengo mi identidad · introducirla"){credentials()};button("Volver"){home()}
    }
    private fun credentials(){
        if(TelegramSession.current!=null){toast("Desconecta CanalDeck antes de cambiar la identidad de aplicación");settings();return}
        page("credentials","Identidad de aplicación","Se guarda cifrada en este teléfono")
        label("api_id y api_hash de tu propia aplicación en Telegram. No son tu teléfono ni tu contraseña.",14f,soft)
        val id=field("api_id (número)");id.inputType=InputType.TYPE_CLASS_NUMBER;id.isSaveEnabled=false
        val hash=field("api_hash (32 letras y números)",password=true)
        button("Guardar y continuar",true){
            val a=id.text.toString().trim();val b=hash.text.toString().trim()
            if(!Vault.validApi(a,b)){toast("Revisa el número api_id y los 32 caracteres hexadecimales de api_hash");return@button}
            try{val v=Vault(this);v.put("api-id",a);v.put("api-hash",b);hash.text.clear();connect()}catch(_:Exception){toast("No se pudo guardar la configuración cifrada")}
        }
        button("Dónde consigo estos datos"){guide()};button("Volver"){home()}
    }
    private fun connect(){
        val s=TelegramSession.current
        if(s!=null){if(s.closing)toast("La conexión anterior se está cerrando. Vuelve a tocar Conectar.")else login();return}
        try{val v=Vault(this);if(!Vault.validApi(v.get("api-id").orEmpty(),v.get("api-hash").orEmpty())){credentials();return}}
        catch(_:Exception){toast("No se pudo abrir el almacén cifrado");return}
        confirm("Conectar con Telegram","Esta app guardará una sesión local cifrada y se conectará a Telegram mediante TDLib. Mantiene una notificación visible mientras la conexión está activa. Puedes desconectar cuando quieras."){
            try{startForegroundService(Intent(this,ConnectionService::class.java));page("login","Conectando","Iniciando el motor nativo…");lastAuthType="initial";handler.postDelayed({if(!isDestroyed){observe();login()}},500)}
            catch(_:Exception){toast("Android no permitió iniciar la conexión");home()}
        }
    }
    private fun login(){
        page("login","Acceso a Telegram","El código y contraseña se introducen solo en tu teléfono")
        val s=TelegramSession.current
        if(s==null){label(ConnectionService.lastError ?: "No hay conexión activa");button("Conectar",true){connect()};button("Volver"){home()};return}
        observe();val state=s.state;lastAuthType=state?.javaClass?.simpleName.orEmpty();statusLabel=label(s.status,17f,accent)
        if(s.ready){chats();return}
        val hint=when(state){
            is AuthorizationStateWaitPhoneNumber->"Teléfono internacional: +52…"
            is AuthorizationStateWaitCode->"Código de acceso de Telegram"
            is AuthorizationStateWaitPassword->"Contraseña de verificación en dos pasos"
            is AuthorizationStateWaitEmailAddress->"Correo solicitado por Telegram"
            is AuthorizationStateWaitEmailCode->"Código recibido por correo"
            else->null
        }
        if(state is AuthorizationStateWaitCode)label("El destino del código lo decide Telegram. Revisa tu app oficial y la vía indicada por Telegram; no siempre llega por SMS.",13f,soft)
        if(state is AuthorizationStateWaitPassword && state.passwordHint.isNotBlank())label("Pista de contraseña: ${state.passwordHint}",13f,soft)
        if(hint!=null){
            val secret=state !is AuthorizationStateWaitPhoneNumber && state !is AuthorizationStateWaitEmailAddress
            val input=field(hint,password=secret);input.isSaveEnabled=false;input.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            if(state is AuthorizationStateWaitPhoneNumber)input.inputType=InputType.TYPE_CLASS_PHONE
            if(state is AuthorizationStateWaitEmailAddress)input.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            authButton=button("Continuar",true){if(s.state?.javaClass!=state?.javaClass){login();return@button};val value=input.text.toString();input.text.clear();s.authenticate(value)}
            if(state is AuthorizationStateWaitCode)button("Solicitar otro código"){confirm("Solicitar código","Telegram aplica límites y puede pedirte esperar. ¿Solicitar un nuevo código?"){s.resendCode()}}
        }else label("Si Telegram exige registro nuevo, compra, passkey u otra comprobación no soportada, esta beta no la omite. Conserva la aplicación oficial para esos casos.",13f,soft)
        button("Actualizar estado"){login()};button("Volver"){home()}
    }
    private fun sampleRows()=listOf(
        ChatRow(-1,"Noticias del día","Nueva publicación del canal de ejemplo",System.currentTimeMillis()/1000,5,"channel",true,important=true),
        ChatRow(-2,"Amigos","¿Cómo va tu día?",System.currentTimeMillis()/1000-80,2,"group"),
        ChatRow(-3,"Música","Un nuevo álbum para escuchar",System.currentTimeMillis()/1000-180,0,"channel"),
        ChatRow(-4,"Deportes","Una actualización de ejemplo",System.currentTimeMillis()/1000-240,12,"channel"),
        ChatRow(-5,"Contacto de ejemplo","Nos vemos más tarde",System.currentTimeMillis()/1000-300,1,"private")
    )
    private fun chats(){
        if(!demo&&TelegramSession.current?.ready!=true){login();return}
        linearPage("list",if(demo)"CanalDeck · DEMO" else "CanalDeck",if(demo)"Ejemplos ficticios · no hay cuenta conectada" else "Carpetas y favoritos exclusivos de este cliente")
        statusLabel=label(if(demo)"Modo de ejemplo" else "${TelegramSession.current?.accountName} · ${TelegramSession.current?.network}",12f,accent)
        val kinds=listOf("all","channel","group","private","unread","favorite","important","important-unread")
        menu(listOf("Todos","Canales","Grupos","Privados","No leídos","Favoritos","Prioritarios","Prioritarios nuevos"),kinds.indexOf(kind).coerceAtLeast(0)){kind=kinds[it];refreshChats()}
        val input=field("Buscar chats, canales o último mensaje",query)
        input.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun afterTextChanged(s:android.text.Editable?){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){query=s.toString();refreshChats()}
        })
        val chips=row();val options=listOf("*" to "Todas", "" to "Sin carpeta")+(if(demo)emptyList()else org.folders())
        options.forEach{(id,name)->chips.addView(Button(this).apply{text=name;isAllCaps=false;textSize=12f;setTextColor(if(folder==id)ink else Color.WHITE);backgroundTintList=ColorStateList.valueOf(if(folder==id)accent else Color.rgb(25,39,57));setOnClickListener{folder=id;chats()}})}
        body.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(chips)})
        countLabel=label("",12f,soft)
        chatsList=ListView(this).apply{divider=null;dividerHeight=dp(8);adapter=chatAdapter;clipToPadding=false}
        body.addView(chatsList,LinearLayout.LayoutParams(-1,0,1f))
        val actions=row()
        actions.addView(Button(this).apply{text="Ordenar";isAllCaps=false;setOnClickListener{dialog().setTitle("Orden de conversaciones").setSingleChoiceItems(arrayOf("Prioritarios, favoritos y recientes","Nombre","Más no leídos"),sort){d,n->sort=n;d.dismiss();refreshChats()}.show()}},LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Button(this).apply{text=if(demo)"Salir del demo" else "Cargar más";isAllCaps=false;setOnClickListener{if(demo){demo=false;home()}else{limit=(limit+200).coerceAtMost(1000);TelegramSession.current?.refresh(limit);toast("Solicitada carga de hasta $limit conversaciones")}}},LinearLayout.LayoutParams(0,-2,1f))
        body.addView(actions);nav();refreshChats()
    }
    private fun refreshChats(){
        if(screen!="list"||chatsList==null)return
        val all=if(demo)sampleRows()else TelegramSession.current?.chats?.values.orEmpty().map{c->
            val type=when(val t=c.type){is ChatTypeSupergroup->if(t.isChannel)"channel"else"group";is ChatTypeBasicGroup->"group";else->"private"}
            ChatRow(c.id,c.title,TelegramSession.preview(c.lastMessage?.content),c.lastMessage?.date?.toLong()?:0,c.unreadCount,type,org.favorite(c.id),org.folder(c.id),org.important(c.id))
        }
        rows=DeckRules.filter(all,query,kind,folder,sort)
        countLabel?.text=if(rows.isEmpty())"Sin coincidencias. Cambia filtros o carga conversaciones." else "${rows.size} conversaciones · mantén pulsada una para organizar"
        chatAdapter.notifyDataSetChanged()
    }
    private val chatAdapter=object:BaseAdapter(){
        override fun getCount()=rows.size
        override fun getItem(p:Int)=rows[p]
        override fun getItemId(p:Int)=rows[p].id
        override fun getView(p:Int,recycled:View?,parent:ViewGroup?):View{
            val c=rows[p];val box=column().apply{setPadding(dp(15),dp(10),dp(15),dp(10));background=GradientDrawable().apply{setColor(Color.rgb(23,36,53));cornerRadius=dp(16).toFloat()}}
            box.addView(text((if(c.important)"●  " else "")+(if(c.favorite)"★  " else "")+c.title,17f,Color.WHITE,true).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END})
            box.addView(text(c.preview.ifBlank{"Sin mensaje reciente"},13f,soft).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END})
            box.addView(text("${when(c.kind){"channel"->"CANAL";"group"->"GRUPO";else->"PRIVADO"}}  ·  ${c.unread} no leídos${if(c.important)" · PRIORIDAD"else""}",11f,if(c.important||c.unread>0)accent else soft))
            box.isFocusable=true;box.setOnClickListener{if(demo)toast("Este es un ejemplo. Conecta tu cuenta para abrir mensajes reales.")else openChat(c.id)}
            box.setOnLongClickListener{if(demo)toast("Las acciones reales se activan al conectar tu cuenta")else organize(c.id);true};return box
        }
    }
    private fun folders(){
        page("folders","Tus carpetas","Organización local · no cambia las carpetas de Telegram")
        if(demo||TelegramSession.current?.ready!=true){label("Conecta tu cuenta para crear carpetas reales.");button("Volver"){if(demo)chats()else home()};return}
        button("+ Crear carpeta",true){prompt("Nombre de carpeta"){name->if(org.createFolder(name)==null)toast("Usa un nombre único de 1 a 40 caracteres")else folders()}}
        org.folders().forEach{(id,name)->
            val count=TelegramSession.current?.chats?.keys?.count{org.folder(it)==id}?:0
            card(name,"$count conversaciones cargadas"){folder=id;kind="all";chats()}
            button("Orden y opciones · $name"){dialog().setItems(arrayOf("↑ Subir", "↓ Bajar", "Cambiar nombre", "Eliminar carpeta")){_,choice->
                when(choice) {
                    0 -> { if(!org.moveFolder(id,-1))toast("Ya es la primera carpeta");folders() }
                    1 -> { if(!org.moveFolder(id,1))toast("Ya es la última carpeta");folders() }
                    2 -> prompt("Nuevo nombre"){n->if(org.renameFolder(id,n))folders()else toast("Nombre duplicado o inválido")}
                    else -> confirm("Eliminar carpeta","Solo elimina esta carpeta local; no borra chats, mensajes ni favoritos."){org.deleteFolder(id);if(folder==id)folder="*";folders()}
                }
            }.show()}
        }
        label("Para mover un canal: mantén pulsada su tarjeta → Carpeta. Puedes poner nombres como Trabajo, Música o Deportes.",13f,soft);nav()
    }
    private fun organize(id:Long){
        if(TelegramSession.current?.ready!=true)return
        dialog().setTitle(TelegramSession.current?.chats?.get(id)?.title ?: "Organizar")
            .setItems(arrayOf(if(org.favorite(id))"Quitar favorito"else"Marcar favorito",
                if(org.important(id))"Quitar prioridad"else"Prioridad: arriba y aviso destacado",
                "Mover a carpeta","Configurar avisos de este chat")){_,i->when(i){
                0->{org.toggleFavorite(id);refreshChats()}
                1->{org.toggleImportant(id);ConnectionService.current?.clear(id);refreshChats()}
                2->{val fs=listOf("" to "Sin carpeta")+org.folders();dialog().setTitle("Carpeta local").setItems(fs.map{it.second}.toTypedArray()){_,n->org.assign(id,fs[n].first);refreshChats()}.setNegativeButton("Cancelar",null).show()}
                else->noticeSettings(id)
            }}.show()
    }
    private fun noticeSettings(id:Long){
        val box=column().apply{setPadding(dp(20),0,dp(20),0)}
        val choices=RadioGroup(this);val names=listOf("Apagados","Cada publicación nueva","Solo si contiene mis palabras")
        names.forEachIndexed{i,s->choices.addView(RadioButton(this).apply{this.id=200+i;text=s;setTextColor(Color.WHITE)})};choices.check(200+org.mode(id).ordinal);box.addView(choices)
        val words=EditText(this).apply{hint="Palabras separadas por comas";setText(org.words(id));setTextColor(Color.WHITE);setHintTextColor(soft)};box.addView(words)
        box.addView(text("Son avisos de CanalDeck, separados de Telegram/Nicegram. Dependen de que esta conexión siga activa.",12f,soft))
        dialog().setTitle("Avisos de la conversación").setView(box).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar"){_,_->
                val mode=NoticeMode.entries.getOrElse(choices.checkedRadioButtonId-200){NoticeMode.OFF}
                org.mode(id,mode);org.words(id,words.text.toString());ConnectionService.current?.clear(id)
                if(mode!=NoticeMode.OFF&&Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),9)
                toast("Preferencias guardadas")
            }.setNeutralButton("Sonido Android"){_,_->
                val i=Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName).putExtra(Settings.EXTRA_CHANNEL_ID,ConnectionService.channel(id,org.important(id)))
                try{startActivity(i)}catch(_:Exception){toast("El canal de Android aparece después de su primer aviso")}
            }.show()
    }
    private fun settings(){
        page("settings","Ajustes","Tu sesión y tu privacidad")
        val s=TelegramSession.current
        statusLabel=label(s?.let{if(it.ready)"${it.accountName} · ${it.network}"else it.status} ?: "Sin conexión",15f,accent)
        if(s?.ready==true&&!demo){
            label("Notificaciones de CanalDeck",18f)
            val age=org.lastLocalNoticeAgeMillis()
            val timing=if(age==null)"Sin observaciones recientes" else "Último aviso solicitado: ${age/1000} s desde la fecha del mensaje · medición local aproximada"
            label("Diagnóstico: $timing. No mide cuándo Android mostró el banner ni garantiza puntualidad.",12f,soft)
            body.addView(Switch(this).apply{text="Mostrar texto en los avisos";setTextColor(Color.WHITE);isChecked=org.previews;minHeight=dp(48);setOnCheckedChangeListener{_,v->org.previews=v;ConnectionService.current?.clearAll()}})
            body.addView(Switch(this).apply{text="Horario silencioso";setTextColor(Color.WHITE);isChecked=org.quietEnabled;minHeight=dp(48);setOnCheckedChangeListener{_,v->org.quietEnabled=v}})
            fun time(n:Int)=String.format(Locale.ROOT,"%02d:%02d",n/60,n%60)
            val start=field("Desde HH:mm",time(org.quietStart()));val end=field("Hasta HH:mm",time(org.quietEnd()))
            button("Guardar horario"){if(org.hours(start.text.toString(),end.text.toString()))toast("Horario guardado")else toast("Usa HH:mm válido y horas diferentes")}
            button("Cargar también conversaciones archivadas"){s.refresh(1000,true);toast("Se solicitó el archivo. Revisa Chats.")}
            button("Borrar los avisos actuales de CanalDeck"){ConnectionService.current?.clearAll()}
        }
        button("Permisos y notificaciones de Android"){try{startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}catch(_:Exception){toast("Revisa CanalDeck en Ajustes de Android")}}
        label("Mantén pulsado un chat → Prioridad para verlo arriba; puedes filtrar solo prioritarios. Prioridad NO activa avisos apagados. Los avisos normales y prioritarios usan grupos Android separados; el sistema puede seguir agrupándolos.",13f,soft)
        label("Para activar un canal: mantén pulsado el chat → Configurar avisos. Todos empiezan apagados. La vista previa está oculta de fábrica.",13f,soft)
        card("Conexión visible, no garantía de entrega instantánea","No hay Firebase/FCM configurado. Esta beta usa una conexión visible en segundo plano; HyperOS puede cerrarla o retrasarla. No reemplaza todavía las alertas del cliente oficial.")
        if(s!=null){button("Desconectar CanalDeck"){stopService(Intent(this,ConnectionService::class.java));home()};button("Cerrar únicamente esta sesión"){confirm("Cerrar sesión","Cierra CanalDeck en Telegram; no cierra Telegram oficial ni Nicegram. Tus carpetas locales siguen separadas por cuenta."){drafts.clear();ConnectionService.current?.clearAll();s.logout();home()}}}
        else button("Conectar mi cuenta",true){demo=false;connect()}
        button("Ayuda de activación"){guide()}
        button("Licencias y alcance"){dialog().setTitle("CanalDeck Personal 0.3.1 beta").setMessage("Cliente independiente, no afiliado a Nicegram ni Telegram. TDLib: Boost 1.0. TDL Coroutines y Kotlin Coroutines: Apache 2.0. Licencias incluidas en assets. Una cuenta. Sin llamadas, chats secretos, exportación de contenido protegido ni pagos. La beta usa firma de prueba y no ha validado tu cuenta real.").setPositiveButton("Cerrar",null).show()}
        nav()
    }
    private fun openChat(id:Long){
        val s=TelegramSession.current ?: return
        if(!s.ready)return
        saveDraft();composer=null
        activeChat=id;history=emptyList();reply=null;searchHistory=false;historyTicket++;historyLayout();s.showChat(id);loadHistory()
    }
    private fun historyLayout(){
        val chat=TelegramSession.current?.chats?.get(activeChat)
        linearPage("history",chat?.title ?: "Conversación","Lectura real de Telegram · los mensajes visibles se marcan leídos")
        val tools=row()
        fun tool(name:String,action:()->Unit){tools.addView(Button(this).apply{text=name;isAllCaps=false;textSize=12f;setOnClickListener{action()}},LinearLayout.LayoutParams(0,-2,1f))}
        tool("‹ Chats"){chats()};tool("Organizar"){organize(activeChat)};tool("Buscar"){prompt("Buscar en esta conversación"){q->if(q.isNotBlank()){searchHistory=true;val id=activeChat;val ticket=++historyTicket;TelegramSession.current?.searchMessages(id,q){m,e->if(!isDestroyed&&screen=="history"&&id==activeChat&&ticket==historyTicket){history=m.orEmpty().sortedBy{it.id};historyError?.text=e ?: "Resultados de búsqueda: ${history.size}";refreshMessages()}}}}}
        body.addView(tools)
        historyError=label("Cargando mensajes…",12f,accent)
        button("Cargar anteriores / salir de búsqueda"){if(searchHistory){searchHistory=false;history=emptyList();loadHistory()}else loadHistory(older=true)}
        messagesList=ListView(this).apply{divider=null;dividerHeight=dp(8);adapter=messageAdapter;transcriptMode=AbsListView.TRANSCRIPT_MODE_NORMAL
            setOnScrollListener(object:AbsListView.OnScrollListener{override fun onScrollStateChanged(v:AbsListView?,state:Int){scheduleRead()};override fun onScroll(v:AbsListView?,first:Int,visible:Int,total:Int){scheduleRead()}})
        }
        body.addView(messagesList,LinearLayout.LayoutParams(-1,0,1f))
        replyLabel=label("",12f,accent);replyLabel?.setOnClickListener{reply=null;replyLabel?.text=""}
        val isChannel=(chat?.type as? ChatTypeSupergroup)?.isChannel==true
        if(isChannel||chat?.viewAsTopics==true){label(if(isChannel)"Canal de lectura · publicación administrativa no implementada"else"Los foros con temas se consultan, pero no se envía desde esta vista",12f,soft)}
        else{
            composer=EditText(this).apply{hint="Escribe un mensaje";setTextColor(Color.WHITE);setHintTextColor(soft);minLines=1;maxLines=4;isSingleLine=false;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;isSaveEnabled=false;filters=arrayOf(android.text.InputFilter.LengthFilter(4096));setText(drafts[(TelegramSession.current?.accountId?:0) to activeChat].orEmpty())}
            body.addView(composer)
            sendButton=button("Enviar texto",true){send()}
        }
        refreshMessages()
    }
    private fun loadHistory(older:Boolean=false){
        val s=TelegramSession.current ?: return;val id=activeChat;val ticket=++historyTicket;val before=if(older)history.minOfOrNull{it.id}?:0 else 0
        s.history(id,before){messages,error->
            if(isDestroyed||screen!="history"||id!=activeChat||ticket!=historyTicket||TelegramSession.current!==s)return@history
            val firstId=history.getOrNull(messagesList?.firstVisiblePosition?:0)?.id
            if(messages!=null)history=(history+messages).associateBy{it.id}.values.filter{(id to it.id) !in s.removed}.sortedBy{it.id}.let{if(older)it.take(240)else it.takeLast(240)}
            historyError?.text=error ?: if(messages.isNullOrEmpty())"Sin más mensajes en esta respuesta" else "${history.size} mensajes cargados"
            refreshMessages()
            if(older&&firstId!=null)messagesList?.setSelection(history.indexOfFirst{it.id==firstId}.coerceAtLeast(0))else if(!older)messagesList?.setSelection((history.size-1).coerceAtLeast(0))
        }
    }
    private fun refreshMessages(){if(screen!="history"||messagesList==null)return;messageAdapter.notifyDataSetChanged();scheduleRead()}
    private fun scheduleRead(){handler.removeCallbacks(readVisible);handler.postDelayed(readVisible,500)}
    private fun acknowledgeVisible(){
        if(!foreground||screen!="history"||!hasWindowFocus())return
        val list=messagesList?:return;val a=list.firstVisiblePosition.coerceAtLeast(0);val b=list.lastVisiblePosition.coerceAtMost(history.lastIndex)
        if(a<=b&&a in history.indices)TelegramSession.current?.viewed(activeChat,history.subList(a,b+1).map{it.id}.toLongArray())
    }
    private val messageAdapter=object:BaseAdapter(){
        override fun getCount()=history.size
        override fun getItem(p:Int)=history[p]
        override fun getItemId(p:Int)=history[p].id
        override fun getView(p:Int,recycled:View?,parent:ViewGroup?):View{
            val m=history[p];val c=column().apply{setPadding(dp(14),dp(10),dp(14),dp(10));background=GradientDrawable().apply{setColor(if(m.isOutgoing)Color.rgb(26,63,65)else Color.rgb(23,36,53));cornerRadius=dp(14).toFloat()}}
            val label=if(m.isOutgoing)"Tú" else m.authorSignature.ifBlank{if(m.isChannelPost)"Publicación"else"Mensaje recibido"}
            c.addView(text(label,11f,accent,true));c.addView(text(TelegramSession.preview(m.content),15f).apply{maxLines=18;ellipsize=TextUtils.TruncateAt.END})
            val time=SimpleDateFormat("dd MMM · HH:mm",Locale("es","MX")).format(Date(m.date.toLong()*1000))
            val state=when(m.sendingState){is MessageSendingStatePending->" · enviando…";is MessageSendingStateFailed->" · NO ENVIADO";else->if(m.isOutgoing)" · enviado"else""}
            c.addView(text(time+state+(if(m.editDate>0)" · editado"else""),11f,soft));c.setOnClickListener{messageActions(m)};return c
        }
    }
    private fun send(){
        val s=TelegramSession.current?:return;val field=composer?:return;val value=field.text.toString()
        if(!DeckRules.validMessage(value)){toast("Escribe entre 1 y 4,096 caracteres");return}
        val id=activeChat;val replyTo=reply;sendButton?.isEnabled=false
        s.sendText(id,value,replyTo){message,error->
            if(isDestroyed||TelegramSession.current!==s)return@sendText
            if(screen=="history"&&activeChat==id){sendButton?.isEnabled=true
                if(error!=null){toast(error);historyError?.text=error}else{
                    if(composer?.text.toString()==value)composer?.text?.clear();drafts.remove(s.accountId to id);reply=null;replyLabel?.text=""
                    if(message!=null){history=(history+message).associateBy{it.id}.values.sortedBy{it.id}.takeLast(240);refreshMessages();messagesList?.setSelection(history.lastIndex)}
                    historyError?.text="Envío solicitado · el estado del mensaje confirma el resultado"
                }
            }
        }
    }
    private fun messageActions(m:Message){
        val actions=mutableListOf("Ver texto completo")
        if(composer!=null)actions.add("Responder con texto")
        if(m.content is MessagePhoto)actions.add("Ver foto")
        if(m.content is MessageVideo)actions.add("Reproducir video")
        if(m.content is MessageAudio || m.content is MessageVoiceNote)actions.add("Reproducir audio")
        val links=Regex("https?://[^\\s<>]+",RegexOption.IGNORE_CASE).findAll(TelegramSession.preview(m.content)).map{it.value}.take(5).toList()
        if(links.isNotEmpty())actions.add("Abrir enlace")
        dialog().setTitle("Mensaje").setItems(actions.toTypedArray()){_,n->when(actions[n]){
            "Ver texto completo"->{val t=text(TelegramSession.preview(m.content),15f).apply{setPadding(dp(18),dp(12),dp(18),dp(12))};dialog().setView(ScrollView(this).apply{addView(t)}).setPositiveButton("Cerrar",null).show()}
            "Responder con texto"->{reply=m;replyLabel?.text="Respuesta: ${TelegramSession.preview(m.content).take(80)} · tocar para quitar";composer?.requestFocus()}
            "Abrir enlace"->dialog().setTitle("Enlace externo").setItems(links.toTypedArray()){_,i->confirm("Abrir navegador",links[i]){website(links[i])}}.show()
            else->playMedia(m)
        }}.show()
    }
    private fun playMedia(m:Message){
        if(m.selfDestructType!=null){toast("El contenido de autodestrucción no está implementado en esta beta");return}
        val c=m.content
        val file=when(c){is MessagePhoto->c.photo.sizes.lastOrNull()?.photo;is MessageVideo->c.video.video;is MessageAudio->c.audio.audio;is MessageVoiceNote->c.voiceNote.voice;else->null}?:return
        val cap=if(c is MessageVideo)80_000_000L else 20_000_000L
        if(file.size>cap||file.expectedSize>cap){toast("Este archivo supera el límite de descarga de la beta");return}
        val id=activeChat;val s=TelegramSession.current?:return
        confirm("Descargar y abrir","Se descargará este archivo a la carpeta privada de CanalDeck. Límite: ${cap/1_000_000} MB. Puede consumir datos móviles."){
            toast("Descargando archivo…")
            s.media(file.id,cap){path->if(!isDestroyed&&foreground&&TelegramSession.current===s&&activeChat==id){if(path==null)toast("La descarga no se completó o excede el límite")else displayMedia(path,c is MessagePhoto,c is MessageVideo)}}
        }
    }
    private fun displayMedia(path:String,photo:Boolean,video:Boolean){
        try{
            val f=java.io.File(path);require(f.canonicalPath.startsWith(filesDir.canonicalPath+"/")&&f.isFile)
            mediaDialog?.dismiss()
            if(photo){val opt=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(path,opt);require(opt.outWidth>0&&opt.outHeight>0);var sample=1;while(opt.outWidth/sample>1600||opt.outHeight/sample>1600)sample*=2;opt.inJustDecodeBounds=false;opt.inSampleSize=sample
                val bmp=android.graphics.BitmapFactory.decodeFile(path,opt)?:error("Invalid photo");val image=ImageView(this).apply{adjustViewBounds=true;setImageBitmap(bmp)}
                mediaDialog=dialog().setView(image).setPositiveButton("Cerrar",null).create()
            }else if(video){val v=VideoView(this);val control=MediaController(this);v.setMediaController(control);v.setVideoURI(Uri.fromFile(f));v.setOnPreparedListener{v.start()};v.setOnErrorListener{_,_,_->toast("El teléfono no pudo reproducir este formato");true}
                mediaDialog=dialog().setView(v).setPositiveButton("Cerrar",null).create();mediaDialog?.setOnDismissListener{v.stopPlayback()}
            }else{val box=column();val toggle=Button(this).apply{text="Pausar / continuar";setOnClickListener{mediaPlayer?.let{if(it.isPlaying)it.pause()else it.start()}}};box.addView(toggle)
                mediaPlayer=MediaPlayer().apply{setDataSource(path);setOnPreparedListener{it.start()};setOnErrorListener{_,_,_->toast("Audio no compatible");true};prepareAsync()}
                mediaDialog=dialog().setTitle("Audio de Telegram").setView(box).setPositiveButton("Cerrar",null).create();mediaDialog?.setOnDismissListener{mediaPlayer?.release();mediaPlayer=null}
            }
            mediaDialog?.show()
        }catch(_:Exception){mediaPlayer?.release();mediaPlayer=null;toast("No se pudo abrir el contenido de forma segura")}
    }
}
