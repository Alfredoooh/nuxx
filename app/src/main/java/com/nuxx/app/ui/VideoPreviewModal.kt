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

    // ── Encoding robusto ──────────────────────────────────────────────────────
    // Só faz re-encode se o texto parecer Latin-1 mal interpretado.
    // Nunca toca em texto com caracteres CJK ou já válido UTF-8.
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

    private fun buildPlayerHtml(videoUrl: String): String {
        val escaped = escapeHtmlAttr(videoUrl)
        return """<!DOCTYPE html>
<html lang="pt"><head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no"/>
<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
*{-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent;}
html,body{width:100%;height:100%;background:transparent;overflow:hidden;font-family:-apple-system,Roboto,Arial,sans-serif;}
body.ready{background:#000;}
body.buffering{background:transparent;}
video{position:absolute;inset:0;width:100%;height:100%;display:block;object-fit:contain;transition:opacity .25s,filter .3s;pointer-events:none;opacity:0;}
body.ready video{opacity:1;}
body.buffering video{opacity:0;}
body.ui video{filter:brightness(.6);}
body.ov video{filter:brightness(.35);}
.spin-wrap{display:none;}
.top-bar{position:absolute;top:0;left:0;right:0;display:flex;align-items:center;justify-content:flex-end;padding:12px 16px;gap:4px;opacity:0;transition:opacity .25s;pointer-events:none;z-index:20;}
body.ui .top-bar{opacity:1;pointer-events:all;}
.ctrl{position:absolute;bottom:0;left:0;right:0;opacity:0;transition:opacity .25s;pointer-events:none;z-index:20;}
body.ui .ctrl{opacity:1;pointer-events:all;}
.card{width:100%;padding:4px 14px 18px;}
.prog-wrap{width:100%;padding:10px 0 4px;cursor:pointer;position:relative;}
.prog-track{width:100%;height:3px;background:rgba(255,255,255,.35);border-radius:2px;position:relative;}
.prog-buf{position:absolute;left:0;top:0;height:100%;background:rgba(255,255,255,.42);border-radius:2px;}
.prog-fill{position:absolute;left:0;top:0;height:100%;background:#fff;border-radius:2px;}
.prog-thumb{position:absolute;top:50%;transform:translate(-50%,-50%) scale(0);width:14px;height:14px;border-radius:50%;background:#fff;transition:transform .13s;}
.prog-wrap:hover .prog-thumb,.prog-wrap:active .prog-thumb{transform:translate(-50%,-50%) scale(1);}
.brow{display:flex;align-items:center;padding:0 2px;}
.bl{display:flex;align-items:center;flex-shrink:0;}
.bc{display:flex;align-items:center;flex:1;justify-content:center;}
.br{display:flex;align-items:center;flex-shrink:0;}
.td{font-size:13px;font-weight:500;color:rgba(255,255,255,.85);white-space:nowrap;padding:0 4px;line-height:1;}
.td .sep{color:rgba(255,255,255,.4);}
.ib{background:none;border:none;color:#fff;width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;cursor:pointer;flex-shrink:0;transition:background .15s;padding:0;}
.ib:hover{background:rgba(255,255,255,.15);}
.ib img{width:24px;height:24px;filter:invert(1);}
.ib.pb img{width:32px;height:32px;}
.ib.lg img{width:28px;height:28px;}
.spd{background:none;border:none;color:#fff;font:600 13px/1 -apple-system,Roboto,Arial,sans-serif;padding:8px 10px;border-radius:8px;cursor:pointer;transition:background .15s;}
.spd:hover{background:rgba(255,255,255,.15);}
.ov-wrap{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;pointer-events:none;opacity:0;transition:opacity .22s;z-index:30;}
.ov-wrap.act{opacity:1;pointer-events:all;}
.ov-lbl{font-size:12px;font-weight:600;color:rgba(255,255,255,.6);letter-spacing:.08em;text-transform:uppercase;margin-bottom:8px;}
.ov-val{font-size:44px;font-weight:700;color:#fff;line-height:1;margin-bottom:28px;text-shadow:0 2px 16px rgba(0,0,0,.7);}
.ov-sl{-webkit-appearance:none;appearance:none;width:min(340px,72%);height:4px;border-radius:2px;outline:none;cursor:pointer;background:rgba(255,255,255,.22);}
.ov-sl::-webkit-slider-thumb{-webkit-appearance:none;width:22px;height:22px;border-radius:50%;background:#fff;margin-top:-9px;box-shadow:0 2px 10px rgba(0,0,0,.5);}
.ov-cl{position:absolute;top:14px;right:16px;background:none;border:none;cursor:pointer;display:flex;align-items:center;justify-content:center;padding:6px;border-radius:50%;transition:background .15s;}
.ov-cl:hover{background:rgba(255,255,255,.12);}
.ov-cl img{width:28px;height:28px;filter:invert(1);opacity:.75;}
.ov-spdl{display:flex;flex-direction:column;gap:4px;width:min(280px,75%);}
.ov-opt{display:flex;align-items:center;justify-content:space-between;padding:13px 18px;border-radius:12px;cursor:pointer;transition:background .15s;}
.ov-opt:hover{background:rgba(255,255,255,.1);}
.ov-opt-lbl{font-size:15px;font-weight:500;color:#fff;}
.ov-opt-ck{width:20px;height:20px;opacity:0;transition:opacity .15s;}
.ov-opt-ck img{width:20px;height:20px;filter:invert(1) sepia(1) saturate(5) hue-rotate(80deg);}
.ov-opt.sel .ov-opt-ck{opacity:1;}
</style></head>
<body>
<video id="vid" src="$escaped" autoplay playsinline webkit-playsinline preload="auto"></video>
<div class="spin-wrap" id="sw"></div>
<div class="top-bar">
  <button class="ib lg" id="volBtn"><img id="volIcon" src="file:///android_asset/icons/svg/volume_up.svg" onerror="this.style.display='none'"/></button>
</div>
<div class="ov-wrap" id="ovVol">
  <button class="ov-cl" id="ovc1"><img src="file:///android_asset/icons/svg/close.svg" onerror="this.style.display='none'"/></button>
  <div class="ov-lbl">Volume</div><div class="ov-val" id="ovVV">100%</div>
  <input class="ov-sl" id="ovVS" type="range" min="0" max="100" step="1" value="100"/>
</div>
<div class="ov-wrap" id="ovSpd">
  <button class="ov-cl" id="ovc2"><img src="file:///android_asset/icons/svg/close.svg" onerror="this.style.display='none'"/></button>
  <div class="ov-lbl">Velocidade</div><div class="ov-val" id="ovSV">1x</div>
  <div class="ov-spdl" id="spdList"></div>
</div>
<div class="ctrl" id="ctrl">
  <div class="card">
    <div class="prog-wrap" id="pw">
      <div class="prog-track">
        <div class="prog-buf" id="pb" style="width:0%"></div>
        <div class="prog-fill" id="pf" style="width:0%"></div>
        <div class="prog-thumb" id="pt" style="left:0%"></div>
      </div>
    </div>
    <div class="brow">
      <div class="bl"><div class="td"><span id="ct">0:00</span>&nbsp;<span class="sep">/</span>&nbsp;<span id="dt">0:00</span></div></div>
      <div class="bc">
        <button class="ib" id="bk"><img src="file:///android_asset/icons/svg/replay_10.svg" onerror="this.style.display='none'"/></button>
        <button class="ib pb" id="pl"><img id="pi" src="file:///android_asset/icons/svg/play_arrow.svg" onerror="this.style.display='none'"/></button>
        <button class="ib" id="fw"><img src="file:///android_asset/icons/svg/forward_10.svg" onerror="this.style.display='none'"/></button>
      </div>
      <div class="br">
        <button class="spd" id="sb">1x</button>
        <button class="ib" id="fs"><img id="fi" src="file:///android_asset/icons/svg/fullscreen.svg" onerror="this.style.display='none'"/></button>
      </div>
    </div>
  </div>
</div>
<script>
(function(){
'use strict';
const $=id=>document.getElementById(id);
const body=document.body,vid=$('vid'),sw=$('sw'),
  pl=$('pl'),pi=$('pi'),pw=$('pw'),pf=$('pf'),pb=$('pb'),pt=$('pt'),ct=$('ct'),dt=$('dt'),
  volBtn=$('volBtn'),volIcon=$('volIcon'),bk=$('bk'),fw=$('fw'),sb=$('sb'),fs=$('fs'),fi=$('fi'),
  ovVol=$('ovVol'),ovc1=$('ovc1'),ovVV=$('ovVV'),ovVS=$('ovVS'),
  ovSpd=$('ovSpd'),ovc2=$('ovc2'),ovSV=$('ovSV'),spdList=$('spdList');
const ICO={play:'file:///android_asset/icons/svg/play_arrow.svg',pause:'file:///android_asset/icons/svg/pause.svg',
  vu:'file:///android_asset/icons/svg/volume_up.svg',vd:'file:///android_asset/icons/svg/volume_down.svg',
  vo:'file:///android_asset/icons/svg/volume_off.svg',fs:'file:///android_asset/icons/svg/fullscreen.svg',
  fe:'file:///android_asset/icons/svg/fullscreen_exit.svg',ck:'file:///android_asset/icons/svg/check.svg'};
const SPEEDS=[0.25,0.5,0.75,1,1.25,1.5,1.75,2,2.5,3];
let curSpd=1,curVol=100;

function send(name){
  try { if (window.Android && typeof Android[name] === 'function') Android[name](); } catch(_){}
}

function fmt(s){if(!isFinite(s)||s<0)return'0:00';s=Math.floor(s);const h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sec=String(s%60).padStart(2,'0');return h?h+':'+String(m).padStart(2,'0')+':'+sec:m+':'+sec;}
function slGrad(el){const p=((+el.value-+el.min)/(+el.max-+el.min))*100;el.style.background='linear-gradient(to right,rgba(255,255,255,.95) '+p+'%,rgba(255,255,255,.22) '+p+'%)';}

send('onLoading');

vid.addEventListener('waiting',()=>{
  sw.classList.remove('h');
  body.classList.add('buffering');
  body.classList.remove('ready');
  send('onBuffering');
});
vid.addEventListener('playing',()=>{
  sw.classList.add('h');
  body.classList.add('ready');
  body.classList.remove('buffering');
  send('onReady');
});
vid.addEventListener('canplay',()=>{
  sw.classList.add('h');
  body.classList.add('ready');
  body.classList.remove('buffering');
  send('onReady');
});

const allOvs=[ovVol,ovSpd];
let uiV=false,hideT=null,dragging=false;
function anyOpen(){return allOvs.some(o=>o.classList.contains('act'));}
function openOv(w){allOvs.forEach(o=>o.classList.remove('act'));body.classList.add('ov');w.classList.add('act');clearTimeout(hideT);}
function closeOv(){allOvs.forEach(o=>o.classList.remove('act'));body.classList.remove('ov');if(uiV)resetHT();}
ovc1.addEventListener('click',e=>{e.stopPropagation();closeOv();});
ovc2.addEventListener('click',e=>{e.stopPropagation();closeOv();});
function showUI(){uiV=true;body.classList.add('ui');resetHT();}
function resetHT(){clearTimeout(hideT);if(!anyOpen()&&!dragging)hideT=setTimeout(()=>{uiV=false;body.classList.remove('ui');closeOv();},3500);}
document.addEventListener('click',e=>{
  if(e.target.closest('.ctrl')||e.target.closest('.ov-wrap')||e.target.closest('.top-bar'))return;
  if(uiV){clearTimeout(hideT);uiV=false;body.classList.remove('ui');closeOv();}else showUI();
});
function setPlaying(p){pi.src=p?ICO.pause:ICO.play;if(!p){clearTimeout(hideT);showUI();}}
function tgPlay(){vid.paused?vid.play():vid.pause();}
pl.addEventListener('click',e=>{e.stopPropagation();tgPlay();});
vid.addEventListener('play',()=>setPlaying(true));
vid.addEventListener('pause',()=>setPlaying(false));
vid.addEventListener('ended',()=>setPlaying(false));
function setPct(p){pf.style.width=p+'%';pt.style.left=p+'%';}
vid.addEventListener('loadedmetadata',()=>dt.textContent=fmt(vid.duration));
vid.addEventListener('timeupdate',()=>{if(!seeking){const p=vid.duration?(vid.currentTime/vid.duration)*100:0;setPct(p);}ct.textContent=fmt(vid.currentTime);});
vid.addEventListener('progress',()=>{try{if(vid.buffered.length&&vid.duration)pb.style.width=(vid.buffered.end(vid.buffered.length-1)/vid.duration*100)+'%';}catch(_){}});
let seeking=false;
function seekTo(cx){const r=pw.getBoundingClientRect(),p=Math.min(1,Math.max(0,(cx-r.left)/r.width));if(vid.duration)vid.currentTime=p*vid.duration;setPct(p*100);}
pw.addEventListener('touchstart',e=>{e.stopPropagation();seeking=true;dragging=true;clearTimeout(hideT);seekTo(e.touches[0].clientX);},{passive:true});
window.addEventListener('touchmove',e=>{if(seeking)seekTo(e.touches[0].clientX);},{passive:true});
window.addEventListener('touchend',()=>{if(seeking){seeking=false;dragging=false;resetHT();}});
pw.addEventListener('mousedown',e=>{e.stopPropagation();seeking=true;dragging=true;clearTimeout(hideT);seekTo(e.clientX);});
window.addEventListener('mousemove',e=>{if(seeking)seekTo(e.clientX);});
window.addEventListener('mouseup',()=>{if(seeking){seeking=false;dragging=false;resetHT();}});
bk.addEventListener('click',e=>{e.stopPropagation();vid.currentTime=Math.max(0,(vid.currentTime||0)-10);});
fw.addEventListener('click',e=>{e.stopPropagation();vid.currentTime=Math.min(vid.duration||0,(vid.currentTime||0)+10);});
volBtn.addEventListener('click',e=>{e.stopPropagation();openOv(ovVol);slGrad(ovVS);});
ovVS.addEventListener('input',()=>{curVol=+ovVS.value;vid.volume=curVol/100;ovVV.textContent=curVol+'%';slGrad(ovVS);volIcon.src=curVol===0?ICO.vo:curVol<50?ICO.vd:ICO.vu;});
SPEEDS.forEach(sp=>{
  const opt=document.createElement('div');opt.className='ov-opt'+(sp===1?' sel':'');
  const lbl=document.createElement('div');lbl.className='ov-opt-lbl';lbl.textContent=sp+'x';
  const ck=document.createElement('div');ck.className='ov-opt-ck';
  const ci=document.createElement('img');ci.src=ICO.ck;ck.appendChild(ci);
  opt.appendChild(lbl);opt.appendChild(ck);
  opt.addEventListener('click',e=>{e.stopPropagation();curSpd=sp;vid.playbackRate=sp;ovSV.textContent=sp+'x';sb.textContent=sp+'x';
    document.querySelectorAll('.ov-opt').forEach(o=>o.classList.remove('sel'));opt.classList.add('sel');closeOv();});
  spdList.appendChild(opt);
});
sb.addEventListener('click',e=>{e.stopPropagation();openOv(ovSpd);});
let isFull=false;
function enterFs(){if(vid.requestFullscreen)vid.requestFullscreen();else if(vid.webkitRequestFullscreen)vid.webkitRequestFullscreen();isFull=true;fi.src=ICO.fe;}
function exitFs(){if(document.exitFullscreen)document.exitFullscreen();else if(document.webkitExitFullscreen)document.webkitExitFullscreen();isFull=false;fi.src=ICO.fs;}
fs.addEventListener('click',e=>{e.stopPropagation();isFull?exitFs():enterFs();});
document.addEventListener('fullscreenchange',()=>{if(!document.fullscreenElement){isFull=false;fi.src=ICO.fs;}});
window.playerPause=()=>vid.pause();
window.playerPlay=()=>vid.play();
window.playerIsPlaying=()=>!vid.paused;
})();
</script>
</body></html>"""
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun show(activity: MainActivity, video: FeedVideo) {
        val ctx     = activity as Context
        val handler = Handler(Looper.getMainLooper())

        thread { HistoryManager.addToHistory(ctx, video) }

        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )

        val extracting = booleanArrayOf(false)

        val webView = WebView(ctx).apply {
            setBackgroundColor(Color.TRANSPARENT)
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
            webViewClient = object : WebViewClient() {}
        }

        val thumbPreview = android.widget.ImageView(ctx).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            visibility = View.VISIBLE
        }
        if (video.thumb.isNotEmpty()) {
            com.bumptech.glide.Glide.with(ctx)
                .load(GlideUrl(video.thumb, LazyHeaders.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "https://www.google.com/").build()))
                .centerCrop()
                .into(thumbPreview)
        }

        val bridge = object {
            @JavascriptInterface
            fun onLoading() {
                handler.post {
                    thumbPreview.visibility = View.VISIBLE
                }
            }

            @JavascriptInterface
            fun onBuffering() {
                handler.post {
                    thumbPreview.visibility = View.VISIBLE
                }
            }

            @JavascriptInterface
            fun onReady() {
                handler.post {
                    thumbPreview.visibility = View.GONE
                }
            }
        }
        webView.addJavascriptInterface(bridge, "Android")

        lateinit var errorView: FrameLayout

        fun extractAndPlay(url: String) {
            if (extracting[0]) return
            extracting[0] = true
            errorView.visibility = View.GONE
            thumbPreview.visibility = View.VISIBLE

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
                            connectTimeout = 15_000
                            readTimeout = 90_000
                            requestMethod = "GET"
                        }
                        if (conn.responseCode == 200) {
                            val body = conn.inputStream.bufferedReader().readText()
                            val link = org.json.JSONObject(body).optString("link", "")
                            if (link.isNotEmpty() && done.compareAndSet(false, true)) {
                                handler.post {
                                    extracting[0] = false
                                    thumbPreview.visibility = View.VISIBLE
                                    webView.loadDataWithBaseURL(
                                        "https://nuxxx.app",
                                        buildPlayerHtml(link),
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
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
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            col.addView(TextView(ctx).apply {
                text = "Não foi possível obter o vídeo."
                setTextColor(Color.parseColor("#99FFFFFF"))
                textSize = 12f
                gravity = Gravity.CENTER
            })
            col.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 12)))
            col.addView(TextView(ctx).apply {
                text = "Tentar novamente"
                setTextColor(Color.parseColor("#B3FFFFFF"))
                textSize = 12f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(ctx, 8).toFloat()
                    setStroke(dp(ctx, 1), Color.parseColor("#80FFFFFF"))
                }
                setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), dp(ctx, 8))
                isClickable = true
                isFocusable = true
                setOnClickListener { extractAndPlay(video.videoUrl) }
            })
            addView(col, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER })
        }

        val screenW = activity.resources.displayMetrics.widthPixels
        val screenH = activity.resources.displayMetrics.heightPixels
        val playerH = (screenW * 9f / 16f).toInt()

        // Player frame: thumb nativo + webview + error
        val playerFrame = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }
        playerFrame.addView(thumbPreview, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        playerFrame.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        playerFrame.addView(errorView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Handle bar / info
        val fixedInfo = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dp(ctx, 10).toFloat(), dp(ctx, 10).toFloat(),
                    dp(ctx, 10).toFloat(), dp(ctx, 10).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setColor(AppTheme.bg)
            }
        }

        val handleBar = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }
        fixedInfo.addView(handleBar, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(ctx, 12)
            it.bottomMargin = dp(ctx, 12)
        })

        fixedInfo.addView(TextView(ctx).apply {
            text = fixEnc(video.title)
            setTextColor(AppTheme.text)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            maxLines = 3
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        fixedInfo.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 6)))

        val metaRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val faviconIv = android.widget.ImageView(ctx).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(ctx).load(faviconUrl(video.source))
            .override(dp(ctx, 16), dp(ctx, 16)).circleCrop().into(faviconIv)
        metaRow.addView(faviconIv, LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)))
        metaRow.addView(View(ctx), LinearLayout.LayoutParams(dp(ctx, 6), 0))
        metaRow.addView(TextView(ctx).apply {
            setTextColor(AppTheme.textSecondary)
            textSize = 11.5f
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
        })
        fixedInfo.addView(metaRow)

        // ── Ações ─────────────────────────────────────────────────────────────
        val actionsScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        }
        val actionsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(ctx, 50).toFloat()
                    setColor(Color.parseColor("#F2F2F2"))
                    setStroke(dp(ctx, 1), Color.parseColor("#E0E0E0"))
                }
                setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
                isClickable = true
                isFocusable = true
            }
            val iconView = android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(Color.parseColor("#606060"))
            }
            try {
                val svg = com.caverock.androidsvg.SVG.getFromAsset(ctx.assets, item.ico)
                val sz  = dp(ctx, 17)
                svg.documentWidth = sz.toFloat()
                svg.documentHeight = sz.toFloat()
                val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bmp))
                iconView.setImageBitmap(bmp)
            } catch (_: Exception) {}
            val lbl = TextView(ctx).apply {
                text = item.label
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#606060"))
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
        fixedInfo.addView(actionsScroll)

        fixedInfo.addView(View(ctx).apply { setBackgroundColor(Color.parseColor("#F0F0F0")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // ── Tags ──────────────────────────────────────────────────────────────
        val allTags = (video.tags + video.categories)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (allTags.isNotEmpty()) {
            val tagRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
            }
            allTags.take(3).forEachIndexed { i, tag ->
                if (i > 0) tagRow.addView(TextView(ctx).apply {
                    text = "  ·  "
                    setTextColor(Color.parseColor("#BBBBBB"))
                    textSize = 12f
                })
                tagRow.addView(TextView(ctx).apply {
                    text = "#$tag"
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 12f
                })
            }
            if (allTags.size > 3) {
                tagRow.addView(View(ctx), LinearLayout.LayoutParams(dp(ctx, 8), 0))
                tagRow.addView(TextView(ctx).apply {
                    text = "ver mais"
                    setTextColor(Color.parseColor("#1877F2"))
                    textSize = 12f
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showAllTagsSheet(activity, allTags) }
                })
            }
            fixedInfo.addView(tagRow)
            fixedInfo.addView(View(ctx).apply { setBackgroundColor(Color.parseColor("#F0F0F0")) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        // ── Relacionados ──────────────────────────────────────────────────────
        fixedInfo.addView(TextView(ctx).apply {
            text = "Relacionados"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(AppTheme.text)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 6))
        })

        val relatedAdapter = RelatedAdapter(
            ctx      = ctx,
            activity = activity,
            onTap    = { rel -> dialog.dismiss(); show(activity, rel) },
            onMenuTap = { rel -> show(activity, rel) }
        )
        val recycler = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter        = relatedAdapter
            itemAnimator   = null
            isNestedScrollingEnabled = false
            setBackgroundColor(AppTheme.bg)
            setPadding(0, 0, 0, dp(ctx, 8))
            clipToPadding = false
        }
        fixedInfo.addView(recycler, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        fixedInfo.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 16)))

        val contentArea = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
        }
        contentArea.addView(fixedInfo, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        val rootContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        rootContainer.addView(playerFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, playerH))
        rootContainer.addView(contentArea, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val backBtn = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            alpha = 0.92f
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener { exitVideoOnlyMode() }
        }
        playerFrame.addView(backBtn, FrameLayout.LayoutParams(
            dp(ctx, 40),
            dp(ctx, 40)
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.topMargin = dp(ctx, 12)
            it.leftMargin = dp(ctx, 12)
        })

        var videoOnlyMode = false

        fun enterVideoOnlyMode() {
            if (videoOnlyMode) return
            videoOnlyMode = true

            contentArea.animate().alpha(0f).setDuration(180).withEndAction {
                contentArea.visibility = View.GONE
            }.start()

            backBtn.visibility = View.VISIBLE
            (playerFrame.layoutParams as LinearLayout.LayoutParams).apply {
                height = 0
                weight = 1f
                playerFrame.layoutParams = this
            }

            rootContainer.setBackgroundColor(Color.BLACK)
            playerFrame.requestLayout()
            rootContainer.requestLayout()
        }

        fun exitVideoOnlyMode() {
            if (!videoOnlyMode) return
            videoOnlyMode = false

            contentArea.visibility = View.VISIBLE
            contentArea.alpha = 0f
            contentArea.animate().alpha(1f).setDuration(180).start()

            backBtn.visibility = View.GONE
            (playerFrame.layoutParams as LinearLayout.LayoutParams).apply {
                height = playerH
                weight = 0f
                playerFrame.layoutParams = this
            }

            playerFrame.requestLayout()
            rootContainer.requestLayout()
        }

        var dragStartY = 0f
        var dragDistance = 0f
        handleBar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    dragDistance = 0f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - dragStartY).coerceAtLeast(0f)
                    dragDistance = dy

                    val scale = 1f - (dy / (screenH * 6f)).coerceAtMost(0.08f)
                    playerFrame.translationY = dy * 0.18f
                    playerFrame.scaleX = scale
                    playerFrame.scaleY = scale
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragDistance > dp(ctx, 90)) {
                        enterVideoOnlyMode()
                    } else {
                        playerFrame.animate()
                            .translationY(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(180)
                            .start()
                    }
                    true
                }

                else -> true
            }
        }

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

        // Busca mínimo 20 relacionados
        thread {
            try {
                val p1     = FeedFetcher.fetchAll(Random.nextInt(1, 20))
                    .filter { it.videoUrl != video.videoUrl }
                val result = if (p1.size >= 20) p1 else {
                    val p2 = FeedFetcher.fetchAll(Random.nextInt(1, 20))
                        .filter { it.videoUrl != video.videoUrl }
                    (p1 + p2).distinctBy { it.videoUrl }
                }
                handler.post { relatedAdapter.setItems(result.take(40)) }
            } catch (_: Exception) {}
        }
    }

    private fun applyPillActive(pill: LinearLayout, active: Boolean, ctx: Context) {
        val bg = pill.background as? GradientDrawable ?: return
        val activeColor = AppTheme.primary
        if (active) {
            bg.setColor(Color.argb(20, Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor)))
            bg.setStroke((1 * ctx.resources.displayMetrics.density).toInt(), activeColor)
            (pill.getChildAt(0) as? android.widget.ImageView)?.setColorFilter(activeColor)
            (pill.getChildAt(2) as? TextView)?.setTextColor(activeColor)
        } else {
            bg.setColor(Color.parseColor("#F2F2F2"))
            bg.setStroke((1 * ctx.resources.displayMetrics.density).toInt(), Color.parseColor("#E0E0E0"))
            (pill.getChildAt(0) as? android.widget.ImageView)?.setColorFilter(Color.parseColor("#606060"))
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
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }, LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(activity, 12)
            it.bottomMargin = dp(activity, 12)
        })
        root.addView(TextView(activity).apply {
            text = "Tags"
            setTextColor(Color.parseColor("#1C1B1F"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(activity, 20), 0, dp(activity, 20), dp(activity, 12))
        })
        val flow = FlexboxLayout(activity, tags)
        root.addView(flow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin = dp(activity, 16)
            it.rightMargin = dp(activity, 16)
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
                    text = "#$tag"
                    textSize = 12f
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

    // ── RelatedAdapter ─────────────────────────────────────────────────────────
    private class RelatedAdapter(
        private val ctx: Context,
        private val activity: MainActivity,
        private val onTap: (FeedVideo) -> Unit,
        private val onMenuTap: (FeedVideo) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<FeedVideo> = emptyList()
        private var showSkel = true

        private val TYPE_SKELETON = 0
        private val TYPE_ITEM     = 1

        private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

        private fun fixEncLocal(raw: String): String {
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

        fun setItems(newItems: List<FeedVideo>) {
            showSkel = false
            items    = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(pos: Int) = if (showSkel) TYPE_SKELETON else TYPE_ITEM
        override fun getItemCount() = if (showSkel) 5 else items.size

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): RecyclerView.ViewHolder {
            return if (vt == TYPE_SKELETON)
                object : RecyclerView.ViewHolder(buildSkel()) {}
            else
                ItemVH(buildItem())
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            if (h is ItemVH) h.bind(items[pos])
        }

        // Skeleton compacto — menos espaço vertical
        private fun buildSkel(): View {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(6), dp(12), dp(6))
                gravity = Gravity.TOP
            }
            val thumb = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(AppTheme.thumbShimmer1)
                }
            }
            row.addView(thumb, LinearLayout.LayoutParams(dp(120), dp(68)))
            row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 0))
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP }

            fun sk(w: Int, h: Int, topM: Int = 0) {
                val v = View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        setColor(AppTheme.thumbShimmer1)
                    }
                }
                col.addView(v, LinearLayout.LayoutParams(
                    if (w < 0) ViewGroup.LayoutParams.MATCH_PARENT else dp(w), dp(h)
                ).also { if (topM > 0) it.topMargin = dp(topM) })
            }

            sk(-1, 12); sk(-1, 12, 4); sk(100, 12, 4)
            sk(-1, 10, 6); sk(80, 10, 3)

            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }

        // Item compacto com descrição no lado direito
        private fun buildItem(): View {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(6), dp(12), dp(6))
                gravity = Gravity.TOP
                isClickable = true
                isFocusable = true
                val tv = android.util.TypedValue()
                if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                    background = ctx.getDrawable(tv.resourceId)
            }

            // Thumb
            val thumbFr = FrameLayout(ctx).apply {
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(AppTheme.thumbBg)
                }
            }
            val thumbIv = android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                tag = "thumb"
            }
            val durBadge = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#CC000000"))
                }
                setPadding(dp(3), dp(1), dp(3), dp(1))
                visibility = View.GONE
                tag = "dur"
            }
            thumbFr.addView(thumbIv, FrameLayout.LayoutParams(dp(120), dp(68)))
            thumbFr.addView(durBadge, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.BOTTOM or Gravity.END; it.bottomMargin = dp(3); it.rightMargin = dp(3) })
            row.addView(thumbFr, LinearLayout.LayoutParams(dp(120), dp(68)))
            row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 0))

            // Coluna direita — título + fonte + views + duração + descrição
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP }

            val titleTv = TextView(ctx).apply {
                setTextColor(AppTheme.text)
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                maxLines = 2
                setLineSpacing(0f, 1.2f)
                tag = "title"
            }
            val srcTv = TextView(ctx).apply {
                setTextColor(AppTheme.textSecondary)
                textSize = 10.5f
                maxLines = 1
                tag = "source"
            }
            val metaTv = TextView(ctx).apply {
                setTextColor(AppTheme.textSecondary)
                textSize = 10.5f
                maxLines = 1
                tag = "meta"
            }
            val descTv = TextView(ctx).apply {
                setTextColor(AppTheme.textTertiary)
                textSize = 10f
                maxLines = 2
                setLineSpacing(0f, 1.3f)
                tag = "desc"
                visibility = View.VISIBLE
            }

            col.addView(titleTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addView(View(ctx), LinearLayout.LayoutParams(1, dp(3)))
            col.addView(srcTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addView(View(ctx), LinearLayout.LayoutParams(1, dp(2)))
            col.addView(metaTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addView(View(ctx), LinearLayout.LayoutParams(1, dp(3)))
            col.addView(descTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }

        inner class ItemVH(val root: View) : RecyclerView.ViewHolder(root) {
            fun bind(v: FeedVideo) {
                val row      = root as LinearLayout
                val thumbFr  = row.getChildAt(0) as FrameLayout
                val thumbIv  = thumbFr.findViewWithTag<android.widget.ImageView>("thumb")!!
                val durBadge = thumbFr.findViewWithTag<TextView>("dur")!!
                val col      = row.getChildAt(2) as LinearLayout
                val titleTv  = col.findViewWithTag<TextView>("title")!!
                val srcTv    = col.findViewWithTag<TextView>("source")!!
                val metaTv   = col.findViewWithTag<TextView>("meta")!!
                val descTv   = col.findViewWithTag<TextView>("desc")!!

                titleTv.text = fixEncLocal(v.title)
                srcTv.text   = v.source.label

                val metaStr = buildString {
                    if (v.views.isNotEmpty()) append(v.views)
                    if (v.duration.isNotEmpty()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(v.duration)
                    }
                }
                metaTv.text = metaStr
                metaTv.visibility = if (metaStr.isNotEmpty()) View.VISIBLE else View.GONE

                if (v.duration.isNotEmpty()) {
                    durBadge.text = v.duration
                    durBadge.visibility = View.VISIBLE
                } else durBadge.visibility = View.GONE

                val desc = (v.tags + v.categories)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(5)
                    .joinToString("  ·  ")

                descTv.text = if (desc.isNotEmpty()) desc else "Sem descrição"
                descTv.visibility = View.VISIBLE

                if (v.thumb.isNotEmpty()) {
                    Glide.with(ctx)
                        .load(GlideUrl(v.thumb, LazyHeaders.Builder()
                            .addHeader("User-Agent", UA)
                            .addHeader("Referer", "https://www.google.com/").build()))
                        .override(240, 135).centerCrop().into(thumbIv)
                }

                root.setOnClickListener { onTap(v) }
                root.setOnLongClickListener { onMenuTap(v); true }
            }
        }
    }
}