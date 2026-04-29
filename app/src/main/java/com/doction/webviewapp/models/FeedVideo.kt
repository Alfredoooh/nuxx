// FeedVideo.kt
package com.doction.webviewapp.models

import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

enum class VideoSource {
    EPORNER, PORNHUB, REDTUBE, YOUPORN, XVIDEOS, XHAMSTER, SPANKBANG,
    BRAVOTUBE, DRTUBER, TXXX, GOTPORN, PORNDIG, BEEG, TUBE8, TNAFLIX,
    EMPFLIX, PORNTREX, HCLIPS, TUBEDUPE, NUVID, SUNPORNO, PORNONE,
    SLUTLOAD, ICEPORN, VJAV, JIZZBUNKER, CLIPHUNTER, SEXVID, YEPTUBE,
    XNXX, PORNOXO, ANYSEX, FUQER, FAPSTER, PROPORN, H2PORN,
    ALPHAPORNO, WATCHMYGF, XCAFE, TUBECUP, VIDLOX, NAUGHTYAMERICA;

    val label: String get() = when (this) {
        EPORNER        -> "Eporner"
        PORNHUB        -> "Pornhub"
        REDTUBE        -> "RedTube"
        YOUPORN        -> "YouPorn"
        XVIDEOS        -> "XVideos"
        XHAMSTER       -> "xHamster"
        SPANKBANG      -> "SpankBang"
        BRAVOTUBE      -> "BravoTube"
        DRTUBER        -> "DrTuber"
        TXXX           -> "TXXX"
        GOTPORN        -> "GotPorn"
        PORNDIG        -> "PornDig"
        BEEG           -> "Beeg"
        TUBE8          -> "Tube8"
        TNAFLIX        -> "TNAFlix"
        EMPFLIX        -> "EmpFlix"
        PORNTREX       -> "PornTrex"
        HCLIPS         -> "HClips"
        TUBEDUPE       -> "TubeDupe"
        NUVID          -> "Nuvid"
        SUNPORNO       -> "SunPorno"
        PORNONE        -> "PornOne"
        SLUTLOAD       -> "SlutLoad"
        ICEPORN        -> "IcePorn"
        VJAV           -> "vJav"
        JIZZBUNKER     -> "JizzBunker"
        CLIPHUNTER     -> "ClipHunter"
        SEXVID         -> "SexVid"
        YEPTUBE        -> "YepTube"
        XNXX           -> "XNXX"
        PORNOXO        -> "PornoXO"
        ANYSEX         -> "AnySex"
        FUQER          -> "Fuqer"
        FAPSTER        -> "Fapster"
        PROPORN        -> "ProPorn"
        H2PORN         -> "H2Porn"
        ALPHAPORNO     -> "AlphaPorno"
        WATCHMYGF      -> "WatchMyGF"
        XCAFE          -> "xCafe"
        TUBECUP        -> "TubeCup"
        VIDLOX         -> "Vidlox"
        NAUGHTYAMERICA -> "NaughtyAmerica"
    }

    // Termos de pesquisa associados a esta fonte — usados no fetchSearch
    val searchTerms: List<String> get() = when (this) {
        EPORNER   -> listOf("amateur","milf","teen","asian","latina","blonde","anal","gay","lesbian","bdsm")
        PORNHUB   -> listOf("amateur","milf","teen","asian","latina","blonde","anal","gay","lesbian","bdsm")
        REDTUBE   -> listOf("amateur","milf","teen","asian","latina","blonde","anal","gay","lesbian","bdsm")
        XHAMSTER  -> listOf("amateur","milf","teen","asian","latina","blonde","anal","gay","lesbian","bdsm")
        YOUPORN   -> listOf("amateur","milf","teen","asian","latina","blonde")
        else      -> listOf("amateur","milf","teen","asian","latina")
    }
}

data class FeedVideo(
    val title:       String,
    val thumb:       String,
    val videoUrl:    String,
    val duration:    String,
    val views:       String,
    val source:      VideoSource,
    val publishedAt: Date? = null,
    val tags:        List<String> = emptyList(),
    val categories:  List<String> = emptyList(),
    val performer:   String = "",
) {
    companion object {
        fun cleanTitle(raw: String): String {
            return try {
                val bytes   = raw.toByteArray(Charsets.ISO_8859_1)
                val decoded = String(bytes, Charsets.UTF_8)
                if (decoded.count { it.code > 127 } < raw.count { it.code > 127 }) decoded else raw
            } catch (_: Exception) { raw }
        }

        fun fmtViews(raw: Any?): String {
            if (raw == null) return ""
            val n = raw.toString().toLongOrNull() ?: return ""
            return when {
                n >= 1_000_000L -> "${"%.1f".format(n / 1_000_000.0)}M"
                n >= 1_000L     -> "${"%.0f".format(n / 1_000.0)}K"
                n > 0           -> n.toString()
                else            -> ""
            }
        }

        fun parseDate(raw: Any?): Date? {
            val s = raw?.toString()?.trim() ?: return null
            if (s.isEmpty()) return null
            s.toLongOrNull()?.let { return Date(it * 1000) }
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "EEE, dd MMM yyyy HH:mm:ss Z",
            )
            for (fmt in formats) {
                try { return SimpleDateFormat(fmt, Locale.US).parse(s) } catch (_: Exception) {}
            }
            return null
        }
    }
}

object FeedFetcher {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val CONNECT_TIMEOUT = 6_000
    private const val READ_TIMEOUT    = 6_000

    private val TERMS = listOf(
        "", "amateur", "teen", "milf", "blonde", "brunette", "asian",
        "latina", "hot", "sexy", "beautiful", "young", "wild", "homemade",
        "big", "lesbian", "college", "mature", "ebony", "babe",
        "gay", "anal", "bdsm", "bondage", "japanese", "korean",
    )

    private fun rndTerm() = TERMS[Random.nextInt(TERMS.size)]
    private fun rndPage(max: Int) = Random.nextInt(max) + 1

    // ── fetchAll ──────────────────────────────────────────────────────────────
    fun fetchAll(page: Int): List<FeedVideo> {
        val pool    = Executors.newFixedThreadPool(12)
        val results = Collections.synchronizedList(mutableListOf<FeedVideo>())
        val fetchers: List<() -> List<FeedVideo>> = listOf(
            ::fetchRedTube, ::fetchEporner, ::fetchPornHub,
            ::fetchXVideos, ::fetchXHamster, ::fetchYouPorn,
            ::fetchSpankBang, ::fetchBravoTube, ::fetchDrTuber,
            ::fetchTXXX, ::fetchGotPorn, ::fetchPornDig,
        )
        fetchers.forEach { f -> pool.submit<Unit> { try { results.addAll(f()) } catch (_: Exception) {} } }
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
        results.removeAll { it.videoUrl.isEmpty() }
        results.shuffle()
        return results
    }

    // ── fetchSearch — pesquisa real por termo em todas as fontes ──────────────
    fun fetchSearch(query: String): List<FeedVideo> {
        val pool    = Executors.newFixedThreadPool(12)
        val results = Collections.synchronizedList(mutableListOf<FeedVideo>())
        val q       = query.trim()

        val fetchers: List<() -> List<FeedVideo>> = listOf(
            { fetchRedTubeSearch(q) },
            { fetchEpornerSearch(q) },
            { fetchPornHubSearch(q) },
            { fetchXHamsterSearch(q) },
            { fetchYouPornSearch(q) },
            // RSS sources — filtra localmente
            { fetchXVideos().filter { it.matches(q) } },
            { fetchSpankBang().filter { it.matches(q) } },
            { fetchBravoTube().filter { it.matches(q) } },
            { fetchDrTuber().filter { it.matches(q) } },
            { fetchTXXX().filter { it.matches(q) } },
            { fetchGotPorn().filter { it.matches(q) } },
            { fetchPornDig().filter { it.matches(q) } },
        )

        fetchers.forEach { f -> pool.submit<Unit> { try { results.addAll(f()) } catch (_: Exception) {} } }
        pool.shutdown()
        pool.awaitTermination(12, TimeUnit.SECONDS)
        results.removeAll { it.videoUrl.isEmpty() }
        // Ordena por relevância — título começa com o termo primeiro
        results.sortByDescending { v ->
            val t = v.title.lowercase(); val tq = q.lowercase()
            when {
                t.startsWith(tq)  -> 3
                t.contains(tq)    -> 2
                v.tags.any { it.lowercase().contains(tq) } -> 1
                else -> 0
            }
        }
        return results
    }

    private fun FeedVideo.matches(q: String): Boolean {
        val ql = q.lowercase()
        return title.lowercase().contains(ql) ||
               tags.any { it.lowercase().contains(ql) } ||
               categories.any { it.lowercase().contains(ql) } ||
               performer.lowercase().contains(ql)
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────
    private fun get(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout    = READ_TIMEOUT
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json, text/xml, */*")
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect(); body
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
                    mediaThumbs.length  > 0 -> (mediaThumbs.item(0)  as? Element)?.getAttribute("url") ?: ""
                    mediaContent.length > 0 -> (mediaContent.item(0) as? Element)?.getAttribute("url") ?: ""
                    enclosures.length   > 0 -> (enclosures.item(0)   as? Element)?.getAttribute("url") ?: ""
                    else -> ""
                }
                // tags via media:keywords ou media:category
                val keywords = node.getElementsByTagNameNS("*", "keywords").item(0)?.textContent?.trim() ?: ""
                map["tags"] = keywords
                items.add(map)
            }
        } catch (_: Exception) {}
        return items
    }

    private fun tagsFromString(raw: String): List<String> =
        raw.split(",", ";").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    // ── RedTube fetch normal ──────────────────────────────────────────────────
    fun fetchRedTube(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val order = listOf("newest", "mostviewed", "hottest", "rating").random()
        return try {
            val term = rndTerm()
            val body = get(
                "https://api.redtube.com/?data=redtube.Videos.searchVideos" +
                "&output=json&search=${java.net.URLEncoder.encode(term, "UTF-8")}" +
                "&thumbsize=big&count=30&ordering=$order&page=${rndPage(20)}"
            ) ?: return items
            parseRedTubeJson(body, items); items
        } catch (_: Exception) { items }
    }

    // ── RedTube pesquisa ──────────────────────────────────────────────────────
    private fun fetchRedTubeSearch(q: String): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        return try {
            val body = get(
                "https://api.redtube.com/?data=redtube.Videos.searchVideos" +
                "&output=json&search=${java.net.URLEncoder.encode(q, "UTF-8")}" +
                "&thumbsize=big&count=40&ordering=mostviewed&page=1"
            ) ?: return items
            parseRedTubeJson(body, items); items
        } catch (_: Exception) { items }
    }

    private fun parseRedTubeJson(body: String, items: MutableList<FeedVideo>) {
        val videos = org.json.JSONObject(body).optJSONArray("videos") ?: return
        for (i in 0 until videos.length()) {
            val vm    = (videos.opt(i) as? org.json.JSONObject)?.optJSONObject("video") ?: continue
            val thumb = vm.optString("thumb"); val id = vm.optString("video_id")
            if (thumb.isEmpty() || id.isEmpty()) continue
            val tags = mutableListOf<String>()
            vm.optJSONArray("tags")?.let { arr ->
                for (t in 0 until arr.length()) {
                    (arr.opt(t) as? org.json.JSONObject)?.optString("tag")?.let { tags.add(it.lowercase()) }
                }
            }
            items.add(FeedVideo(
                title    = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                thumb    = thumb,
                videoUrl = "https://www.redtube.com/$id",
                duration = vm.optString("duration"),
                views    = vm.optString("views"),
                source   = VideoSource.REDTUBE,
                tags     = tags,
            ))
        }
    }

    // ── Eporner fetch normal ──────────────────────────────────────────────────
    fun fetchEporner(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val order = listOf("latest", "top-weekly", "top-monthly").random()
        return try {
            val body = get(
                "https://www.eporner.com/api/v2/video/search/" +
                "?per_page=30&page=${rndPage(40)}&order=$order&format=json" +
                "&thumbsize=big&query=${java.net.URLEncoder.encode(rndTerm(), "UTF-8")}"
            ) ?: return items
            parseEpornerJson(body, items); items
        } catch (_: Exception) { items }
    }

    // ── Eporner pesquisa ──────────────────────────────────────────────────────
    private fun fetchEpornerSearch(q: String): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        return try {
            val body = get(
                "https://www.eporner.com/api/v2/video/search/" +
                "?per_page=40&page=1&order=top-monthly&format=json" +
                "&thumbsize=big&query=${java.net.URLEncoder.encode(q, "UTF-8")}"
            ) ?: return items
            parseEpornerJson(body, items); items
        } catch (_: Exception) { items }
    }

    private fun parseEpornerJson(body: String, items: MutableList<FeedVideo>) {
        val videos = org.json.JSONObject(body).optJSONArray("videos") ?: return
        for (i in 0 until videos.length()) {
            val vm = videos.opt(i) as? org.json.JSONObject ?: continue
            val id = vm.optString("id"); if (id.isEmpty()) continue
            val thumbArr = vm.optJSONArray("thumbs")
            val thumb = if (thumbArr != null && thumbArr.length() > 0)
                (thumbArr.opt(thumbArr.length() - 1) as? org.json.JSONObject)?.optString("src") ?: ""
            else vm.optString("thumb")
            if (thumb.isEmpty()) continue
            val keywords = vm.optString("keywords")
            val tags = tagsFromString(keywords)
            items.add(FeedVideo(
                title    = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                thumb    = thumb,
                videoUrl = "https://www.eporner.com/video-$id/",
                duration = vm.optString("length_min"),
                views    = vm.optString("views"),
                source   = VideoSource.EPORNER,
                tags     = tags,
            ))
        }
    }

    // ── PornHub fetch normal ──────────────────────────────────────────────────
    fun fetchPornHub(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val order = listOf("newest", "mostviewed").random()
        return try {
            val body = get(
                "https://www.pornhub.com/webmasters/search" +
                "?search=${java.net.URLEncoder.encode(rndTerm(), "UTF-8")}" +
                "&ordering=$order&page=${rndPage(30)}&thumbsize=medium&format=json"
            ) ?: return items
            parsePornHubJson(body, items); items
        } catch (_: Exception) { items }
    }

    // ── PornHub pesquisa ──────────────────────────────────────────────────────
    private fun fetchPornHubSearch(q: String): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        return try {
            val body = get(
                "https://www.pornhub.com/webmasters/search" +
                "?search=${java.net.URLEncoder.encode(q, "UTF-8")}" +
                "&ordering=mostviewed&page=1&thumbsize=medium&format=json"
            ) ?: return items
            parsePornHubJson(body, items); items
        } catch (_: Exception) { items }
    }

    private fun parsePornHubJson(body: String, items: MutableList<FeedVideo>) {
        val data   = org.json.JSONObject(body)
        val videos = data.optJSONArray("videos") ?: data.optJSONArray("video") ?: return
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
            val tags = mutableListOf<String>()
            vm.optJSONArray("tags")?.let { arr ->
                for (t in 0 until arr.length()) {
                    (arr.opt(t) as? org.json.JSONObject)?.optString("tag_name")?.let { tags.add(it.lowercase()) }
                }
            }
            val cats = mutableListOf<String>()
            vm.optJSONArray("categories")?.let { arr ->
                for (t in 0 until arr.length()) {
                    (arr.opt(t) as? org.json.JSONObject)?.optString("category")?.let { cats.add(it.lowercase()) }
                }
            }
            items.add(FeedVideo(
                title      = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                thumb      = thumb,
                videoUrl   = "https://www.pornhub.com/view_video.php?viewkey=$viewkey",
                duration   = vm.optString("duration"),
                views      = vm.optString("views"),
                source     = VideoSource.PORNHUB,
                tags       = tags,
                categories = cats,
            ))
        }
    }

    // ── xHamster fetch normal ─────────────────────────────────────────────────
    fun fetchXHamster(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        return try {
            val term = rndTerm()
            val conn = URL(
                "https://xhamster.com/api/front/search" +
                "?q=${java.net.URLEncoder.encode(term, "UTF-8")}" +
                "&page=${rndPage(20)}&sectionName=video"
            ).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT; conn.readTimeout = READ_TIMEOUT
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            if (conn.responseCode != 200) { conn.disconnect(); return items }
            val body = conn.inputStream.bufferedReader().readText(); conn.disconnect()
            parseXHamsterJson(body, items); items
        } catch (_: Exception) { items }
    }

    // ── xHamster pesquisa ─────────────────────────────────────────────────────
    private fun fetchXHamsterSearch(q: String): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        return try {
            val conn = URL(
                "https://xhamster.com/api/front/search" +
                "?q=${java.net.URLEncoder.encode(q, "UTF-8")}" +
                "&page=1&sectionName=video"
            ).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT; conn.readTimeout = READ_TIMEOUT
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            if (conn.responseCode != 200) { conn.disconnect(); return items }
            val body = conn.inputStream.bufferedReader().readText(); conn.disconnect()
            parseXHamsterJson(body, items); items
        } catch (_: Exception) { items }
    }

    private fun parseXHamsterJson(body: String, items: MutableList<FeedVideo>) {
        val models = org.json.JSONObject(body)
            .optJSONObject("data")?.optJSONObject("videos")
            ?.optJSONArray("models") ?: return
        for (i in 0 until models.length()) {
            val vm    = models.opt(i) as? org.json.JSONObject ?: continue
            val id    = vm.optString("id")
            val thumb = vm.optString("thumbUrl").ifEmpty { vm.optString("thumb") }
            val url   = vm.optString("pageURL").ifEmpty { vm.optString("url") }
            if (id.isEmpty() || thumb.isEmpty() || url.isEmpty()) continue
            val cats = mutableListOf<String>()
            vm.optJSONArray("categories")?.let { arr ->
                for (t in 0 until arr.length()) {
                    (arr.opt(t) as? org.json.JSONObject)?.optString("name")?.let { cats.add(it.lowercase()) }
                }
            }
            items.add(FeedVideo(
                title      = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                thumb      = thumb, videoUrl = url,
                duration   = vm.optString("duration"),
                views      = FeedVideo.fmtViews(vm.optString("views")),
                source     = VideoSource.XHAMSTER,
                categories = cats,
            ))
        }
    }

    // ── YouPorn fetch normal ──────────────────────────────────────────────────
    fun fetchYouPorn(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        return try {
            val body = get("https://www.youporn.com/api/video/search/?is_top=1&page=${rndPage(15)}&per_page=30") ?: return items
            parseYouPornJson(body, items); items
        } catch (_: Exception) { items }
    }

    // ── YouPorn pesquisa ──────────────────────────────────────────────────────
    private fun fetchYouPornSearch(q: String): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        return try {
            val body = get(
                "https://www.youporn.com/api/video/search/" +
                "?search=${java.net.URLEncoder.encode(q, "UTF-8")}&per_page=30&page=1"
            ) ?: return items
            parseYouPornJson(body, items); items
        } catch (_: Exception) { items }
    }

    private fun parseYouPornJson(body: String, items: MutableList<FeedVideo>) {
        val data   = org.json.JSONObject(body)
        val videos = data.optJSONArray("videos") ?: data.optJSONArray("data") ?: return
        for (i in 0 until videos.length()) {
            val vm    = videos.opt(i) as? org.json.JSONObject ?: continue
            val id    = vm.optString("id").ifEmpty { vm.optString("video_id") }
            val thumb = vm.optString("thumb").ifEmpty { vm.optString("default_thumb") }
            if (id.isEmpty() || id == "0" || thumb.isEmpty()) continue
            val tags = mutableListOf<String>()
            vm.optJSONArray("tags")?.let { arr ->
                for (t in 0 until arr.length()) {
                    (arr.opt(t) as? org.json.JSONObject)?.optString("tag_name")?.let { tags.add(it.lowercase()) }
                    if (arr.opt(t) is String) tags.add((arr.opt(t) as String).lowercase())
                }
            }
            items.add(FeedVideo(
                title    = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                thumb    = thumb,
                videoUrl = "https://www.youporn.com/watch/$id/",
                duration = vm.optString("duration"),
                views    = FeedVideo.fmtViews(vm.optString("views")),
                source   = VideoSource.YOUPORN,
                tags     = tags,
            ))
        }
    }

    // ── RSS sources ───────────────────────────────────────────────────────────
    fun fetchXVideos(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://www.xvideos.com/feeds/rss-new/0","https://www.xvideos.com/feeds/rss-most-viewed-alltime/0","https://www.xvideos.com/feeds/rss-new/straight/0").random()
        return try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(title = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"), thumb = item["thumb"] ?: "",
                    videoUrl = link, duration = "", views = "", source = VideoSource.XVIDEOS, tags = tagsFromString(item["tags"] ?: "")))
            }
            items
        } catch (_: Exception) { items }
    }

    fun fetchSpankBang(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://spankbang.com/rss/", "https://spankbang.com/rss/trending/").random()
        return try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(title = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"), thumb = item["thumb"] ?: "",
                    videoUrl = link, duration = "", views = "", source = VideoSource.SPANKBANG, tags = tagsFromString(item["tags"] ?: "")))
            }
            items
        } catch (_: Exception) { items }
    }

    fun fetchBravoTube(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://www.bravotube.net/rss/new/", "https://www.bravotube.net/rss/popular/").random()
        return try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(title = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"), thumb = item["thumb"] ?: "",
                    videoUrl = link, duration = "", views = "", source = VideoSource.BRAVOTUBE, tags = tagsFromString(item["tags"] ?: "")))
            }
            items
        } catch (_: Exception) { items }
    }

    fun fetchDrTuber(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://www.drtuber.com/rss/latest", "https://www.drtuber.com/rss/popular").random()
        return try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(title = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"), thumb = item["thumb"] ?: "",
                    videoUrl = link, duration = "", views = "", source = VideoSource.DRTUBER, tags = tagsFromString(item["tags"] ?: "")))
            }
            items
        } catch (_: Exception) { items }
    }

    fun fetchTXXX(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://www.txxx.com/rss/new/", "https://www.txxx.com/rss/popular/").random()
        return try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(title = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"), thumb = item["thumb"] ?: "",
                    videoUrl = link, duration = "", views = "", source = VideoSource.TXXX, tags = tagsFromString(item["tags"] ?: "")))
            }
            items
        } catch (_: Exception) { items }
    }

    fun fetchGotPorn(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        val url   = listOf("https://www.gotporn.com/rss/latest", "https://www.gotporn.com/rss/popular").random()
        return try {
            val body = get(url) ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(title = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"), thumb = item["thumb"] ?: "",
                    videoUrl = link, duration = "", views = "", source = VideoSource.GOTPORN, tags = tagsFromString(item["tags"] ?: "")))
            }
            items
        } catch (_: Exception) { items }
    }

    fun fetchPornDig(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        return try {
            val body = get("https://www.porndig.com/rss") ?: get("https://www.porndig.com/rss?category=latest") ?: return items
            for (item in parseRss(body)) {
                val link = item["link"] ?: continue; if (link.isEmpty()) continue
                items.add(FeedVideo(title = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"), thumb = item["thumb"] ?: "",
                    videoUrl = link, duration = "", views = "", source = VideoSource.PORNDIG, tags = tagsFromString(item["tags"] ?: "")))
            }
            items
        } catch (_: Exception) { items }
    }
}