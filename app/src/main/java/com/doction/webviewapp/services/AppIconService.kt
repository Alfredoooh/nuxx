package com.nuxx.app.services

import android.content.Context
import android.content.SharedPreferences

enum class AppIconVariant { CLASSIC, LIGHT, ORIGINAL }

class AppIconService private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("icon_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_KEY = "selected_app_icon"
        @Volatile private var _instance: AppIconService? = null
        fun init(context: Context) {
            if (_instance == null) _instance = AppIconService(context)
        }
        val instance: AppIconService get() = _instance!!
    }

    fun getActiveIcon(): AppIconVariant {
        return when (prefs.getString(PREF_KEY, "classic")) {
            "light"    -> AppIconVariant.LIGHT
            "original" -> AppIconVariant.ORIGINAL
            else       -> AppIconVariant.CLASSIC
        }
    }

    fun setIcon(variant: AppIconVariant) {
        val name = when (variant) {
            AppIconVariant.LIGHT    -> "light"
            AppIconVariant.ORIGINAL -> "original"
            AppIconVariant.CLASSIC  -> "classic"
        }
        prefs.edit().putString(PREF_KEY, name).apply()
    }
}