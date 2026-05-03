package com.nuxx.app.services

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class FaviconService private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("favicon_prefs", Context.MODE_PRIVATE)

    private val pathCache  = mutableMapOf<String, String>()

    companion object {
        @Volatile private var _instance: FaviconService? = null
        fun init(context: Context) {
            if (_instance == null) _instance = FaviconService(context)
        }
        val instance: FaviconService get() = _instance!!
    }

    fun preloadAll() {
        // No Kotlin puro não temos kSites — deixar vazio ou ligar ao modelo de sites
    }

    fun getLocalPath(siteId: String, faviconUrl: String): String? {
        pathCache[siteId]?.let { return it }
        val stored = prefs.getString("fav_$siteId", null)
        if (stored != null && File(stored).exists()) {
            pathCache[siteId] = stored
            return stored
        }
        return ensureFavicon(siteId, faviconUrl)
    }

    private fun ensureFavicon(siteId: String, faviconUrl: String): String? {
        return try {
            val dir = File(context.filesDir, "favicons").also { it.mkdirs() }
            val path = "${dir.absolutePath}/$siteId.png"
            val conn = URL(faviconUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 15_000
            conn.inputStream.use { input ->
                File(path).outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            prefs.edit().putString("fav_$siteId", path).apply()
            pathCache[siteId] = path
            path
        } catch (_: Exception) { null }
    }

    fun loadBitmap(siteId: String, faviconUrl: String): Bitmap? {
        val path = getLocalPath(siteId, faviconUrl) ?: return null
        return try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
    }

    fun clearAll() {
        pathCache.clear()
        val dir = File(context.filesDir, "favicons")
        if (dir.exists()) dir.deleteRecursively()
        val keys = prefs.all.keys.filter { it.startsWith("fav_") }
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }
}