// ShortiesPage.kt
package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.graphics.drawable.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import com.caverock.androidsvg.SVG
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nuxx.app.MainActivity

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ShortiesPage(private val activity: MainActivity) : FrameLayout(activity) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val videos      = mutableListOf<ShortVideo>()

    // WebViews em vez de ExoPlayers
    private val webViews    = mutableMapOf<Int, WebView>()
    private var currentIdx  = 0
    private var currentPage = 1
    private var isFetching  = false
    private var firstShown  = false
    private var isMuted     = false

    private var isInForeground        = true
    private var isOnHomeTab           = true
    private var wasPlayingBeforePause = false

    // Estado dos controlos nativos
    private var currentDuration = 0.0
    private var currentTime     = 0.0
    private var isPlaying       = false
    private var isSeeking       = false
    private var currentRate     = 1.0f

    private val likedSet    = mutableSetOf<Int>()
    private val dislikedSet = mutableSetOf<Int>()

    private val MATCH = LayoutParams.MATCH_PARENT
    private val WRAP  = LayoutParams.WRAP_CONTENT

    private lateinit var slidesContainer: FrameLayout
    private val slideFrames = mutableListOf<FrameLayout>()

    private var swipeStartY    = 0f
    private var swipeDelta     = 0f
    private var isSwiping      = false
    private var lastTouchEndMs = 0L

    // Controlos nativos
    private lateinit var progressFill:   View
    private lateinit var progressTrack:  FrameLayout
    private lateinit var timeCurrent:    TextView
    private lateinit var timeDuration:   TextView
    private lateinit var playPauseBtn:   FrameLayout
    private lateinit var playPauseIcon:  android.widget.ImageView
    private lateinit var speedBtn:       TextView

    private var progressJob: Runnable? = null

    private lateinit var pauseIconOverlay: FrameLayout
    private lateinit var muteIndicator:    FrameLayout
    private lateinit var muteIconView:     android.widget.ImageView

    private lateinit var likeIconView:    android.widget.ImageView
    private lateinit var dislikeIconView: android.widget.ImageView
    private lateinit var likeLabelTv:     TextView
    private lateinit var dislikeLabelTv:  TextView

    private val loaderView = buildLoaderView()
    private val noNetView  = buildNoNetView()

    private val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        checkNetAndLoad()
    }

    // ── HTML do player ────────────────────────────────────────────────────────

    private fun buildPlayerHtml(videoUrl: String): String {
        val escaped = videoUrl
            .replace("&", "&amp;").replace("\"", "&quot;")
            .replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;")
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
<video id="v" src="$escaped" playsinline webkit-playsinline preload="auto" loop></video>
<script>
(function(){
  var v=document.getElementById('v');
  v.addEventListener('timeupdate',function(){ Android.onTimeUpdate(v.currentTime, v.duration||0); });
  v.addEventListener('play',    function(){ Android.onPlayState(true);  });
  v.addEventListener('pause',   function(){ Android.onPlayState(false); });
  v.addEventListener('ended',   function(){ Android.onPlayState(false); });
  v.addEventListener('canplay', function(){ Android.onCanPlay();        });
  window.playerPlay      = function(){ v.play(); };
  window.playerPause     = function(){ v.pause(); };
  window.playerSeekTo    = function(s){ v.currentTime=s; };
  window.playerSetRate   = function(r){ v.playbackRate=r; };
  window.playerSetVolume = function(vol){ v.volume=vol; };
  window.playerGetTime   = function(){ return v.currentTime; };
  window.playerGetDur    = function(){ return v.duration||0; };
})();
</script>
</body></html>"""
    }

    // ── Bridge JS → Kotlin ────────────────────────────────────────────────────

    private inner class PlayerBridge(private val idx: Int) {
        @JavascriptInterface fun onTimeUpdate(c: Double, d: Double) {
            mainHandler.post {
                if (idx != currentIdx) return@post
                currentTime     = c
                currentDuration = d
                timeCurrent.text  = fmt(c)
                timeDuration.text = fmt(d)
                if (!isSeeking && d > 0) updateProgressFill(c / d)
            }
        }
        @JavascriptInterface fun onPlayState(p: Boolean) {
            mainHandler.post {
                if (idx != currentIdx) return@post
                isPlaying = p
                updatePlayIcon()
                showPauseIcon(!p)
            }
        }
        @JavascriptInterface fun onCanPlay() {
            mainHandler.post {
                if (idx != currentIdx) return@post
                if (isOnHomeTab && isInForeground) js(idx, "playerPlay()")
            }
        }
    }

    private fun js(idx: Int, script: String) {
        mainHandler.post { webViews[idx]?.evaluateJavascript(script, null) }
    }

    private fun jsCurrent(script: String) = js(currentIdx, script)

    // ── Formato de tempo ──────────────────────────────────────────────────────

    private fun fmt(s: Double): String {
        if (!s.isFinite() || s < 0) return "0:00"
        val t   = s.toInt()
        val h   = t / 3600
        val m   = (t % 3600) / 60
        val sec = String.format("%02d", t % 60)
        return if (h > 0) "$h:${String.format("%02d", m)}:$sec" else "$m:$sec"
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    fun pauseForBackground() {
        if (!isInForeground) return
        isInForeground = false
        wasPlayingBeforePause = isPlaying
        jsCurrent("playerPause()")
    }

    fun resumeFromBackground() {
        isInForeground = true
        if (isOnHomeTab && wasPlayingBeforePause) jsCurrent("playerPlay()")
    }

    fun pauseForTabSwitch() {
        isOnHomeTab = false
        wasPlayingBeforePause = isPlaying
        jsCurrent("playerPause()")
    }

    fun resumeFromTabSwitch() {
        isOnHomeTab = true
        if (isInForeground && wasPlayingBeforePause) jsCurrent("playerPlay()")
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        slidesContainer = FrameLayout(context)
        addView(slidesContainer, LayoutParams(MATCH, MATCH))
        slidesContainer.setOnTouchListener { _, e -> handleSwipeTouch(e); true }

        val grad = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(220, 0, 0, 0), Color.TRANSPARENT)
            )
            isClickable = false; isFocusable = false
        }
        addView(grad, FrameLayout.LayoutParams(MATCH, dp(340)).also { it.gravity = Gravity.BOTTOM })

        buildAppBar()
        buildPauseIconOverlay()
        buildRightActions()
        buildNativeControls()
        buildMuteIndicator()

        addView(loaderView, LayoutParams(MATCH, MATCH))
        addView(noNetView,  LayoutParams(MATCH, MATCH))
        noNetView.visibility = View.GONE
    }

    private fun buildAppBar() {
        val statusH = activity.statusBarHeight
        val bar     = FrameLayout(context)

        val title = TextView(context).apply {
            text     = "Shorts"
            textSize = 26f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(10f, 0f, 2f, Color.parseColor("#88000000"))
        }
        bar.addView(title, FrameLayout.LayoutParams(WRAP, WRAP).also {
            it.gravity = Gravity.CENTER_VERTICAL or Gravity.START; it.leftMargin = dp(16)
        })

        val searchBtn = svgBtn("icons/svg/phosphor-icons/regular/magnifying-glass.svg", 24)
        searchBtn.setOnClickListener {
            activity.setStatusBarDark(true)
            activity.addContentOverlay(SearchResultsPage(activity, ""))
        }
        bar.addView(searchBtn, FrameLayout.LayoutParams(dp(40), dp(40)).also {
            it.gravity = Gravity.CENTER_VERTICAL or Gravity.END; it.rightMargin = dp(8)
        })

        addView(bar, FrameLayout.LayoutParams(MATCH, statusH + dp(56)).also {
            it.gravity = Gravity.TOP
        })
    }

    // ── Controlos nativos ─────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun buildNativeControls() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }

        // Linha de tempo
        val timeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        timeCurrent = TextView(context).apply {
            text = "0:00"; setTextColor(Color.WHITE)
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        }
        timeDuration = TextView(context).apply {
            text = "0:00"; setTextColor(Color.parseColor("#AAFFFFFF")); textSize = 11f
        }

        // Track de progresso
        progressTrack = FrameLayout(context)
        val trackBg = View(context).apply { setBackgroundColor(Color.argb(80, 255, 255, 255)) }
        progressFill = View(context).apply { setBackgroundColor(Color.WHITE) }
        progressTrack.addView(trackBg, FrameLayout.LayoutParams(MATCH, dp(4)))
        progressTrack.addView(progressFill, FrameLayout.LayoutParams(0, dp(4)))

        progressTrack.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    isSeeking = true
                    val pct = (e.x / progressTrack.width).coerceIn(0f, 1f)
                    updateProgressFill(pct.toDouble())
                    if (currentDuration > 0) jsCurrent("playerSeekTo(${pct * currentDuration})")
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isSeeking = false
            }
            true
        }

        timeRow.addView(timeCurrent, LinearLayout.LayoutParams(WRAP, WRAP))
        timeRow.addView(View(context), LinearLayout.LayoutParams(dp(6), 0))
        timeRow.addView(progressTrack, LinearLayout.LayoutParams(0, dp(28), 1f))
        timeRow.addView(View(context), LinearLayout.LayoutParams(dp(6), 0))
        timeRow.addView(timeDuration, LinearLayout.LayoutParams(WRAP, WRAP))

        // Linha de botões
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }

        // Volume / mute
        val volBtn = buildCtrlBtn("icons/svg/phosphor-icons/regular/speaker-high.svg", 22)
        volBtn.setOnClickListener { toggleMute() }

        // Recuar 10s
        val rewindBtn = buildCtrlBtn("icons/svg/phosphor-icons/regular/rewind.svg", 22)
        rewindBtn.setOnClickListener { jsCurrent("playerSeekTo(Math.max(0,playerGetTime()-10))") }

        // Play / pause
        playPauseBtn  = FrameLayout(context).apply { isClickable = true; isFocusable = true }
        playPauseIcon = svgIv("icons/svg/phosphor-icons/fill/play.svg", 32, Color.WHITE)
        playPauseBtn.addView(playPauseIcon,
            FrameLayout.LayoutParams(dp(32), dp(32)).also { it.gravity = Gravity.CENTER })
        playPauseBtn.setOnClickListener {
            if (isPlaying) jsCurrent("playerPause()") else jsCurrent("playerPlay()")
        }

        // Avançar 10s
        val forwardBtn = buildCtrlBtn("icons/svg/phosphor-icons/regular/fast-forward.svg", 22)
        forwardBtn.setOnClickListener { jsCurrent("playerSeekTo(Math.min(playerGetDur(),playerGetTime()+10))") }

        // Velocidade
        speedBtn = TextView(context).apply {
            text = "1x"; setTextColor(Color.WHITE)
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), 0, dp(8), 0)
            isClickable = true; isFocusable = true
        }
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        speedBtn.setOnClickListener {
            val idx  = speeds.indexOf(currentRate)
            val next = speeds[(idx + 1) % speeds.size]
            currentRate    = next
            speedBtn.text  = "${next}x"
            jsCurrent("playerSetRate($next)")
        }

        btnRow.addView(volBtn,      LinearLayout.LayoutParams(dp(40), dp(40)))
        btnRow.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        btnRow.addView(rewindBtn,   LinearLayout.LayoutParams(dp(40), dp(40)))
        btnRow.addView(playPauseBtn, LinearLayout.LayoutParams(dp(48), dp(48)))
        btnRow.addView(forwardBtn,  LinearLayout.LayoutParams(dp(40), dp(40)))
        btnRow.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        btnRow.addView(speedBtn,    LinearLayout.LayoutParams(dp(44), dp(40)))

        container.addView(timeRow, LinearLayout.LayoutParams(MATCH, WRAP))
        container.addView(View(context), LinearLayout.LayoutParams(1, dp(2)))
        container.addView(btnRow, LinearLayout.LayoutParams(MATCH, WRAP))

        addView(container, FrameLayout.LayoutParams(MATCH, WRAP).also {
            it.gravity = Gravity.BOTTOM
        })
    }

    private fun buildCtrlBtn(iconPath: String, sizeDp: Int): FrameLayout {
        val btn = FrameLayout(context).apply { isClickable = true; isFocusable = true }
        val iv  = svgIv(iconPath, sizeDp, Color.WHITE)
        btn.addView(iv, FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).also { it.gravity = Gravity.CENTER })
        return btn
    }

    private fun updatePlayIcon() {
        refreshSvgIv(
            playPauseIcon,
            if (isPlaying) "icons/svg/phosphor-icons/fill/pause.svg"
            else "icons/svg/phosphor-icons/fill/play.svg",
            32
        )
    }

    private fun updateProgressFill(fraction: Double) {
        progressTrack.post {
            val totalW = progressTrack.width
            val lp     = progressFill.layoutParams as FrameLayout.LayoutParams
            lp.width   = (fraction * totalW).toInt().coerceIn(0, totalW)
            progressFill.layoutParams = lp
        }
    }

    // ── Pause icon overlay ────────────────────────────────────────────────────

    private fun buildPauseIconOverlay() {
        pauseIconOverlay = FrameLayout(context).apply {
            alpha = 0f; isClickable = false; isFocusable = false
        }
        val bg = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.argb(140, 0, 0, 0))
            }
        }
        val iv = svgIv("icons/svg/phosphor-icons/fill/pause.svg", 48, Color.WHITE)
        val sz = dp(80)
        bg.addView(iv, FrameLayout.LayoutParams(dp(48), dp(48)).also { it.gravity = Gravity.CENTER })
        pauseIconOverlay.addView(bg, FrameLayout.LayoutParams(sz, sz).also { it.gravity = Gravity.CENTER })
        addView(pauseIconOverlay, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun showPauseIcon(paused: Boolean) {
        if (!paused) { pauseIconOverlay.animate().alpha(0f).setDuration(180).start(); return }
        pauseIconOverlay.animate().cancel(); pauseIconOverlay.alpha = 1f
    }

    // ── Right action buttons — descidos 20% ───────────────────────────────────

    private fun buildRightActions() {
        val screenH     = resources.displayMetrics.heightPixels
        val bottomMargin = (screenH * 0.20f).toInt() // 20% do ecrã a partir do fundo

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
        }

        val likeCell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true; isFocusable = true
        }
        likeIconView = svgIv("icons/svg/phosphor-icons/regular/thumbs-up.svg", 28, Color.WHITE)
        likeLabelTv  = TextView(context).apply {
            text = "Gosto"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 0f, 1f, Color.parseColor("#B3000000"))
            gravity = Gravity.CENTER; maxLines = 1
        }
        likeCell.addView(likeIconView, LinearLayout.LayoutParams(dp(28), dp(28)))
        likeCell.addView(likeLabelTv,  LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(4) })
        likeCell.setOnClickListener { toggleLike() }
        col.addView(likeCell, LinearLayout.LayoutParams(WRAP, WRAP).also { it.bottomMargin = dp(20) })

        val dislikeCell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true; isFocusable = true
        }
        dislikeIconView = svgIv("icons/svg/phosphor-icons/regular/thumbs-down.svg", 28, Color.WHITE)
        dislikeLabelTv  = TextView(context).apply {
            text = "Não gosto"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 0f, 1f, Color.parseColor("#B3000000"))
            gravity = Gravity.CENTER; maxLines = 1
        }
        dislikeCell.addView(dislikeIconView, LinearLayout.LayoutParams(dp(28), dp(28)))
        dislikeCell.addView(dislikeLabelTv,  LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(4) })
        dislikeCell.setOnClickListener { toggleDislike() }
        col.addView(dislikeCell, LinearLayout.LayoutParams(WRAP, WRAP).also { it.bottomMargin = dp(20) })

        col.addView(buildActionBtn(
            "icons/svg/phosphor-icons/regular/chat-circle.svg", "Comentar"
        ) { openCommentSheet() }, LinearLayout.LayoutParams(WRAP, WRAP).also { it.bottomMargin = dp(20) })

        col.addView(buildActionBtn(
            "icons/svg/phosphor-icons/regular/share-fat.svg", "Partilhar"
        ) { openShareSheet() }, LinearLayout.LayoutParams(WRAP, WRAP).also { it.bottomMargin = dp(20) })

        col.addView(buildMuteActionBtn(), LinearLayout.LayoutParams(WRAP, WRAP))

        addView(col, FrameLayout.LayoutParams(dp(72), WRAP).also {
            it.gravity      = Gravity.END or Gravity.BOTTOM
            it.rightMargin  = dp(8)
            it.bottomMargin = bottomMargin
        })
    }

    private fun buildActionBtn(iconPath: String, label: String, onClick: (() -> Unit)?): LinearLayout {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true; isFocusable = true
        }
        val iv = svgIv(iconPath, 28, Color.WHITE)
        val tv = TextView(context).apply {
            text = label; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 0f, 1f, Color.parseColor("#B3000000"))
            gravity = Gravity.CENTER; maxLines = 1
        }
        cell.addView(iv, LinearLayout.LayoutParams(dp(28), dp(28)))
        cell.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(4) })
        if (onClick != null) cell.setOnClickListener { onClick() }
        return cell
    }

    private fun buildMuteActionBtn(): LinearLayout {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true; isFocusable = true
        }
        muteIconView = svgIv("icons/svg/phosphor-icons/regular/speaker-high.svg", 28, Color.WHITE)
        val tv = TextView(context).apply {
            text = "Som"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 0f, 1f, Color.parseColor("#B3000000"))
            gravity = Gravity.CENTER; maxLines = 1
        }
        cell.addView(muteIconView, LinearLayout.LayoutParams(dp(28), dp(28)))
        cell.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(4) })
        cell.setOnClickListener { toggleMute() }
        return cell
    }

    // ── Like / Dislike ────────────────────────────────────────────────────────

    private fun toggleLike() {
        val wasLiked = currentIdx in likedSet
        if (wasLiked) likedSet.remove(currentIdx)
        else { likedSet.add(currentIdx); dislikedSet.remove(currentIdx); updateDislikeVisual(false) }
        updateLikeVisual(!wasLiked)
    }

    private fun toggleDislike() {
        val wasDisliked = currentIdx in dislikedSet
        if (wasDisliked) dislikedSet.remove(currentIdx)
        else { dislikedSet.add(currentIdx); likedSet.remove(currentIdx); updateLikeVisual(false) }
        updateDislikeVisual(!wasDisliked)
    }

    private fun updateLikeVisual(active: Boolean) {
        val color = if (active) Color.parseColor("#4CAF50") else Color.WHITE
        likeIconView.setColorFilter(color); likeLabelTv.setTextColor(color)
        refreshSvgIv(likeIconView,
            if (active) "icons/svg/phosphor-icons/fill/thumbs-up.svg"
            else        "icons/svg/phosphor-icons/regular/thumbs-up.svg", 28)
        likeIconView.setColorFilter(color)
    }

    private fun updateDislikeVisual(active: Boolean) {
        val color = if (active) Color.parseColor("#F44336") else Color.WHITE
        dislikeIconView.setColorFilter(color); dislikeLabelTv.setTextColor(color)
        refreshSvgIv(dislikeIconView,
            if (active) "icons/svg/phosphor-icons/fill/thumbs-down.svg"
            else        "icons/svg/phosphor-icons/regular/thumbs-down.svg", 28)
        dislikeIconView.setColorFilter(color)
    }

    private fun refreshActionIconsForCurrentIdx() {
        updateLikeVisual(currentIdx in likedSet)
        updateDislikeVisual(currentIdx in dislikedSet)
    }

    // ── Mute indicator ────────────────────────────────────────────────────────

    private fun buildMuteIndicator() {
        muteIndicator = FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(166, 0, 0, 0)) }
            alpha = 0f; scaleX = 0f; scaleY = 0f
            isClickable = false; isFocusable = false
        }
        val iv = svgIv("icons/svg/phosphor-icons/regular/speaker-high.svg", 36, Color.WHITE)
        muteIndicator.addView(iv, FrameLayout.LayoutParams(dp(36), dp(36)).also { it.gravity = Gravity.CENTER })
        addView(muteIndicator, FrameLayout.LayoutParams(dp(72), dp(72)).also { it.gravity = Gravity.CENTER })
    }

    private fun toggleMute() {
        isMuted = !isMuted
        jsCurrent("playerSetVolume(${if (isMuted) 0 else 1})")
        refreshSvgIv(muteIconView,
            if (isMuted) "icons/svg/phosphor-icons/regular/speaker-slash.svg"
            else         "icons/svg/phosphor-icons/regular/speaker-high.svg", 28)
        val indicatorIv = muteIndicator.getChildAt(0) as? android.widget.ImageView
        indicatorIv?.let {
            refreshSvgIv(it,
                if (isMuted) "icons/svg/phosphor-icons/regular/speaker-slash.svg"
                else         "icons/svg/phosphor-icons/regular/speaker-high.svg", 36)
        }
        muteIndicator.animate().cancel()
        muteIndicator.scaleX = 0f; muteIndicator.scaleY = 0f; muteIndicator.alpha = 0f
        muteIndicator.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).withEndAction {
            mainHandler.postDelayed({
                muteIndicator.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(300).start()
            }, 900)
        }.start()
    }

    // ── Slides ────────────────────────────────────────────────────────────────

    private fun screenH() = resources.displayMetrics.heightPixels.toFloat()

    @SuppressLint("SetJavaScriptEnabled")
    private fun createSlide(idx: Int): FrameLayout {
        val h     = screenH().toInt()
        val frame = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            translationY = idx * screenH()
        }

        val wv = WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            settings.apply {
                javaScriptEnabled               = true
                domStorageEnabled               = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode                = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort                 = true
                loadWithOverviewMode            = true
                setSupportZoom(false)
                userAgentString                 = UA
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webChromeClient = WebChromeClient()
            webViewClient   = object : WebViewClient() {}
            addJavascriptInterface(PlayerBridge(idx), "Android")
        }

        webViews[idx] = wv
        frame.addView(wv, FrameLayout.LayoutParams(MATCH, MATCH))
        slidesContainer.addView(frame, FrameLayout.LayoutParams(MATCH, h))
        slideFrames.add(frame)
        return frame
    }

    private fun applyPositions(deltaY: Float = 0f, animate: Boolean = false) {
        val h = screenH()
        for (i in slideFrames.indices) {
            val target = (i - currentIdx) * h + deltaY
            if (animate) {
                slideFrames[i].animate().translationY(target)
                    .setDuration(280).setInterpolator(DecelerateInterpolator(1.6f)).start()
            } else {
                slideFrames[i].animate().cancel()
                slideFrames[i].translationY = target
            }
        }
    }

    // ── Swipe ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun handleSwipeTouch(e: MotionEvent) {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartY = e.rawY; swipeDelta = 0f; isSwiping = true
                slideFrames.forEach { it.animate().cancel() }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isSwiping) return
                swipeDelta = e.rawY - swipeStartY
                val resistance = if ((swipeDelta < 0 && currentIdx >= slideFrames.size - 1) ||
                                     (swipeDelta > 0 && currentIdx <= 0)) 0.3f else 1f
                applyPositions(deltaY = swipeDelta * resistance, animate = false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSwiping      = false
                lastTouchEndMs = System.currentTimeMillis()
                val threshold  = screenH() * 0.15f
                when {
                    swipeDelta < -threshold && currentIdx < slideFrames.size - 1 -> {
                        currentIdx++
                        applyPositions(animate = true)
                        onPageSettled(currentIdx)
                        refreshActionIconsForCurrentIdx()
                    }
                    swipeDelta > threshold && currentIdx > 0 -> {
                        currentIdx--
                        applyPositions(animate = true)
                        onPageSettled(currentIdx)
                        refreshActionIconsForCurrentIdx()
                    }
                    else -> {
                        applyPositions(animate = true)
                        if (kotlin.math.abs(swipeDelta) < dp(8)) handleTap()
                    }
                }
                swipeDelta = 0f
            }
        }
    }

    private fun handleTap() {
        if (isPlaying) jsCurrent("playerPause()") else {
            if (isOnHomeTab && isInForeground) jsCurrent("playerPlay()")
        }
    }

    // ── Rede ──────────────────────────────────────────────────────────────────

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkNetAndLoad() {
        if (!isOnline()) { showNoNet(); mainHandler.postDelayed({ checkNetAndLoad() }, 3000) }
        else             { hideNoNet(); startEverything() }
    }

    private fun showNoNet() { loaderView.visibility = View.GONE; noNetView.visibility = View.VISIBLE }
    private fun hideNoNet() { noNetView.visibility = View.GONE; loaderView.visibility = View.VISIBLE }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private fun startEverything() {
        currentPage = 1; currentIdx = 0; firstShown = false; isFetching = false
        wasPlayingBeforePause = false; isPlaying = false; currentTime = 0.0; currentDuration = 0.0
        videos.clear()
        webViews.values.forEach { it.stopLoading(); it.destroy() }; webViews.clear()
        slideFrames.clear()
        slidesContainer.removeAllViews()
        fetchPage(1)
    }

    private fun fetchPage(page: Int) {
        if (isFetching) return
        isFetching = true
        Thread {
            val result = ShortiesApi.fetchVideos(page)
            mainHandler.post {
                isFetching = false
                if (result.isEmpty()) {
                    if (videos.isEmpty()) mainHandler.postDelayed({ startEverything() }, 3000)
                    return@post
                }
                val startIdx = videos.size
                videos.addAll(result)
                currentPage = page
                for (i in startIdx until videos.size) createSlide(i)
                if (!firstShown) {
                    firstShown = true
                    loaderView.animate().alpha(0f).setDuration(300).withEndAction {
                        loaderView.visibility = View.GONE; loaderView.alpha = 1f
                    }.start()
                    loadWebViewAt(0)
                }
                if (videos.size < 20 && !isFetching)
                    mainHandler.postDelayed({ fetchPage(page + 1) }, 800)
            }
        }.start()
    }

    // ── WebView load ──────────────────────────────────────────────────────────

    private fun loadWebViewAt(idx: Int) {
        val url = videos.getOrNull(idx)?.link ?: return
        val wv  = webViews[idx] ?: return
        wv.loadDataWithBaseURL("https://nuxx.app", buildPlayerHtml(url), "text/html", "UTF-8", null)
    }

    private fun onPageSettled(idx: Int) {
        // Pausa todos os outros
        webViews.forEach { (i, wv) -> if (i != idx) wv.evaluateJavascript("playerPause()", null) }

        // Reseta estado
        currentTime = 0.0; currentDuration = 0.0; isPlaying = false
        timeCurrent.text = "0:00"; timeDuration.text = "0:00"
        updateProgressFill(0.0)
        updatePlayIcon()

        // Carrega o WebView se ainda não tiver HTML
        if (webViews[idx]?.url == null) loadWebViewAt(idx)
        else if (isOnHomeTab && isInForeground) js(idx, "playerPlay()")

        // Pré-carrega próximos
        for (offset in 1..3) {
            val next = idx + offset
            if (next < videos.size && webViews[next]?.url == null) loadWebViewAt(next)
        }

        // Liberta WebViews distantes
        webViews.keys.filter { it < idx - 2 || it > idx + 5 }.forEach { i ->
            webViews[i]?.loadUrl("about:blank")
        }

        if (idx >= videos.size - 6 && !isFetching) fetchPage(currentPage + 1)
    }

    // ── Loader customizado ────────────────────────────────────────────────────

    private fun buildLoaderView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val loaderSize = dp(72)
        val arcView = object : View(context) {
            private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = dp(7).toFloat()
                strokeCap = Paint.Cap.ROUND; color = Color.argb(40, 255, 255, 255)
            }
            private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = dp(7).toFloat()
                strokeCap = Paint.Cap.ROUND; color = Color.WHITE
            }
            private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; color = Color.WHITE
            }
            private var angle   = 0f
            private var running = true
            private val runner  = object : Runnable {
                override fun run() {
                    if (!running) return
                    angle = (angle + 5f) % 360f; invalidate(); postDelayed(this, 14)
                }
            }
            init { post(runner) }
            fun stop() { running = false; removeCallbacks(runner) }
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f
                val r  = cx - dp(7) / 2f - dp(2)
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                c.drawCircle(cx, cy, r, trackPaint)
                c.drawArc(rect, angle, 260f, false, arcPaint)
                val dotAngle = Math.toRadians((angle + 260f).toDouble())
                val dx = cx + r * Math.cos(dotAngle).toFloat()
                val dy = cy + r * Math.sin(dotAngle).toFloat()
                c.drawCircle(dx, dy, dp(5).toFloat(), dotPaint)
            }
            override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stop() }
        }
        frame.addView(arcView, FrameLayout.LayoutParams(loaderSize, loaderSize).also { it.gravity = Gravity.CENTER })
        return frame
    }

    // ── No net ────────────────────────────────────────────────────────────────

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val ll    = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        ll.addView(TextView(context).apply {
            text = "Sem ligação à internet"; textSize = 15f
            setTextColor(Color.argb(180, 255, 255, 255)); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(WRAP, WRAP).also { it.bottomMargin = dp(20) })
        val btn = TextView(context).apply {
            text = "Tentar novamente"; textSize = 14f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(dp(28), dp(12), dp(28), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat(); setColor(Color.argb(255, 40, 40, 40))
                setStroke(dp(1), Color.argb(80, 255, 255, 255))
            }
        }
        btn.setOnClickListener { hideNoNet(); checkNetAndLoad() }
        ll.addView(btn)
        frame.addView(ll, FrameLayout.LayoutParams(WRAP, WRAP).also { it.gravity = Gravity.CENTER })
        return frame
    }

    // ── Share sheet ───────────────────────────────────────────────────────────

    private fun openShareSheet() {
        val dialog = BottomSheetDialog(
            activity, com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog)
        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
        }
        val bar = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }
        sheet.addView(bar, LinearLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(12); it.bottomMargin = dp(12)
        })
        sheet.addView(TextView(context).apply {
            text = "Partilhar"; setTextColor(Color.parseColor("#0F1419"))
            textSize = 18f; setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(4), dp(20), dp(16))
        })
        sheet.addView(View(context).apply { setBackgroundColor(Color.parseColor("#EFF3F4")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        data class ShareOpt(val ico: String, val label: String)
        listOf(
            ShareOpt("icons/svg/phosphor-icons/regular/copy.svg",          "Copiar link"),
            ShareOpt("icons/svg/phosphor-icons/regular/whatsapp-logo.svg", "WhatsApp"),
            ShareOpt("icons/svg/phosphor-icons/regular/telegram-logo.svg", "Telegram"),
            ShareOpt("icons/svg/phosphor-icons/regular/twitter-logo.svg",  "Twitter / X"),
            ShareOpt("icons/svg/phosphor-icons/regular/envelope.svg",      "Email"),
            ShareOpt("icons/svg/phosphor-icons/regular/share-fat.svg",     "Outras apps"),
        ).forEach { opt ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
                isClickable = true; isFocusable = true; background = pressDrawable()
                setOnClickListener {
                    val url = videos.getOrNull(currentIdx)?.link ?: ""
                    when (opt.label) {
                        "Copiar link" -> {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("link", url))
                            Toast.makeText(context, "Link copiado", Toast.LENGTH_SHORT).show(); dialog.dismiss()
                        }
                        "Outras apps" -> {
                            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, url)
                            }
                            activity.startActivity(android.content.Intent.createChooser(i, "Partilhar via"))
                            dialog.dismiss()
                        }
                        else -> { Toast.makeText(context, "${opt.label} — em breve", Toast.LENGTH_SHORT).show(); dialog.dismiss() }
                    }
                }
            }
            try {
                val px = dp(22); val svg = SVG.getFromAsset(activity.assets, opt.ico)
                svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888); svg.renderToCanvas(Canvas(bmp))
                row.addView(android.widget.ImageView(context).apply {
                    setImageBitmap(bmp); setColorFilter(Color.parseColor("#536471"))
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                }, LinearLayout.LayoutParams(dp(22), dp(22)))
            } catch (_: Exception) {}
            row.addView(View(context), LinearLayout.LayoutParams(dp(16), 0))
            row.addView(TextView(context).apply {
                text = opt.label; setTextColor(Color.parseColor("#0F1419")); textSize = 15f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            sheet.addView(row)
            sheet.addView(View(context).apply { setBackgroundColor(Color.parseColor("#EFF3F4")) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }
        sheet.addView(View(context), LinearLayout.LayoutParams(1, dp(24)))
        dialog.setContentView(sheet)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    // ── Comment sheet ─────────────────────────────────────────────────────────

    private fun openCommentSheet() {
        val dialog = BottomSheetDialog(
            activity, com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog)
        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
        }
        val bar = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }
        sheet.addView(bar, LinearLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(12); it.bottomMargin = dp(12)
        })
        sheet.addView(TextView(context).apply {
            text = "Comentários"; setTextColor(Color.parseColor("#0F1419"))
            textSize = 18f; setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(4), dp(20), dp(16))
        })
        sheet.addView(View(context).apply { setBackgroundColor(Color.parseColor("#EFF3F4")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        val commentScroll = android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
        }
        val commentCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        listOf(Pair("Utilizador1","Que vídeo incrível! 🔥"), Pair("Utilizador2","Adorei, continua assim!"), Pair("Utilizador3","Top demais 👏")).forEach { (user, msg) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            row.addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#EFF3F4")) }
            }, LinearLayout.LayoutParams(dp(36), dp(36)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(10), 0))
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(context).apply { text = user; setTextColor(Color.parseColor("#0F1419")); textSize = 13f; setTypeface(null, Typeface.BOLD) })
            col.addView(TextView(context).apply { text = msg; setTextColor(Color.parseColor("#536471")); textSize = 13f; setPadding(0, dp(2), 0, 0) })
            row.addView(col)
            commentCol.addView(row)
            commentCol.addView(View(context).apply { setBackgroundColor(Color.parseColor("#EFF3F4")) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }
        commentScroll.addView(commentCol, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sheet.addView(commentScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        sheet.addView(View(context).apply { setBackgroundColor(Color.parseColor("#EFF3F4")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val input = EditText(context).apply {
            hint = "Adicionar comentário..."; setHintTextColor(Color.parseColor("#8899AA"))
            setTextColor(Color.parseColor("#0F1419")); textSize = 14f
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(24).toFloat(); setColor(Color.parseColor("#EFF3F4")) }
            setPadding(dp(16), dp(10), dp(16), dp(10)); maxLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val sendBtn = FrameLayout(context).apply {
            isClickable = true; isFocusable = true
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E01462")) }
            setOnClickListener {
                if (input.text.toString().trim().isNotEmpty()) {
                    Toast.makeText(context, "Comentário enviado", Toast.LENGTH_SHORT).show()
                    input.setText(""); dialog.dismiss()
                }
            }
        }
        try {
            val px = dp(20); val svg = SVG.getFromAsset(activity.assets, "icons/svg/phosphor-icons/regular/paper-plane-tilt.svg")
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888); svg.renderToCanvas(Canvas(bmp))
            sendBtn.addView(android.widget.ImageView(context).apply {
                setImageBitmap(bmp); setColorFilter(Color.WHITE)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }, FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}
        inputRow.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(View(context), LinearLayout.LayoutParams(dp(8), 0))
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp(42), dp(42)))
        sheet.addView(inputRow)

        dialog.setContentView(sheet)
        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let {
                val beh = BottomSheetBehavior.from(it)
                val h   = (activity.resources.displayMetrics.heightPixels * 0.75f).toInt()
                it.layoutParams.height = h; it.requestLayout()
                beh.peekHeight = h; beh.state = BottomSheetBehavior.STATE_EXPANDED
                beh.skipCollapsed = true; it.setBackgroundColor(Color.TRANSPARENT)
            }
        }
        dialog.show()
    }

    // ── SVG helpers ───────────────────────────────────────────────────────────

    private fun svgIv(path: String, sizeDp: Int, tint: Int): android.widget.ImageView {
        val iv = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE; setColorFilter(tint)
        }
        refreshSvgIv(iv, path, sizeDp)
        return iv
    }

    private fun refreshSvgIv(iv: android.widget.ImageView, path: String, sizeDp: Int) {
        try {
            val px = dp(sizeDp); val svg = SVG.getFromAsset(activity.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp)); iv.setImageBitmap(bmp)
        } catch (_: Exception) {}
    }

    private fun svgBtn(path: String, sizeDp: Int): FrameLayout {
        val btn = FrameLayout(context).apply { isClickable = true; isFocusable = true }
        val iv  = svgIv(path, sizeDp, Color.WHITE)
        val px  = dp(sizeDp)
        btn.addView(iv, FrameLayout.LayoutParams(px, px).also { it.gravity = Gravity.CENTER })
        return btn
    }

    private fun pressDrawable(): Drawable {
        val pressed = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(Color.parseColor("#F7F9F9")) }
        val normal  = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(Color.TRANSPARENT) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun onDestroy() {
        webViews.values.forEach { it.stopLoading(); it.destroy() }
        webViews.clear()
    }
}