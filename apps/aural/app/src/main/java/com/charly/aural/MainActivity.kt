package com.charly.aural

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.charly.audio.*
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : Activity() {
    private val bg = Color.rgb(15,18,32)
    private val surface = Color.rgb(29,33,52)
    private val ink = Color.rgb(242,242,255)
    private val muted = Color.rgb(174,181,204)
    private val accent = Color.rgb(180,181,255)
    private lateinit var store: LibraryStore
    private lateinit var content: LinearLayout
    private lateinit var navigation: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var nowStatus: TextView
    private lateinit var times: TextView
    private lateinit var seek: SeekBar
    private lateinit var playButton: Button
    private lateinit var repeatButton: Button
    private lateinit var shuffleButton: Button
    private var sourceText: TextView? = null
    private var counter: TextView? = null
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var dragging = false
    private var tab = 0
    private var query = ""
    private var favoritesOnly = false
    private var shown = emptyList<AudioEntry>()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { updateNow() }
    }
    private val tick = object : Runnable {
        override fun run() { updateNow(); handler.postDelayed(this, 1000) }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = LibraryStore(this)
        tab = state?.getInt("tab")?.coerceIn(0,3) ?: 0
        val root = column().apply { setBackgroundColor(bg) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val s = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(s.left+dp(16),s.top+dp(8),s.right+dp(16),s.bottom+dp(8))
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft+dp(16),insets.systemWindowInsetTop+dp(8),insets.systemWindowInsetRight+dp(16),insets.systemWindowInsetBottom+dp(8))
            }
            insets
        }
        root.addView(label("AURAL",27f,accent,true))
        root.addView(label("TU MÚSICA · TU RADIO · PILOTO 0.1",11f,muted))
        navigation = row(); root.addView(navigation)
        content = column(); root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
        val transport = column().apply { background=shape(surface); setPadding(dp(12),dp(8),dp(12),dp(6)) }
        nowTitle = label("Elige algo para escuchar",16f,ink,true).apply { maxLines=2 }
        nowStatus = label("Sin reproducción automática",11f,muted).apply { maxLines=2 }
        transport.addView(nowTitle); transport.addView(nowStatus)
        seek = SeekBar(this).apply {
            max=1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(s: SeekBar?) { dragging=true }
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    controller?.let { c -> if (c.isCurrentMediaItemSeekable && c.duration>0) c.seekTo(c.duration*(s?.progress?:0)/1000) }
                    dragging=false
                }
            })
        }
        transport.addView(seek)
        times = label("0:00",11f,muted); transport.addView(times)
        val controls=row()
        controls.addView(button("Anterior") { controller?.seekToPreviousMediaItem() }, weight())
        playButton=button("Reproducir",true) {
            controller?.let { c ->
                if (c.isPlaying) c.pause() else if(c.mediaItemCount>0) { if(c.playbackState==Player.STATE_IDLE || c.playbackState==Player.STATE_ENDED) c.prepare(); c.play() }
                else toast("Selecciona un archivo o una emisora")
            } ?: toast("Conectando el reproductor; vuelve a tocar en un momento")
        }
        controls.addView(playButton,weight())
        controls.addView(button("Siguiente") { controller?.seekToNextMediaItem() },weight())
        transport.addView(controls)
        val options=row()
        shuffleButton=button("Aleatorio: no") { controller?.let { it.shuffleModeEnabled=!it.shuffleModeEnabled; updateNow() } }
        repeatButton=button("Repetir: no") { controller?.let { it.repeatMode=(it.repeatMode+1)%3; updateNow() } }
        options.addView(shuffleButton,weight()); options.addView(repeatButton,weight())
        options.addView(button("Detener") { controller?.let { it.stop(); it.clearMediaItems() }; updateNow() },weight())
        transport.addView(options); root.addView(transport)
        setContentView(root); root.requestApplyInsets(); render()
    }
    override fun onStart() {
        super.onStart()
        val ready=MediaController.Builder(this,SessionToken(this,ComponentName(this,PlaybackService::class.java))).buildAsync()
        future=ready
        ready.addListener({
            if (future!==ready || isDestroyed || isFinishing) return@addListener
            try { controller=ready.get(); controller?.addListener(listener); updateNow() }
            catch (_: Exception) { nowStatus.text="No se pudo conectar. Cierra y vuelve a abrir Aural." }
        },mainExecutor)
        handler.post(tick)
    }
    override fun onStop() {
        handler.removeCallbacks(tick)
        controller?.removeListener(listener); controller=null
        future?.let { MediaController.releaseFuture(it) }; future=null
        super.onStop()
    }
    override fun onDestroy() { io.shutdown(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) { out.putInt("tab",tab); super.onSaveInstanceState(out) }
    private fun dp(n:Int)=(resources.displayMetrics.density*n+.5f).toInt()
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun weight()=LinearLayout.LayoutParams(0,-2,1f)
    private fun shape(color:Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(16).toFloat() }
    private fun label(value:String,size:Float=14f,color:Int=ink,bold:Boolean=false)=TextView(this).apply {
        text=value; textSize=size; setTextColor(color); setPadding(0,dp(4),0,dp(4))
        if(bold) typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }
    private fun button(value:String,strong:Boolean=false,action:()->Unit)=Button(this).apply {
        text=value; textSize=12f; isAllCaps=false; minHeight=dp(48); setPadding(dp(3),0,dp(3),0)
        setTextColor(if(strong) bg else ink); backgroundTintList=ColorStateList.valueOf(if(strong) accent else surface)
        setOnClickListener { action() }
    }
    private fun toast(text:String)=Toast.makeText(this,text,Toast.LENGTH_LONG).show()
    private fun render() {
        content.removeAllViews(); navigation.removeAllViews(); sourceText=null; counter=null
        listOf("Música","Radio","Calidad","Servicios").forEachIndexed { i,name ->
            navigation.addView(button(name,tab==i) { tab=i; query=""; favoritesOnly=false; render() },weight())
        }
        if(tab<2) library() else {
            val sc=ScrollView(this); val body=column().apply { setPadding(0,dp(8),0,dp(12)) }
            sc.addView(body); content.addView(sc,LinearLayout.LayoutParams(-1,-1))
            if(tab==2) {
                body.addView(label("Calidad sin etiquetas falsas",20f,ink,true))
                sourceText=label(sourceDescription(),13f,accent); body.addView(sourceText)
                body.addView(label(AudioDiagnostics.report(this),13f,muted))
                body.addView(button("Volver a consultar salidas") { render() })
            } else services(body)
        }
    }
    private fun library() {
        content.addView(label(if(tab==0) "Biblioteca local" else "Emisoras por Internet",21f,ink,true))
        val actions=row()
        actions.addView(button(if(tab==0) "Añadir archivos" else "Añadir emisora",true) { if(tab==0) chooseFiles() else addRadio() },weight())
        actions.addView(button(if(favoritesOnly) "Ver todos" else "Ver favoritos") { favoritesOnly=!favoritesOnly; render() },weight())
        content.addView(actions)
        if(tab==0) content.addView(button("Probar sonido · tono original de 8 segundos") { play(listOf(AudioPolicy.demo),0) })
        content.addView(label(if(tab==0) "Toca para escuchar. Mantén pulsado para favorito o quitar. Solo se accede a archivos que elijas."
            else "URL directa HTTPS de audio o HLS (.m3u8), no la página de una estación. Requiere conexión; consume datos.",12f,muted))
        val search=EditText(this).apply {
            hint="Buscar en esta lista"; setSingleLine(true); setTextColor(ink); setHintTextColor(muted); textSize=14f; setText(query)
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun afterTextChanged(s:Editable?) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { query=s.toString(); refreshList() }
            })
        }
        content.addView(search); counter=label("",12f,muted); content.addView(counter)
        val list=ListView(this).apply {
            divider=null; dividerHeight=dp(6); adapter=entriesAdapter
            setOnItemClickListener { _,_,i,_ -> val e=shown[i]; if(e.kind==EntryKind.RADIO) play(listOf(e),0) else play(shown,i) }
            setOnItemLongClickListener { _,_,i,_ -> entryOptions(shown[i]); true }
        }
        content.addView(list,LinearLayout.LayoutParams(-1,0,1f)); refreshList()
    }
    private fun refreshList() {
        val kind=if(tab==0) EntryKind.LOCAL else EntryKind.RADIO
        shown=store.entries().filter { it.kind==kind && (!favoritesOnly || it.favorite) && AudioPolicy.matches(it,query) }
            .sortedBy { AudioPolicy.normalized(it.title) }
        counter?.text=if(shown.isEmpty()) "Lista vacía. Añade ${if(tab==0) "tus archivos" else "una emisora"} para empezar." else "${shown.size} elementos · favoritos y listas guardados en este teléfono"
        entriesAdapter.notifyDataSetChanged()
    }
    private val entriesAdapter=object:BaseAdapter() {
        override fun getCount()=shown.size
        override fun getItem(position:Int)=shown[position]
        override fun getItemId(position:Int)=position.toLong()
        override fun getView(position:Int,recycled:View?,parent:ViewGroup?):View {
            val host=(recycled as? LinearLayout) ?: column().apply {
                background=shape(surface); setPadding(dp(12),dp(8),dp(12),dp(8))
                addView(label("",15f,ink,true).apply { maxLines=2 }); addView(label("",11f,muted))
            }
            val e=shown[position]
            (host.getChildAt(0) as TextView).text=(if(e.favorite) "★  " else "")+e.title
            (host.getChildAt(1) as TextView).text=if(e.kind==EntryKind.RADIO) "RADIO · ${Uri.parse(e.uri).host}" else "ARCHIVO LOCAL · toca para reproducir"
            return host
        }
    }
    private fun play(items:List<AudioEntry>,index:Int) {
        val c=controller ?: run { toast("El reproductor se está conectando. Vuelve a tocar en un momento."); return }
        if(items.any { it.kind==EntryKind.RADIO } && !store.networkAllowed()) {
            AlertDialog.Builder(this).setTitle("Escuchar radio por Internet")
                .setMessage("Aural se conectará a la emisora que elegiste. El servidor verá tu dirección IP y la reproducción consumirá datos. No se envían tus archivos locales. Puedes revocar este permiso desde Servicios.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Permitir y escuchar") { _,_->store.allowNetwork(true); play(items,index) }.show()
            return
        }
        c.setMediaItems(items.map(::playable),index,C.TIME_UNSET); c.prepare(); c.play(); updateNow()
    }
    @Suppress("DEPRECATION")
    private fun chooseFiles() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type="audio/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            },40)
        } catch (_: Exception) { toast("No se encontró un selector de archivos compatible") }
    }
    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=40 || resultCode!=RESULT_OK || data==null) return
        val clip=data.clipData
        val uris=if(clip!=null) (0 until minOf(clip.itemCount,100)).map { clip.getItemAt(it).uri } else listOfNotNull(data.data)
        io.execute {
            var count=0
            for(uri in uris.distinct()) {
                try {
                    if(uri.scheme!="content") continue
                    contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    var name="Audio seleccionado"
                    contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                        if(cursor.moveToFirst()) { val at=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(at>=0) name=cursor.getString(at).orEmpty() }
                    }
                    if(store.add(AudioEntry(uri.toString(),AudioPolicy.title(name),EntryKind.LOCAL))) count++
                } catch (_: Exception) { /* No paths, filenames or exception contents are logged. */ }
            }
            runOnUiThread { if(!isDestroyed && !isFinishing) { toast("$count archivos añadidos. Duplicados, permisos no persistentes o límite alcanzado se omiten; máximo 100 por selección."); render() } }
        }
    }
    private fun addRadio() {
        val form=column().apply { setPadding(dp(20),0,dp(20),0) }
        val name=EditText(this).apply { hint="Nombre de la emisora"; setSingleLine(true) }
        val url=EditText(this).apply { hint="https://… dirección directa del stream"; setSingleLine(true); inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
        form.addView(name); form.addView(url)
        val d=AlertDialog.Builder(this).setTitle("Añadir radio").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val input=url.text.toString().trim()
            if(!AudioPolicy.validRadio(input)) { url.error="Usa una URL HTTPS válida, sin usuario, contraseña ni fragmento"; return@setOnClickListener }
            if(name.text.toString().isBlank()) { name.error="Escribe un nombre"; return@setOnClickListener }
            if(store.add(AudioEntry(input,AudioPolicy.title(name.text.toString()),EntryKind.RADIO))) { d.dismiss(); render() }
            else url.error="Ya existe o se alcanzó el máximo de 100 emisoras"
        } }; d.show()
    }
    private fun entryOptions(e:AudioEntry) {
        AlertDialog.Builder(this).setTitle(e.title).setItems(arrayOf(if(e.favorite) "Quitar favorito" else "Marcar favorito","Quitar de Aural; no borrar archivo")) { _,which ->
            if(which==0) store.favorite(e.id) else {
                controller?.let { c -> for(i in c.mediaItemCount-1 downTo 0) if(c.getMediaItemAt(i).mediaId==e.id) c.removeMediaItem(i) }
                store.remove(e.id)
                if(e.kind==EntryKind.LOCAL) try { contentResolver.releasePersistableUriPermission(Uri.parse(e.uri),Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            }
            refreshList()
        }.setNegativeButton("Cancelar",null).show()
    }
    private fun sourceDescription():String {
        val c=controller ?: return "Fuente: todavía no hay reproducción"
        for(group in c.currentTracks.groups) if(group.type==C.TRACK_TYPE_AUDIO) {
            for(i in 0 until group.length) if(group.isTrackSelected(i)) {
                val f=group.getTrackFormat(i); return AudioPolicy.sourceFormat(f.sampleRate,f.channelCount,f.sampleMimeType)
            }
        }
        return "Fuente: formato todavía no informado por el reproductor"
    }
    private fun updateNow() {
        if(!::nowTitle.isInitialized) return
        val c=controller
        nowTitle.text=c?.mediaMetadata?.title ?: "Elige algo para escuchar"
        nowStatus.text=when {
            c==null -> "Conectando el reproductor…"
            c.playerError!=null -> "No se pudo reproducir (${c.playerError?.errorCodeName}). Revisa conexión, permiso o formato y pulsa Reproducir."
            c.mediaItemCount==0 -> "Nada en cola · sin reproducción automática"
            c.playbackState==Player.STATE_BUFFERING -> "Cargando audio…"
            c.isPlaying -> "Reproduciendo · salida normal de Android"
            c.playbackState==Player.STATE_ENDED -> "Fin de reproducción"
            else -> "En pausa"
        }
        playButton.text=if(c?.isPlaying==true) "Pausar" else "Reproducir"
        shuffleButton.text=if(c?.shuffleModeEnabled==true) "Aleatorio: sí" else "Aleatorio: no"
        repeatButton.text="Repetir: "+when(c?.repeatMode) { Player.REPEAT_MODE_ONE->"una"; Player.REPEAT_MODE_ALL->"todas"; else->"no" }
        val duration=c?.duration ?: 0
        seek.isEnabled=c?.isCurrentMediaItemSeekable==true && duration>0
        if(!dragging) seek.progress=if(duration>0) ((c?.currentPosition?:0)*1000/duration).toInt().coerceIn(0,1000) else 0
        times.text=AudioPolicy.clock(c?.currentPosition?:0)+if(duration>0) " / "+AudioPolicy.clock(duration) else " · duración no disponible"
        sourceText?.text=sourceDescription()
    }
    private fun services(body:LinearLayout) {
        body.addView(label("Qué está disponible",20f,ink,true))
        body.addView(label("Archivos locales y radio por URL HTTPS: implementados en este piloto. Cola, favoritos, búsqueda, repetición y controles multimedia. No hay catálogo de emisoras todavía.",14f,muted))
        body.addView(label("TIDAL · PENDIENTE",17f,accent,true))
        body.addView(label("No hay inicio de sesión ni reproducción TIDAL en esta versión. Falta acceso propio autorizado para integrar el servicio; el SDK público no equivale a permiso para streaming completo ni para una salida USB personalizada.",14f,muted))
        body.addView(label("QOBUZ · PENDIENTE",17f,accent,true))
        body.addView(label("No hay catálogo ni reproducción Qobuz en esta versión. Su integración requiere acceso de desarrollador/partner y una cuenta con los derechos de reproducción correspondientes. No se extraen claves de otras apps.",14f,muted))
        body.addView(label("BIT-PERFECT · PENDIENTE",17f,accent,true))
        body.addView(label("La pestaña Calidad solo consulta capacidades anunciadas. No cambia el mezclador ni certifica muestras. El controlador y la prueba con tu DAC siguen pendientes; no es aún un reemplazo completo de UAPP.",14f,muted))
        body.addView(button(if(store.networkAllowed()) "Radio permitida · revocar y detener" else "Radio no autorizada todavía") {
            if(store.networkAllowed()) { controller?.let { it.stop(); it.clearMediaItems() }; store.allowNetwork(false); render() }
            else toast("El consentimiento se pide al tocar una emisora")
        })
        body.addView(label("Privacidad",17f,ink,true))
        body.addView(label("Sin analítica, cuentas propias, publicidad ni envío de tu biblioteca. La emisora elegida recibe las solicitudes necesarias para reproducir. El selector de Android puede mostrar proveedores en la nube. Quitar una entrada no borra el archivo. Sin copia de seguridad automática. Firma de prueba temporal; futuras actualizaciones requieren resolver firma estable.",12f,muted))
    }
}
