package com.nuxx.app.ui

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class ShortVideo(
    val viewkey: String,
    val title: String,
    val thumb: String,
    val link: String,
    val likes: String,
    val views: String,
    var isLiked: Boolean = false,
    var isMuted: Boolean = false
)

object ShortiesApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // Pool de todos os vídeos carregados dos 3 ficheiros
    private val videoPool = mutableListOf<ShortVideo>()
    private var poolLoaded = false

    // Índices já entregues na sessão atual para evitar repetição
    private val deliveredIndices = mutableSetOf<Int>()

    private val jsonUrls = listOf(
        "https://nuxxweb.onrender.com/shorties1.json",
        "https://nuxxweb.onrender.com/shorties2.json",
        "https://nuxxweb.onrender.com/shorties3.json"
    )

    private fun loadPool() {
        if (poolLoaded) return
        val combined = mutableListOf<ShortVideo>()
        for (url in jsonUrls) {
            try {
                val req  = Request.Builder().url(url).build()
                val body = client.newCall(req).execute().use { it.body?.string() ?: "[]" }
                val arr  = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val link = obj.optString("link")
                    if (link.isBlank()) continue
                    combined.add(
                        ShortVideo(
                            viewkey = link.hashCode().toString(),
                            title   = obj.optString("title", ""),
                            thumb   = "",
                            link    = link,
                            likes   = "0",
                            views   = "0"
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        // Baralha o pool uma vez ao carregar
        combined.shuffle()
        videoPool.addAll(combined)
        poolLoaded = true
    }

    /**
     * Devolve [count] vídeos aleatórios sem repetir dentro da sessão.
     * Quando o pool está esgotado, reseta os índices entregues e volta a baralhar.
     */
    fun fetchVideos(page: Int = 1, count: Int = 20): List<ShortVideo> {
        return try {
            loadPool()
            if (videoPool.isEmpty()) return emptyList()

            // Se já entregámos tudo, reset e nova ordem aleatória
            if (deliveredIndices.size >= videoPool.size) {
                deliveredIndices.clear()
                videoPool.shuffle()
            }

            val available = (videoPool.indices - deliveredIndices).toMutableList().shuffled()
            val picked    = available.take(count)
            deliveredIndices.addAll(picked)
            picked.map { videoPool[it] }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Chama ao destruir a app para reset completo */
    fun reset() {
        videoPool.clear()
        deliveredIndices.clear()
        poolLoaded = false
    }
}