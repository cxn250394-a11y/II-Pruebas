package mx.charly.canal

import android.os.Bundle
import mx.charly.ui.Screen

/** Private owner rehearsal; no live account/session/client is created or consulted. */
class AsyncOfflineDemoActivity : Screen() {
    private lateinit var rehearsal: AsyncOfflineDemo

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        rehearsal = AsyncOfflineDemo(this)
        render()
    }

    private fun render() {
        layout("Ensayo offline", "Picks ficticios · 0 envíos reales")
        label("No necesitas cuenta ni conexión. Estas tarjetas representan dos fotos inventadas; no se carga ninguna imagen.", 14f, soft)
        val status = when (rehearsal.step) {
            AsyncOfflineDemo.Step.IDLE -> "Listo para un ensayo nuevo"
            AsyncOfflineDemo.Step.PARTIAL_HOLD -> "Álbum incompleto · retenido"
            AsyncOfflineDemo.Step.HEADER_WAITING -> "Encabezado pendiente · fotos sin solicitar"
            AsyncOfflineDemo.Step.QUICK_ACK_WAITING -> "Acuse rápido recibido · sigue pendiente"
            AsyncOfflineDemo.Step.MEDIA_WAITING -> "Encabezado confirmado · 0 de 2 fotos confirmadas"
            AsyncOfflineDemo.Step.ONE_PHOTO_WAITING -> "1 de 2 fotos confirmadas · sigue pendiente"
            AsyncOfflineDemo.Step.COMPLETE -> "Escenario ficticio confirmado · 2 de 2 fotos"
            AsyncOfflineDemo.Step.UNKNOWN -> "UNKNOWN / incierto · no se reintenta"
        }
        card(status, "Solicitudes ficticias: ${rehearsal.requestCount}. Todo ocurre dentro del teléfono con Fake; no existe transporte real en este ensayo.")
        if (rehearsal.step != AsyncOfflineDemo.Step.IDLE) {
            card("Foto ficticia A", "Tarjeta de ejemplo · sin archivo ni descarga")
            card("Foto ficticia B", if (rehearsal.step == AsyncOfflineDemo.Step.PARTIAL_HOLD)
                "Todavía no observada en el escenario" else "Tarjeta de ejemplo · sin archivo ni descarga")
        }
        if (rehearsal.reopened) label("Estado conservado al reabrir · nuevas solicitudes: ${rehearsal.newRequestsAfterReopen}", 14f, accent)
        val next = when (rehearsal.step) {
            AsyncOfflineDemo.Step.PARTIAL_HOLD -> "Completar álbum ficticio"
            AsyncOfflineDemo.Step.HEADER_WAITING -> "Simular acuse rápido"
            AsyncOfflineDemo.Step.QUICK_ACK_WAITING -> "Confirmar encabezado ficticio"
            AsyncOfflineDemo.Step.MEDIA_WAITING -> "Confirmar primera foto ficticia"
            AsyncOfflineDemo.Step.ONE_PHOTO_WAITING -> "Confirmar segunda foto ficticia"
            else -> null
        }
        if (next != null) button(next, true) { if (!rehearsal.advance()) toast("El ensayo quedó detenido; inicia otro escenario."); render() }
        if (rehearsal.snapshot?.terminal == false) button("Simular corte · conservar incierto") { rehearsal.stopScenario(); render() }
        if (rehearsal.snapshot != null) button("Comprobar reapertura · sin reintentar") { rehearsal.reopen(); render() }
        button("Nuevo ensayo ficticio") { if (!rehearsal.newScenario()) toast("No se pudo guardar el ensayo; queda detenido."); render() }
        label("Cada ensayo nuevo conserva los anteriores. Cerrar esta pantalla no activa Telegram ni convierte un resultado incierto en confirmado.", 13f, soft)
        button("Volver") { finish() }
    }

    override fun onDestroy() {
        if (::rehearsal.isInitialized) rehearsal.close()
        super.onDestroy()
    }
}
