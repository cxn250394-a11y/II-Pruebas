package com.charly.alertcenter

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
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** No network dependency or third-party UI SDK. Every permission requires an explicit user action. */
class MainActivity : Activity() {
    private val bg = Color.rgb(10, 15, 28)
    private val panel = Color.rgb(23, 31, 49)
    private val panelSecondary = Color.rgb(29, 39, 60)
    private val mint = Color.rgb(99, 237, 199)
    private val sky = Color.rgb(140, 173, 255)
    private val white = Color.rgb(242, 246, 255)
    private val muted = Color.rgb(157, 170, 197)
    private val amber = Color.rgb(255, 202, 120)

    private lateinit var screen: LinearLayout
    private lateinit var settings: AlertSettings
    private lateinit var database: AlertDatabase
    private var selectedCategory: AlertCategory? = null
    private var query: String = ""
    private var viewingSettings = false
    private var receiverRegistered = false
    private lateinit var rowsHost: LinearLayout
    private lateinit var resultsLabel: TextView
    private lateinit var filterHost: LinearLayout

    private val changesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!viewingSettings) refreshRows()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        settings = AlertSettings(this)
        database = AlertDatabase(this)
        database.purgeExpired()
        screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        setContentView(screen)
        showHome()
    }

    // API < 33 lacks receiver flags; the API 33+ branch explicitly uses NOT_EXPORTED.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(AlertListenerService.ACTION_HISTORY_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(changesReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(changesReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onResume() {
        super.onResume()
        if (!viewingSettings) showHome()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(changesReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    @Deprecated("Maintained for compatibility with the base Activity API")
    override fun onBackPressed() {
        if (viewingSettings) showHome() else super.onBackPressed()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(fill: Int, radius: Int = 18, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun label(
        content: String,
        sp: Float = 14f,
        color: Int = white,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = content
        textSize = sp
        setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = true
    }

    private fun spacing(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun vertical(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun horizontal(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun card(): LinearLayout = vertical().apply {
        background = shape(panel)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun action(text: String, onClick: () -> Unit, strong: Boolean = false): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(if (strong) bg else white)
            backgroundTintList = ColorStateList.valueOf(if (strong) mint else panelSecondary)
            minimumHeight = dp(40)
            setOnClickListener { onClick() }
        }

    private fun addVerticalPadding(host: LinearLayout, size: Int = 20) {
        host.setPadding(dp(size), dp(20), dp(size), dp(12))
    }

    private fun header(subtitle: String, settingsButton: Boolean = true): LinearLayout = vertical().apply {
        val brand = horizontal()
        val logo = label("CA", 16f, bg, true).apply {
            gravity = Gravity.CENTER
            background = shape(mint, 14)
            layoutParams = LinearLayout.LayoutParams(dp(47), dp(47))
        }
        brand.addView(logo)
        val words = vertical().apply { setPadding(dp(12), 0, 0, 0) }
        words.addView(label("CENTRO DE ALERTAS", 10f, mint, true))
        words.addView(label("Privado · Android", 18f, white, true))
        brand.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (settingsButton) brand.addView(action("Ajustes", { showSettings() }))
        else brand.addView(action("Volver", { showHome() }))
        addView(brand)
        addView(spacing(17))
        addView(label(subtitle, 14f, muted))
    }

    private fun hasListenerAccess(): Boolean = try {
        getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(this, AlertListenerService::class.java))
    } catch (_: Exception) { false }

    private fun permissionButton() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Busca Acceso a notificaciones en Ajustes", Toast.LENGTH_LONG).show()
        }
    }

    private fun showHome() {
        viewingSettings = false
        screen.removeAllViews()
        val top = vertical()
        addVerticalPadding(top)
        top.addView(header("Tus avisos, organizados por importancia y hora de recepción."))
        top.addView(spacing(16))
        top.addView(permissionStatus())
        top.addView(spacing(12))

        val summary = horizontal()
        resultsLabel = label("Cargando…", 12f, muted)
        summary.addView(resultsLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        summary.addView(label("MÁX. 14 DÍAS", 10f, sky, true))
        top.addView(summary)
        top.addView(spacing(11))

        filterHost = horizontal()
        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(filterHost)
        }
        top.addView(filterScroll)
        top.addView(spacing(12))

        val search = EditText(this).apply {
            setSingleLine(true)
            hint = "Buscar texto en tus alertas…"
            setHintTextColor(muted)
            textSize = 14f
            setTextColor(white)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = shape(panelSecondary, 13)
            setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty()
                    refreshRows()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        top.addView(search)
        top.addView(spacing(11))
        screen.addView(top)

        val listScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        rowsHost = vertical().apply {
            setPadding(dp(20), 0, dp(20), dp(16))
        }
        listScroll.addView(rowsHost)
        screen.addView(listScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val footer = label("SIN NUBE  ·  SIN RASTREADORES  ·  SOLO FUENTES AUTORIZADAS", 9f, muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(11), dp(8), dp(13))
        }
        screen.addView(footer)
        refreshFilters()
        refreshRows()
    }

    private fun permissionStatus(): LinearLayout = card().apply {
        val granted = hasListenerAccess()
        background = shape(panel, 16, if (granted) Color.rgb(48, 104, 96) else Color.rgb(123, 92, 44))
        addView(label(if (granted) "●  Acceso a notificaciones activo" else "●  Acceso a notificaciones pendiente",
            14f, if (granted) mint else amber, true))
        addView(spacing(5))
        addView(label(
            if (granted) "Capturamos únicamente las aplicaciones que actives en Ajustes."
            else "Android exige que concedas este permiso manualmente. Tus fuentes empiezan desactivadas.",
            12f, muted
        ))
        if (!granted) {
            addView(spacing(8))
            addView(action("Abrir permiso de Android", { permissionButton() }, strong = true))
        }
    }

    private fun refreshFilters() {
        filterHost.removeAllViews()
        val filters = listOf(
            Pair("Todas", null),
            Pair("Telegram", AlertCategory.TELEGRAM),
            Pair("Deportes", AlertCategory.SPORTS),
            Pair("Otras", AlertCategory.OTHER)
        )
        for ((name, category) in filters) {
            val active = selectedCategory == category
            val chip = label(name, 13f, if (active) bg else white, active).apply {
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(10), dp(15), dp(10))
                background = shape(if (active) mint else panelSecondary, 24)
                setOnClickListener {
                    selectedCategory = category
                    refreshFilters()
                    refreshRows()
                }
            }
            filterHost.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
    }

    private fun refreshRows() {
        if (!::rowsHost.isInitialized || viewingSettings) return
        val all = database.recent(250)
        val current = all.filter { alert ->
            (selectedCategory == null || selectedCategory == settings.categoryFor(alert.packageId)) &&
                (AlertRules.matchesQuery(alert.title, alert.body, query) ||
                    AlertRules.normalized(settings.displayName(alert.packageId)).contains(AlertRules.normalized(query)))
        }
        rowsHost.removeAllViews()
        resultsLabel.text = "${current.size} alertas · ${all.size} recientes"
        if (current.isEmpty()) {
            rowsHost.addView(spacing(32))
            val empty = card()
            empty.addView(label(if (all.isEmpty()) "Todavía no hay notificaciones guardadas" else "Sin coincidencias", 17f, white, true))
            empty.addView(spacing(8))
            empty.addView(label(
                if (all.isEmpty()) "Abre Ajustes, activa las fuentes que quieras vigilar y concede el permiso de Android. No registramos nada anterior a la activación."
                else "Prueba otra búsqueda o un filtro diferente.", 13f, muted
            ))
            empty.addView(spacing(12))
            empty.addView(action("Elegir fuentes", { showSettings() }, strong = true))
            rowsHost.addView(empty)
            return
        }
        current.forEach { entry ->
            rowsHost.addView(alertCard(entry), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
    }

    private fun alertCard(entry: AlertEntry): LinearLayout = card().apply {
        val category = settings.categoryFor(entry.packageId)
        val color = when (category) {
            AlertCategory.TELEGRAM -> sky
            AlertCategory.SPORTS -> mint
            AlertCategory.OTHER -> muted
        }
        val important = AlertRules.hasPriorityWord(entry.title, entry.body, settings.keywords())
        if (important) background = shape(panel, 16, Color.rgb(92, 134, 111))
        val topRow = horizontal()
        val source = settings.displayName(entry.packageId) +
            if (entry.androidUserId != Process.myUserHandle().hashCode()) " · perfil alterno" else ""
        val sourceLabel = label("●  $source", 12f, color, true)
        sourceLabel.maxLines = 1
        topRow.addView(sourceLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val timestamp = SimpleDateFormat("dd MMM · HH:mm:ss", Locale("es", "MX"))
            .format(Date(entry.receivedAt))
        topRow.addView(label(timestamp, 10f, muted))
        addView(topRow)
        addView(spacing(6))
        addView(label(entry.title.ifBlank { "Aviso sin título" }, 15f, white, true).apply { maxLines = 2 })
        if (entry.body.isNotEmpty()) {
            addView(spacing(4))
            addView(label(entry.body, 13f, muted).apply { maxLines = 4 })
        }
        // Android posting time is not the original Telegram/server event time.
        val arrivalGap = entry.receivedAt - entry.postedAt
        if (arrivalGap > 15_000L && arrivalGap < 86_400_000L) {
            addView(spacing(7))
            addView(label("Hora publicada por Android: " +
                SimpleDateFormat("HH:mm:ss", Locale("es", "MX")).format(Date(entry.postedAt)),
                10f, muted))
        }
        if (important) {
            addView(spacing(8))
            addView(label("★  Coincidencia con palabras prioritarias", 11f, mint, true))
        }
    }

    private fun showSettings() {
        viewingSettings = true
        screen.removeAllViews()
        val scrolling = ScrollView(this)
        val contents = vertical()
        addVerticalPadding(contents)
        contents.addView(header("Elige exactamente qué aplicaciones pueden incorporarse a tu historial.", false))
        contents.addView(spacing(16))
        val privacy = card()
        privacy.addView(label("CONTROL Y PRIVACIDAD", 11f, mint, true))
        privacy.addView(spacing(6))
        privacy.addView(label("Aunque concedas el acceso de Android, Centro de Alertas guarda solo los avisos de las fuentes que tú actives aquí. Nunca solicita acceso a tu cuenta de Telegram.", 13f, muted))
        privacy.addView(spacing(8))
        privacy.addView(action(if (hasListenerAccess()) "Permiso activo · revisar" else "Conceder acceso de Android", { permissionButton() }))
        contents.addView(privacy)
        contents.addView(spacing(22))
        contents.addView(label("FUENTES PERMITIDAS", 12f, sky, true))
        contents.addView(spacing(8))
        for (source in settings.sources()) {
            val line = horizontal().apply {
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = shape(panel, 12)
            }
            val details = vertical()
            details.addView(label(source.name, 14f, white, true))
            details.addView(label("${source.category.displayName} · ${source.packageId}", 11f, muted))
            line.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val toggle = Switch(this).apply {
                isChecked = settings.isSelected(source.packageId)
                thumbTintList = ColorStateList.valueOf(if (isChecked) mint else muted)
                setOnCheckedChangeListener { _, checked ->
                    settings.select(source.packageId, checked)
                    thumbTintList = ColorStateList.valueOf(if (checked) mint else muted)
                }
                contentDescription = "Permitir notificaciones de ${source.name}"
            }
            line.addView(toggle)
            contents.addView(line)
            contents.addView(spacing(6))
        }
        contents.addView(spacing(12))
        contents.addView(label("AÑADIR OTRA APLICACIÓN", 12f, sky, true))
        contents.addView(spacing(5))
        contents.addView(label("Introduce el identificador exacto del paquete Android, por ejemplo el de una app de pronósticos o de otra versión de Telegram.", 12f, muted))
        contents.addView(spacing(8))
        val packageInput = EditText(this).apply {
            hint = "com.ejemplo.notificaciones"
            setSingleLine(true)
            textSize = 13f
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = shape(panelSecondary, 12)
        }
        contents.addView(packageInput)
        contents.addView(spacing(7))
        val categorySpinner = Spinner(this)
        val names = AlertCategory.entries.map { it.displayName }
        categorySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        contents.addView(categorySpinner)
        contents.addView(action("Agregar fuente", {
            val id = packageInput.text.toString().trim()
            val category = AlertCategory.entries[categorySpinner.selectedItemPosition]
            if (settings.addCustom(id, category)) {
                Toast.makeText(this, "Fuente agregada; actívala en la lista", Toast.LENGTH_LONG).show()
                showSettings()
            } else {
                Toast.makeText(this, "ID inválido o ya existente", Toast.LENGTH_LONG).show()
            }
        }, strong = true))
        if (settings.customSources().isNotEmpty()) {
            contents.addView(spacing(6))
            contents.addView(action("Eliminar una fuente personalizada", {
                val custom = settings.customSources().map { it.packageId }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Eliminar fuente")
                    .setItems(custom) { _, index ->
                        settings.removeCustom(custom[index]); showSettings()
                    }.setNegativeButton("Cancelar", null).show()
            }))
        }
        contents.addView(spacing(23))
        contents.addView(label("PALABRAS A DESTACAR", 12f, sky, true))
        contents.addView(spacing(6))
        contents.addView(label("Separadas por comas. Se buscan como palabras o frases completas; no significan que se haya verificado un evento deportivo.", 12f, muted))
        contents.addView(spacing(8))
        val keywordsInput = EditText(this).apply {
            setText(settings.keywords())
            minLines = 2
            maxLines = 4
            setTextColor(white)
            textSize = 13f
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = shape(panelSecondary, 12)
        }
        contents.addView(keywordsInput)
        contents.addView(action("Guardar palabras", {
            settings.updateKeywords(keywordsInput.text.toString())
            Toast.makeText(this, "Palabras guardadas", Toast.LENGTH_SHORT).show()
        }, strong = true))
        contents.addView(spacing(23))
        contents.addView(label("HISTORIAL", 12f, sky, true))
        contents.addView(spacing(6))
        contents.addView(label("Hasta 2,000 avisos durante 14 días. La base es local; sin sincronización ni respaldo automático.", 12f, muted))
        contents.addView(spacing(8))
        contents.addView(action("Borrar todo el historial", {
            AlertDialog.Builder(this)
                .setTitle("¿Borrar historial local?")
                .setMessage("La operación no puede deshacerse.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar") { _, _ ->
                    database.deleteAll()
                    Toast.makeText(this, "Historial eliminado", Toast.LENGTH_SHORT).show()
                    showHome()
                }.show()
        }))
        contents.addView(spacing(20))
        contents.addView(label("v0.1 · Sin permiso de Internet · Sin accesibilidad · Sin modificación de otras apps.", 11f, muted))
        contents.addView(spacing(24))
        scrolling.addView(contents)
        screen.addView(scrolling)
    }
}
