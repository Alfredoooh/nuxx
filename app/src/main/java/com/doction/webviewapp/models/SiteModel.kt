// SiteModel.kt
package com.doction.webviewapp.models

import android.graphics.Color

data class SiteModel(
    val id: String,
    val name: String,
    val baseUrl: String,
    val allowedDomain: String,
    val searchUrl: String,
    val localIconAsset: String? = null,
    val primaryColor: Int = Color.parseColor("#2A2A2A"),
) {
    val faviconUrl: String
        get() = "https://www.google.com/s2/favicons?domain=$allowedDomain&sz=128"

    val hasLocalIcon: Boolean
        get() = localIconAsset != null

    fun buildUrl(query: String? = null): String {
        if (!query.isNullOrBlank()) {
            return searchUrl.replace("{q}", android.net.Uri.encode(query.trim()))
        }
        return baseUrl
    }

    fun buildSearchUrl(query: String): String = buildUrl(query)

    fun isAllowed(url: String): Boolean {
        if (allowedDomain.isEmpty()) return true
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
            host == allowedDomain.lowercase() || host.endsWith(".${allowedDomain.lowercase()}")
        } catch (_: Exception) { false }
    }
}

val kSites = listOf(
    SiteModel(
        id = "xvideos",
        name = "XVideos",
        baseUrl = "https://www.xvideos.com",
        allowedDomain = "xvideos.com",
        searchUrl = "https://www.xvideos.com/?k={q}",
        primaryColor = Color.parseColor("#FF6600"),
    ),
    SiteModel(
        id = "xxx",
        name = "XXX.com",
        baseUrl = "https://www.xxx.com",
        allowedDomain = "xxx.com",
        searchUrl = "https://www.xxx.com/search?q={q}",
        primaryColor = Color.parseColor("#AA0000"),
    ),
    SiteModel(
        id = "pornhub",
        name = "PornHub",
        baseUrl = "https://pt.pornhub.com",
        allowedDomain = "pornhub.com",
        searchUrl = "https://pt.pornhub.com/video/search?search={q}",
        localIconAsset = "icons/app1.png",
        primaryColor = Color.parseColor("#FF9900"),
    ),
    SiteModel(
        id = "xnxx",
        name = "XNXX",
        baseUrl = "https://www.xnxx.com",
        allowedDomain = "xnxx.com",
        searchUrl = "https://www.xnxx.com/search/{q}",
        primaryColor = Color.parseColor("#006600"),
    ),
    SiteModel(
        id = "tikporn",
        name = "Tik Porn",
        baseUrl = "https://tik.porn",
        allowedDomain = "tik.porn",
        searchUrl = "https://tik.porn/?s={q}",
        primaryColor = Color.parseColor("#0044DD"),
    ),
    SiteModel(
        id = "bangbros",
        name = "Bang Bros",
        baseUrl = "https://bangbros.com",
        allowedDomain = "bangbros.com",
        searchUrl = "https://bangbros.com/video?q={q}",
        primaryColor = Color.parseColor("#DD2200"),
    ),
    SiteModel(
        id = "xhamster",
        name = "xHamster",
        baseUrl = "https://xhamster.com",
        allowedDomain = "xhamster.com",
        searchUrl = "https://xhamster.com/search/{q}",
        primaryColor = Color.parseColor("#FF5500"),
    ),
    SiteModel(
        id = "redtube",
        name = "RedTube",
        baseUrl = "https://www.redtube.com.br",
        allowedDomain = "redtube.com",
        searchUrl = "https://www.redtube.com.br/?search={q}",
        primaryColor = Color.parseColor("#CC0000"),
    ),
    SiteModel(
        id = "youxxx",
        name = "YouX",
        baseUrl = "https://www.youx.xxx/videos/",
        allowedDomain = "youx.xxx",
        searchUrl = "https://www.youx.xxx/search/{q}",
        primaryColor = Color.parseColor("#7700CC"),
    ),
    SiteModel(
        id = "pornpics",
        name = "PornPics",
        baseUrl = "https://www.pornpics.com/pt/",
        allowedDomain = "pornpics.com",
        searchUrl = "https://www.pornpics.com/search/?q={q}",
        primaryColor = Color.parseColor("#CC0055"),
    ),
    SiteModel(
        id = "eporner",
        name = "Eporner",
        baseUrl = "https://www.eporner.com",
        allowedDomain = "eporner.com",
        searchUrl = "https://www.eporner.com/search/{q}/",
        primaryColor = Color.parseColor("#0055AA"),
    ),
    SiteModel(
        id = "spankbang",
        name = "SpankBang",
        baseUrl = "https://spankbang.com",
        allowedDomain = "spankbang.com",
        searchUrl = "https://spankbang.com/s/{q}/",
        primaryColor = Color.parseColor("#FF3300"),
    ),
    SiteModel(
        id = "txxx",
        name = "Txxx",
        baseUrl = "https://www.txxx.com",
        allowedDomain = "txxx.com",
        searchUrl = "https://www.txxx.com/videos/{q}/",
        primaryColor = Color.parseColor("#CC6600"),
    ),
    SiteModel(
        id = "drtuber",
        name = "DrTuber",
        baseUrl = "https://www.drtuber.com",
        allowedDomain = "drtuber.com",
        searchUrl = "https://www.drtuber.com/videos/search?q={q}",
        primaryColor = Color.parseColor("#009900"),
    ),
    SiteModel(
        id = "tube8",
        name = "Tube8",
        baseUrl = "https://www.tube8.com",
        allowedDomain = "tube8.com",
        searchUrl = "https://www.tube8.com/search.html?q={q}",
        primaryColor = Color.parseColor("#CC3300"),
    ),
)