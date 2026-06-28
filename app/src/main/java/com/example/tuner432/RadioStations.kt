package com.example.tuner432

/**
 * Stacja: nazwa + strumień. [nowPlayingId] = ID w API ZPR (now_playing)
 * dla stacji Eska/VOX, które tytuł podają osobnym API (nie w streamie).
 * Ogólnopolska Radio ESKA = 2980 (feed muzyczny zgodny z ESKA Toruń).
 */
data class RadioStation(
    val name: String,
    val url: String,
    val nowPlayingId: Int? = null,
    val logoUrl: String? = null
)

object RadioStations {

    /** ZAWSZE pierwsza. ESKA Toruń + tytuły z API ZPR (id 2980). */
    val pinned = RadioStation("ESKA Toru\u0144", "https://ic1.smcdn.pl/2150-1.mp3", nowPlayingId = 2980)

    val fallback: List<RadioStation> = listOf(
        pinned,
        RadioStation("RMF FM", "http://217.74.72.3:8000/rmf_fm"),
        RadioStation("RMF MAXXX", "http://217.74.72.3:8000/rmf_maxxx"),
        RadioStation("RMF Classic", "http://rs201-krk.rmfstream.pl/rmf_classic_waw"),
        RadioStation("Radio Eska", "https://ic1.smcdn.pl/2380-1.mp3"),
        RadioStation("VOX FM", "https://ic1.smcdn.pl/3990-1.mp3"),
        RadioStation("Polskie Radio Tr\u00F3jka", "https://stream13.polskieradio.pl/pr3/pr3.sdp/playlist.m3u8"),
        RadioStation("Polskie Radio Jedynka", "https://stream11.polskieradio.pl/pr1/pr1.sdp/playlist.m3u8"),
        RadioStation("Polskie Radio Dw\u00F3jka", "https://stream12.polskieradio.pl/pr2/pr2.sdp/playlist.m3u8"),
    )
}
