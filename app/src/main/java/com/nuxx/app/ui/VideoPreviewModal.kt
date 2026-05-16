package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nuxx.app.MainActivity
import com.nuxx.app.models.FeedFetcher
import com.nuxx.app.models.FeedVideo
import com.nuxx.app.models.VideoSource
import com.nuxx.app.theme.AppTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

object VideoPreviewModal {

    private val CONVERT_APIS = listOf(
        "https://nuxxconvert1.onrender.com",
        "https://nuxxconvert2.onrender.com",
        "https://nuxxconvert3.onrender.com",
        "https://nuxxconvert4.onrender.com",
        "https://nuxxconvert5.onrender.com",
    )

    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val ICO_BROWSER  = "icons/svg/phosphor-icons/regular/globe.svg"
    private const val ICO_COPY     = "icons/svg/phosphor-icons/regular/copy.svg"
    private const val ICO_BOOKMARK = "icons/svg/phosphor-icons/regular/bookmark.svg"
    private const val ICO_PLAYLIST = "icons/svg/phosphor-icons/regular/playlist.svg"

    private fun dp(ctx: Context, v: Int) =
        (v * ctx.resources.displayMetrics.density).toInt()

    private fun fixEnc(raw: String): String {
        if (raw.isEmpty()) return raw
        if (raw.any { it.code > 0xFF }) return raw
        val hasMojibake = raw.any { it.code in 0xC0..0xFF }
        if (!hasMojibake) return raw
        return try {
            val bytes   = raw.toByteArray(Charsets.ISO_8859_1)
            val decoded = String(bytes, Charsets.UTF_8)
            if (decoded.contains('\uFFFD')) raw else decoded
        } catch (_: Exception) { raw }
    }

    private fun escapeHtmlAttr(s: String) = s
        .replace("&", "&amp;").replace("\"", "&quot;")
        .replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;")

    private fun faviconUrl(src: VideoSource): String {
        val domain = when (src) {
            VideoSource.EPORNER   -> "eporner.com"
            VideoSource.PORNHUB   -> "pornhub.com"
            VideoSource.REDTUBE   -> "redtube.com"
            VideoSource.YOUPORN   -> "youporn.com"
            VideoSource.XVIDEOS   -> "xvideos.com"
            VideoSource.XHAMSTER  -> "xhamster.com"
            VideoSource.SPANKBANG -> "spankbang.com"
            VideoSource.BRAVOTUBE -> "bravotube.net"
            VideoSource.DRTUBER   -> "drtuber.com"
            VideoSource.TXXX      -> "txxx.com"
            VideoSource.GOTPORN   -> "gotporn.com"
            VideoSource.PORNDIG   -> "porndig.com"
            else                  -> "google.com"
        }
        return "https://www.google.com/s2/favicons?sz=32&domain=$domain"
    }

    private fun buildPlayerHtml(videoUrl: String, thumbUrl: String): String {
        val escapedVideo = escapeHtmlAttr(videoUrl)
        val escapedThumb = escapeHtmlAttr(thumbUrl)
        return """<!DOCTYPE html>
<html><head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no"/>
<style>
*{margin:0;padding:0;box-sizing:border-box;}
html,body{width:100%;height:100%;background:#000;overflow:hidden;}
video{position:absolute;inset:0;width:100%;height:100%;object-fit:contain;display:block;}
</style></head>
<body>
<video id="v" src="$escapedVideo" poster="$escapedThumb" playsinline webkit-playsinline preload="auto"></video>
<script>
(function(){
  var v = document.getElementById('v');
  v.addEventListener('timeupdate', function(){
    Android.onTimeUpdate(v.currentTime, v.duration || 0);
  });
  v.addEventListener('play',    function(){ Android.onPlayState(true);  });
  v.addEventListener('pause',   function(){ Android.onPlayState(false); });
  v.addEventListener('ended',   function(){ Android.onPlayState(false); });
  v.addEventListener('canplay', function(){ Android.onCanPlay();        });
  v.addEventListener('error',   function(){ Android.onError();          });
  window.playerPlay       = function(){ v.play(); };
  window.playerPause      = function(){ v.pause(); };
  window.playerSeekTo     = function(s){ v.currentTime = s; };
  window.playerSetRate    = function(r){ v.playbackRate = r; };
  window.playerSetVolume  = function(vol){ v.volume = vol; };
  window.playerIsPlaying  = function(){ return !v.paused; };
  window.playerGetTime    = function(){ return v.currentTime; };
  window.playerGetDur     = function(){ return v.duration || 0; };
})();
</script>
</body></html>"""
    }

    private fun fmt(s: Double): String {
        if (!s.isFinite() || s < 0) return "0:00"
        val total = s.toInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val sec = String.format("%02d", total % 60)
        return if (h > 0) "$h:${String.format("%02d", m)}:$sec" else "$m:$sec"
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "ClickableViewAccessibility")
    fun show(activity: MainActivity, video: FeedVideo) {
        val ctx     = activity as Context
        val handler = Handler(Looper.getMainLooper())

        thread { HistoryManager.addToHistory(ctx, video) }

        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )

        val extracting = booleanArrayOf(false)

        // ── WebView ───────────────────────────────────────────────────────────
        val webView = WebView(ctx).apply {
            setBackgroundColor(Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccessFromFileURLs = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                userAgentString = UA
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webChromeClient = WebChromeClient()
            webViewClient   = object : WebViewClient() {}
        }

        // ── Estado do player ──────────────────────────────────────────────────
        var isPlaying   = false
        var currentTime = 0.0
        var duration    = 0.0
        var isSeeking   = false
        var currentRate = 1.0f
        var currentVol  = 1.0f

        // ── Controlos nativos ─────────────────────────────────────────────────

        val progressBar = SeekBar(ctx).apply {
            max     = 1000
            progress = 0
            thumb   = null
            progressDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = dp(ctx, 2).toFloat()
            }
            setPadding(0, 0, 0, 0)
        }

        val timeCurrent = TextView(ctx).apply {
            text = "0:00"; setTextColor(Color.WHITE); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        }
        val timeDuration = TextView(ctx).apply {
            text = "0:00"; setTextColor(Color.parseColor("#AAFFFFFF")); textSize = 11f
        }

        val playPauseBtn = ImageButton(ctx).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
        }

        fun updatePlayIcon() {
            playPauseBtn.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }
        updatePlayIcon()

        val rewindBtn = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_media_rew)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
        }
        val forwardBtn = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_media_ff)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
        }

        val speedBtn = Button(ctx).apply {
            text = "1x"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }

        val fullscreenBtn = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_zoom)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
        }

        val volumeBtn = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
        }

        // ── Bridge JS → Kotlin ────────────────────────────────────────────────
        val bridge = object {
            @JavascriptInterface fun onTimeUpdate(c: Double, d: Double) {
                handler.post {
                    currentTime = c
                    duration    = d
                    timeCurrent.text  = fmt(c)
                    timeDuration.text = fmt(d)
                    if (!isSeeking && d > 0)
                        progressBar.progress = ((c / d) * 1000).toInt()
                }
            }
            @JavascriptInterface fun onPlayState(p: Boolean) {
                handler.post { isPlaying = p; updatePlayIcon() }
            }
            @JavascriptInterface fun onCanPlay() { /* pode mostrar algo se quiseres */ }
            @JavascriptInterface fun onError()   { /* tratar erro */ }
        }
        webView.addJavascriptInterface(bridge, "Android")

        fun js(script: String) = webView.evaluateJavascript(script, null)

        playPauseBtn.setOnClickListener {
            if (isPlaying) js("playerPause()") else js("playerPlay()")
        }
        rewindBtn.setOnClickListener  { js("playerSeekTo(Math.max(0, playerGetTime()-10))") }
        forwardBtn.setOnClickListener { js("playerSeekTo(Math.min(playerGetDur(), playerGetTime()+10))") }

        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) { isSeeking = true }
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && duration > 0) {
                    val t = (progress / 1000.0) * duration
                    timeCurrent.text = fmt(t)
                }
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isSeeking = false
                if (duration > 0) {
                    val t = (progressBar.progress / 1000.0) * duration
                    js("playerSeekTo($t)")
                }
            }
        })

        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        speedBtn.setOnClickListener {
            val idx  = speeds.indexOf(currentRate)
            val next = speeds[(idx + 1) % speeds.size]
            currentRate  = next
            speedBtn.text = "${next}x"
            js("playerSetRate($next)")
        }

        fullscreenBtn.setOnClickListener {
            js("var v=document.getElementById('v');if(v.requestFullscreen)v.requestFullscreen();else if(v.webkitRequestFullscreen)v.webkitRequestFullscreen();")
        }

        var muted = false
        volumeBtn.setOnClickListener {
            muted = !muted
            js("playerSetVolume(${if (muted) 0 else 1})")
            volumeBtn.setImageResource(
                if (muted) android.R.drawable.ic_lock_silent_mode
                else android.R.drawable.ic_lock_silent_mode_off
            )
        }

        // ── Barra de controlos nativos ────────────────────────────────────────
        val controlsBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 8))
        }

        val progressRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        progressRow.addView(timeCurrent, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        progressRow.addView(View(ctx), LinearLayout.LayoutParams(dp(ctx, 6), 0))
        progressRow.addView(progressBar, LinearLayout.LayoutParams(0, dp(ctx, 32), 1f))
        progressRow.addView(View(ctx), LinearLayout.LayoutParams(dp(ctx, 6), 0))
        progressRow.addView(timeDuration, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttonsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnSize = dp(ctx, 44)
        buttonsRow.addView(volumeBtn,     LinearLayout.LayoutParams(btnSize, btnSize))
        buttonsRow.addView(View(ctx),     LinearLayout.LayoutParams(0, 0, 1f))
        buttonsRow.addView(rewindBtn,     LinearLayout.LayoutParams(btnSize, btnSize))
        buttonsRow.addView(playPauseBtn,  LinearLayout.LayoutParams(dp(ctx, 52), dp(ctx, 52)))
        buttonsRow.addView(forwardBtn,    LinearLayout.LayoutParams(btnSize, btnSize))
        buttonsRow.addView(View(ctx),     LinearLayout.LayoutParams(0, 0, 1f))
        buttonsRow.addView(speedBtn,      LinearLayout.LayoutParams(dp(ctx, 44), btnSize))
        buttonsRow.addView(fullscreenBtn, LinearLayout.LayoutParams(btnSize, btnSize))

        controlsBar.addView(progressRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        controlsBar.addView(buttonsRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ── Player frame = WebView + controlsBar ──────────────────────────────
        val playerFrame = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val screenW = activity.resources.displayMetrics.widthPixels
        val screenH = activity.resources.displayMetrics.heightPixels
        val playerH = (screenW * 9f / 16f).toInt()

        playerFrame.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        playerFrame.addView(controlsBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ── Error view ────────────────────────────────────────────────────────
        lateinit var errorView: FrameLayout

        fun extractAndPlay(url: String) {
            if (extracting[0]) return
            extracting[0] = true
            errorView.visibility = View.GONE
            val done    = AtomicBoolean(false)
            val errDone = AtomicBoolean(false)
            val failed  = AtomicInteger(0)
            val total   = CONVERT_APIS.size
            CONVERT_APIS.forEach { api ->
                thread {
                    var conn: java.net.HttpURLConnection? = null
                    try {
                        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                        conn = (java.net.URL("$api/extract?url=$encoded")
                            .openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 15_000; readTimeout = 90_000; requestMethod = "GET"
                        }
                        if (conn.responseCode == 200) {
                            val body = conn.inputStream.bufferedReader().readText()
                            val link = org.json.JSONObject(body).optString("link", "")
                            if (link.isNotEmpty() && done.compareAndSet(false, true)) {
                                handler.post {
                                    extracting[0] = false
                                    errorView.visibility = View.GONE
                                    webView.loadDataWithBaseURL(
                                        "https://nuxxx.app",
                                        buildPlayerHtml(link, video.thumb),
                                        "text/html", "UTF-8", null
                                    )
                                    js("playerPlay()")
                                }
                            }
                        } else {
                            if (failed.incrementAndGet() == total && !done.get() && errDone.compareAndSet(false, true)) {
                                handler.post {
                                    extracting[0] = false
                                    errorView.visibility = View.VISIBLE
                                }
                            }
                        }
                    } catch (_: Exception) {
                        if (failed.incrementAndGet() == total && !done.get() && errDone.compareAndSet(false, true)) {
                            handler.post {
                                extracting[0] = false
                                errorView.visibility = View.VISIBLE
                            }
                        }
                    } finally { conn?.disconnect() }
                }
            }
        }

        errorView = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#AA000000"))
            visibility = View.GONE
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            col.addView(TextView(ctx).apply {
                text = "Não foi possível obter o vídeo."
                setTextColor(Color.parseColor("#99FFFFFF")); textSize = 12f; gravity = Gravity.CENTER
            })
            col.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 12)))
            col.addView(TextView(ctx).apply {
                text = "Tentar novamente"; setTextColor(Color.WHITE); textSize = 12f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(ctx, 8).toFloat()
                    setStroke(dp(ctx, 1), Color.WHITE)
                }
                setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), dp(ctx, 8))
                isClickable = true; isFocusable = true
                setOnClickListener { extractAndPlay(video.videoUrl) }
            })
            addView(col, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER })
        }

        val playerWrapper = FrameLayout(ctx)
        playerWrapper.addView(playerFrame, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, playerH))
        playerWrapper.addView(errorView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, playerH))

        // ── Info container ────────────────────────────────────────────────────
        val infoContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dp(ctx, 16).toFloat(), dp(ctx, 16).toFloat(),
                    dp(ctx, 16).toFloat(), dp(ctx, 16).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setColor(AppTheme.bg)
            }
        }

        val gradientOverlay = object : View(ctx) {
            var dominantColor: Int = Color.TRANSPARENT
            override fun onDraw(c: Canvas) {
                if (dominantColor == Color.TRANSPARENT) return
                val startColor = Color.argb(80,
                    Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor))
                val p = Paint()
                p.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    startColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            }
            init { setWillNotDraw(false) }
        }

        val infoBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 10))
        }

        val handleBar = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(ctx, 100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }
        infoBox.addView(handleBar, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = dp(ctx, 12)
        })

        infoBox.addView(TextView(ctx).apply {
            text = fixEnc(video.title)
            setTextColor(AppTheme.text)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            maxLines = 3
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        infoBox.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 6)))

        val metaRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val faviconIv = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(ctx).load(faviconUrl(video.source))
            .override(dp(ctx, 16), dp(ctx, 16)).circleCrop().into(faviconIv)
        metaRow.addView(faviconIv, LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)))
        metaRow.addView(View(ctx), LinearLayout.LayoutParams(dp(ctx, 6), 0))
        metaRow.addView(TextView(ctx).apply {
            setTextColor(AppTheme.textSecondary); textSize = 11.5f
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
        })
        infoBox.addView(metaRow)

        val gradientWrapper = FrameLayout(ctx)
        gradientWrapper.addView(infoBox, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        gradientWrapper.addView(gradientOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        infoContainer.addView(gradientWrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        if (video.thumb.isNotEmpty()) {
            thread {
                try {
                    val conn = (java.net.URL(video.thumb).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 5_000; readTimeout = 5_000
                        setRequestProperty("User-Agent", UA)
                        setRequestProperty("Referer", "https://www.google.com/")
                    }
                    if (conn.responseCode == 200) {
                        val bmp  = BitmapFactory.decodeStream(conn.inputStream)
                        conn.disconnect()
                        if (bmp != null) {
                            val scaled   = Bitmap.createScaledBitmap(bmp, 1, 1, true)
                            val dominant = scaled.getPixel(0, 0)
                            handler.post { gradientOverlay.dominantColor = dominant; gradientOverlay.invalidate() }
                        }
                    } else conn.disconnect()
                } catch (_: Exception) {}
            }
        }

        infoContainer.addView(View(ctx).apply { setBackgroundColor(Color.parseColor("#F0F0F0")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // ── Ações ─────────────────────────────────────────────────────────────
        val actionsScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        }
        val actionsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }

        val savedState = booleanArrayOf(SavedVideosManager.isVideoSaved(ctx, video.videoUrl))

        data class ActionItem(val ico: String, val label: String, val action: (LinearLayout) -> Unit)
        val actions = listOf(
            ActionItem(ICO_BROWSER, "Browser") { _ ->
                dialog.dismiss()
                activity.addContentOverlay(BrowserPage(ctx, freeNavigation = true, externalUrl = video.videoUrl))
            },
            ActionItem(ICO_COPY, "Copiar link") { _ ->
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("link", video.videoUrl))
                activity.showSnackbarGlobal("Link copiado")
            },
            ActionItem(ICO_BOOKMARK, "Guardar") { pill ->
                savedState[0] = !savedState[0]
                if (savedState[0]) {
                    SavedVideosManager.saveVideo(ctx, video)
                    activity.showSnackbarGlobal("Guardado nos vídeos guardados")
                } else {
                    SavedVideosManager.removeVideo(ctx, video.videoUrl)
                    activity.showSnackbarGlobal("Removido dos vídeos guardados")
                }
                applyPillActive(pill, savedState[0], ctx)
            },
            ActionItem(ICO_PLAYLIST, "Playlist") { _ ->
                activity.showSnackbarGlobal("Funcionalidade ainda em desenvolvimento")
            }
        )

        actions.forEachIndexed { i, item ->
            val pill = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(ctx, 50).toFloat()
                    setColor(Color.parseColor("#F2F2F2"))
                    setStroke(dp(ctx, 1), Color.parseColor("#E0E0E0"))
                }
                setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
                isClickable = true; isFocusable = true
            }
            val iconView = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(Color.parseColor("#606060"))
            }
            try {
                val svg = com.caverock.androidsvg.SVG.getFromAsset(ctx.assets, item.ico)
                val sz  = dp(ctx, 17)
                svg.documentWidth = sz.toFloat(); svg.documentHeight = sz.toFloat()
                val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bmp)); iconView.setImageBitmap(bmp)
            } catch (_: Exception) {}
            val lbl = TextView(ctx).apply {
                text = item.label; textSize = 12.5f
                setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#606060"))
            }
            pill.addView(iconView, LinearLayout.LayoutParams(dp(ctx, 17), dp(ctx, 17)))
            pill.addView(View(ctx), LinearLayout.LayoutParams(dp(ctx, 5), 0))
            pill.addView(lbl)
            if (item.ico == ICO_BOOKMARK && savedState[0]) applyPillActive(pill, true, ctx)
            pill.setOnClickListener { item.action(pill) }
            actionsRow.addView(pill, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { if (i > 0) it.leftMargin = dp(ctx, 8) })
        }
        actionsScroll.addView(actionsRow)
        infoContainer.addView(actionsScroll)

        infoContainer.addView(View(ctx).apply { setBackgroundColor(Color.parseColor("#F0F0F0")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // ── Tags ──────────────────────────────────────────────────────────────
        val allTags = (video.tags + video.categories)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (allTags.isNotEmpty()) {
            val tagRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
            }
            allTags.take(3).forEachIndexed { i, tag ->
                if (i > 0) tagRow.addView(TextView(ctx).apply {
                    text = "  ·  "; setTextColor(Color.parseColor("#BBBBBB")); textSize = 12f
                })
                tagRow.addView(TextView(ctx).apply {
                    text = "#$tag"; setTextColor(Color.parseColor("#888888")); textSize = 12f
                })
            }
            if (allTags.size > 3) {
                tagRow.addView(View(ctx), LinearLayout.LayoutParams(dp(ctx, 8), 0))
                tagRow.addView(TextView(ctx).apply {
                    text = "ver mais"; setTextColor(Color.parseColor("#1877F2")); textSize = 12f
                    isClickable = true; isFocusable = true
                    setOnClickListener { showAllTagsSheet(activity, allTags) }
                })
            }
            infoContainer.addView(tagRow)
            infoContainer.addView(View(ctx).apply { setBackgroundColor(Color.parseColor("#F0F0F0")) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        // ── Mais vídeos ───────────────────────────────────────────────────────
        infoContainer.addView(TextView(ctx).apply {
            text = "Mais vídeos"; textSize = 13f
            setTypeface(null, Typeface.BOLD); setTextColor(AppTheme.text)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 6))
        })

        val moreVideosScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), dp(ctx, 12))
            clipToPadding = false
        }
        val moreVideosRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        moreVideosScroll.addView(moreVideosRow)
        infoContainer.addView(moreVideosScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        infoContainer.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 16)))

        fun buildMoreVideoCard(v: FeedVideo): View {
            val cardW = dp(ctx, 160)
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(ctx, 10).toFloat()
                    setColor(AppTheme.thumbBg)
                }
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                    foreground = ctx.getDrawable(tv.resourceId)
            }

            val thumbFrame = FrameLayout(ctx)
            val thumbIv = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(AppTheme.thumbBg)
            }
            val durBadge = TextView(ctx).apply {
                setTextColor(Color.WHITE); textSize = 9.5f; setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(ctx, 4).toFloat()
                    setColor(Color.parseColor("#CC000000"))
                }
                setPadding(dp(ctx, 3), dp(ctx, 1), dp(ctx, 3), dp(ctx, 1))
                visibility = if (v.duration.isNotEmpty()) View.VISIBLE else View.GONE
                text = v.duration
            }
            thumbFrame.addView(thumbIv, FrameLayout.LayoutParams(cardW, dp(ctx, 90)))
            thumbFrame.addView(durBadge, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.BOTTOM or Gravity.END; it.bottomMargin = dp(ctx, 4); it.rightMargin = dp(ctx, 4) })

            if (v.thumb.isNotEmpty()) {
                Glide.with(ctx)
                    .load(GlideUrl(v.thumb, LazyHeaders.Builder()
                        .addHeader("User-Agent", UA)
                        .addHeader("Referer", "https://www.google.com/").build()))
                    .override(cardW, dp(ctx, 90))
                    .centerCrop()
                    .into(thumbIv)
            }

            val infoBox2 = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 8))
            }

            val titleTv = TextView(ctx).apply {
                text = fixEnc(v.title)
                setTextColor(AppTheme.text)
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                maxLines = 2
                setLineSpacing(0f, 1.2f)
            }

            val srcTv = TextView(ctx).apply {
                text = v.source.label
                setTextColor(AppTheme.textSecondary)
                textSize = 10f
            }

            val descStr = buildString {
                if (v.views.isNotEmpty()) append(v.views)
                if (v.duration.isNotEmpty()) {
                    if (isNotEmpty()) append("  ·  ")
                    append(v.duration)
                }
                if (isEmpty()) append("Sem informação")
            }
            val descTv = TextView(ctx).apply {
                text = descStr
                setTextColor(AppTheme.textSecondary)
                textSize = 10f
                maxLines = 1
            }

            infoBox2.addView(titleTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            infoBox2.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 3)))
            infoBox2.addView(srcTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            infoBox2.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 2)))
            infoBox2.addView(descTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            card.addView(thumbFrame, LinearLayout.LayoutParams(cardW, dp(ctx, 90)))
            card.addView(infoBox2, LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT))

            card.setOnClickListener { dialog.dismiss(); show(activity, v) }

            return card
        }

        repeat(5) { i ->
            val skel = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(ctx, 10).toFloat()
                    setColor(AppTheme.thumbBg)
                }
            }
            val skelThumb = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(AppTheme.thumbShimmer1)
                }
            }
            skel.addView(skelThumb, LinearLayout.LayoutParams(dp(ctx, 160), dp(ctx, 90)))
            val skelInfo = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 8))
            }
            fun skelLine(w: Int, h: Int, topM: Int = 0) {
                skelInfo.addView(View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(ctx, 4).toFloat()
                        setColor(AppTheme.thumbShimmer1)
                    }
                }, LinearLayout.LayoutParams(
                    if (w < 0) ViewGroup.LayoutParams.MATCH_PARENT else dp(ctx, w), dp(ctx, h)
                ).also { if (topM > 0) it.topMargin = dp(ctx, topM) })
            }
            skelLine(-1, 11); skelLine(-1, 11, 3); skelLine(100, 10, 5)
            skel.addView(skelInfo, LinearLayout.LayoutParams(dp(ctx, 160), ViewGroup.LayoutParams.WRAP_CONTENT))
            moreVideosRow.addView(skel, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { if (i > 0) it.leftMargin = dp(ctx, 10) })
        }

        // ── ScrollView de info ────────────────────────────────────────────────
        val infoScroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        infoScroll.addView(infoContainer, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ── Root ──────────────────────────────────────────────────────────────
        val rootContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        rootContainer.addView(playerWrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, playerH))
        rootContainer.addView(infoScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // ── BottomSheet ───────────────────────────────────────────────────────
        dialog.setContentView(rootContainer)

        dialog.setOnDismissListener {
            handler.removeCallbacksAndMessages(null)
            try { webView.stopLoading(); webView.destroy() } catch (_: Exception) {}
        }

        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let {
                val behavior = BottomSheetBehavior.from(it)
                it.setBackgroundColor(Color.TRANSPARENT)
                it.layoutParams.height = screenH
                it.requestLayout()
                behavior.peekHeight    = screenH
                behavior.state         = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isHideable    = false
                behavior.isDraggable   = false
            }
        }

        dialog.show()
        extractAndPlay(video.videoUrl)

        // ── Buscar mais vídeos ────────────────────────────────────────────────
        thread {
            try {
                val tagKeywords = allTags.take(3)
                var results = FeedFetcher.fetchAll(Random.nextInt(1, 20))
                    .filter { it.videoUrl != video.videoUrl }

                val filtered = results.filter { v ->
                    val vTags = (v.tags + v.categories).map { it.trim().lowercase() }
                    tagKeywords.any { tag -> vTags.any { it.contains(tag.lowercase()) } }
                }
                if (filtered.size >= 10) results = filtered

                if (results.size < 20) {
                    val extra = FeedFetcher.fetchAll(Random.nextInt(1, 20))
                        .filter { it.videoUrl != video.videoUrl }
                    results = (results + extra).distinctBy { it.videoUrl }
                }

                val final = results.take(30)
                handler.post {
                    moreVideosRow.removeAllViews()
                    final.forEachIndexed { i, v ->
                        val card = buildMoreVideoCard(v)
                        moreVideosRow.addView(card, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).also { if (i > 0) it.leftMargin = dp(ctx, 10) })
                    }
                }
            } catch (_: Exception) {
                handler.post { moreVideosRow.removeAllViews() }
            }
        }
    }

    private fun applyPillActive(pill: LinearLayout, active: Boolean, ctx: Context) {
        val bg = pill.background as? GradientDrawable ?: return
        val activeColor = AppTheme.primary
        if (active) {
            bg.setColor(Color.argb(20, Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor)))
            bg.setStroke((1 * ctx.resources.displayMetrics.density).toInt(), activeColor)
            (pill.getChildAt(0) as? ImageView)?.setColorFilter(activeColor)
            (pill.getChildAt(2) as? TextView)?.setTextColor(activeColor)
        } else {
            bg.setColor(Color.parseColor("#F2F2F2"))
            bg.setStroke((1 * ctx.resources.displayMetrics.density).toInt(), Color.parseColor("#E0E0E0"))
            (pill.getChildAt(0) as? ImageView)?.setColorFilter(Color.parseColor("#606060"))
            (pill.getChildAt(2) as? TextView)?.setTextColor(Color.parseColor("#606060"))
        }
    }

    private fun showAllTagsSheet(activity: MainActivity, tags: List<String>) {
        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dp(activity, 16).toFloat(), dp(activity, 16).toFloat(),
                    dp(activity, 16).toFloat(), dp(activity, 16).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setColor(Color.WHITE)
            }
        }
        root.addView(View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(activity, 100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }, LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(activity, 12); it.bottomMargin = dp(activity, 12)
        })
        root.addView(TextView(activity).apply {
            text = "Tags"; setTextColor(Color.parseColor("#1C1B1F")); textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(activity, 20), 0, dp(activity, 20), dp(activity, 12))
        })
        val flow = FlexboxLayout(activity, tags)
        root.addView(flow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin = dp(activity, 16); it.rightMargin = dp(activity, 16)
        })
        root.addView(View(activity), LinearLayout.LayoutParams(1, dp(activity, 24)))
        dialog.setContentView(root)
        dialog.show()
    }

    private class FlexboxLayout(ctx: Context, tags: List<String>) : ViewGroup(ctx) {
        private val density = ctx.resources.displayMetrics.density
        private fun dp(v: Int) = (v * density).toInt()
        init {
            tags.forEach { tag ->
                addView(TextView(ctx).apply {
                    text = "#$tag"; textSize = 12f
                    setTextColor(Color.parseColor("#555555"))
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                })
            }
        }
        override fun onMeasure(wSpec: Int, hSpec: Int) {
            val w = MeasureSpec.getSize(wSpec)
            var x = 0; var y = 0; var rowH = 0; val gap = dp(8)
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                c.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
                if (x + c.measuredWidth > w && x > 0) { x = 0; y += rowH + gap; rowH = 0 }
                x += c.measuredWidth + gap; rowH = maxOf(rowH, c.measuredHeight)
            }
            setMeasuredDimension(w, y + rowH + dp(8))
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val w = r - l; var x = 0; var y = 0; var rowH = 0; val gap = dp(8)
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (x + c.measuredWidth > w && x > 0) { x = 0; y += rowH + gap; rowH = 0 }
                c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
                x += c.measuredWidth + gap; rowH = maxOf(rowH, c.measuredHeight)
            }
        }
    }
}