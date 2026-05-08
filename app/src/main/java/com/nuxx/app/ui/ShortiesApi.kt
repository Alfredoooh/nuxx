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

    fun fetchVideos(page: Int = 1): List<ShortVideo> {
        return try {
            val req = Request.Builder()
                .url("https://shorties1.onrender.com/videos?page=$page")
                .build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: "[]" }
            val arr  = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ShortVideo(
                    viewkey = obj.optString("viewkey"),
                    title   = obj.optString("title"),
                    thumb   = obj.optString("thumb"),
                    link    = obj.optString("link"),
                    likes   = obj.optString("likes", "0"),
                    views   = obj.optString("views", "0")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}