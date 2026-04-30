// ui/ShortiesPage.kt
package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.R
import com.doction.webviewapp.models.ShortVideo
import com.doction.webviewapp.theme.AppTheme
import kotlin.math.abs

@SuppressLint("ViewConstructor", "SetJavaScriptEnabled", "ClickableViewAccessibility")
class ShortiesPage(private val activity: MainActivity) : FrameLayout(activity) {

    // ── WebView oculto que faz o scraping e executa ações ────────────────────
    private val scraperWeb = WebView(context)

    // ── Dados ─────────────────────────────────────────────────────────────────
    private val videos     = mutableListOf<ShortVideo>()
    private var currentIdx = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Player WebView (visível) ───────────────────────────────────────────────
    private val playerWeb  = WebView(context)

    // ── Layout principal ──────────────────────────────────────────────────────
    private val playerFrame   = FrameLayout(context)
    private val overlayRight  = LinearLayout(context)   // coluna direita TikTok
    private val infoBottom    = LinearLayout(context)   // linha inferior esquerda
    private val loadingView   = buildLoadingView()
    private val noNetView     = buildNoNetView()

    // ── Controlos ─────────────────────────────────────────────────────────────
    private val btnLike    = buildIconBtn("heart_outline")
    private val tvLikes    = buildLabel("0")
    private val btnMute    = buildIconBtn("volume_on")
    private val btnShare   = buildIconBtn("share")
    private val avatarView = buildAvatar()
    private val tvAuthor   = buildBoldLabel("")
    private val tvTitle    = buildSmallLabel("")
    private val tvTags     = buildSmallLabel("")

    // ── Estado ────────────────────────────────────────────────────────────────
    private var isMuted    = false
    private var isLiked    = false

    // ── Swipe vertical ────────────────────────────────────────────────────────
    private var touchStartY   = 0f
    private var touchStartX   = 0f
    private var isDragging    = false
    private val SWIPE_THRESH  = 80f

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        checkNetAndLoad()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI BUILD
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        // Player ocupa tudo
        playerFrame.addView(playerWeb, lp(MATCH_PARENT, MATCH_PARENT))
        addView(playerFrame, lp(MATCH_PARENT, MATCH_PARENT))

        // Overlay direito — coluna TikTok
        overlayRight.orientation = LinearLayout.VERTICAL
        overlayRight.gravity     = Gravity.CENTER_HORIZONTAL
        overlayRight.setPadding(0, 0, dp(12), dp(100))

        // Avatar + Like + Mute + Share
        overlayRight.addView(avatarView, lp(dp(52), dp(52)).also {
            it.bottomMargin = dp(20)
        })
        overlayRight.addView(btnLike,  lp(dp(44), dp(44)))
        overlayRight.addView(tvLikes,  lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(20) })
        overlayRight.addView(btnMute,  lp(dp(44), dp(44)).also { it.bottomMargin = dp(20) })
        overlayRight.addView(btnShare, lp(dp(44), dp(44)))

        val rlp = FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).also {
            it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        addView(overlayRight, rlp)

        // Info inferior esquerda
        infoBottom.orientation = LinearLayout.VERTICAL
        infoBottom.setPadding(dp(14), 0, dp(70), dp(90))
        infoBottom.addView(tvAuthor, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(4) })
        infoBottom.addView(tvTitle,  lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(4) })
        infoBottom.addView(tvTags,   lp(WRAP_CONTENT, WRAP_CONTENT))

        val blp = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        }
        addView(infoBottom, blp)

        // Loading e sem rede
        addView(loadingView, lp(MATCH_PARENT, MATCH_PARENT))
        addView(noNetView,   lp(MATCH_PARENT, MATCH_PARENT))
        noNetView.visibility = GONE

        // Gestos no playerFrame
        playerFrame.setOnTouchListener { _, e -> handleTouch(e) }
        overlayRight.setOnTouchListener { _, e -> handleTouch(e) }
        infoBottom.setOnTouchListener  { _, e -> handleTouch(e) }

        // Listeners dos botões
        btnLike.setOnClickListener  { toggleLike() }
        btnMute.setOnClickListener  { toggleMute() }
        btnShare.setOnClickListener { showShareDialog() }
        avatarView.setOnClickListener { openPublisher() }
        tvAuthor.setOnClickListener   { openPublisher() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REDE
    // ─────────────────────────────────────────────────────────────────────────

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkNetAndLoad() {
        if (!isOnline()) {
            showNoNet()
            // Retry a cada 3s
            mainHandler.postDelayed({
                if (isOnline()) { hideNoNet(); initScraper() } else checkNetAndLoad()
            }, 3000)
        } else {
            initScraper()
        }
    }

    private fun showNoNet() {
        loadingView.visibility = GONE
        noNetView.visibility   = VISIBLE
    }

    private fun hideNoNet() {
        noNetView.visibility   = GONE
        loadingView.visibility = VISIBLE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCRAPER WEBVIEW
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun initScraper() {
        scraperWeb.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString   = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        val bridge = ShortiesBridge(
            onVideosReady = { list -> mainHandler.post { onVideosLoaded(list) } },
            onLikeResult  = { key, liked -> mainHandler.post { onLikeResult(key, liked) } },
            onMuteResult  = { muted -> mainHandler.post { onMuteResult(muted) } },
        )
        scraperWeb.addJavascriptInterface(bridge, "ShortiesBridge")
        scraperWeb.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectScraper(view)
            }
        }
        scraperWeb.loadUrl("https://www.pornhub.com/shorties")
    }

    private fun injectScraper(view: WebView?) {
        val js = """
        (function() {
          // Age gate
          var gates=['[data-testid="age-confirmation-confirm"]','.age-gate-button','button.enterButton'];
          for(var g=0;g<gates.length;g++){var el=document.querySelector(gates[g]);if(el){el.click();break;}}
          
          // Esconde UI do pornhub
          var hide=document.createElement('style');
          hide.textContent='header,footer,.siteMenu,.topMenu,.menuContainer,.headerLogo,.rightMenuSection,.joinNowWrapper,.externalLinkButton,.actionScribe,.flag.topMenuFlag{display:none!important}';
          document.head && document.head.appendChild(hide);
          
          // Scrape após carregamento
          setTimeout(function() {
            var videos=[];
            var items=document.querySelectorAll('.shortyContainer,.shorty-container,[class*="shorty"]');
            if(!items || items.length===0) {
              items=document.querySelectorAll('.pcVideoListItem,.videoblock');
            }
            items.forEach(function(el){
              try {
                var a      = el.querySelector('a[href*="viewkey"]') || el.querySelector('a[href*="view_video"]');
                var href   = a ? a.href : '';
                var vk     = (href.match(/viewkey=([^&]+)/)||[])[1] || '';
                var img    = el.querySelector('img');
                var thumb  = img ? (img.getAttribute('data-src')||img.src||'') : '';
                var title  = el.querySelector('.title a,.video-title,h3 a');
                var tStr   = title ? title.textContent.trim() : '';
                var likes  = el.querySelector('.votesUp,.likesCount,[class*="likes"]');
                var lStr   = likes ? likes.textContent.trim() : '0';
                var views  = el.querySelector('.views,[class*="views"]');
                var vStr   = views ? views.textContent.trim() : '';
                var dur    = el.querySelector('.duration,[class*="duration"]');
                var dStr   = dur ? dur.textContent.trim() : '';
                var auth   = el.querySelector('.usernameWrap a,.usernameBadgesWrap a,[class*="username"] a');
                var aStr   = auth ? auth.textContent.trim() : '';
                var authHr = auth ? auth.href : '';
                var authKey= (authHr.match(/\/model\/([^\/\?]+)/)||authHr.match(/\/pornstar\/([^\/\?]+)/)||[])[1]||'';
                var avEl   = el.querySelector('.userAvatar img,[class*="avatar"] img');
                var avStr  = avEl ? (avEl.getAttribute('data-src')||avEl.src||'') : '';
                var tagEls = el.querySelectorAll('.tagsWrapper a,[class*="tag"] a');
                var tags   = [];
                tagEls.forEach(function(t){tags.push(t.textContent.trim());});
                if(vk) videos.push({
                  viewKey:vk, title:tStr, thumb:thumb, likes:lStr,
                  views:vStr, duration:dStr, publisherName:aStr,
                  publisherThumb:avStr, publisherUrl:authHr,
                  publisherKey:authKey, tags:tags.join(',')
                });
              } catch(e){}
            });
            window.ShortiesBridge.onVideosScraped(JSON.stringify(videos));
          }, 2500);
        })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PLAYER WEBVIEW
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPlayerWeb() {
        playerWeb.settings.apply {
            javaScriptEnabled              = true
            domStorageEnabled              = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort                = true
            loadWithOverviewMode           = true
            setSupportZoom(false)
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        playerWeb.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        playerWeb.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectPlayerCSS(view)
                injectPlayerControls(view)
                loadingView.animate().alpha(0f).setDuration(300).withEndAction {
                    loadingView.visibility = GONE
                    loadingView.alpha      = 1f
                }.start()
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val u = r?.url?.toString() ?: return true
                return !u.contains("pornhub.com")
            }
        }
    }

    private fun injectPlayerCSS(view: WebView?) {
        val css = """
          /* Remove tudo do pornhub exceto o player */
          header,footer,.siteMenu,.topMenu,.menuContainer,.headerLogo,
          .rightMenuSection,.joinNowWrapper,.externalLinkButton,
          .actionScribe,.flag.topMenuFlag,.videoPageTitle,
          .relatedVideosSection,.recommendedVideos,
          .commentsSection,.votesWrapper,.ratingPercent,
          .addToSection,.shareContainer,.categoriesWrapper,
          .tagsWrapper,.modelBlock,.paginationBlock,.buttonReportSection,
          .moreLikeWrapper,.upNextSection,.descriptionSection,.userBlock,
          .videoDetailBlock,.abovePlayer,.sectionWrapper {
            display:none!important;
          }
          /* Player fullscreen */
          body { background:#000!important; margin:0!important; padding:0!important; overflow:hidden!important; }
          #player, .playerWrapper, #mgp, .mgp_container,
          .mainPlayerSection, .centerVideoSection, #mainPlayerDiv {
            position:fixed!important; top:0!important; left:0!important;
            width:100vw!important; height:100vh!important; z-index:9999!important;
            background:#000!important;
          }
          /* Remove seleção de texto */
          * { -webkit-user-select:none!important; user-select:none!important;
              -webkit-tap-highlight-color:transparent!important; outline:none!important; }
          /* Remove scrollbar */
          ::-webkit-scrollbar { display:none!important; }
          /* Remove controles nativos do player pornhub — substituímos pelos nossos */
          .mgp_bottom-controls,.mgp_top-controls,.mgp_shareContainer,
          .mgp_follow,.mgp_actionScribe,.mgp_logo,.mgp_btn-settings,
          .mgp_qualitiesMenu,.mgp_quality-btn,.mgp_autoplay,
          .mgp_nextVideoOverlay,.mgp_gridMenu,.mgp_slideout-outer-wrapper,
          .mgp_castOverlay,.mgp_unmute,.mgp_pipButton {
            display:none!important;
          }
        """.trimIndent().replace("\n", " ")

        view?.evaluateJavascript("""
            (function(){
              var s=document.getElementById('_px_shorty');
              if(!s){s=document.createElement('style');s.id='_px_shorty';document.head.appendChild(s);}
              s.textContent='$css';
            })();
        """.trimIndent(), null)
    }

    private fun injectPlayerControls(view: WebView?) {
        // Auto-play, remove highlight, auto-age-gate
        view?.evaluateJavascript("""
            (function(){
              // Age gate
              var gs=['[data-testid="age-confirmation-confirm"]','.age-gate-button','button.enterButton'];
              for(var i=0;i<gs.length;i++){var e=document.querySelector(gs[i]);if(e){e.click();break;}}
              // Auto play
              setTimeout(function(){
                var v=document.querySelector('video');
                if(v){v.play();v.muted=false;}
                // Tenta click no bigPlay
                var bp=document.querySelector('.mgp_bigPlay,.mgp_playbackBtn');
                if(bp) bp.click();
              },1000);
            })();
        """.trimIndent(), null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DADOS CARREGADOS
    // ─────────────────────────────────────────────────────────────────────────

    private fun onVideosLoaded(list: List<ShortVideo>) {
        if (list.isEmpty()) {
            // Fallback: carrega a página direto
            loadFallback()
            return
        }
        videos.clear()
        videos.addAll(list)
        currentIdx = 0
        setupPlayerWeb()
        loadVideo(0, animate = false)
    }

    private fun loadFallback() {
        // Se o scraper não conseguiu vídeos, carrega a página normal com CSS limpo
        setupPlayerWeb()
        playerWeb.loadUrl("https://www.pornhub.com/shorties")
        loadingView.visibility = GONE
    }

    private fun loadVideo(index: Int, animate: Boolean = true) {
        if (index < 0 || index >= videos.size) return
        val v = videos[index]
        isLiked = v.isLiked
        isMuted = v.isMuted

        // Atualiza UI
        tvAuthor.text = v.publisherName
        tvTitle.text  = v.title
        tvTags.text   = if (v.tags.isEmpty()) "" else v.tags.take(3).joinToString(" ") { "#$it" }
        tvLikes.text  = v.likes
        updateLikeIcon()
        updateMuteIcon()

        // Avatar do publisher
        loadAvatarAsync(v.publisherThumb)

        if (animate) {
            animateTransition { playerWeb.loadUrl(v.videoUrl) }
        } else {
            playerWeb.loadUrl(v.videoUrl)
            loadingView.visibility = VISIBLE
            loadingView.alpha      = 1f
        }
    }

    private fun animateTransition(onMid: () -> Unit) {
        playerFrame.animate()
            .translationY(-height.toFloat() * 0.05f)
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                onMid()
                playerFrame.translationY = height.toFloat() * 0.08f
                playerFrame.alpha        = 0f
                playerFrame.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(280)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
                loadingView.visibility = VISIBLE
                loadingView.alpha      = 1f
            }.start()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GESTOS SWIPE
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleTouch(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = e.y; touchStartX = e.x; isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - touchStartY
                val dx = e.x - touchStartX
                if (!isDragging && abs(dy) > 10 && abs(dy) > abs(dx)) {
                    isDragging = true
                }
                if (isDragging) {
                    playerFrame.translationY = dy * 0.3f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = e.y - touchStartY
                if (isDragging) {
                    if (dy < -SWIPE_THRESH && currentIdx < videos.size - 1) {
                        snapBack { currentIdx++; loadVideo(currentIdx) }
                    } else if (dy > SWIPE_THRESH && currentIdx > 0) {
                        snapBack { currentIdx--; loadVideo(currentIdx) }
                    } else {
                        playerFrame.animate().translationY(0f).setDuration(200)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }
                    isDragging = false
                }
            }
        }
        return true
    }

    private fun snapBack(onEnd: () -> Unit) {
        playerFrame.animate().translationY(0f).setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction(onEnd).start()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AÇÕES
    // ─────────────────────────────────────────────────────────────────────────

    private fun toggleLike() {
        isLiked = !isLiked
        videos.getOrNull(currentIdx)?.isLiked = isLiked
        updateLikeIcon()
        // Injeta like no player se tiver viewkey
        val vk = videos.getOrNull(currentIdx)?.viewKey ?: return
        playerWeb.evaluateJavascript("""
            (function(){
              var btn=document.querySelector('.voteUp,.mgp_likeBtn,[class*="likeBtn"],[class*="thumbUp"]');
              if(btn) btn.click();
            })();
        """.trimIndent(), null)
    }

    private fun toggleMute() {
        isMuted = !isMuted
        videos.getOrNull(currentIdx)?.isMuted = isMuted
        updateMuteIcon()
        playerWeb.evaluateJavascript("""
            (function(){
              var v=document.querySelector('video');
              if(v) { v.muted=${isMuted}; }
              var mb=document.querySelector('.mgp_button[data-action="mute"],.volumeBtn');
              if(mb) mb.click();
            })();
        """.trimIndent(), null)
    }

    private fun showShareDialog() {
        val v   = videos.getOrNull(currentIdx) ?: return
        val url = v.videoUrl
        val embed = """<iframe src="https://www.pornhub.com/embed/${v.viewKey}" frameborder="0" width="560" height="315" scrolling="no" allowfullscreen></iframe>"""

        val dialog = android.app.AlertDialog.Builder(context, R.style.NativeDialogTheme)
            .setTitle("Partilhar vídeo")
            .setItems(arrayOf("Copiar link", "Copiar código embed", "Partilhar via...")) { _, which ->
                when (which) {
                    0 -> copyToClipboard("URL do vídeo", url)
                    1 -> copyToClipboard("Código embed", embed)
                    2 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(intent, "Partilhar via"))
                    }
                }
            }.create()
        dialog.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
    }

    private fun openPublisher() {
        val v = videos.getOrNull(currentIdx) ?: return
        if (v.publisherKey.isEmpty() && v.publisherUrl.isEmpty()) return
        val page = PublisherPage(activity, v.publisherKey, v.publisherName, v.publisherThumb, v.publisherUrl)
        activity.addContentOverlay(page)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ÍCONES / LABELS
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateLikeIcon() {
        val color = if (isLiked) Color.parseColor("#FF4D4D") else Color.WHITE
        drawSvgOnView(btnLike, if (isLiked) "heart_filled" else "heart_outline", color)
    }

    private fun updateMuteIcon() {
        drawSvgOnView(btnMute, if (isMuted) "volume_off" else "volume_on", Color.WHITE)
    }

    private fun drawSvgOnView(view: ImageView, name: String, tint: Int) {
        try {
            val px  = dp(28)
            val svg = com.caverock.androidsvg.SVG.getFromAsset(context.assets, "icons/$name.svg")
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            view.setImageBitmap(bmp)
            view.setColorFilter(tint)
        } catch (_: Exception) {
            view.setImageBitmap(null)
        }
    }

    private fun loadAvatarAsync(url: String) {
        if (url.isEmpty()) return
        Thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bmp  = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                conn.disconnect()
                mainHandler.post {
                    avatarView.setImageBitmap(bmp)
                    avatarView.clipToOutline = true
                    avatarView.outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(v: View?, o: android.graphics.Outline?) {
                            o?.setOval(0, 0, v!!.width, v.height)
                        }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOADING VIEW CUSTOMIZADO
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildLoadingView(): FrameLayout {
        val frame = FrameLayout(context)
        frame.setBackgroundColor(Color.BLACK)

        val spinner = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color   = Color.parseColor("#FF6600")
                style   = Paint.Style.STROKE
                strokeWidth = dp(3).toFloat()
                strokeCap   = Paint.Cap.ROUND
            }
            private var angle = 0f
            private val run   = object : Runnable {
                override fun run() { angle = (angle + 6f) % 360f; invalidate(); postDelayed(this, 16) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx * 0.7f
                c.drawArc(cx-r, cy-r, cx+r, cy+r, angle, 260f, false, paint)
            }
        }
        val slp = FrameLayout.LayoutParams(dp(52), dp(52)).also { it.gravity = Gravity.CENTER }
        frame.addView(spinner, slp)
        return frame
    }

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context)
        frame.setBackgroundColor(Color.BLACK)
        val ll = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }

        // Spinner laranja igual mas cinzento
        val spinner = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY; style = Paint.Style.STROKE
                strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND
            }
            private var angle = 0f
            private val run = object : Runnable {
                override fun run() { angle = (angle + 2f) % 360f; invalidate(); postDelayed(this, 32) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val cx = width/2f; val cy = height/2f; val r = cx*0.7f
                c.drawArc(cx-r, cy-r, cx+r, cy+r, angle, 200f, false, paint)
            }
        }
        ll.addView(spinner, lp(dp(52), dp(52)).also { it.bottomMargin = dp(16) })

        val tv = TextView(context).apply {
            text      = "Sem ligação à internet"
            textSize  = 15f
            setTextColor(Color.GRAY)
            gravity   = Gravity.CENTER
        }
        ll.addView(tv, lp(WRAP_CONTENT, WRAP_CONTENT))

        val btn = TextView(context).apply {
            text      = "Tentar novamente"
            textSize  = 14f
            setTextColor(Color.parseColor("#FF6600"))
            gravity   = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        btn.setOnClickListener { hideNoNet(); checkNetAndLoad() }
        ll.addView(btn, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.topMargin = dp(12) })

        frame.addView(ll, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
        return frame
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS DE VIEW
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildIconBtn(iconName: String): ImageView {
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundResource(0)
        }
        drawSvgOnView(iv, iconName, Color.WHITE)
        return iv
    }

    private fun buildAvatar(): ImageView {
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.DKGRAY)
        }
    }

    private fun buildBoldLabel(text: String) = TextView(context).apply {
        this.text  = text
        textSize   = 14f
        typeface   = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }

    private fun buildLabel(text: String) = TextView(context).apply {
        this.text  = text
        textSize   = 12f
        setTextColor(Color.WHITE)
        gravity    = Gravity.CENTER
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }

    private fun buildSmallLabel(text: String) = TextView(context).apply {
        this.text  = text
        textSize   = 13f
        setTextColor(Color.WHITE)
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
        maxLines   = 2
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h)

    private val MATCH_PARENT = FrameLayout.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = FrameLayout.LayoutParams.WRAP_CONTENT

    fun onDestroy() {
        scraperWeb.destroy()
        playerWeb.destroy()
    }
}