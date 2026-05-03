package com.nuxx.app.services

import android.content.Context
import android.content.SharedPreferences

class LockService private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN     = "app_pin"
        private const val KEY_ENABLED = "lock_enabled"
        const val DEFAULT_PIN         = "0123"

        @Volatile private var _instance: LockService? = null
        fun init(context: Context) {
            if (_instance == null) {
                _instance = LockService(context)
                _instance!!.initDefaults()
            }
        }
        val instance: LockService get() = _instance!!
    }

    private fun initDefaults() {
        if (!prefs.contains(KEY_PIN)) {
            prefs.edit()
                .putString(KEY_PIN, DEFAULT_PIN)
                .putBoolean(KEY_ENABLED, true)
                .apply()
        }
    }

    fun getPin(): String = prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun verify(input: String): Boolean = input == getPin()
}