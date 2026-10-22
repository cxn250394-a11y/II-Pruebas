package mx.charly.canal

import android.os.Bundle
import android.view.WindowManager
import android.widget.Switch
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import mx.charly.ui.Screen

/** Private route preparation only. Uses the already-connected client; never creates a TDLib client. */
class RelaySettingsActivity : Screen() {
    private var boundAccount = 0L
    private var coverageChat = 0L
    private val changed: () -> Unit = { if (!isFinishing && !isDestroyed) render() }
    private var observed: TelegramSession? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        boundAccount = TelegramSession.current?.takeIf { it.ready && !it.closing }?.accountId ?: 0L
        render()
    }
    override fun onResume() {
        super.onResume()
        observed = TelegramSession.current
        observed?.observers?.add(changed)
        render()
    }
    override fun onPause() {
        observed?.observers?.remove(changed)
        observed = null
        super.onPause()
    }

    private fun connected(): TelegramSession? = TelegramSession.current?.takeIf {
        boundAccount > 0L && it.accountId == boundAccount && it.ready && !it.closing
    }

    private fun loaded(session: TelegramSession): List<RelayLoadedChat> = session.chats.values
        .filter { it.type is ChatTypeSupergroup || it.type is ChatTypeBasicGroup }
        .map { RelayLoadedChat(it.id, it.title, it.lastMessage?.id ?: 0L) }
        .sortedBy { PickBridgeRules.normalized(it.title) }

    private fun render() {
        layout("Puente a Pick$ · v0.4", "Preparación privada · salida real OFF")
        card("Salida real bloqueada en esta versión",
            "Puedes preparar las identidades de tus fuentes y destino. Esta compilación no permite encender el reenvío. " +
                "Las pruebas usan Fake TDLib y no demuestran un envío real a Pick$.")
        val session = connected()
        if (session == null) {
            label("Abre esta pantalla desde tu cuenta conectada en CanalDeck. Una sesión distinta no hereda la selección.", 14f, soft)
            button("Volver a CanalDeck") { finish() }
            return
        }
        val preferences = RelayPreferences(this, boundAccount)
        val choices = loaded(session)
        label("Cuenta: ${session.accountName} · selección local por cuenta y chat ID", 13f, soft)
        val destination = preferences.destinationChatId()
        card("Destino elegido por ti", if (destination == 0L) "Sin seleccionar" else
            session.chats[destination]?.let { "${it.title}\nID local: ${it.id}" } ?: "La conversación elegida aún no está cargada")
        button("Elegir mi canal destino Pick$", true) { chooseDestination() }
        label("Elige tu canal real de la lista cargada. Dos chats con el mismo nombre siguen siendo identidades distintas. No se inventan usernames ni IDs.", 12f, soft)

        label("Fuentes preparadas", 18f)
        if (preferences.sources().isEmpty()) label("Ninguna fuente seleccionada. Todas comienzan OFF.", 13f, soft)
        preferences.sources().forEach { (chat, _) ->
            val policy = preferences.sourcePolicy(chat)
            val title = session.chats[chat]?.title ?: "Fuente no cargada"
            card(title, (policy?.let { RelaySettingsSelection.label(it) } ?: "Política no verificada") + "\nID local: $chat")
            button("Quitar esta fuente") {
                if (connected() !== session) { render(); return@button }
                preferences.selectSource(chat, PickWatchMode.OFF, 0L)
                render()
            }
        }
        button("Elegir Fano o una fuente UnicoRey") { chooseSource() }
        label("Selecciona Fano como canal directo; selecciona cada una de las cuatro fuentes UnicoRey por separado. Mexicanos de Mazdita no hereda la lista UnicoRey. El nombre visible no verifica su identidad.", 12f, soft)
        label("Tipsters admitidos en UnicoRey: ${PickBridgeRules.allowedTipsters.joinToString(", ")}", 12f, soft)

        body.addView(Switch(this).apply {
            text = "Reenvío automático a Pick$ · OFF"
            setTextColor(android.graphics.Color.WHITE)
            isChecked = BuildConfig.PICK_RELAY_REAL_ENABLED && preferences.enabled
            isEnabled = BuildConfig.PICK_RELAY_REAL_ENABLED
            setOnCheckedChangeListener { _, value ->
                if (!BuildConfig.PICK_RELAY_REAL_ENABLED || connected() !== session) {
                    preferences.stop()
                    isChecked = false
                } else if (!preferences.changeEnabled(value)) {
                    isChecked = false
                    toast("Completa destino y fuentes válidas primero")
                }
            }
        })
        button("STOP · mantener salida apagada") {
            preferences.stop()
            toast("Salida apagada. Se conservan identidades, checkpoints y entregas inciertas.")
            render()
        }
        label("Cambiar destino o fuentes deja el interruptor OFF. Volver a abrir la app no reenvía publicaciones anteriores. Una entrega UNKNOWN no se reintenta automáticamente.", 12f, soft)
        renderCoverage(preferences)
        button("Ver cobertura de otro chat cargado") {
            val current = connected()
            if (current == null) { render(); return@button }
            val chats = loaded(current)
            if (chats.isEmpty()) { toast("Carga canales o grupos primero"); return@button }
            dialog().setTitle("Cobertura observada · no autoriza una fuente")
                .setItems(chats.map { "${it.title}\nID local: ${it.chatId}" }.toTypedArray()) { _, index ->
                    if (connected() === current) coverageChat = chats[index].chatId
                    render()
                }.setNegativeButton("Cancelar", null).show()
        }
        card("POCO / HyperOS",
            "La conexión usa el servicio visible de CanalDeck. HyperOS puede suspenderla al ahorrar batería o cerrar la app. " +
                "Revisa Inicio automático y batería para CanalDeck si tú lo decides; no se prometen 24 horas de cobertura ni avisos instantáneos.")
        if (choices.isEmpty()) label("Carga tus canales y grupos desde Chats y vuelve a esta pantalla.", 13f, soft)
        button("Volver a CanalDeck") { finish() }
    }

    private fun chooseDestination() {
        val session = connected() ?: return
        // Pick$ is a channel: exclude groups and private chats from the destination picker.
        val choices = loaded(session).filter { (session.chats[it.chatId]?.type as? ChatTypeSupergroup)?.isChannel == true }
        if (choices.isEmpty()) { toast("Carga tu canal destino en Chats antes de elegirlo"); return }
        dialog().setTitle("Selecciona tu canal Pick$ real")
            .setItems(choices.map { "${it.title}\nID local: ${it.chatId}" }.toTypedArray()) { _, index ->
                if (connected() !== session) { render(); return@setItems }
                val current = loaded(session)
                val saved = RelaySettingsSelection(RelayPreferences(this, boundAccount))
                    .selectDestination(session.accountId, current, choices[index].chatId)
                if (!saved) toast("No se guardó: identidad no cargada o destino configurado como fuente")
                render()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun chooseSource() {
        val session = connected() ?: return
        val policies = RelaySettingsSelection.allowedPolicies
        dialog().setTitle("¿Qué fuente autorizada vas a identificar?")
            .setItems(policies.map { RelaySettingsSelection.label(it) }.toTypedArray()) policyChoice@ { _, policyIndex ->
                if (connected() !== session) { render(); return@policyChoice }
                val policy = policies[policyIndex]
                val choices = loaded(session).filter { it.chatId != RelayPreferences(this, boundAccount).destinationChatId() }
                if (choices.isEmpty()) { toast("No hay canales o grupos cargados disponibles"); return@policyChoice }
                dialog().setTitle(RelaySettingsSelection.label(policy))
                    .setItems(choices.map { "${it.title}\nID local: ${it.chatId}" }.toTypedArray()) sourceChoice@ { _, index ->
                        if (connected() !== session) { render(); return@sourceChoice }
                        val saved = RelaySettingsSelection(RelayPreferences(this, boundAccount)).selectSource(
                            session.accountId, loaded(session), choices[index].chatId, policy, System.currentTimeMillis() / 1000L)
                        if (!saved) toast("No se guardó: revisa la identidad y que fuente y destino sean distintos")
                        render()
                    }.setNegativeButton("Cancelar", null).show()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun renderCoverage(preferences: RelayPreferences) {
        label("Cobertura local y privada", 18f)
        label("Estos son eventos vistos por CanalDeck estando conectado, no el total publicado en Telegram. No se guardan textos, captions, fotos ni enlaces en los contadores.", 12f, soft)
        try {
            RelayDatabase(this).use { database ->
                (preferences.sources().keys + listOf(coverageChat).filter { it != 0L }).forEach { chat ->
                    val window = database.counterWindows(boundAccount, chat).firstOrNull()
                        ?: ((System.currentTimeMillis() / 1000L) / 3600L) * 3600L
                    val counts = database.counters(RelayCounterKey(boundAccount, chat, window))
                    fun count(metric: RelayMetric) = counts[metric] ?: 0L
                    val name = TelegramSession.current?.chats?.get(chat)?.title ?: "Fuente preparada"
                    card(name + " · " + PickBridgeRules.originalDate(window),
                        "Vistos: ${count(RelayMetric.SEEN)} · candidatos: ${count(RelayMetric.CANDIDATES)}\n" +
                            "Tipster verificado: ${count(RelayMetric.VERIFIED_TIPSTER)} · descartados: ${count(RelayMetric.DISCARDED)}\n" +
                            "Protegidos: ${count(RelayMetric.PROTECTED)} · únicos: ${count(RelayMetric.UNIQUE)}\n" +
                            "Avisados localmente: ${count(RelayMetric.LOCAL_NOTIFIED)} · preparados: ${count(RelayMetric.PREPARED)}\n" +
                            "Confirmados reales: ${count(RelayMetric.REAL_CONFIRMED)}")
                }
            }
        } catch (_: Exception) {
            label("No se pudieron leer los contadores privados; no se infiere cobertura ni se vacían los registros.", 12f, soft)
        }
    }
}
