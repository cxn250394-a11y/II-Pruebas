package com.charly.aural

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import com.charly.audio.AudioPolicy

object AudioDiagnostics {
    /** Enumerates advertised capabilities. Does not change route, gain, sample rate or mixer mode. */
    fun report(context: Context): String = buildString {
        appendLine(AudioPolicy.BIT_PERFECT_STATUS)
        appendLine("Este piloto usa la salida normal de Android. No activa un controlador USB exclusivo.")
        appendLine("La frecuencia de un archivo no demuestra la frecuencia que llega al DAC.")
        appendLine()
        appendLine("Android API ${Build.VERSION.SDK_INT}")
        appendLine("Salidas disponibles (no significa que estén reproduciendo):")
        val manager = context.getSystemService(AudioManager::class.java)
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        if (devices.isEmpty()) appendLine("No se informaron dispositivos.")
        for (device in devices) {
            val usb = device.type == AudioDeviceInfo.TYPE_USB_DEVICE || device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            appendLine("• ${device.productName} · ${if (usb) "USB" else "tipo ${device.type}"}")
            appendLine("  Frecuencias anunciadas: ${device.sampleRates.take(12).joinToString().ifBlank { "no especificadas" }}")
            if (usb && Build.VERSION.SDK_INT >= 34) {
                try {
                    val mixers = manager.getSupportedMixerAttributes(device)
                    val perfect = mixers.filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
                    appendLine("  Perfiles BIT_PERFECT anunciados: ${perfect.size}; ninguno activado por esta app.")
                    perfect.take(8).forEach { appendLine("  ${it.format.sampleRate} Hz · encoding ${it.format.encoding} · ${it.format.channelCount} canales") }
                } catch (_: Exception) { appendLine("  El sistema no permitió consultar sus perfiles.") }
            }
        }
        appendLine()
        appendLine("Bluetooth no se etiqueta como bit-perfect. Un perfil USB disponible tampoco prueba integridad de muestras. Se requiere implementar y validar la ruta exacta con el DAC real.")
    }
}
