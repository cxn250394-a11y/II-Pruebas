package com.charly.alertaslab

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import com.charly.alertcenter.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Native local UI. The demo never writes notification data or requests access. */
class LabActivity : Activity() {
    private val bg = Color.rgb(16, 22, 36)
    private val panel = Color.rgb(28, 38, 55)
    private val mint = Color.rgb(115, 229, 190)
    private val white = Color.rgb(242, 246, 255)
    private val muted = Color.rgb(178, 190, 209)
    private val amber = Color.rgb(255, 207, 126)
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var prefs: LabPrefs
    private lateinit var root: LinearLayout
    private lateinit var page: LinearLayout
    private lateinit var nav: LinearLayout
    private var tab = 0
    private var demo = false
    private var real: List<LabAlert> = emptyList()
    private var samples: List<LabAlert> = emptyList()
    private val demoFolders = mutableMapOf<String, String>()
    private var query = ""
    private var mode = 0
    private var folderFilter = "*"
    private var groupFilter: String? = null
    private var sportFilter: SportMention? = null
    private var rows = emptyList<ListRow>()
    private var list: ListView? = null
    private var countLabel: TextView? = null
    private var registered = false
    private var generation = 0
    private sealed class ListRow {
        data class Alert(val value: LabAlert) : ListRow()
        data class Group(val value: ChannelGroup) : ListRow()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { reload() }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        prefs = LabPrefs(this)
        demo = state?.getBoolean("demo") ?: false
        tab = state?.getInt("tab")?.coerceIn(0, 4) ?: 0
        if (demo) samples = LabEngine.demo(System.currentTimeMillis())
        root = column().apply { setBackgroundColor(bg) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val s = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(s.left, s.top, s.right, s.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        page = column()
        nav = row()
        root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(nav)
        setContentView(root)
        root.requestApplyInsets()
        render()
    }
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, IntentFilter(LabListener.CHANGED), RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, IntentFilter(LabListener.CHANGED))
        }
        registered = true
    }
    override fun onResume() { super.onResume(); render(); reload() }
    override fun onStop() { if (registered) { unregisterReceiver(receiver); registered = false }; super.onStop() }
    override fun onDestroy() { generation++; io.shutdown(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) { out.putBoolean("demo", demo); out.putInt("tab", tab); super.onSaveInstanceState(out) }
    @Deprecated("Base Activity compatibility")
    override fun onBackPressed() { if (tab != 0) openTab(0) else super.onBackPressed() }

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
    private fun text(value: String, size: Float = 14f, color: Int = white, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(3), 0, dp(3))
    }
    private fun button(value: String, strong: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 13f; minHeight = dp(48)
        setTextColor(if (strong) bg else white)
        backgroundTintList = ColorStateList.valueOf(if (strong) mint else panel)
        setOnClickListener { click() }
    }
    private fun card(host: LinearLayout): LinearLayout = column().apply {
        background = shape(panel); setPadding(dp(16), dp(12), dp(16), dp(12))
        host.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun contentScroll(): LinearLayout {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = column().apply { setPadding(dp(16), dp(10), dp(16), dp(20)) }
        scroll.addView(content); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return content
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun granted() = getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(this, LabListener::class.java))
    private fun permission() {
        AlertDialog.Builder(this).setTitle("Acceso amplio de Android")
            .setMessage("Android permite al lector recibir contenido de notificaciones. Esta app solo guarda las fuentes que tú habilites, pero los avisos pueden contener información privada. No se envía nada a Internet.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Abrir Ajustes") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                catch (_: Exception) { toast("Busca Acceso a notificaciones en los Ajustes de Android") }
            }.show()
    }
    private fun data() = if (demo) samples else real
    private fun folder(key: String) = if (demo) demoFolders[key].orEmpty() else prefs.folder(key)
    private fun time(value: Long) = SimpleDateFormat("dd MMM · HH:mm:ss", Locale("es", "MX")).format(Date(value))
    private fun openTab(value: Int) {
        tab = value; query = ""; mode = 0; groupFilter = null; sportFilter = null; render()
    }
    private fun toggleDemo() {
        demo = !demo; samples = if (demo) LabEngine.demo(System.currentTimeMillis()) else emptyList()
        demoFolders.clear(); openTab(if (demo) 1 else 0)
    }
    private fun reload() {
        val ticket = ++generation
        io.execute {
            val loaded = try {
                LabStore(applicationContext).use { it.purge(); it.all() }.also { alerts ->
                    prefs.pruneAssignments(alerts.map { it.groupKey }.toSet())
                }
            } catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed || isFinishing || ticket != generation) return@runOnUiThread
                if (loaded == null) { toast("No se pudo leer el historial local"); return@runOnUiThread }
                real = loaded
                if (tab in 1..3) refreshList() else if (tab == 0) render()
            }
        }
    }
    private fun mutate(block: (LabStore) -> Unit) {
        io.execute {
            try { LabStore(applicationContext).use(block) }
            catch (_: Exception) { runOnUiThread { if (!isDestroyed) toast("No se pudo guardar el cambio") } }
            runOnUiThread { if (!isDestroyed && !isFinishing) reload() }
        }
    }

    private fun render() {
        if (!::page.isInitialized) return
        list = null; countLabel = null
        page.removeAllViews(); nav.removeAllViews()
        val heading = column().apply { setPadding(dp(20), dp(12), dp(20), dp(6)) }
        heading.addView(text("CENTRO DE ALERTAS · LAB 0.2", 11f, mint, true))
        heading.addView(text(listOf("Tu centro de avisos", "Lo nuevo, primero", "Tus canales, ordenados", "Radar de avisos deportivos", "Tú decides qué guardar")[tab], 23f, white, true))
        if (demo) heading.addView(button("DEMO · ejemplos ficticios · salir") { toggleDemo() })
        page.addView(heading)
        listOf("Inicio", "Bandeja", "Canales", "Radar", "Ajustes").forEachIndexed { i, name ->
            nav.addView(button(name, tab == i) { openTab(i) }.apply { textSize = 11f; setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        when (tab) { 0 -> home(); 4 -> settings(); else -> history() }
    }

    private fun home() {
        val c = contentScroll()
        card(c).apply {
            addView(text("Esto no sustituye a Telegram", 17f, white, true))
            addView(text("Reúne los avisos nuevos que Android ya entregó. No inicia sesión, no importa tus chats ni hace llegar antes los mensajes atrasados.", color = muted))
            addView(button(if (demo) "Salir de los ejemplos" else "Probar con ejemplos, sin permisos", true) { toggleDemo() })
        }
        val count = prefs.selected().size
        val ready = granted()
        val status = when {
            prefs.paused() -> "Captura en pausa"
            count == 0 -> "Paso 1 pendiente: elegir fuentes"
            !ready -> "Paso 2 pendiente: permiso de Android"
            !LabListener.connected -> "Permiso concedido; esperando conexión del lector"
            real.isEmpty() -> "Paso 3: esperando un aviso NUEVO"
            else -> "Lector conectado · ${real.size} avisos guardados"
        }
        card(c).apply {
            addView(text(status, 16f, if (ready && count > 0) mint else amber, true))
            addView(text("1. Elige Telegram, Nicegram u otras fuentes en Ajustes.\n2. Concede el acceso de Android.\n3. Espera una notificación nueva y abre Bandeja.", color = muted))
            addView(button("1 · Elegir fuentes") { openTab(4) })
            addView(button("2 · Revisar acceso de Android") { permission() })
            addView(button("3 · Abrir bandeja") { openTab(1) })
            if (ready && !LabListener.connected) addView(button("Solicitar reconexión del lector") {
                try { NotificationListenerService.requestRebind(ComponentName(this@LabActivity, LabListener::class.java)); toast("Reconexión solicitada a Android") }
                catch (_: Exception) { toast("Revisa el permiso del lector en Android") }
            })
        }
        card(c).apply {
            addView(text("Estado local", 16f, white, true))
            addView(text("Fuentes habilitadas: $count\nSin revisar aquí: ${real.count { !it.read }}\nÚltima recepción: ${real.firstOrNull()?.let { time(it.receivedAt) } ?: "ninguna"}\nLotes omitidos por saturación: ${prefs.dropped()}", color = muted))
            addView(text("La hora es de recepción en esta app, no la hora real del servidor. Máximo 2,000 avisos / 14 días.", 12f, muted))
        }
    }

    private fun history() {
        val controls = column().apply { setPadding(dp(16), 0, dp(16), dp(8)) }
        when (tab) {
            2 -> {
                controls.addView(text("Agrupa por título observado, aplicación y perfil. Dos chats con el mismo nombre pueden quedar juntos; no identifica cuentas.", 12f, muted))
                val folderNames = listOf("Todas las carpetas", "Sin carpeta") + prefs.folders()
                controls.addView(spinner(folderNames, when (folderFilter) { "*" -> 0; "" -> 1; else -> folderNames.indexOf(folderFilter).coerceAtLeast(0) }) { i ->
                    folderFilter = when (i) { 0 -> "*"; 1 -> ""; else -> folderNames[i] }; refreshList()
                })
                controls.addView(button("Crear carpeta") { createFolder() })
            }
            3 -> {
                controls.addView(text("Texto detectado, NO evento verificado. No cuenta goles ni córners y no recupera avisos que la fuente no emitió.", 12f, amber))
                val kinds = listOf("Todas las menciones") + SportMention.entries.map { it.label }
                controls.addView(spinner(kinds, sportFilter?.ordinal?.plus(1) ?: 0) { i -> sportFilter = if (i == 0) null else SportMention.entries[i - 1]; refreshList() })
            }
            else -> controls.addView(spinner(listOf("Todos los avisos", "Nuevos aquí", "Favoritos", "Solo Telegram / Nicegram", "Solo deportes"), mode) { mode = it; refreshList() })
        }
        if (groupFilter != null) controls.addView(button("Grupo seleccionado · quitar filtro") { groupFilter = null; refreshList() })
        val search = EditText(this).apply {
            hint = "Buscar en todo el historial local"; setSingleLine(true); textSize = 14f
            setTextColor(white); setHintTextColor(muted); setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); refreshList() }
            })
        }
        controls.addView(search)
        countLabel = text("", 12f, muted); controls.addView(countLabel)
        page.addView(controls)
        list = ListView(this).apply {
            divider = null; dividerHeight = dp(10); clipToPadding = false
            setPadding(dp(16), 0, dp(16), dp(12)); adapter = adapterRows
        }
        page.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        refreshList()
    }
    private fun spinner(names: List<String>, selection: Int = 0, changed: (Int) -> Unit): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@LabActivity, android.R.layout.simple_spinner_dropdown_item, names)
        setSelection(selection)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { changed(position) }
        }
    }
    private fun refreshList() {
        if (tab !in 1..3 || list == null) return
        val data = LabEngine.sorted(data()).filter { LabEngine.matches(it, query) }
        rows = when (tab) {
            2 -> LabEngine.groups(data.filter { prefs.category(it.source) == AlertCategory.TELEGRAM })
                .filter { folderFilter == "*" || folder(it.key) == folderFilter }.map { ListRow.Group(it) }
            3 -> data.filter {
                val mention = LabEngine.sportMention(it.title, it.body, prefs.category(it.source))
                mention != null && (sportFilter == null || sportFilter == mention)
            }.map { ListRow.Alert(it) }
            else -> data.filter {
                (groupFilter == null || groupFilter == it.groupKey) && when (mode) {
                    1 -> !it.read; 2 -> it.favorite
                    3 -> prefs.category(it.source) == AlertCategory.TELEGRAM
                    4 -> prefs.category(it.source) == AlertCategory.SPORTS
                    else -> true
                }
            }.map { ListRow.Alert(it) }
        }
        countLabel?.text = if (rows.isEmpty()) "Sin resultados. Prueba el demo o espera un aviso nuevo de una fuente habilitada."
            else "${rows.size} ${if (tab == 2) "grupos" else "avisos"} · ${if (demo) "ejemplos ficticios" else "más nuevos primero"}"
        adapterRows.notifyDataSetChanged()
    }
    private val adapterRows = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
            val host = column().apply { background = shape(panel); setPadding(dp(14), dp(12), dp(14), dp(12)) }
            when (val item = rows[position]) {
                is ListRow.Group -> {
                    val g = item.value
                    host.addView(text(g.latest.title.ifBlank { "Sin título" }, 16f, white, true))
                    host.addView(text("${prefs.name(g.latest.source)} · perfil ${g.latest.profile}\n${g.alerts.size} avisos · ${g.unread} nuevos aquí · ${folder(g.key).ifBlank { "Sin carpeta" }}", 12f, muted))
                    host.addView(text(g.latest.body, 13f, muted).apply { maxLines = 2 })
                    host.addView(button("Ver avisos") { tab = 1; query = ""; mode = 0; groupFilter = g.key; render() })
                    host.addView(button("Mover a carpeta") { assignFolder(g.key) })
                }
                is ListRow.Alert -> {
                    val a = item.value
                    host.addView(text("${if (!a.read) "● NUEVO · " else ""}${prefs.name(a.source)} · perfil ${a.profile}", 11f, mint, true))
                    host.addView(text(a.title.ifBlank { "Sin título" }, 16f, white, true).apply { maxLines = 2 })
                    if (tab == 3) host.addView(text(LabEngine.sportMention(a.title, a.body, prefs.category(a.source))?.label.orEmpty(), 13f, amber, true))
                    host.addView(text(a.body, 13f, muted).apply { maxLines = 3 })
                    host.addView(text("${if (a.favorite) "★ · " else ""}${time(a.receivedAt)} · toca para ver", 11f, muted))
                    if (AlertRules.hasPriorityWord(a.title, a.body, prefs.words())) host.addView(text("Coincide con tus palabras prioritarias", 11f, mint))
                    host.setOnClickListener { detail(a) }
                    host.isFocusable = true
                }
            }
            return host
        }
    }
    private fun read(a: LabAlert, value: Boolean) {
        if (a.id < 0) { samples = samples.map { if (it.id == a.id) it.copy(read = value) else it }; refreshList() }
        else mutate { it.markRead(a.id, value) }
    }
    private fun favorite(a: LabAlert) {
        if (a.id < 0) { samples = samples.map { if (it.id == a.id) it.copy(favorite = !a.favorite) else it }; refreshList() }
        else mutate { it.favorite(a.id, !a.favorite) }
    }
    private fun detail(a: LabAlert) {
        read(a, true)
        val msg = text("${a.body}\n\nFuente: ${prefs.name(a.source)}\nPaquete: ${a.source}\nPerfil Android: ${a.profile}\nRecibido aquí: ${time(a.receivedAt)}\nPublicado por Android: ${time(a.postedAt)}\n\nLeído y favorito solo cambian esta bandeja, nunca Telegram. El texto deportivo no es una verificación independiente.", color = muted).apply {
            setPadding(dp(20), dp(12), dp(20), dp(12)); setTextIsSelectable(true)
        }
        AlertDialog.Builder(this).setTitle(a.title.ifBlank { "Aviso" }).setView(ScrollView(this).apply { addView(msg) })
            .setPositiveButton("Cerrar", null).setNegativeButton("Marcar nuevo") { _, _ -> read(a, false) }
            .setNeutralButton(if (a.favorite) "Quitar ★" else "Favorito ★") { _, _ -> favorite(a) }.show()
    }
    private fun assignFolder(key: String) {
        val choices = listOf("Sin carpeta") + prefs.folders()
        AlertDialog.Builder(this).setTitle("Carpeta de este grupo").setItems(choices.toTypedArray()) { _, i ->
            val value = if (i == 0) "" else choices[i]
            if (demo) demoFolders[key] = value else prefs.assign(key, value)
            refreshList()
        }.setNegativeButton("Cancelar", null).show()
    }
    private fun createFolder() {
        if (demo) { toast("El demo permite mover grupos a las carpetas de ejemplo; no guarda carpetas nuevas"); return }
        val input = EditText(this).apply { hint = "Nombre de carpeta"; setSingleLine(true) }
        val d = AlertDialog.Builder(this).setTitle("Nueva carpeta local").setView(input)
            .setNegativeButton("Cancelar", null).setPositiveButton("Crear", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (prefs.addFolder(input.text.toString())) { d.dismiss(); render() }
            else input.error = "Usa un nombre único de 1 a 36 caracteres"
        } }
        d.show()
    }
    private fun settings() {
        val c = contentScroll()
        if (demo) {
            card(c).apply {
                addView(text("Modo de ejemplo", 18f, white, true))
                addView(text("Los ejemplos no solicitan permisos ni se guardan. Sal del demo para configurar las fuentes reales.", color = muted))
                addView(button("Salir del demo", true) { toggleDemo() })
            }
            return
        }
        card(c).apply {
            addView(text("Sin cuenta ni Internet", 17f, white, true))
            addView(text("Android concede acceso amplio a las notificaciones; solo persistimos las fuentes que actives. Sus avisos pueden incluir datos privados. El filtro de códigos sensibles es limitado, no infalible.", color = muted))
            addView(button(if (granted()) "Acceso concedido · revisar" else "Conceder acceso de Android") { permission() })
            addView(Switch(this@LabActivity).apply {
                text = "Pausar captura"; setTextColor(white); isChecked = prefs.paused()
                minHeight = dp(48); setOnCheckedChangeListener { _, value -> prefs.pause(value) }
            })
        }
        c.addView(text("FUENTES · todas apagadas de fábrica", 12f, mint, true))
        prefs.sources().forEach { source ->
            card(c).apply {
                addView(Switch(this@LabActivity).apply {
                    text = source.name; setTextColor(white); minHeight = dp(48)
                    isChecked = source.packageId in prefs.selected()
                    contentDescription = "Guardar avisos de ${source.name}"
                    setOnCheckedChangeListener { _, value -> prefs.select(source.packageId, value) }
                })
                addView(text(source.packageId, 11f, muted))
            }
        }
        c.addView(button("Agregar otro paquete Android") {
            val box = column().apply { setPadding(dp(20), 0, dp(20), 0) }
            val input = EditText(this).apply { hint = "com.ejemplo.app"; setSingleLine(true) }
            var category = AlertCategory.OTHER
            box.addView(input)
            box.addView(spinner(AlertCategory.entries.map { it.displayName }, 2) { category = AlertCategory.entries[it] })
            AlertDialog.Builder(this).setTitle("Fuente personalizada").setView(box).setNegativeButton("Cancelar", null)
                .setPositiveButton("Agregar") { _, _ ->
                    if (prefs.addSource(input.text.toString(), category)) { toast("Agregada, todavía apagada"); render() }
                    else toast("Paquete inválido o ya registrado")
                }.show()
        })
        card(c).apply {
            addView(text("Palabras prioritarias", 16f, white, true))
            val words = EditText(this@LabActivity).apply { setText(prefs.words()); minLines = 2; maxLines = 4; setTextColor(white) }
            addView(words); addView(button("Guardar palabras") { prefs.words(words.text.toString()); toast("Guardadas") })
            addView(text("Solo destaca texto. No crea alarmas nuevas ni señales de apuesta.", 12f, muted))
        }
        c.addView(button("Borrar historial LAB y pausar captura") {
            AlertDialog.Builder(this).setTitle("¿Borrar historial de este LAB?")
                .setMessage("Se borrarán avisos, favoritos y asignaciones de carpeta del LAB. No cambia Telegram ni la app v0.1. La captura quedará pausada.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Borrar") { _, _ ->
                    prefs.pause(true)
                    mutate { it.clear(); prefs.pruneAssignments(emptySet()) }
                    openTab(0)
                }.show()
        })
        c.addView(text("v0.2.0-lab · 2,000 avisos / 14 días. Sin copia de seguridad ni exportación. No recibe eventos que Android no publique. Las apps duales solo aparecen si Android entrega sus notificaciones a este lector.", 12f, muted))
    }
}
