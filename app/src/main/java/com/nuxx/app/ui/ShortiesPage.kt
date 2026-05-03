// ui/ShortiesPage.kt
package com.nuxx.app.ui

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
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.nuxx.app.MainActivity
import com.nuxx.app.models.ShortVideo
import kotlin.math.abs

@SuppressLint("ViewConstructor", "SetJavaScriptEnabled", "ClickableViewAccessibility")
class ShortiesPage(private val activity: MainActivity) : FrameLayout(activity) {

    private val scraperWeb  = WebView(context)
    private val playerWeb   = WebView(context)
    private val videos      = mutableListOf<ShortVideo>()
    private var currentIdx  = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    private val playerFrame  = FrameLayout(context)
    private val overlayRight = LinearLayout(context)
    private val infoBottom   = LinearLayout(context)
    private val loadingView  = buildLoadingView()
    private val noNetView    = buildNoNetView()

    private val btnLike    = buildIconButton(ICON_HEART_OUTLINE, Color.WHITE)
    private val tvLikes    = buildLabel("0")
    private val btnMute    = buildIconButton(ICON_VOLUME_ON, Color.WHITE)
    private val btnShare   = buildIconButton(ICON_SHARE, Color.WHITE)
    private val avatarView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.parseColor("#333333"))
    }
    private val tvAuthor = buildBoldLabel("")
    private val tvTitle  = buildSmallLabel("")
    private val tvTags   = buildSmallLabel("")

    private var isMuted      = false
    private var isLiked      = false
    private var touchStartY  = 0f
    private var touchStartX  = 0f
    private var isDragging   = false
    private val SWIPE_THRESH = 70f
    private val MATCH_PARENT = FrameLayout.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = FrameLayout.LayoutParams.WRAP_CONTENT

    init {
        setBackgroundColor(Color.BLACK)
        applyConsentCookies()
        buildUI()
        checkNetAndLoad()
    }

    // ── Cookies ───────────────────────────────────────────────────────────────

    private fun applyConsentCookies() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(scraperWeb, true)
        cm.setAcceptThirdPartyCookies(playerWeb, true)
        val domain = ".pornhub.com"
        listOf(
            "age_verified=1",
            "cookiesBannerSeen=1",
            "cookiesAccepted=1",
            "has_seen_age_gate=1",
            "il=1",
            "platform=pc",
            "accessAgeDisclaimerPH=1",
            "accessAgeDisclaimerAVS=1",
            "ph_gdpr_notice_accepted=1"
        ).forEach { cm.setCookie(domain, "$it; path=/; domain=.pornhub.com") }
        cm.flush()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        playerFrame.addView(playerWeb, lp(MATCH_PARENT, MATCH_PARENT))
        addView(playerFrame, lp(MATCH_PARENT, MATCH_PARENT))

        overlayRight.orientation = LinearLayout.VERTICAL
        overlayRight.gravity     = Gravity.CENTER_HORIZONTAL
        overlayRight.setPadding(0, 0, dp(16), dp(140))

        avatarView.clipToOutline = true
        avatarView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View?, o: Outline?) {
                o?.setOval(0, 0, v!!.width, v.height)
            }
        }
        overlayRight.addView(avatarView, lp(dp(50), dp(50)).also { it.bottomMargin = dp(20) })
        overlayRight.addView(btnLike,   lp(dp(44), dp(44)))
        overlayRight.addView(tvLikes,   lp(WRAP_CONTENT, WRAP_CONTENT).also {
            it.topMargin = dp(2); it.bottomMargin = dp(18)
        })
        overlayRight.addView(btnMute,   lp(dp(44), dp(44)).also { it.bottomMargin = dp(18) })
        overlayRight.addView(btnShare,  lp(dp(44), dp(44)))

        addView(overlayRight, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).also {
            it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })

        infoBottom.orientation = LinearLayout.VERTICAL
        infoBottom.setPadding(dp(14), 0, dp(70), dp(110))
        infoBottom.addView(tvAuthor, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(6) })
        infoBottom.addView(tvTitle,  lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(4) })
        infoBottom.addView(tvTags,   lp(WRAP_CONTENT, WRAP_CONTENT))
        addView(infoBottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })

        addView(loadingView, lp(MATCH_PARENT, MATCH_PARENT))
        addView(noNetView,   lp(MATCH_PARENT, MATCH_PARENT))
        noNetView.visibility = GONE

        playerFrame.setOnTouchListener  { _, e -> handleTouch(e) }
        overlayRight.setOnTouchListener { _, e -> handleTouch(e) }
        infoBottom.setOnTouchListener   { _, e -> handleTouch(e) }

        btnLike.setOnClickListener    { toggleLike() }
        btnMute.setOnClickListener    { toggleMute() }
        btnShare.setOnClickListener   { showShareDialog() }
        avatarView.setOnClickListener { openPublisher() }
        tvAuthor.setOnClickListener   { openPublisher() }
    }

    // ── Rede ──────────────────────────────────────────────────────────────────

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkNetAndLoad() {
        if (!isOnline()) {
            showNoNet()
            mainHandler.postDelayed({
                if (isOnline()) { hideNoNet(); initScraper() } else checkNetAndLoad()
            }, 3000)
        } else {
            initScraper()
        }
    }

    private fun showNoNet() { loadingView.visibility = GONE;  noNetView.visibility = VISIBLE }
    private fun hideNoNet() { noNetView.visibility   = GONE;  loadingView.visibility = VISIBLE }

    // ── Scraper ───────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun initScraper() {
        scraperWeb.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString   = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        val bridge = ShortiesBridge(
            onVideosReady  = { list -> mainHandler.post { onVideosLoaded(list) } },
            onLikeCallback = { key, liked -> mainHandler.post { handleLikeResult(key, liked) } },
            onMuteCallback = { muted -> mainHandler.post { handleMuteResult(muted) } },
        )
        scraperWeb.addJavascriptInterface(bridge, "ShortiesBridge")
        scraperWeb.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                mainHandler.postDelayed({ injectScraper(view) }, 3500)
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?) = true
        }
        scraperWeb.loadUrl("https://www.pornhub.com/shorties")
    }

    private fun injectScraper(view: WebView?) {
        view?.evaluateJavascript("""
        (function(){
          var videos=[];
          var items=document.querySelectorAll('[class*="shorty"],[class*="Shorty"],.pcVideoListItem,.videoblock,.wrap');
          if(!items||items.length===0) {
            items=document.querySelectorAll('li[data-video-vkey],li[data-id],.videoBox');
          }
          items.forEach(function(el){
            try{
              var a=el.querySelector('a[href*="viewkey"]')||el.querySelector('a[href*="view_video"]');
              var href=a?a.href:''; if(!href) return;
              var vk=(href.match(/viewkey=([^&\s]+)/)||[])[1]||''; if(!vk) return;
              var img=el.querySelector('img');
              var thumb=img?(img.getAttribute('data-src')||img.getAttribute('src')||''):'';
              var titleEl=el.querySelector('.title a,.video-title,h3 a,[class*="title"] a');
              var tStr=titleEl?titleEl.textContent.trim():'';
              var likesEl=el.querySelector('.votesUp,.likesCount,[class*="likes"],[class*="Likes"],[class*="vote"]');
              var lStr=likesEl?likesEl.textContent.trim():'0';
              var authEl=el.querySelector('.usernameWrap a,.usernameBadgesWrap a,[class*="username"] a,[class*="author"] a,[class*="model"] a');
              var aStr=authEl?authEl.textContent.trim():'';
              var authHr=authEl?authEl.href:'';
              var authKey=(authHr.match(/\/model\/([^\/\?]+)/)||authHr.match(/\/pornstar\/([^\/\?]+)/)||authHr.match(/\/channels\/([^\/\?]+)/)||[])[1]||'';
              var avEl=el.querySelector('.userAvatar img,[class*="avatar"] img,[class*="Avatar"] img');
              var avStr=avEl?(avEl.getAttribute('data-src')||avEl.getAttribute('src')||''):'';
              var durEl=el.querySelector('.duration,[class*="duration"]');
              var dStr=durEl?durEl.textContent.trim():'';
              var viewsEl=el.querySelector('.views,[class*="views"]');
              var vStr=viewsEl?viewsEl.textContent.trim():'';
              var tags=[];
              el.querySelectorAll('.tagsWrapper a,[class*="tag"] a').forEach(function(t){
                if(t.textContent.trim()) tags.push(t.textContent.trim());
              });
              videos.push({viewKey:vk,title:tStr,thumb:thumb,likes:lStr,
                videoUrl:'https://www.pornhub.com/view_video.php?viewkey='+vk,
                views:vStr,duration:dStr,publisherName:aStr,
                publisherThumb:avStr,publisherUrl:authHr,
                publisherKey:authKey,tags:tags.join(',')});
            }catch(e){}
          });
          window.ShortiesBridge.onVideosScraped(JSON.stringify(videos));
        })();
        """.trimIndent(), null)
    }

    // ── Player ────────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPlayerWeb() {
        playerWeb.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort                  = true
            loadWithOverviewMode             = true
            setSupportZoom(false)
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        playerWeb.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        playerWeb.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                mainHandler.postDelayed({
                    injectPlayerCSS(view)
                    injectAutoPlay(view)
                }, 1200)
                mainHandler.postDelayed({
                    loadingView.animate().alpha(0f).setDuration(300).withEndAction {
                        loadingView.visibility = GONE
                        loadingView.alpha      = 1f
                    }.start()
                }, 2000)
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val u = r?.url?.toString() ?: return true
                return !u.contains("pornhub.com/view_video") &&
                       !u.contains("phncdn.com") &&
                       !u.contains("pornhub.com/embed")
            }
        }
    }

    private fun injectPlayerCSS(view: WebView?) {
        val css = """
            header,footer,.siteMenu,.topMenu,.menuContainer,.headerLogo,
            .rightMenuSection,.joinNowWrapper,.externalLinkButton,.actionScribe,
            .flag.topMenuFlag,.videoPageTitle,.relatedVideosSection,
            .recommendedVideos,.commentsSection,.votesWrapper,.ratingPercent,
            .addToSection,.shareContainer,.categoriesWrapper,.tagsWrapper,
            .modelBlock,.paginationBlock,.buttonReportSection,.moreLikeWrapper,
            .upNextSection,.descriptionSection,.userBlock,.videoDetailBlock,
            .abovePlayer,.sectionWrapper,.cookiesBanner,.cookiesNotice,
            #cookieNotice,.cookie-notice,.age-gate,.ageGate,#age-gate,
            [class*="cookie"],[id*="cookie"],[class*="ageGate"],[id*="ageGate"],
            .mgp_shareContainer,.mgp_follow,.mgp_actionScribe,.mgp_logo,
            .mgp_qualitiesMenu,.mgp_quality-btn,.mgp_autoplay,
            .mgp_nextVideoOverlay,.mgp_gridMenu,.mgp_slideout-outer-wrapper,
            .mgp_castOverlay,.mgp_unmute,.mgp_pipButton,
            .mgp_btn-settings,.mgp_top-controls {
              display:none!important;
            }
            body {
              background:#000!important;margin:0!important;
              padding:0!important;overflow:hidden!important;
            }
            #player,.playerWrapper,#mgp,.mgp_container,
            .mainPlayerSection,.centerVideoSection,#mainPlayerDiv {
              position:fixed!important;top:0!important;left:0!important;
              width:100vw!important;height:100vh!important;
              z-index:9999!important;background:#000!important;
            }
            * {
              -webkit-user-select:none!important;user-select:none!important;
              -webkit-tap-highlight-color:transparent!important;
              outline:none!important;
            }
            ::-webkit-scrollbar{display:none!important}
        """.trimIndent().replace(Regex("\\s+"), " ")

        view?.evaluateJavascript("""
            (function(){
              var s=document.getElementById('_px_s');
              if(!s){s=document.createElement('style');s.id='_px_s';document.head.appendChild(s);}
              s.textContent='$css';
            })();
        """.trimIndent(), null)
    }

    private fun injectAutoPlay(view: WebView?) {
        view?.evaluateJavascript("""
            (function(){
              var sels=[
                '[data-testid="age-confirmation-confirm"]','.age-gate-button',
                'button.enterButton','a.enterButton',
                '#onetrust-accept-btn-handler','.cc-btn.cc-dismiss',
                'button[id*="accept"]','button[class*="accept"]',
                '.cookieOk','.acceptCookies'
              ];
              sels.forEach(function(s){
                var el=document.querySelector(s);if(el)el.click();
              });
              setTimeout(function(){
                var v=document.querySelector('video');
                if(v){v.muted=false;v.volume=1;v.play();}
                var bp=document.querySelector('.mgp_bigPlay,.mgp_playbackBtn,.mgp_btn-playback');
                if(bp)bp.click();
              },500);
            })();
        """.trimIndent(), null)
    }

    // ── Dados ─────────────────────────────────────────────────────────────────

    private fun onVideosLoaded(list: List<ShortVideo>) {
        if (list.isEmpty()) { loadFallback(); return }
        videos.clear(); videos.addAll(list)
        currentIdx = 0
        setupPlayerWeb()
        loadVideo(0, animate = false)
    }

    private fun loadFallback() {
        setupPlayerWeb()
        playerWeb.loadUrl("https://www.pornhub.com/shorties")
        mainHandler.postDelayed({
            loadingView.animate().alpha(0f).setDuration(300).withEndAction {
                loadingView.visibility = GONE
                loadingView.alpha = 1f
            }.start()
        }, 4000)
    }

    private fun loadVideo(index: Int, animate: Boolean = true) {
        if (index < 0 || index >= videos.size) return
        val v = videos[index]
        isLiked = v.isLiked
        isMuted = v.isMuted
        tvAuthor.text = if (v.publisherName.isNotEmpty()) "@${v.publisherName}" else ""
        tvTitle.text  = v.title
        tvTags.text   = if (v.tags.isEmpty()) "" else v.tags.take(3).joinToString(" ") { "#$it" }
        tvLikes.text  = v.likes.ifEmpty { "0" }
        updateLikeIcon()
        updateMuteIcon()
        updateShareIcon()
        loadAvatarAsync(v.publisherThumb)
        loadingView.visibility = VISIBLE
        loadingView.alpha      = 1f
        if (animate) {
            animateTransition { playerWeb.loadUrl(v.videoUrl) }
        } else {
            playerWeb.loadUrl(v.videoUrl)
        }
    }

    private fun animateTransition(onMid: () -> Unit) {
        playerFrame.animate()
            .translationY(-height.toFloat() * 0.04f).alpha(0f).setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                onMid()
                playerFrame.translationY = height.toFloat() * 0.06f
                playerFrame.alpha        = 0f
                playerFrame.animate()
                    .translationY(0f).alpha(1f).setDuration(240)
                    .setInterpolator(FastOutSlowInInterpolator()).start()
            }.start()
    }

    // ── Gestos ────────────────────────────────────────────────────────────────

    private fun handleTouch(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN  -> {
                touchStartY = e.y; touchStartX = e.x; isDragging = false
            }
            MotionEvent.ACTION_MOVE  -> {
                val dy = e.y - touchStartY
                val dx = e.x - touchStartX
                if (!isDragging && abs(dy) > 12 && abs(dy) > abs(dx)) isDragging = true
                if (isDragging) playerFrame.translationY = dy * 0.25f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = e.y - touchStartY
                if (isDragging) {
                    when {
                        dy < -SWIPE_THRESH && currentIdx < videos.size - 1 ->
                            snapBack { currentIdx++; loadVideo(currentIdx) }
                        dy > SWIPE_THRESH && currentIdx > 0 ->
                            snapBack { currentIdx--; loadVideo(currentIdx) }
                        else -> playerFrame.animate().translationY(0f).setDuration(200)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }
                    isDragging = false
                }
            }
        }
        return true
    }

    private fun snapBack(onEnd: () -> Unit) {
        playerFrame.animate().translationY(0f).setDuration(120)
            .setInterpolator(DecelerateInterpolator()).withEndAction(onEnd).start()
    }

    // ── Ações ─────────────────────────────────────────────────────────────────

    private fun toggleLike() {
        isLiked = !isLiked
        videos.getOrNull(currentIdx)?.isLiked = isLiked
        updateLikeIcon()
        playerWeb.evaluateJavascript("""
            (function(){
              var btn=document.querySelector('.voteUp,.thumbUpBtn,[class*="likeBtn"],[class*="thumbUp"]');
              if(btn)btn.click();
            })();
        """.trimIndent(), null)
    }

    private fun toggleMute() {
        isMuted = !isMuted
        videos.getOrNull(currentIdx)?.isMuted = isMuted
        updateMuteIcon()
        playerWeb.evaluateJavascript("""
            (function(){var v=document.querySelector('video');if(v)v.muted=${isMuted};})();
        """.trimIndent(), null)
    }

    private fun showShareDialog() {
        val v     = videos.getOrNull(currentIdx) ?: return
        val url   = v.videoUrl
        val embed = "<iframe src=\"https://www.pornhub.com/embed/${v.viewKey}\" " +
            "frameborder=\"0\" width=\"560\" height=\"315\" scrolling=\"no\" allowfullscreen></iframe>"
        val dialog = android.app.AlertDialog.Builder(context)
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
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
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
        activity.addContentOverlay(
            PublisherPage(activity, v.publisherKey, v.publisherName, v.publisherThumb, v.publisherUrl)
        )
    }

    private fun handleLikeResult(viewKey: String, liked: Boolean) {
        if (videos.getOrNull(currentIdx)?.viewKey == viewKey) {
            isLiked = liked; videos.getOrNull(currentIdx)?.isLiked = liked; updateLikeIcon()
        }
    }

    private fun handleMuteResult(muted: Boolean) {
        isMuted = muted; videos.getOrNull(currentIdx)?.isMuted = muted; updateMuteIcon()
    }

    // ── Ícones ────────────────────────────────────────────────────────────────

    private fun updateLikeIcon() {
        val color = if (isLiked) Color.parseColor("#FF4D4D") else Color.WHITE
        val icon  = if (isLiked) ICON_HEART_FILLED else ICON_HEART_OUTLINE
        refreshIconButton(btnLike, icon, color)
    }

    private fun updateMuteIcon() {
        refreshIconButton(btnMute, if (isMuted) ICON_VOLUME_OFF else ICON_VOLUME_ON, Color.WHITE)
    }

    private fun updateShareIcon() {
        refreshIconButton(btnShare, ICON_SHARE, Color.WHITE)
    }

    private fun buildIconButton(iconType: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(Color.TRANSPARENT)
        }
        refreshIconButton(iv, iconType, tint)
        return iv
    }

    private fun refreshIconButton(iv: ImageView, iconType: Int, tint: Int) {
        val size = dp(40)
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c    = Canvas(bmp)
        val p    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = tint
            style     = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val pf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.FILL }
        val cx = size / 2f; val cy = size / 2f
        val s  = size * 0.28f

        when (iconType) {
            ICON_HEART_OUTLINE, ICON_HEART_FILLED -> {
                val path = Path().apply {
                    moveTo(cx, cy + s * 0.6f)
                    cubicTo(cx - s * 1.4f, cy - s * 0.2f, cx - s * 1.4f, cy - s * 1.1f, cx, cy - s * 0.3f)
                    cubicTo(cx + s * 1.4f, cy - s * 1.1f, cx + s * 1.4f, cy - s * 0.2f, cx, cy + s * 0.6f)
                    close()
                }
                if (iconType == ICON_HEART_FILLED) c.drawPath(path, pf) else c.drawPath(path, p)
            }
            ICON_VOLUME_ON -> {
                p.style = Paint.Style.FILL
                val spk = Path().apply {
                    moveTo(cx - s * 0.8f, cy - s * 0.5f)
                    lineTo(cx - s * 0.1f, cy - s * 0.5f)
                    lineTo(cx + s * 0.6f, cy - s * 1.1f)
                    lineTo(cx + s * 0.6f, cy + s * 1.1f)
                    lineTo(cx - s * 0.1f, cy + s * 0.5f)
                    lineTo(cx - s * 0.8f, cy + s * 0.5f)
                    close()
                }
                c.drawPath(spk, pf)
                p.style = Paint.Style.STROKE
                val r1 = s * 1.0f; val r2 = s * 1.5f
                c.drawArc(cx + s * 0.2f - r1, cy - r1, cx + s * 0.2f + r1, cy + r1, -50f, 100f, false, p)
                c.drawArc(cx + s * 0.2f - r2, cy - r2, cx + s * 0.2f + r2, cy + r2, -50f, 100f, false, p)
            }
            ICON_VOLUME_OFF -> {
                p.style = Paint.Style.FILL
                val spk = Path().apply {
                    moveTo(cx - s * 0.8f, cy - s * 0.5f)
                    lineTo(cx - s * 0.1f, cy - s * 0.5f)
                    lineTo(cx + s * 0.6f, cy - s * 1.1f)
                    lineTo(cx + s * 0.6f, cy + s * 1.1f)
                    lineTo(cx - s * 0.1f, cy + s * 0.5f)
                    lineTo(cx - s * 0.8f, cy + s * 0.5f)
                    close()
                }
                c.drawPath(spk, pf)
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(2).toFloat()
                c.drawLine(cx + s * 0.9f, cy - s * 0.8f, cx + s * 1.6f, cy + s * 0.8f, p)
                c.drawLine(cx + s * 1.6f, cy - s * 0.8f, cx + s * 0.9f, cy + s * 0.8f, p)
            }
            ICON_SHARE -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(2).toFloat()
                val path = Path().apply {
                    moveTo(cx - s, cy + s * 0.2f)
                    lineTo(cx - s, cy + s * 1.1f)
                    lineTo(cx + s, cy + s * 1.1f)
                    lineTo(cx + s, cy + s * 0.2f)
                }
                c.drawPath(path, p)
                c.drawLine(cx, cy + s * 0.6f, cx, cy - s * 0.8f, p)
                val arrowPath = Path().apply {
                    moveTo(cx - s * 0.55f, cy - s * 0.25f)
                    lineTo(cx, cy - s * 0.85f)
                    lineTo(cx + s * 0.55f, cy - s * 0.25f)
                }
                c.drawPath(arrowPath, p)
            }
        }
        iv.setImageBitmap(bmp)
    }

    private fun loadAvatarAsync(url: String) {
        if (url.isEmpty()) return
        Thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                conn.disconnect()
                mainHandler.post {
                    avatarView.setImageBitmap(bmp)
                    avatarView.clipToOutline = true
                }
            } catch (_: Exception) {}
        }.start()
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private fun buildLoadingView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val spinner = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF6600"); style = Paint.Style.STROKE
                strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND
            }
            private var angle = 0f
            private val run = object : Runnable {
                override fun run() { angle = (angle + 6f) % 360f; invalidate(); postDelayed(this, 16) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx * 0.7f
                c.drawArc(cx - r, cy - r, cx + r, cy + r, angle, 260f, false, paint)
            }
        }
        frame.addView(spinner, FrameLayout.LayoutParams(dp(52), dp(52)).also {
            it.gravity = Gravity.CENTER
        })
        return frame
    }

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val ll    = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
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
                val cx = width / 2f; val cy = height / 2f; val r = cx * 0.7f
                c.drawArc(cx - r, cy - r, cx + r, cy + r, angle, 200f, false, paint)
            }
        }
        ll.addView(spinner, lp(dp(52), dp(52)).also { it.bottomMargin = dp(16) })
        ll.addView(TextView(context).apply {
            text = "Sem ligação à internet"; textSize = 15f
            setTextColor(Color.GRAY); gravity = Gravity.CENTER
        }, lp(WRAP_CONTENT, WRAP_CONTENT))
        val btn = TextView(context).apply {
            text = "Tentar novamente"; textSize = 14f
            setTextColor(Color.parseColor("#FF6600")); gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        btn.setOnClickListener { hideNoNet(); checkNetAndLoad() }
        ll.addView(btn, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.topMargin = dp(12) })
        frame.addView(ll, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER
        })
        return frame
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildBoldLabel(text: String) = TextView(context).apply {
        this.text = text; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); setShadowLayer(6f, 1f, 1f, Color.BLACK)
    }

    private fun buildSmallLabel(text: String) = TextView(context).apply {
        this.text = text; textSize = 13f; maxLines = 2
        setTextColor(Color.WHITE); setShadowLayer(6f, 1f, 1f, Color.Black)
    }

    private fun buildLabel(text: String) = TextView(context).apply {
        this.text = text; textSize = 12f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setShadowLayer(6f, 1f, 1f, Color.BLACK)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h)

    fun onDestroy() { scraperWeb.destroy(); playerWeb.destroy() }

    companion object {
        const val ICON_HEART_OUTLINE = 0
        const val ICON_HEART_FILLED  = 1
        const val ICON_VOLUME_ON     = 2
        const val ICON_VOLUME_OFF    = 3
        const val ICON_SHARE         = 4
    }
}