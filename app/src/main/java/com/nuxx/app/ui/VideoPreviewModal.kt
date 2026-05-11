package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

    private fun dp(ctx: Context, v: Int) =
        (v * ctx.resources.displayMetrics.density).toInt()

    private fun fixEnc(raw: String): String {
        return try {
            val bytes   = raw.toByteArray(Charsets.ISO_8859_1)
            val decoded = String(bytes, Charsets.UTF_8)
            if (decoded.any { it.code > 127 } || raw.none { it.code > 127 }) decoded else raw
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
html,body{width:100%;height:100%;background:#000;overflow:hidden;font-family:-apple-system,Roboto,Arial,sans-serif;}
video{position:absolute;inset:0;width:100%;height:100%;display:block;object-fit:contain;transition:filter .3s;pointer-events:none;}
body.ui video{filter:brightness(.6);}body.ov video{filter:brightness(.35);}
.spin-wrap{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;pointer-events:none;z-index:5;}
.spin-wrap.h{opacity:0;}.spinner{width:36px;height:36px;border-radius:50%;border:3px solid rgba(255,255,255,.18);border-top-color:rgba(255,255,255,.85);animation:spin .8s linear infinite;}
@keyframes spin{to{transform:rotate(360deg);}}
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
.ib.sa img{filter:invert(1) sepia(1) saturate(5) hue-rotate(80deg);}
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
::cue{background:rgba(0,0,0,.7);color:#fff;font-size:16px;}
</style></head>
<body>
<video id="vid" src="$escaped" autoplay playsinline webkit-playsinline preload="auto"></video>
<div class="spin-wrap" id="sw"><div class="spinner"></div></div>
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
const ${'$'}=id=>document.getElementById(id);
const body=document.body,vid=${'$'}('vid'),sw=${'$'}('sw'),
  pl=${'$'}('pl'),pi=${'$'}('pi'),pw=${'$'}('pw'),pf=${'$'}('pf'),pb=${'$'}('pb'),pt=${'$'}('pt'),ct=${'$'}('ct'),dt=${'$'}('dt'),
  volBtn=${'$'}('volBtn'),volIcon=${'$'}('volIcon'),bk=${'$'}('bk'),fw=${'$'}('fw'),sb=${'$'}('sb'),fs=${'$'}('fs'),fi=${'$'}('fi'),
  ovVol=${'$'}('ovVol'),ovc1=${'$'}('ovc1'),ovVV=${'$'}('ovVV'),ovVS=${'$'}('ovVS'),
  ovSpd=${'$'}('ovSpd'),ovc2=${'$'}('ovc2'),ovSV=${'$'}('ovSV'),spdList=${'$'}('spdList');
const ICO={play:'file:///android_asset/icons/svg/play_arrow.svg',pause:'file:///android_asset/icons/svg/pause.svg',
  vu:'file:///android_asset/icons/svg/volume_up.svg',vd:'file:///android_asset/icons/svg/volume_down.svg',
  vo:'file:///android_asset/icons/svg/volume_off.svg',fs:'file:///android_asset/icons/svg/fullscreen.svg',
  fe:'file:///android_asset/icons/svg/fullscreen_exit.svg',ck:'file:///android_asset/icons/svg/check.svg'};
const SPEEDS=[0.25,0.5,0.75,1,1.25,1.5,1.75,2,2.5,3];
let curSpd=1,curVol=100;
function fmt(s){if(!isFinite(s)||s<0)return'0:00';s=Math.floor(s);const h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sec=String(s%60).padStart(2,'0');return h?h+':'+String(m).padStart(2,'0')+':'+sec:m+':'+sec;}
function slGrad(el){const p=((+el.value-+el.min)/(+el.max-+el.min))*100;el.style.background='linear-gradient(to right,rgba(255,255,255,.95) '+p+'%,rgba(255,255,255,.22) '+p+'%)';}
vid.addEventListener('waiting',()=>sw.classList.remove('h'));
vid.addEventListener('playing',()=>sw.classList.add('h'));
vid.addEventListener('canplay',()=>sw.classList.add('h'));
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

    @SuppressLint("SetJavaScriptEnabled")
    fun show(activity: MainActivity, video: FeedVideo) {
        val ctx     = activity as Context
        val handler = Handler(Looper.getMainLooper())

        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )

        // estado de extração local ao modal
        var extracting = false

        // ── WebView ───────────────────────────────────────────────────────────
        val webView = WebView(ctx).apply {
            setBackgroundColor(Color.BLACK)
            settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccessFromFileURLs      = true
                allowUniversalAccessFromFileURLs = false
                mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort                  = true
                loadWithOverviewMode             = true
                setSupportZoom(false)
                userAgentString                  = UA
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webChromeClient = WebChromeClient()
            webViewClient   = object : WebViewClient() {}
        }

        // ── Spinner (igual ao ExibicaoView) ───────────────────────────────────
        val spinnerView = FrameLayout(ctx).apply {
            setBackgroundColor(Color.BLACK)
            var running = true
            val spinner = object : View(ctx) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                private var phase = 0f
                private val runner = object : Runnable {
                    override fun run() {
                        if (!running) return
                        phase = (phase + 3f) % 360f; invalidate(); postDelayed(this, 16)
                    }
                }
                init { post(runner) }
                fun stop() { running = false; removeCallbacks(runner) }
                override fun onDraw(c: Canvas) {
                    val cx = width / 2f; val cy = height / 2f; val em = width / 2.5f
                    val a1 = Math.toRadians(phase.toDouble())
                    val a2 = Math.toRadians((phase + 180f).toDouble())
                    val a3 = Math.toRadians((phase * 0.7f).toDouble())
                    val a4 = Math.toRadians((phase * 0.7f + 180f).toDouble())
                    paint.color = Color.argb(220, 225, 20, 98)
                    c.drawCircle(cx + (em * Math.cos(a1)).toFloat(), cy + (em * 0.5f * Math.sin(a1 * 0.5f)).toFloat(), em * 0.22f, paint)
                    paint.color = Color.argb(220, 111, 202, 220)
                    c.drawCircle(cx + (em * Math.cos(a2)).toFloat(), cy + (em * 0.5f * Math.sin(a2 * 0.5f)).toFloat(), em * 0.22f, paint)
                    paint.color = Color.argb(220, 61, 184, 143)
                    c.drawCircle(cx + (em * 0.5f * Math.cos(a3 * 0.5f)).toFloat(), cy + (em * Math.sin(a3)).toFloat(), em * 0.22f, paint)
                    paint.color = Color.argb(220, 233, 169, 32)
                    c.drawCircle(cx + (em * 0.5f * Math.cos(a4 * 0.5f)).toFloat(), cy + (em * Math.sin(a4)).toFloat(), em * 0.22f, paint)
                }
            }
            addView(spinner, FrameLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44)).also { it.gravity = Gravity.CENTER })
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) { spinner.stop() }
            })
        }

        // ── Error view ────────────────────────────────────────────────────────
        lateinit var errorView: FrameLayout

        fun extractAndPlay(url: String) {
            if (extracting) return
            extracting = true
            spinnerView.visibility = View.VISIBLE
            errorView.visibility   = View.GONE

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
                                    extracting = false
                                    spinnerView.visibility = View.GONE
                                    webView.loadDataWithBaseURL(
                                        "https://nuxxx.app", buildPlayerHtml(link),
                                        "text/html", "UTF-8", null
                                    )
                                }
                            }
                        } else {
                            if (failed.incrementAndGet() == total && !done.get()
                                && errDone.compareAndSet(false, true))
                                handler.post {
                                    extracting = false
                                    spinnerView.visibility = View.GONE
                                    errorView.visibility   = View.VISIBLE
                                }
                        }
                    } catch (_: Exception) {
                        if (failed.incrementAndGet() == total && !done.get()
                            && errDone.compareAndSet(false, true))
                            handler.post {
                                extracting = false
                                spinnerView.visibility = View.GONE
                                errorView.visibility   = View.VISIBLE
                            }
                    } finally { conn?.disconnect() }
                }
            }
        }

        errorView = FrameLayout(ctx).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            }
            col.addView(TextView(ctx).apply {
                text = "Não foi possível obter o vídeo."
                setTextColor(Color.parseColor("#99FFFFFF")); textSize = 12f; gravity = Gravity.CENTER
            })
            col.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 12)))
            col.addView(TextView(ctx).apply {
                text = "Tentar novamente"
                setTextColor(Color.parseColor("#B3FFFFFF")); textSize = 12f; gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(ctx, 8).toFloat()
                    setStroke(dp(ctx, 1), Color.parseColor("#80FFFFFF"))
                }
                setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), dp(ctx, 8))
                isClickable = true; isFocusable = true
                setOnClickListener { extractAndPlay(video.videoUrl) }
            })
            addView(col, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER
            })
        }

        // ── Player frame ──────────────────────────────────────────────────────
        val screenW = activity.resources.displayMetrics.widthPixels
        val playerH = (screenW * 9f / 16f).toInt()

        val playerFrame = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }
        playerFrame.addView(webView,     FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        playerFrame.addView(spinnerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        playerFrame.addView(errorView,   FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ── Sheet root ────────────────────────────────────────────────────────
        val sheetRoot = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
        }

        sheetRoot.addView(playerFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, playerH
        ))

        // ── Info (título + meta) ──────────────────────────────────────────────
        val infoBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 10))
            setBackgroundColor(AppTheme.bg)
        }
        infoBox.addView(TextView(ctx).apply {
            text = fixEnc(video.title)
            setTextColor(AppTheme.text); textSize = 14.5f
            setTypeface(null, Typeface.BOLD); maxLines = 3
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 6)))
        val metaRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val faviconIv = android.widget.ImageView(ctx).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
        Glide.with(ctx).load(faviconUrl(video.source)).override(dp(ctx, 16), dp(ctx, 16)).circleCrop().into(faviconIv)
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
        infoBox.addView(metaRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        sheetRoot.addView(infoBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ── Divider ───────────────────────────────────────────────────────────
        sheetRoot.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // ── Link row ──────────────────────────────────────────────────────────
        sheetRoot.addView(TextView(ctx).apply {
            text = "Link"
            textSize = 11f; setTypeface(null, Typeface.BOLD); letterSpacing = 0.04f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 4))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val linkRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(ctx, 10).toFloat()
                setColor(Color.parseColor("#F6F6F6")); setStroke(dp(ctx, 1), Color.parseColor("#E0E0E0"))
            }
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
        }
        linkRow.addView(TextView(ctx).apply {
            text = video.videoUrl; textSize = 11.5f; maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextColor(Color.parseColor("#1C1B1F"))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val copyBtn = FrameLayout(ctx).apply {
            isClickable = true; isFocusable = true
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 4), dp(ctx, 8))
            setOnClickListener {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("link", video.videoUrl))
                activity.showSnackbarGlobal("Link copiado")
            }
        }
        copyBtn.addView(
            activity.svgImageView("icons/svg/content_copy.svg", 18, Color.parseColor("#555555")),
            FrameLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)).also { it.gravity = Gravity.CENTER }
        )
        linkRow.addView(copyBtn, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)))
        sheetRoot.addView(linkRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.leftMargin = dp(ctx, 16); it.rightMargin = dp(ctx, 16) })

        // ── Tags ──────────────────────────────────────────────────────────────
        val allTags = (video.tags + video.categories)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12)
        if (allTags.isNotEmpty()) {
            sheetRoot.addView(TextView(ctx).apply {
                text = "Tags"; textSize = 11f; setTypeface(null, Typeface.BOLD); letterSpacing = 0.04f
                setTextColor(Color.parseColor("#888888"))
                setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 6))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val tagsScroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
                setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0)
            }
            val tagsRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            allTags.forEachIndexed { i, tag ->
                tagsRow.addView(TextView(ctx).apply {
                    text = tag; textSize = 11f; setTextColor(Color.parseColor("#444444"))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(ctx, 100).toFloat()
                        setColor(Color.parseColor("#F0F0F0")); setStroke(dp(ctx, 1), Color.parseColor("#DDDDDD"))
                    }
                    setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { if (i > 0) it.leftMargin = dp(ctx, 6) })
            }
            tagsScroll.addView(tagsRow)
            sheetRoot.addView(tagsScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        // ── Divider ───────────────────────────────────────────────────────────
        sheetRoot.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.topMargin = dp(ctx, 12) })

        // ── Ações ─────────────────────────────────────────────────────────────
        data class SI(val icon: String, val label: String, val action: () -> Unit)
        listOf(
            SI("icons/svg/open_in_browser.svg", "Ver no browser") {
                dialog.dismiss()
                activity.addContentOverlay(BrowserPage(ctx, freeNavigation = true, externalUrl = video.videoUrl))
            },
            SI("icons/svg/bookmark.svg", "Guardar para ver mais tarde") {
                dialog.dismiss(); activity.showSnackbarGlobal("Guardado")
            },
            SI("icons/svg/playlist_add.svg", "Adicionar à playlist") {
                dialog.dismiss(); activity.showSnackbarGlobal("Adicionado à playlist")
            },
        ).forEach { item ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                val ok = activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                if (ok) background = activity.getDrawable(tv.resourceId)
                setOnClickListener { item.action() }
            }
            row.addView(activity.svgImageView(item.icon, 22, Color.parseColor("#555555")),
                LinearLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22)))
            row.addView(View(ctx), LinearLayout.LayoutParams(dp(ctx, 16), 1))
            row.addView(TextView(ctx).apply {
                text = item.label; setTextColor(Color.parseColor("#1C1B1F")); textSize = 15f
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            sheetRoot.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        // ── Divider + Relacionados label ──────────────────────────────────────
        sheetRoot.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        sheetRoot.addView(TextView(ctx).apply {
            text = "Relacionados"; textSize = 13f; setTypeface(null, Typeface.BOLD)
            setTextColor(AppTheme.text)
            setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 8))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ── RecyclerView de relacionados (igual ao ExibicaoView) ─────────────
        val relatedAdapter = RelatedAdapter(
            ctx     = ctx,
            activity = activity,
            onTap   = { rel ->
                dialog.dismiss()
                show(activity, rel)
            },
            onMenuTap = { rel ->
                show(activity, rel)
            }
        )

        val recycler = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter        = relatedAdapter
            itemAnimator   = null
            isNestedScrollingEnabled = false
            setBackgroundColor(AppTheme.bg)
        }
        sheetRoot.addView(recycler, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        sheetRoot.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 24)))

        // ── ScrollView wrapper ────────────────────────────────────────────────
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(sheetRoot, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        dialog.setContentView(scroll)

        dialog.setOnDismissListener {
            handler.removeCallbacksAndMessages(null)
            try { webView.stopLoading(); webView.destroy() } catch (_: Exception) {}
        }

        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let {
                val behavior = BottomSheetBehavior.from(it)
                val screenH  = activity.resources.displayMetrics.heightPixels
                it.setBackgroundColor(AppTheme.bg)
                it.layoutParams.height = screenH
                it.requestLayout()
                behavior.peekHeight    = screenH
                behavior.state         = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        dialog.show()

        // arrancar extração e relacionados depois do show
        extractAndPlay(video.videoUrl)

        thread {
            try {
                val result = FeedFetcher.fetchAll(Random.nextInt(1, 30))
                    .filter { it.videoUrl != video.videoUrl }.take(40)
                handler.post { relatedAdapter.setItems(result) }
            } catch (_: Exception) {}
        }
    }

    // ── Adapter de relacionados (idêntico ao do ExibicaoView) ─────────────────
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

        private fun fixEnc(raw: String): String {
            return try {
                val bytes   = raw.toByteArray(Charsets.ISO_8859_1)
                val decoded = String(bytes, Charsets.UTF_8)
                if (decoded.any { it.code > 127 } || raw.none { it.code > 127 }) decoded else raw
            } catch (_: Exception) { raw }
        }

        fun setItems(newItems: List<FeedVideo>) {
            showSkel = false; items = newItems; notifyDataSetChanged()
        }

        override fun getItemViewType(p: Int) = if (showSkel) TYPE_SKELETON else TYPE_ITEM
        override fun getItemCount() = if (showSkel) 6 else items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_SKELETON)
                object : RecyclerView.ViewHolder(buildSkeletonItem()) {}
            else
                ItemVH(buildItemView())
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ItemVH) holder.bind(items[position])
        }

        private fun buildSkeletonItem(): View {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), 0, dp(8), dp(14))
            }
            row.addView(View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                    setColor(AppTheme.thumbShimmer1)
                }
            }, LinearLayout.LayoutParams(dp(160), dp(90)))
            row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 0))
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            repeat(3) { i ->
                col.addView(View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                        setColor(AppTheme.thumbShimmer1)
                    }
                }, LinearLayout.LayoutParams(if (i == 0) ViewGroup.LayoutParams.MATCH_PARENT else dp(120 - i * 20), dp(11)))
                if (i < 2) col.addView(View(ctx), LinearLayout.LayoutParams(1, dp(5)))
            }
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }

        private fun buildItemView(): LinearLayout {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), 0, dp(4), dp(14))
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                val ok = ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                if (ok) background = ctx.getDrawable(tv.resourceId)
            }
            val thumbFrame = FrameLayout(ctx).apply {
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                    setColor(AppTheme.thumbBg)
                }
            }
            val thumb = android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; tag = "thumb"
            }
            val durBadge = TextView(ctx).apply {
                setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#CC000000"))
                }
                setPadding(dp(4), dp(1), dp(4), dp(1)); visibility = View.GONE; tag = "dur"
            }
            thumbFrame.addView(thumb, FrameLayout.LayoutParams(dp(160), dp(90)))
            thumbFrame.addView(durBadge, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.BOTTOM or Gravity.END
                it.bottomMargin = dp(4); it.rightMargin = dp(4)
            })
            row.addView(thumbFrame, LinearLayout.LayoutParams(dp(160), dp(90)))
            row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 0))
            val infoCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val titleRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
            }
            val title = TextView(ctx).apply {
                setTextColor(AppTheme.text); textSize = 13f; maxLines = 2; tag = "title"
            }
            val menuBtn = android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(6), dp(2), dp(2), dp(2))
                isClickable = true; isFocusable = true; tag = "menu"
            }
            titleRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            titleRow.addView(menuBtn, LinearLayout.LayoutParams(dp(32), dp(32)))
            val meta = TextView(ctx).apply {
                setTextColor(AppTheme.textSecondary); textSize = 11f; maxLines = 1; tag = "meta"
            }
            infoCol.addView(titleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            infoCol.addView(View(ctx), LinearLayout.LayoutParams(1, dp(4)))
            infoCol.addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }

        inner class ItemVH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
            fun bind(v: FeedVideo) {
                val thumb   = root.findViewWithTag<android.widget.ImageView>("thumb")
                val title   = root.findViewWithTag<TextView>("title")
                val meta    = root.findViewWithTag<TextView>("meta")
                val dur     = root.findViewWithTag<TextView>("dur")
                val menuBtn = root.findViewWithTag<android.widget.ImageView>("menu")
                title.text = fixEnc(v.title)
                meta.text  = buildString {
                    append(v.source.label)
                    if (v.views.isNotEmpty()) append("  ·  ${v.views} vis.")
                    if (v.duration.isNotEmpty()) append("  ·  ${v.duration}")
                }
                if (v.duration.isNotEmpty()) { dur.text = v.duration; dur.visibility = View.VISIBLE }
                else dur.visibility = View.GONE
                if (v.thumb.isNotEmpty()) {
                    Glide.with(ctx).load(
                        GlideUrl(v.thumb, LazyHeaders.Builder()
                            .addHeader("User-Agent", UA)
                            .addHeader("Referer", "https://www.google.com/").build())
                    ).override(320, 180).centerCrop().into(thumb)
                }
                try {
                    val svg = com.caverock.androidsvg.SVG.getFromAsset(ctx.assets, "icons/svg/more_vert.svg")
                    val sz  = (18 * ctx.resources.displayMetrics.density).toInt()
                    svg.documentWidth = sz.toFloat(); svg.documentHeight = sz.toFloat()
                    val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
                    svg.renderToCanvas(Canvas(bmp))
                    menuBtn.setImageBitmap(bmp); menuBtn.setColorFilter(AppTheme.iconSub)
                } catch (_: Exception) {}
                menuBtn.setOnClickListener { onMenuTap(v) }
                root.setOnClickListener { onTap(v) }
            }
        }
    }
}