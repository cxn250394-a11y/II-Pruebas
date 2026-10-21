package com.charly.aural

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.charly.audio.AudioEntry
import com.charly.audio.EntryKind

internal fun playable(entry: AudioEntry): MediaItem = MediaItem.Builder()
    .setMediaId(entry.id).setUri(entry.uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title)
        .setArtist(when (entry.kind) { EntryKind.LOCAL -> "Archivo elegido por ti"; EntryKind.RADIO -> "Radio por Internet"; EntryKind.DEMO -> "Aural · prueba original" })
        .build()).build()
