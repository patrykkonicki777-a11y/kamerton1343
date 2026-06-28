package com.example.tuner432

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pobiera aktualny utwór z API grupy ZPR (Eska, VOX itd.).
 *
 * Format odpowiedzi:
 *   { "current": { "name": "Pocałunki", "artists": ["sanah"], "image": "...jpg" }, ... }
 *
 * ID stacji (npId) to ID w przestrzeni "now_playing" (NIE to ze streamu).
 * Np. ogólnopolska Radio ESKA = 2980.
 */
object NowPlayingProvider {

    data class Track(val artist: String, val title: String, val artUrl: String?)

    fun fetch(npId: Int): Track? {
        return try {
            val ts = System.currentTimeMillis() / 1000
            val url = URL("https://front-api.grupazprmedia.pl/music/v2/now_playing/$npId/?timestamp=$ts")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                // serwer odrzuca boty - udajemy zwykłą przeglądarkę
                setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val cur = JSONObject(text).optJSONObject("current") ?: return null
            val title = cur.optString("name").trim()
            if (title.isEmpty()) return null
            val arr = cur.optJSONArray("artists")
            val artist = if (arr != null)
                (0 until arr.length()).joinToString(", ") { arr.optString(it) }.trim()
            else ""
            val art = cur.optString("image").trim().ifEmpty { null }
            Track(artist, title, art)
        } catch (e: Exception) {
            null
        }
    }
}
