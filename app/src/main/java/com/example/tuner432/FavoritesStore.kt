package com.example.tuner432

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Trwały magazyn ulubionych stacji (SharedPreferences, format JSON). */
object FavoritesStore {

    private const val PREF = "kamerton_prefs"
    private const val KEY = "favorites"

    fun load(ctx: Context): MutableList<RadioStation> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<RadioStation>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    RadioStation(
                        name = o.getString("name"),
                        url = o.getString("url"),
                        nowPlayingId = if (o.has("npId")) o.getInt("npId") else null,
                        logoUrl = o.optString("logo").ifEmpty { null }
                    )
                )
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(ctx: Context, list: List<RadioStation>) {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("name", s.name)
            o.put("url", s.url)
            s.nowPlayingId?.let { o.put("npId", it) }
            s.logoUrl?.let { o.put("logo", it) }
            arr.put(o)
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
