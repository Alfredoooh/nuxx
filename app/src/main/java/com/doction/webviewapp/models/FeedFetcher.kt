// FeedFetcher.kt
package com.doction.webviewapp.models

import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

object FeedFetcher {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val TERMS = listOf(
        "", "amateur", "teen", "milf", "blonde", "brunette", "asian",
        "latina", "hot", "sexy", "beautiful", "young", "wild", "homemade",
        "big", "lesbian", "college", "mature", "ebony", "babe",
    )

    private fun rndTerm() = TERMS[Random.nextInt(TERMS.size)]
    private fun rndPage(max: Int) = Random.nextInt(max) + 1

    // ── fetchAll — corre todos em paralelo e aguarda ───────────────────────────
    fun fetchAll(page: Int): List<FeedVideo> {
        val fetchers: List<() -> List<FeedVideo>> = listOf(
            ::fetchRedTube, ::fetchEporner, ::fetchPornHub,
            ::fetchXVideos, ::fetchXHamster, ::fetchYouPorn,
            ::fetchSpankBang, ::fetchBravoTube, ::fetchDrTuber,
            ::fetchTXXX, ::fetchGotPorn, ::fetchPornDig,
        )
        val results = Collections.synchronizedList(mutableListOf<FeedVideo>())
        val threads = fetchers.map { f ->
            Thread {
                try { results.addAll(f()) } catch (_: Exception) {}
            }.also { it.start() }
        }
        threads.forEach { it.join(15_000) } // espera máx 15s por thread
        results.removeAll { it.videoUrl.isEmpty() }
        results.shuffle()
        return results
    }

    // ── Métodos públicos para fetch individual (usado pelo ExploreView) ────────
    fun fetchRedTube()   = _fetchRedTube()
    fun fetchEporner()   = _fetchEporner()
    fun fetchPornHub()   = _fetchPornHub()
    fun fetchXVideos()   = _fetchXVideos()
    fun fetchXHamster()  = _fetchXHamster()
    fun fetchYouPorn()   = _fetchYouPorn()
    fun fetchSpankBang() = _fetchSpankBang()
    fun fetchBravoTube() = _fetchBravoTube()
    fun fetchDrTuber()   = _fetchDrTuber()
    fun fetchTXXX()      = _fetchTXXX()
    fun fetchGotPorn()   = _fetchGotPorn()
    fun fetchPornDig()   = _fetchPornDig()

    // ── HTTP ──────────────────────────────────────────────────────────────────
    private fun get(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json, text/xml, */*")
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            body
        } catch (_: Exception) { null }
    }

    // ── RSS ───────────────────────────────────────────────────────────────────
    private fun parseRss(body: String): List<Map<String, String>> {
        val items = mutableListOf<Map<String, String>>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            }
            val doc      = factory.newDocumentBuilder().parse(body.byteInputStream(Charsets.UTF_8))
            val nodeList = doc.getElementsByTagName("item")
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i) as? Element ?: continue
                val map  = mutableMapOf<String, String>()
                map["link"]  = node.getElementsByTagName("link").item(0)?.textContent?.trim() ?: ""
                map["title"] = node.getElementsByTagName("title").item(0)?.textContent?.trim() ?: ""
                val mediaThumbs  = node.getElementsByTagNameNS("*", "thumbnail")
                val mediaContent = node.getElementsByTagNameNS("*", "content")
                val enclosures   = node.getElementsByTagName("enclosure")
                map["thumb"] = when {
                    mediaThumbs.length > 0  -> (mediaThumbs.item(0)  as? Element)?.getAttribute("url") ?: ""
                    mediaContent.length > 0 -> (mediaContent.item(0) as? Element)?.getAttribute("url") ?: ""
                    enclosures.length > 0   -> (enclosures.item(0)   as? Element)?.getAttribute("url") ?: ""
                    else -> ""
                }
                items.add(map)
            }
        } catch (_: Exception) {}
        return items
    }

    // ── RedTube ───────────────────────────────────────────────────────────────
    private fun _fetchRedTube(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        // Corre só 1 order aleatório em vez de 4 em série
        val order = listOf("newest", "mostviewed", "hottest", "rating").random()
        try {
            val term = rndTerm()
            val body = get(
                "https://api.redtube.com/?data=redtube.Videos.searchVideos" +
                "&output=json&search=${java.net.URLEncoder.encode(term, "UTF-8")}" +
                "&thumbsize=big&count=30&ordering=$order&page=${rndPage(20)}"
            ) ?: return items
            val videos = org.json.JSONObject(body).optJSONArray("videos") ?: return items
            for (i in 0 until videos.length()) {
                val vm    = (videos.opt(i) as? org.json.JSONObject)?.optJSONObject("video") ?: continue
                val thumb = vm.optString("thumb"); val id = vm.optString("video_id")
                if (thumb.isEmpty() || id.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                    thumb    = thumb,
                    videoUrl = "https://www.redtube.com/$id",
                    duration = vm.optString("duration"),
                    views    = vm.optString("views"),
                    source   = VideoSource.REDTUBE,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── Eporner ───────────────────────────────────────────────────────────────
    private fun _fetchEporner(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val order = listOf("latest", "top-weekly", "top-monthly").random()
        try {
            val term = rndTerm()
            val body = get(
                "https://www.eporner.com/api/v2/video/search/" +
                "?per_page=30&page=${rndPage(40)}&order=$order&format=json" +
                "&thumbsize=big&query=${java.net.URLEncoder.encode(term, "UTF-8")}"
            ) ?: return items
            val videos = org.json.JSONObject(body).optJSONArray("videos") ?: return items
            for (i in 0 until videos.length()) {
                val vm = videos.opt(i) as? org.json.JSONObject ?: continue
                val id = vm.optString("id"); if (id.isEmpty()) continue
                val thumbArr = vm.optJSONArray("thumbs")
                val thumb = if (thumbArr != null && thumbArr.length() > 0)
                    (thumbArr.opt(thumbArr.length() - 1) as? org.json.JSONObject)?.optString("src") ?: ""
                else vm.optString("thumb")
                if (thumb.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                    thumb    = thumb,
                    videoUrl = "https://www.eporner.com/video-$id/",
                    duration = vm.optString("length_min"),
                    views    = vm.optString("views"),
                    source   = VideoSource.EPORNER,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── PornHub ───────────────────────────────────────────────────────────────
    private fun _fetchPornHub(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val order = listOf("newest", "mostviewed").random()
        try {
            val term = rndTerm()
            val body = get(
                "https://www.pornhub.com/webmasters/search" +
                "?search=${java.net.URLEncoder.encode(term, "UTF-8")}" +
                "&ordering=$order&page=${rndPage(30)}&thumbsize=medium&format=json"
            ) ?: return items
            val data   = org.json.JSONObject(body)
            val videos = data.optJSONArray("videos") ?: data.optJSONArray("video") ?: return items
            for (i in 0 until videos.length()) {
                val vm      = videos.opt(i) as? org.json.JSONObject ?: continue
                val viewkey = vm.optString("video_id").ifEmpty { vm.optString("viewkey") }
                if (viewkey.isEmpty()) continue
                val thumbs = vm.optJSONArray("thumbs")
                var thumb  = ""
                if (thumbs != null && thumbs.length() > 0) {
                    val t = thumbs.opt(0) as? org.json.JSONObject
                    thumb = t?.optString("src") ?: t?.optString("url") ?: ""
                }
                if (thumb.isEmpty()) thumb = vm.optString("default_thumb")
                if (thumb.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                    thumb    = thumb,
                    videoUrl = "https://www.pornhub.com/view_video.php?viewkey=$viewkey",
                    duration = vm.optString("duration"),
                    views    = vm.optString("views"),
                    source   = VideoSource.PORNHUB,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── XVideos ───────────────────────────────────────────────────────────────
    private fun _fetchXVideos(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf(
            "https://www.xvideos.com/feeds/rss-new/0",
            "https://www.xvideos.com/feeds/rss-most-viewed-alltime/0",
            "https://www.xvideos.com/feeds/rss-new/straight/0",
        ).random()
        try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue
                if (link.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                    thumb    = item["thumb"] ?: "",
                    videoUrl = link,
                    duration = "", views = "",
                    source   = VideoSource.XVIDEOS,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── xHamster ─────────────────────────────────────────────────────────────
    private fun _fetchXHamster(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        try {
            val term = rndTerm()
            val conn = URL(
                "https://xhamster.com/api/front/search" +
                "?q=${java.net.URLEncoder.encode(term, "UTF-8")}" +
                "&page=${rndPage(20)}&sectionName=video"
            ).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000; conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            if (conn.responseCode != 200) { conn.disconnect(); return items }
            val body   = conn.inputStream.bufferedReader().readText(); conn.disconnect()
            val models = org.json.JSONObject(body)
                .optJSONObject("data")?.optJSONObject("videos")
                ?.optJSONArray("models") ?: return items
            for (i in 0 until models.length()) {
                val vm    = models.opt(i) as? org.json.JSONObject ?: continue
                val id    = vm.optString("id")
                val thumb = vm.optString("thumbUrl").ifEmpty { vm.optString("thumb") }
                val url2  = vm.optString("pageURL").ifEmpty { vm.optString("url") }
                if (id.isEmpty() || thumb.isEmpty() || url2.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                    thumb    = thumb, videoUrl = url2,
                    duration = vm.optString("duration"),
                    views    = FeedVideo.fmtViews(vm.optString("views")),
                    source   = VideoSource.XHAMSTER,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── YouPorn ───────────────────────────────────────────────────────────────
    private fun _fetchYouPorn(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        try {
            val body   = get("https://www.youporn.com/api/video/search/?is_top=1&page=${rndPage(15)}&per_page=30") ?: return items
            val data   = org.json.JSONObject(body)
            val videos = data.optJSONArray("videos") ?: data.optJSONArray("data") ?: return items
            for (i in 0 until videos.length()) {
                val vm    = videos.opt(i) as? org.json.JSONObject ?: continue
                val id    = vm.optString("id").ifEmpty { vm.optString("video_id") }
                val thumb = vm.optString("thumb").ifEmpty { vm.optString("default_thumb") }
                if (id.isEmpty() || id == "0" || thumb.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                    thumb    = thumb,
                    videoUrl = "https://www.youporn.com/watch/$id/",
                    duration = vm.optString("duration"),
                    views    = FeedVideo.fmtViews(vm.optString("views")),
                    source   = VideoSource.YOUPORN,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── SpankBang ─────────────────────────────────────────────────────────────
    private fun _fetchSpankBang(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://spankbang.com/rss/", "https://spankbang.com/rss/trending/").random()
        try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                    thumb    = item["thumb"] ?: "", videoUrl = link,
                    duration = "", views = "", source = VideoSource.SPANKBANG,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── BravoTube ─────────────────────────────────────────────────────────────
    private fun _fetchBravoTube(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://www.bravotube.net/rss/new/", "https://www.bravotube.net/rss/popular/").random()
        try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                    thumb    = item["thumb"] ?: "", videoUrl = link,
                    duration = "", views = "", source = VideoSource.BRAVOTUBE,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── DrTuber ───────────────────────────────────────────────────────────────
    private fun _fetchDrTuber(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://www.drtuber.com/rss/latest", "https://www.drtuber.com/rss/popular").random()
        try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                    thumb    = item["thumb"] ?: "", videoUrl = link,
                    duration = "", views = "", source = VideoSource.DRTUBER,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── TXXX ──────────────────────────────────────────────────────────────────
    private fun _fetchTXXX(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://www.txxx.com/rss/new/", "https://www.txxx.com/rss/popular/").random()
        try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                    thumb    = item["thumb"] ?: "", videoUrl = link,
                    duration = "", views = "", source = VideoSource.TXXX,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── GotPorn ───────────────────────────────────────────────────────────────
    private fun _fetchGotPorn(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://www.gotporn.com/rss/latest", "https://www.gotporn.com/rss/popular").random()
        try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                    thumb    = item["thumb"] ?: "", videoUrl = link,
                    duration = "", views = "", source = VideoSource.GOTPORN,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── PornDig ───────────────────────────────────────────────────────────────
    private fun _fetchPornDig(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        try {
            val body = get("https://www.porndig.com/rss") ?: get("https://www.porndig.com/rss?category=latest") ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                    thumb    = item["thumb"] ?: "", videoUrl = link,
                    duration = "", views = "", source = VideoSource.PORNDIG,
                ))
            }
        } catch (_: Exception) {}
        return items
    }
}