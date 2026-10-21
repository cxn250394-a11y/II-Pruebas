package com.charly.aural

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.charly.audio.AudioPolicy
import com.charly.audio.EntryKind
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Real background playback. Android mixer, not an exclusive USB driver. */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val consentListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "radio_consent" && !prefs.getBoolean("radio_consent", false)) {
            // Enforce revocation in the service even while the Activity controller is disconnected.
            session?.player?.let { it.stop(); it.clearMediaItems() }
        }
    }
    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory().setUserAgent("AuralPilot/0.1")
            .setConnectTimeoutMs(12000).setReadTimeoutMs(18000).setAllowCrossProtocolRedirects(false)
        val source = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(source).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                                             mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
                    if (controller.uid != Process.myUid()) return Futures.immediateFuture(mutableListOf())
                    val store = LibraryStore(this@PlaybackService)
                    val permitted = store.entries().filter { it.kind != EntryKind.RADIO || store.networkAllowed() }
                        .associateBy { it.id }
                    // Resolve canonical local entries by ID; never trust caller-provided URIs or metadata.
                    val accepted = mediaItems.take(500).mapNotNull { item ->
                        if (item.mediaId == AudioPolicy.demo.id) playable(AudioPolicy.demo)
                        else permitted[item.mediaId]?.let(::playable)
                    }.toMutableList()
                    return Futures.immediateFuture(accepted)
                }
            }).build()
        getSharedPreferences("aural_library", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(consentListener)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.uid == Process.myUid() || controllerInfo.isTrusted) session else null
    override fun onDestroy() {
        getSharedPreferences("aural_library", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(consentListener)
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}
