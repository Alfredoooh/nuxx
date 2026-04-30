// ui/ShortiesBridge.kt
package com.doction.webviewapp.ui

import android.webkit.JavascriptInterface
import com.doction.webviewapp.models.ShortVideo
import org.json.JSONArray
import org.json.JSONObject

class ShortiesBridge(
    private val onVideosReady: (List<ShortVideo>) -> Unit,
    private val onLikeResult:  (viewKey: String, liked: Boolean) -> Unit,
    private val onMuteResult:  (muted: Boolean) -> Unit,
) {

    @JavascriptInterface
    fun onVideosScraped(json: String) {
        try {
            val arr    = JSONArray(json)
            val videos = mutableListOf<ShortVideo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                videos.add(ShortVideo(
                    viewKey        = o.optString("viewKey"),
                    title          = o.optString("title"),
                    thumb          = o.optString("thumb"),
                    videoUrl       = "https://www.pornhub.com/view_video.php?viewkey=${o.optString("viewKey")}",
                    likes          = o.optString("likes"),
                    views          = o.optString("views"),
                    duration       = o.optString("duration"),
                    publisherName  = o.optString("publisherName"),
                    publisherThumb = o.optString("publisherThumb"),
                    publisherUrl   = o.optString("publisherUrl"),
                    publisherKey   = o.optString("publisherKey"),
                    tags           = o.optString("tags").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                ))
            }
            onVideosReady(videos)
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onLikeToggled(viewKey: String, liked: Boolean) = onLikeResult(viewKey, liked)

    @JavascriptInterface
    fun onMuteToggled(muted: Boolean) = onMuteResult(muted)
}