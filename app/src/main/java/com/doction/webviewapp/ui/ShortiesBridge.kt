// ui/ShortiesBridge.kt
package com.doction.webviewapp.ui

import android.webkit.JavascriptInterface
import com.doction.webviewapp.models.ShortVideo
import org.json.JSONArray

class ShortiesBridge(
    private val onVideosReady:   (List<ShortVideo>) -> Unit,
    private val onLikeCallback:  (viewKey: String, liked: Boolean) -> Unit,
    private val onMuteCallback:  (muted: Boolean) -> Unit,
) {

    @JavascriptInterface
    fun onVideosScraped(json: String) {
        try {
            val arr    = JSONArray(json)
            val videos = mutableListOf<ShortVideo>()
            for (i in 0 until arr.length()) {
                val o       = arr.getJSONObject(i)
                val viewKey = o.optString("viewKey").trim()
                if (viewKey.isEmpty()) continue          // descarta entradas inválidas
                val rawTags = o.optString("tags", "")
                val tags    = if (rawTags.isBlank()) emptyList()
                              else rawTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                videos.add(ShortVideo(
                    viewKey        = viewKey,
                    title          = o.optString("title"),
                    thumb          = o.optString("thumb"),
                    videoUrl       = "https://www.pornhub.com/view_video.php?viewkey=$viewKey",
                    likes          = o.optString("likes", "0"),
                    views          = o.optString("views", ""),
                    duration       = o.optString("duration", ""),
                    publisherName  = o.optString("publisherName"),
                    publisherThumb = o.optString("publisherThumb"),
                    publisherUrl   = o.optString("publisherUrl"),
                    publisherKey   = o.optString("publisherKey"),
                    tags           = tags,
                ))
            }
            onVideosReady(videos)
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onLikeToggled(viewKey: String, liked: Boolean) {
        onLikeCallback(viewKey, liked)
    }

    @JavascriptInterface
    fun onMuteToggled(muted: Boolean) {
        onMuteCallback(muted)
    }
}