package com.doction.webviewapp.models

import android.graphics.Color
import org.jsoup.Jsoup
import org.w3c.dom.Element
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max

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

    val initial: String get() = when (this) {
        EPORNER        -> "EP"
        PORNHUB        -> "PH"
        REDTUBE        -> "RT"
        YOUPORN        -> "YP"
        XVIDEOS        -> "XV"
        XHAMSTER       -> "XH"
        SPANKBANG      -> "SB"
        BRAVOTUBE      -> "BT"
        DRTUBER        -> "DT"
        TXXX           -> "TX"
        GOTPORN        -> "GP"
        PORNDIG        -> "PD"
        BEEG           -> "BG"
        TUBE8          -> "T8"
        TNAFLIX        -> "TN"
        EMPFLIX        -> "EF"
        PORNTREX       -> "PTX"
        HCLIPS         -> "HC"
        TUBEDUPE       -> "TD"
        NUVID          -> "NV"
        SUNPORNO       -> "SP"
        PORNONE        -> "P1"
        SLUTLOAD       -> "SL"
        ICEPORN        -> "IC"
        VJAV           -> "VJ"
        JIZZBUNKER     -> "JB"
        CLIPHUNTER     -> "CH"
        SEXVID         -> "SV"
        YEPTUBE        -> "YT"
        XNXX           -> "XN"
        PORNOXO        -> "PX"
        ANYSEX         -> "AS"
        FUQER          -> "FQ"
        FAPSTER        -> "FS"
        PROPORN        -> "PP"
        H2PORN         -> "H2"
        ALPHAPORNO     -> "AP"
        WATCHMYGF      -> "WG"
        XCAFE          -> "XC"
        TUBECUP        -> "TC"
        VIDLOX         -> "VL"
        NAUGHTYAMERICA -> "NA"
    }

    val favicon: String get() = when (this) {
        EPORNER        -> "https://www.eporner.com/favicon.ico"
        PORNHUB        -> "https://www.pornhub.com/favicon.ico"
        REDTUBE        -> "https://www.redtube.com/favicon.ico"
        YOUPORN        -> "https://www.youporn.com/favicon.ico"
        XVIDEOS        -> "https://www.xvideos.com/favicon.ico"
        XHAMSTER       -> "https://xhamster.com/favicon.ico"
        SPANKBANG      -> "https://spankbang.com/favicon.ico"
        BRAVOTUBE      -> "https://www.bravotube.net/favicon.ico"
        DRTUBER        -> "https://www.drtuber.com/favicon.ico"
        TXXX           -> "https://www.txxx.com/favicon.ico"
        GOTPORN        -> "https://www.gotporn.com/favicon.ico"
        PORNDIG        -> "https://www.porndig.com/favicon.ico"
        BEEG           -> "https://beeg.com/favicon.ico"
        TUBE8          -> "https://www.tube8.com/favicon.ico"
        TNAFLIX        -> "https://www.tnaflix.com/favicon.ico"
        EMPFLIX        -> "https://www.empflix.com/favicon.ico"
        PORNTREX       -> "https://www.porntrex.com/favicon.ico"
        HCLIPS         -> "https://hclips.com/favicon.ico"
        TUBEDUPE       -> "https://www.tubedupe.com/favicon.ico"
        NUVID          -> "https://www.nuvid.com/favicon.ico"
        SUNPORNO       -> "https://www.sunporno.com/favicon.ico"
        PORNONE        -> "https://pornone.com/favicon.ico"
        SLUTLOAD       -> "https://www.slutload.com/favicon.ico"
        ICEPORN        -> "https://www.iceporn.com/favicon.ico"
        VJAV           -> "https://vjav.com/favicon.ico"
        JIZZBUNKER     -> "https://jizzbunker.com/favicon.ico"
        CLIPHUNTER     -> "https://www.cliphunter.com/favicon.ico"
        SEXVID         -> "https://sexvid.xxx/favicon.ico"
        YEPTUBE        -> "https://www.yeptube.com/favicon.ico"
        XNXX           -> "https://www.xnxx.com/favicon.ico"
        PORNOXO        -> "https://www.pornoxo.com/favicon.ico"
        ANYSEX         -> "https://anysex.com/favicon.ico"
        FUQER          -> "https://fuqer.com/favicon.ico"
        FAPSTER        -> "https://fapster.xxx/favicon.ico"
        PROPORN        -> "https://proporn.com/favicon.ico"
        H2PORN         -> "https://www.h2porn.com/favicon.ico"
        ALPHAPORNO     -> "https://www.alphaporno.com/favicon.ico"
        WATCHMYGF      -> "https://watchmygf.me/favicon.ico"
        XCAFE          -> "https://xcafe.com/favicon.ico"
        TUBECUP        -> "https://tubecup.com/favicon.ico"
        VIDLOX         -> "https://vidlox.me/favicon.ico"
        NAUGHTYAMERICA -> "https://www.naughtyamerica.com/favicon.ico"
    }

    val sourceColor: Int get() = Color.parseColor("#222222")
}

// ─────────────────────────────────────────────────────────────────────────────
// FeedVideo
// ─────────────────────────────────────────────────────────────────────────────
data class FeedVideo(
    val title: String,
    val thumb: String,
    val videoUrl: String,
    val duration: String,
    val views: String,
    val source: VideoSource,
    val publishedAt: Date? = null,
) {
    companion object {

        // ── Parsers ────────────────────────────────────────────────────────────

        fun fromEporner(j: Map<String, Any?>): FeedVideo? {
            val id = j["id"] as? String ?: return null
            if (id.isEmpty()) return null
            var thumb = ""
            val thumbs = j["thumbs"] as? List<*>
            if (!thumbs.isNullOrEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val sorted = (thumbs as List<Map<String, Any?>>)
                    .sortedByDescending { (it["width"] as? Int) ?: 0 }
                thumb = sorted.firstOrNull()?.get("src") as? String ?: ""
            }
            if (thumb.isEmpty()) return null
            return FeedVideo(
                title       = cleanTitle(j["title"] as? String ?: ""),
                thumb       = thumb,
                videoUrl    = "https://www.eporner.com/video/$id/",
                duration    = j["duration"] as? String ?: "",
                views       = fmtViews(j["views"]),
                source      = VideoSource.EPORNER,
                publishedAt = parseDate(j["added"] ?: j["published"] ?: j["date"]),
            )
        }

        fun fromPornhub(j: Map<String, Any?>): FeedVideo? {
            val viewkey = (j["video_id"] ?: j["viewkey"]) as? String ?: ""
            if (viewkey.isEmpty()) return null
            var thumb = ""
            val thumbs = j["thumbs"] as? List<*>
            if (!thumbs.isNullOrEmpty()) {
                val first = thumbs.firstOrNull() as? Map<*, *>
                thumb = (first?.get("src") ?: first?.get("url")) as? String ?: ""
            }
            if (thumb.isEmpty()) thumb = j["default_thumb"] as? String ?: ""
            if (thumb.isEmpty()) return null
            return FeedVideo(
                title       = cleanTitle(j["title"] as? String ?: ""),
                thumb       = thumb,
                videoUrl    = "https://www.pornhub.com/view_video.php?viewkey=$viewkey",
                duration    = j["duration"] as? String ?: "",
                views       = fmtViews(j["views"]),
                source      = VideoSource.PORNHUB,
                publishedAt = parseDate(j["publish_date"] ?: j["date_approved"] ?: j["added"]),
            )
        }

        fun fromRedtube(j: Map<String, Any?>): FeedVideo? {
            val vid = j["video_id"] as? String ?: return null
            if (vid.isEmpty()) return null
            val thumb = (j["thumb"] ?: j["default_thumb"]) as? String ?: ""
            if (thumb.isEmpty()) return null
            return FeedVideo(
                title       = cleanTitle(j["title"] as? String ?: ""),
                thumb       = thumb,
                videoUrl    = "https://www.redtube.com/$vid",
                duration    = j["duration"] as? String ?: "",
                views       = fmtViews(j["views"]),
                source      = VideoSource.REDTUBE,
                publishedAt = parseDate(j["publish_date"] ?: j["date"]),
            )
        }

        fun fromYouporn(j: Map<String, Any?>): FeedVideo? {
            val id = (j["id"] ?: j["video_id"])?.toString() ?: ""
            if (id.isEmpty() || id == "0") return null
            val thumb = (j["thumb"] ?: j["default_thumb"]) as? String ?: ""
            if (thumb.isEmpty()) return null
            return FeedVideo(
                title       = cleanTitle(j["title"] as? String ?: ""),
                thumb       = thumb,
                videoUrl    = "https://www.youporn.com/watch/$id/",
                duration    = j["duration"] as? String ?: "",
                views       = fmtViews(j["views"]),
                source      = VideoSource.YOUPORN,
                publishedAt = parseDate(j["publish_date"] ?: j["date"]),
            )
        }

        fun fromBeeg(j: Map<String, Any?>): FeedVideo? {
            val id = (j["id"] ?: j["video_id"])?.toString() ?: ""
            if (id.isEmpty() || id == "0") return null
            var thumb = (j["thumb"] ?: j["thumbnail"]) as? String ?: ""
            if (thumb.isEmpty()) thumb = "https://static.beeg.com/autothumb/$id/bigthumb.jpg"
            return FeedVideo(
                title       = cleanTitle(j["title"] as? String ?: ""),
                thumb       = thumb,
                videoUrl    = "https://beeg.com/$id",
                duration    = j["duration"]?.toString() ?: "",
                views       = fmtViews(j["views"]),
                source      = VideoSource.BEEG,
                publishedAt = parseDate(j["date"] ?: j["created"]),
            )
        }

        fun fromTube8(j: Map<String, Any?>): FeedVideo? {
            val id = (j["video_id"] ?: j["id"])?.toString() ?: ""
            if (id.isEmpty()) return null
            val thumb = (j["thumb"] ?: j["default_thumb"]) as? String ?: ""
            if (thumb.isEmpty()) return null
            return FeedVideo(
                title       = cleanTitle(j["title"] as? String ?: ""),
                thumb       = thumb,
                videoUrl    = "https://www.tube8.com/video/$id",
                duration    = j["duration"]?.toString() ?: "",
                views       = fmtViews(j["views"]),
                source      = VideoSource.TUBE8,
                publishedAt = parseDate(j["date"] ?: j["added"]),
            )
        }

        fun fromScraped(
            title: String,
            thumb: String,
            videoUrl: String,
            source: VideoSource,
            duration: String = "",
            views: String = "",
            publishedAt: Date? = null,
        ) = FeedVideo(
            title       = cleanTitle(title),
            thumb       = thumb,
            videoUrl    = videoUrl,
            duration    = duration,
            views       = views,
            source      = source,
            publishedAt = publishedAt,
        )

        // ── Helpers ────────────────────────────────────────────────────────────

        fun parseDate(raw: Any?): Date? {
            if (raw == null) return null
            val s = raw.toString().trim()
            if (s.isEmpty()) return null
            val epoch = s.toLongOrNull()
            if (epoch != null) return Date(epoch * 1000)
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

        fun cleanTitle(raw: String): String {
            return try {
                val bytes = raw.toByteArray(Charsets.ISO_8859_1)
                val decoded = String(bytes, Charsets.UTF_8)
                if (decoded.count { it.code > 127 } < raw.count { it.code > 127 }) decoded else raw
            } catch (_: Exception) {
                raw
            }
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
    }
}