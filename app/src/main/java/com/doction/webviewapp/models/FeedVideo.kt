package com.doction.webviewapp.models

import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// VideoSource
// ─────────────────────────────────────────────────────────────────────────────
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
}

// ─────────────────────────────────────────────────────────────────────────────
// FeedVideo
// ─────────────────────────────────────────────────────────────────────────────
data class FeedVideo(
    val title:    String,
    val thumb:    String,
    val videoUrl: String,
    val duration: String,
    val views:    String,
    val source:   VideoSource,
    val publishedAt: Date? = null,
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

// ─────────────────────────────────────────────────────────────────────────────
// FeedFetcher — conversão directa do FeedService Flutter
// ─────────────────────────────────────────────────────────────────────────────
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

    fun fetchAll(page: Int): List<FeedVideo> {
        val results = mutableListOf<FeedVideo>()
        results += fetchRedTube()
        results += fetchEporner()
        results += fetchPornHub()
        results += fetchXVideos()
        results += fetchXHamster()
        results += fetchYouPorn()
        results += fetchSpankBang()
        results += fetchBravoTube()
        results += fetchDrTuber()
        results += fetchTXXX()
        results += fetchGotPorn()
        results += fetchPornDig()
        results.removeAll { it.videoUrl.isEmpty() }
        results.shuffle()
        return results
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun get(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 14_000
            conn.readTimeout    = 14_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json, text/xml, */*")
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            body
        } catch (_: Exception) { null }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJson(body: String): Any? {
        return try { org.json.JSONObject(body) } catch (_: Exception) {
            try { org.json.JSONArray(body) } catch (_: Exception) { null }
        }
    }

    private fun jsonMap(obj: org.json.JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in obj.keys()) map[key] = obj.opt(key)
        return map
    }

    private fun jsonList(arr: org.json.JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until arr.length()) list.add(arr.opt(i))
        return list
    }

    // ── RSS XML helper ────────────────────────────────────────────────────────

    private fun parseRss(body: String): List<Map<String, String>> {
        val items = mutableListOf<Map<String, String>>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            }
            val doc = factory.newDocumentBuilder()
                .parse(body.byteInputStream(Charsets.UTF_8))
            val nodeList = doc.getElementsByTagName("item")
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i) as? Element ?: continue
                val map = mutableMapOf<String, String>()
                map["link"]  = node.getElementsByTagName("link").item(0)?.textContent?.trim() ?: ""
                map["title"] = node.getElementsByTagName("title").item(0)?.textContent?.trim() ?: ""
                // Thumbnail — tenta media:thumbnail, media:content, enclosure
                val mediaThumbs = node.getElementsByTagNameNS("*", "thumbnail")
                val mediaContent = node.getElementsByTagNameNS("*", "content")
                val enclosures = node.getElementsByTagName("enclosure")
                map["thumb"] = when {
                    mediaThumbs.length > 0 ->
                        (mediaThumbs.item(0) as? Element)?.getAttribute("url") ?: ""
                    mediaContent.length > 0 ->
                        (mediaContent.item(0) as? Element)?.getAttribute("url") ?: ""
                    enclosures.length > 0 ->
                        (enclosures.item(0) as? Element)?.getAttribute("url") ?: ""
                    else -> ""
                }
                items.add(map)
            }
        } catch (_: Exception) {}
        return items
    }

    private fun toEmbed(pageUrl: String): String {
        if (pageUrl.isEmpty()) return pageUrl
        val uri = try { java.net.URI(pageUrl) } catch (_: Exception) { return pageUrl }
        val host = uri.host?.lowercase() ?: return pageUrl
        val path = uri.path ?: return pageUrl

        if (host.contains("redtube")) {
            val m = Regex("""/(\\d+)""").find(path)
            if (m != null) return "https://embed.redtube.com/?bgcolor=000000&id=${m.groupValues[1]}"
        }
        if (host.contains("eporner")) {
            val m = Regex("""/video-([A-Za-z0-9]+)""").find(path)
            if (m != null) return "https://www.eporner.com/embed/${m.groupValues[1]}/"
        }
        if (host.contains("pornhub")) {
            val viewkey = uri.query?.split("&")
                ?.find { it.startsWith("viewkey=") }?.removePrefix("viewkey=") ?: ""
            if (viewkey.isNotEmpty()) return "https://www.pornhub.com/embed/$viewkey"
        }
        if (host.contains("xvideos")) {
            val m = Regex("""/video(\\d+)""").find(path)
            if (m != null) return "https://www.xvideos.com/embedframe/${m.groupValues[1]}"
        }
        if (host.contains("xhamster")) {
            val m = Regex("""-(\\d+)$""").find(path)
            if (m != null) return "https://xhamster.com/xembed.php?video=${m.groupValues[1]}"
        }
        if (host.contains("youporn")) {
            val m = Regex("""/watch/(\\d+)""").find(path)
            if (m != null) return "https://www.youporn.com/embed/${m.groupValues[1]}/"
        }
        if (host.contains("spankbang")) {
            val m = Regex("""^/([A-Za-z0-9]+)/""").find(path)
            if (m != null) return "https://spankbang.com/${m.groupValues[1]}/embed/"
        }
        if (host.contains("bravotube")) {
            val m = Regex("""-(\\d+)\\.html""").find(path)
            if (m != null) return "https://www.bravotube.net/embed/${m.groupValues[1]}/"
        }
        if (host.contains("drtuber")) {
            val m = Regex("""/video/(\\d+)""").find(path)
            if (m != null) return "https://www.drtuber.com/embed/${m.groupValues[1]}"
        }
        if (host.contains("txxx")) {
            val m = Regex("""-(\\d+)/?$""").find(path)
            if (m != null) return "https://www.txxx.com/embed/${m.groupValues[1]}/"
        }
        if (host.contains("gotporn")) {
            val m = Regex("""/video-(\\d+)""").find(path)
            if (m != null) return "https://www.gotporn.com/video/embed/${m.groupValues[1]}"
        }
        if (host.contains("porndig")) {
            val m = Regex("""-(\\d+)\\.html""").find(path)
            if (m != null) return "https://www.porndig.com/embed/${m.groupValues[1]}"
        }
        return pageUrl
    }

    // ── RedTube ───────────────────────────────────────────────────────────────

    private fun fetchRedTube(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (order in listOf("newest", "mostviewed", "hottest", "rating")) {
            try {
                val term = rndTerm()
                val body = get(
                    "https://api.redtube.com/?data=redtube.Videos.searchVideos" +
                    "&output=json&search=${java.net.URLEncoder.encode(term, "UTF-8")}" +
                    "&thumbsize=big&count=30&ordering=$order&page=${rndPage(20)}"
                ) ?: continue
                val json   = org.json.JSONObject(body)
                val videos = json.optJSONArray("videos") ?: continue
                for (i in 0 until videos.length()) {
                    val vm = (videos.opt(i) as? org.json.JSONObject)
                        ?.optJSONObject("video") ?: continue
                    val thumb = vm.optString("thumb")
                    val id    = vm.optString("video_id")
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
        }
        return items
    }

    // ── Eporner ───────────────────────────────────────────────────────────────

    private fun fetchEporner(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (order in listOf("latest", "top-weekly", "top-monthly")) {
            try {
                val term = rndTerm()
                val body = get(
                    "https://www.eporner.com/api/v2/video/search/" +
                    "?per_page=30&page=${rndPage(40)}&order=$order&format=json" +
                    "&thumbsize=big&query=${java.net.URLEncoder.encode(term, "UTF-8")}"
                ) ?: continue
                val json   = org.json.JSONObject(body)
                val videos = json.optJSONArray("videos") ?: continue
                for (i in 0 until videos.length()) {
                    val vm = videos.opt(i) as? org.json.JSONObject ?: continue
                    val id = vm.optString("id")
                    if (id.isEmpty()) continue
                    val thumbArr = vm.optJSONArray("thumbs")
                    val thumb = if (thumbArr != null && thumbArr.length() > 0)
                        (thumbArr.opt(thumbArr.length() - 1) as? org.json.JSONObject)
                            ?.optString("src") ?: ""
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
        }
        return items
    }

    // ── PornHub ───────────────────────────────────────────────────────────────

    private fun fetchPornHub(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (order in listOf("newest", "mostviewed")) {
            try {
                val term = rndTerm()
                val body = get(
                    "https://www.pornhub.com/webmasters/search" +
                    "?search=${java.net.URLEncoder.encode(term, "UTF-8")}" +
                    "&ordering=$order&page=${rndPage(30)}&thumbsize=medium&format=json"
                ) ?: continue
                val data   = org.json.JSONObject(body)
                val videos = data.optJSONArray("videos") ?: data.optJSONArray("video") ?: continue
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
        }
        return items
    }

    // ── XVideos (RSS) ─────────────────────────────────────────────────────────

    private fun fetchXVideos(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (url in listOf(
            "https://www.xvideos.com/feeds/rss-new/0",
            "https://www.xvideos.com/feeds/rss-most-viewed-alltime/0",
            "https://www.xvideos.com/feeds/rss-new/straight/0",
        )) {
            try {
                val body = get(url) ?: continue
                for (item in parseRss(body)) {
                    val link  = item["link"] ?: continue
                    if (link.isEmpty()) continue
                    val m     = Regex("""/video(\d+)""").find(link)
                    val embed = if (m != null)
                        "https://www.xvideos.com/embedframe/${m.groupValues[1]}"
                    else toEmbed(link)
                    items.add(FeedVideo(
                        title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                        thumb    = item["thumb"] ?: "",
                        videoUrl = link,
                        duration = "",
                        views    = "",
                        source   = VideoSource.XVIDEOS,
                    ))
                }
            } catch (_: Exception) {}
        }
        return items
    }

    // ── xHamster ─────────────────────────────────────────────────────────────

    private fun fetchXHamster(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        try {
            val term = rndTerm()
            val conn = URL(
                "https://xhamster.com/api/front/search" +
                "?q=${java.net.URLEncoder.encode(term, "UTF-8")}" +
                "&page=${rndPage(20)}&sectionName=video"
            ).openConnection() as HttpURLConnection
            conn.connectTimeout = 14_000
            conn.readTimeout    = 14_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            if (conn.responseCode != 200) { conn.disconnect(); return items }
            val body   = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val data   = org.json.JSONObject(body)
            val models = data.optJSONObject("data")
                ?.optJSONObject("videos")
                ?.optJSONArray("models") ?: return items
            for (i in 0 until models.length()) {
                val vm    = models.opt(i) as? org.json.JSONObject ?: continue
                val id    = vm.optString("id")
                val thumb = vm.optString("thumbUrl").ifEmpty { vm.optString("thumb") }
                val url   = vm.optString("pageURL").ifEmpty { vm.optString("url") }
                if (id.isEmpty() || thumb.isEmpty() || url.isEmpty()) continue
                items.add(FeedVideo(
                    title    = FeedVideo.cleanTitle(vm.optString("title", "Vídeo")),
                    thumb    = thumb,
                    videoUrl = url,
                    duration = vm.optString("duration"),
                    views    = FeedVideo.fmtViews(vm.optString("views")),
                    source   = VideoSource.XHAMSTER,
                ))
            }
        } catch (_: Exception) {}
        return items
    }

    // ── YouPorn ───────────────────────────────────────────────────────────────

    private fun fetchYouPorn(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        try {
            val body = get(
                "https://www.youporn.com/api/video/search/" +
                "?is_top=1&page=${rndPage(15)}&per_page=30"
            ) ?: return items
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

    // ── SpankBang (RSS) ───────────────────────────────────────────────────────

    private fun fetchSpankBang(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (url in listOf("https://spankbang.com/rss/", "https://spankbang.com/rss/trending/")) {
            try {
                val body = get(url) ?: continue
                for (item in parseRss(body)) {
                    val link = item["link"] ?: continue
                    if (link.isEmpty()) continue
                    val m = Regex("""^/([A-Za-z0-9]+)/""")
                        .find(java.net.URI(link).path ?: "")
                    items.add(FeedVideo(
                        title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                        thumb    = item["thumb"] ?: "",
                        videoUrl = link,
                        duration = "",
                        views    = "",
                        source   = VideoSource.SPANKBANG,
                    ))
                }
            } catch (_: Exception) {}
        }
        return items
    }

    // ── BravoTube (RSS) ───────────────────────────────────────────────────────

    private fun fetchBravoTube(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (url in listOf(
            "https://www.bravotube.net/rss/new/",
            "https://www.bravotube.net/rss/popular/",
        )) {
            try {
                val body = get(url) ?: continue
                for (item in parseRss(body)) {
                    val link = item["link"] ?: continue
                    if (link.isEmpty()) continue
                    items.add(FeedVideo(
                        title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                        thumb    = item["thumb"] ?: "",
                        videoUrl = link,
                        duration = "",
                        views    = "",
                        source   = VideoSource.BRAVOTUBE,
                    ))
                }
            } catch (_: Exception) {}
        }
        return items
    }

    // ── DrTuber (RSS) ─────────────────────────────────────────────────────────

    private fun fetchDrTuber(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (url in listOf(
            "https://www.drtuber.com/rss/latest",
            "https://www.drtuber.com/rss/popular",
        )) {
            try {
                val body = get(url) ?: continue
                for (item in parseRss(body)) {
                    val link = item["link"] ?: continue
                    if (link.isEmpty()) continue
                    items.add(FeedVideo(
                        title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                        thumb    = item["thumb"] ?: "",
                        videoUrl = link,
                        duration = "",
                        views    = "",
                        source   = VideoSource.DRTUBER,
                    ))
                }
            } catch (_: Exception) {}
        }
        return items
    }

    // ── TXXX (RSS) ────────────────────────────────────────────────────────────

    private fun fetchTXXX(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (url in listOf(
            "https://www.txxx.com/rss/new/",
            "https://www.txxx.com/rss/popular/",
        )) {
            try {
                val body = get(url) ?: continue
                for (item in parseRss(body)) {
                    val link = item["link"] ?: continue
                    if (link.isEmpty()) continue
                    items.add(FeedVideo(
                        title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                        thumb    = item["thumb"] ?: "",
                        videoUrl = link,
                        duration = "",
                        views    = "",
                        source   = VideoSource.TXXX,
                    ))
                }
            } catch (_: Exception) {}
        }
        return items
    }

    // ── GotPorn (RSS) ─────────────────────────────────────────────────────────

    private fun fetchGotPorn(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (url in listOf(
            "https://www.gotporn.com/rss/latest",
            "https://www.gotporn.com/rss/popular",
        )) {
            try {
                val body = get(url) ?: continue
                for (item in parseRss(body)) {
                    val link = item["link"] ?: continue
                    if (link.isEmpty()) continue
                    items.add(FeedVideo(
                        title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                        thumb    = item["thumb"] ?: "",
                        videoUrl = link,
                        duration = "",
                        views    = "",
                        source   = VideoSource.GOTPORN,
                    ))
                }
            } catch (_: Exception) {}
        }
        return items
    }

    // ── PornDig (RSS) ─────────────────────────────────────────────────────────

    private fun fetchPornDig(): List<FeedVideo> {
        val items = mutableListOf<FeedVideo>()
        for (url in listOf(
            "https://www.porndig.com/rss",
            "https://www.porndig.com/rss?category=latest",
        )) {
            try {
                val body = get(url) ?: continue
                for (item in parseRss(body)) {
                    val link = item["link"] ?: continue
                    if (link.isEmpty()) continue
                    items.add(FeedVideo(
                        title    = FeedVideo.cleanTitle(item["title"] ?: "Vídeo"),
                        thumb    = item["thumb"] ?: "",
                        videoUrl = link,
                        duration = "",
                        views    = "",
                        source   = VideoSource.PORNDIG,
                    ))
                }
                if (items.isNotEmpty()) break
            } catch (_: Exception) {}
        }
        return items
    }
}