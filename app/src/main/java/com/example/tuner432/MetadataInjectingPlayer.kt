package com.example.tuner432

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Owija ExoPlayera i pozwala WSTRZYKNĄĆ metadane "z zewnątrz" (z API radia),
 * tak by trafiły do sesji -> ekran blokady + Bluetooth + powiadomienie.
 *
 * getMediaMetadata() zwraca wstrzyknięte dane, a setNowPlaying() powiadamia
 * słuchaczy (w tym MediaSession), żeby odświeżyli widok. Przy zmianie utworu/
 * stacji wstrzyknięcie jest czyszczone.
 */
class MetadataInjectingPlayer(player: Player) : ForwardingPlayer(player) {

    private var injected: MediaMetadata? = null
    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    init {
        // czyść wstrzyknięcie, gdy player przełącza pozycję (nowa stacja/plik)
        super.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                injected = null
            }
        })
    }

    fun setNowPlaying(md: MediaMetadata?) {
        injected = md
        val current = mediaMetadata
        for (l in listeners) l.onMediaMetadataChanged(current)
    }

    override fun getMediaMetadata(): MediaMetadata = injected ?: super.getMediaMetadata()

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }
}
