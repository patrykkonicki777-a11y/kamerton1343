package com.example.tuner432

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * radio-browser.info: top PL oraz wyszukiwanie po nazwie (cały świat).
 * Zwraca tylko działające stacje (hidebroken=true / lastcheckok).
 */
object StationProvider {

    private val SERVERS = listOf(
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info"
    )

    fun fetchTopPolish(limit: Int = 100): List<RadioStation> =
        query("/json/stations/bycountrycodeexact/PL?order=clickcount&reverse=true&limit=200&hidebroken=true", limit)

    fun search(name: String, limit: Int = 40): List<RadioStation> {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        if (q.isEmpty()) return emptyList()
        return query("/json/stations/search?name=$q&order=clickcount&reverse=true&limit=120&hidebroken=true", limit)
    }

    private fun query(path: String, limit: Int): List<RadioStation> {
        for (server in SERVERS) {
            try {
                val conn = (URL(server + path).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Kamerton432/1.0")
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode != 200) { conn.disconnect(); continue }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val out = parse(text, limit)
                if (out.isNotEmpty()) return out
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private fun parse(text: String, limit: Int): List<RadioStation> {
        val arr = JSONArray(text)
        val seen = HashSet<String>()
        val out = ArrayList<RadioStation>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optInt("lastcheckok", 0) != 1) continue
            val name = o.optString("name").trim()
            var url = o.optString("url_resolved").trim()
            if (url.isEmpty()) url = o.optString("url").trim()
            if (name.isEmpty() || url.isEmpty()) continue
            val low = url.lowercase()
            if (low.endsWith(".pls") || low.endsWith(".m3u")) continue
            val key = name.lowercase()
            if (!seen.add(key)) continue
            val logo = o.optString("favicon").trim().ifEmpty { null }
            out.add(RadioStation(name, url, logoUrl = logo))
            if (out.size >= limit) break
        }
        return out
    }
}
