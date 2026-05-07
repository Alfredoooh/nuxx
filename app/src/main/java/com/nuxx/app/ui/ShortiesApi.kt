package com.nuxx.app.ui

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ShortiesApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val COOKIES = "age_verified=1; accessAgeDisclaimerPH=1; il=1; " +
        "platform=pc; cookiesAccepted=1; cookieConsent=3"

    fun fetchViewKeys(page: Int): List<String> {
        return try {
            val req = Request.Builder()
                .url("https://www.pornhub.com/shorties?page=$page")
                .header("User-Agent", UA)
                .header("Cookie", COOKIES)
                .build()
            val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
            Regex("viewkey=([a-zA-Z0-9]{9,})")
                .findAll(html)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fetchVideoUrl(viewkey: String): String? {
        return try {
            val req = Request.Builder()
                .url("https://www.pornhub.com/view_video.php?viewkey=$viewkey")
                .header("User-Agent", UA)
                .header("Cookie", COOKIES)
                .build()
            val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
            // Tenta 1080P primeiro, depois 720P, depois qualquer m3u8
            val regex = Regex(""""videoUrl":"(https:\\/\\/[^"]+\.m3u8[^"]*)"""")
            val all = regex.findAll(html)
                .map { it.groupValues[1].replace("\\/", "/") }
                .toList()
            all.firstOrNull { it.contains("1080P") }
                ?: all.firstOrNull { it.contains("720P") }
                ?: all.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}