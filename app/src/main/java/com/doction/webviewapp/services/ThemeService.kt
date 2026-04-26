package com.doction.webviewapp.services

import android.content.Context
import android.content.SharedPreferences
import com.doction.webviewapp.theme.AppTheme

class ThemeService private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var _instance: ThemeService? = null
        fun init(context: Context) {
            if (_instance == null) _instance = ThemeService(context)
        }
        val instance: ThemeService get() = _instance!!

        val wallpapers = listOf(
            "images/background.png","images/bg2.jpg","images/bg3.jpg",
            "images/bg4.jpg","images/bg5.jpg","images/bg6.jpg",
            "images/bg7.jpg","images/bg8.jpg","images/bg9.jpg",
            "images/bg10.jpg","images/bg11.jpg","images/bg12.jpg",
        )

        val engines = linkedMapOf(
            "google"     to "Google",
            "bing"       to "Bing",
            "duckduckgo" to "DuckDuckGo",
            "brave"      to "Brave",
        )

        val availableFeedSources = linkedMapOf(
            "eporner"   to "EPorner",
            "pornhub"   to "PornHub",
            "redtube"   to "RedTube",
            "youporn"   to "YouPorn",
            "xvideos"   to "XVideos",
            "xhamster"  to "xHamster",
            "spankbang" to "SpankBang",
            "bravotube" to "BravoTube",
            "drtuber"   to "DrTuber",
            "txxx"      to "TXXX",
        )
    }

    // ── Propriedades ──────────────────────────────────────────────────────────

    var isDark: Boolean
        get() = prefs.getBoolean("dark", true)
        set(v) {
            prefs.edit().putBoolean("dark", v).apply()
            AppTheme.isDark = v
            AppTheme.notifyThemeChanged()
        }

    var bg: String
        get() = prefs.getString("bg", wallpapers.first()) ?: wallpapers.first()
        set(v) { prefs.edit().putString("bg", v).apply() }

    var useWallpaper: Boolean
        get() = prefs.getBoolean("use_wallpaper", false)
        set(v) { prefs.edit().putBoolean("use_wallpaper", v).apply() }

    var engine: String
        get() = prefs.getString("engine", "google") ?: "google"
        set(v) { prefs.edit().putString("engine", v).apply() }

    var lockDelay: Int
        get() = prefs.getInt("lock_delay", 0)
        set(v) { prefs.edit().putInt("lock_delay", v).apply() }

    var privacyRecent: Boolean
        get() = prefs.getBoolean("priv_recent", true)
        set(v) { prefs.edit().putBoolean("priv_recent", v).apply() }

    var noScreenshot: Boolean
        get() = prefs.getBoolean("no_ss", true)
        set(v) { prefs.edit().putBoolean("no_ss", v).apply() }

    var maxVolume: Int
        get() = prefs.getInt("max_vol", 100)
        set(v) { prefs.edit().putInt("max_vol", v.coerceIn(10, 100)).apply() }

    var feedPageSize: Int
        get() = prefs.getInt("feed_page_size", 20)
        set(v) { prefs.edit().putInt("feed_page_size", v.coerceIn(10, 60)).apply() }

    var feedAutoplay: Boolean
        get() = prefs.getBoolean("feed_autoplay", false)
        set(v) { prefs.edit().putBoolean("feed_autoplay", v).apply() }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @JvmName("setDarkHelper")          fun setDark(v: Boolean)         { isDark = v }
    @JvmName("setBgHelper")            fun setBg(v: String)             { bg = v }
    @JvmName("setUseWallpaperHelper")  fun setUseWallpaper(v: Boolean)  { useWallpaper = v }
    @JvmName("setEngineHelper")        fun setEngine(v: String)         { engine = v }
    @JvmName("setLockDelayHelper")     fun setLockDelay(v: Int)         { lockDelay = v }
    @JvmName("setPrivacyRecentHelper") fun setPrivacyRecent(v: Boolean) { privacyRecent = v }
    @JvmName("setNoScreenshotHelper")  fun setNoScreenshot(v: Boolean)  { noScreenshot = v }
    @JvmName("setMaxVolumeHelper")     fun setMaxVolume(v: Int)         { maxVolume = v }

    // ── Utils ─────────────────────────────────────────────────────────────────

    fun searchUrl(q: String): String {
        val e = java.net.URLEncoder.encode(q, "UTF-8")
        return when (engine) {
            "bing"       -> "https://www.bing.com/search?q=$e"
            "duckduckgo" -> "https://duckduckgo.com/?q=$e"
            "brave"      -> "https://search.brave.com/search?q=$e"
            else         -> "https://www.google.com/search?q=$e"
        }
    }

    val lockDelayLabel: String get() = when {
        lockDelay == 0   -> "Imediato"
        lockDelay < 60   -> "$lockDelay seg"
        lockDelay < 3600 -> "${lockDelay / 60} min"
        else             -> "${lockDelay / 3600} h"
    }
}