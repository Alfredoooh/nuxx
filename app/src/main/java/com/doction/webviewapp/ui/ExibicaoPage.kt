// ExibicaoPage.kt
package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.doction.webviewapp.models.FeedFetcher
import com.doction.webviewapp.models.FeedVideo
import com.doction.webviewapp.models.VideoSource
import com.doction.webviewapp.theme.AppTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

class ExibicaoPage : AppCompatActivity() {

    companion object {
        private const val EXTRA_VIDEO = "extra_video"
        private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private val CONVERT_APIS = listOf(
            "https://nuxxconvert1.onrender.com",
            "https://nuxxconvert2.onrender.com",
            "https://nuxxconvert3.onrender.com",
            "https://nuxxconvert4.onrender.com",
            "https://nuxxconvert5.onrender.com",
        )

        fun start(context: Context, video: FeedVideo) {
            context.startActivity(
                Intent(context, ExibicaoPage::class.java)
                    .putExtra(EXTRA_VIDEO, video)
            )
        }
    }

    private lateinit var video: FeedVideo
    private lateinit var webView: WebView
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var sheet: CoordinatorLayout

    // Full player views
    private lateinit var playerContainer: FrameLayout
    private lateinit var fullContent: LinearLayout
    private lateinit var fullThumbIv: ImageView
    private lateinit var fullTitleTv: TextView
    private lateinit var fullArtistTv: TextView
    private lateinit var spinnerView: FrameLayout
    private lateinit var errorView: FrameLayout

    // Mini bar views
    private lateinit var miniBar: LinearLayout
    private lateinit var miniThumbIv: ImageView
    private lateinit var miniTitleTv: TextView
    private lateinit var miniPlayBtn: FrameLayout
    private lateinit var miniProgress: View

    // Related
    private val relatedList = mutableListOf<FeedVideo>()
    private lateinit var relatedAdapter: RelatedAdapter
    private lateinit var recycler: RecyclerView

    private val handler = Handler(Looper.getMainLooper())
    private var extracting = false
    private var isExpanded = true

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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

    private fun fixEncoding(raw: String): String {
        return try {
            val bytes = raw.toByteArray(Charsets.ISO_8859_1)
            val decoded = String(bytes, Charsets.UTF_8)
            if (decoded.any { it.code > 127 } || raw.none { it.code > 127 }) decoded else raw
        } catch (_: Exception) { raw }
    }

    private fun escapeHtmlAttr(s: String) = s
        .replace("&", "&amp;").replace("\"", "&quot;")
        .replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;")

    // ── buildPlayerHtml ───────────────────────────────────────────────────────
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
  <button class="ib lg" id="subsBtn"><img id="subsIcon" src="file:///android_asset/icons/svg/subtitles.svg" onerror="this.style.display='none'"/></button>
  <button class="ib lg" id="volBtn"><img id="volIcon" src="file:///android_asset/icons/svg/volume_up.svg" onerror="this.style.display='none'"/></button>
</div>
<div class="ov-wrap" id="ovVol">
  <button class="ov-cl" id="ovc1"><img src="file:///android_asset/icons/svg/close.svg" onerror="this.style.display='none'"/></button>
  <div class="ov-lbl">Volume</div><div class="ov-val" id="ovVV">100%</div>
  <input class="ov-sl" id="ovVS" type="range" min="0" max="100" step="1" value="100"/>
</div>
<div class="ov-wrap" id="ovSpd">
  <button class="ov-cl" id="ovc2"><img src="file:///android_asset/icons/svg/close.svg" onerror="this.style.display='none'"/></button>
  <div class="ov-lbl">Velocidade</div><div class="ov-val" id="ovSV">1×</div>
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
        <button class="spd" id="sb">1×</button>
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
  ovSpd=$('ovSpd'),ovc2=$('ovc2'),ovSV=$('ovSV'),spdList=$('spdList'),
  subsBtn=$('subsBtn'),subsIcon=$('subsIcon');
const ICO={play:'file:///android_asset/icons/svg/play_arrow.svg',pause:'file:///android_asset/icons/svg/pause.svg',
  vu:'file:///android_asset/icons/svg/volume_up.svg',vd:'file:///android_asset/icons/svg/volume_down.svg',
  vo:'file:///android_asset/icons/svg/volume_off.svg',fs:'file:///android_asset/icons/svg/fullscreen.svg',
  fe:'file:///android_asset/icons/svg/fullscreen_exit.svg',ck:'file:///android_asset/icons/svg/check.svg',
  so:'file:///android_asset/icons/svg/subtitles.svg',sf:'file:///android_asset/icons/svg/subtitles_off.svg'};
const SPEEDS=[0.25,0.5,0.75,1,1.25,1.5,1.75,2,2.5,3];
let curSpd=1,curVol=100,subsActive=false,subsTrack=null,_subUrl=null;
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
function vpApply(pct){pct=Math.min(100,Math.max(0,Math.round(pct)));curVol=pct;vid.volume=pct/100;vid.muted=(pct===0);ovVV.textContent=pct+'%';ovVS.value=pct;slGrad(ovVS);volIcon.src=pct===0?ICO.vo:pct<50?ICO.vd:ICO.vu;}
volBtn.addEventListener('click',e=>{e.stopPropagation();openOv(ovVol);slGrad(ovVS);});
ovVS.addEventListener('input',()=>vpApply(+ovVS.value));
ovVS.addEventListener('touchstart',e=>e.stopPropagation(),{passive:true});
vpApply(100);
function buildSpdList(){spdList.innerHTML='';SPEEDS.forEach(s=>{const lbl=(Number.isInteger(s)?s:s.toFixed(2))+'×';const el=document.createElement('div');el.className='ov-opt'+(s===curSpd?' sel':'');el.innerHTML='<span class="ov-opt-lbl">'+lbl+'</span><div class="ov-opt-ck"><img src="'+ICO.ck+'" onerror="this.style.display=\'none\'"/></div>';el.addEventListener('click',e=>{e.stopPropagation();spApply(s);buildSpdList();closeOv();});spdList.appendChild(el);});}
function spApply(s){curSpd=s;vid.playbackRate=s;const lbl=(Number.isInteger(s)?s:s.toFixed(2))+'×';ovSV.textContent=lbl;sb.textContent=lbl;}
sb.addEventListener('click',e=>{e.stopPropagation();buildSpdList();openOv(ovSpd);});spApply(1);
let _subPoll=null;
function applyEmbedded(){for(let i=0;i<vid.textTracks.length;i++)vid.textTracks[i].mode=subsActive?'showing':'hidden';}
function injectVtt(txt){if(subsTrack){try{while(subsTrack.cues&&subsTrack.cues.length)subsTrack.removeCue(subsTrack.cues[0]);}catch(_){}}if(!subsActive)return;if(!subsTrack){const t=vid.addTextTrack('subtitles','Legendas','pt');t.mode='showing';subsTrack=t;}subsTrack.mode='showing';txt.replace(/\r\n/g,'\n').split('\n\n').forEach(block=>{const lines=block.trim().split('\n');const tl=lines.find(l=>l.includes('-->'));if(!tl)return;const[s,e]=tl.split('-->').map(x=>x.trim());function ts(t){const p=t.replace(',','.').split(':');return p.length===3?+p[0]*3600+(+p[1])*60+(+p[2]):(+p[0])*60+(+p[1]);}const st=ts(s),et=ts(e);const tx=lines.slice(lines.indexOf(tl)+1).join('\n');if(!isFinite(st)||!isFinite(et)||st>=et)return;try{subsTrack.addCue(new VTTCue(st,et,tx));}catch(_){};});}
window.loadSubtitles=function(url){_subUrl=url;if(subsActive)fetch(url,{cache:'no-store'}).then(r=>r.text()).then(injectVtt).catch(_=>{});clearInterval(_subPoll);_subPoll=setInterval(()=>{if(_subUrl&&subsActive)fetch(_subUrl,{cache:'no-store'}).then(r=>r.text()).then(injectVtt).catch(_=>{});},5000);};
function applySubtitles(){subsIcon.src=subsActive?ICO.so:ICO.sf;subsBtn.classList.toggle('sa',subsActive);if(vid.textTracks.length>0){applyEmbedded();}else if(_subUrl&&subsActive){fetch(_subUrl,{cache:'no-store'}).then(r=>r.text()).then(injectVtt).catch(_=>{});}}
vid.addEventListener('loadedmetadata',()=>{if(vid.textTracks.length>0)applyEmbedded();});
subsBtn.addEventListener('click',e=>{e.stopPropagation();subsActive=!subsActive;applySubtitles();showUI();});
bk.addEventListener('click',e=>{e.stopPropagation();vid.currentTime=Math.max(0,vid.currentTime-10);});
fw.addEventListener('click',e=>{e.stopPropagation();vid.currentTime=Math.min(vid.duration||0,vid.currentTime+10);});
fs.addEventListener('click',e=>{e.stopPropagation();window.NuxxxBridge&&window.NuxxxBridge.onFullscreenToggle?window.NuxxxBridge.onFullscreenToggle():(document.fullscreenElement?document.exitFullscreen().catch(_=>{}):document.documentElement.requestFullscreen().catch(_=>{}));});
document.addEventListener('fullscreenchange',()=>{fi.src=document.fullscreenElement?ICO.fe:ICO.fs;});
window.setSystemVolume=pct=>vpApply(pct);
window.playerPause=()=>vid.pause();
window.playerPlay=()=>vid.play();
window.playerIsPlaying=()=>!vid.paused;
document.addEventListener('keydown',e=>{
  if(e.code==='Space'){e.preventDefault();tgPlay();}
  if(e.code==='ArrowLeft')vid.currentTime=Math.max(0,vid.currentTime-5);
  if(e.code==='ArrowRight')vid.currentTime=Math.min(vid.duration||0,vid.currentTime+5);
  if(e.code==='ArrowUp')vpApply(curVol+5);
  if(e.code==='ArrowDown')vpApply(curVol-5);
  if(e.code==='Escape')closeOv();
});
showUI();
})();
</script></body></html>"""
    }

    // ── onCreate ──────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // StatusBar escuro isolado desta Activity
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        window.statusBarColor = Color.BLACK

        video = intent.getParcelableExtra(EXTRA_VIDEO)!!

        val root = buildUI()
        setContentView(root)

        extractAndPlay(video.videoUrl)
        loadRelated()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.destroy()
    }

    override fun onBackPressed() {
        if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            super.onBackPressed()
        }
    }

    // ── buildUI ───────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun buildUI(): CoordinatorLayout {
        val screenW = resources.displayMetrics.widthPixels
        val playerH = (screenW * 9f / 16f).toInt()
        val miniH   = dp(68)
        val statusH = run {
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) resources.getDimensionPixelSize(resId) else dp(24)
        }

        // ── Root CoordinatorLayout ─────────────────────────────────────────
        val coordinator = CoordinatorLayout(this).apply {
            setBackgroundColor(AppTheme.bg)
            isClickable = true
            isFocusable = true
        }

        // ── Background scroll content (RecyclerView) ───────────────────────
        // ViewType 0 = header (player + info), ViewType 1 = related item, ViewType 2 = skeleton
        webView = buildWebView()
        spinnerView = buildSpinner()
        errorView   = buildErrorView()
        errorView.visibility = View.GONE

        playerContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        playerContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        playerContainer.addView(spinnerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        playerContainer.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Info box
        val infoBox = buildInfoBox()

        // Header view que vai no RecyclerView como item 0
        val headerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
            isClickable = true
            isFocusable = true
        }
        headerView.addView(View(this).apply { setBackgroundColor(Color.BLACK) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, statusH))
        headerView.addView(playerContainer,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, playerH))
        headerView.addView(infoBox,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        headerView.addView(View(this).apply { setBackgroundColor(AppTheme.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        headerView.addView(TextView(this).apply {
            text = "Relacionados"; setTextColor(AppTheme.text)
            textSize = 13.5f; setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(4))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        relatedAdapter = RelatedAdapter(
            header      = headerView,
            items       = relatedList,
            showSkeleton = true,
            onTap       = { v -> ExibicaoPage.start(this, v) },
            onMenuTap   = { v -> showVideoBottomSheet(v) }
        )

        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ExibicaoPage)
            setHasFixedSize(false)
            adapter = relatedAdapter
            itemAnimator = null
            setBackgroundColor(AppTheme.bg)
            // padding bottom para o mini bar não tapar o último item
            setPadding(0, 0, 0, miniH)
            clipToPadding = false
        }

        coordinator.addView(recycler, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ))

        // ── Bottom Sheet (player sheet) ────────────────────────────────────
        val sheetView = buildSheetView(playerH, miniH, screenW)
        val sheetParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ).apply {
            behavior = BottomSheetBehavior<View>()
        }
        coordinator.addView(sheetView, sheetParams)

        behavior = BottomSheetBehavior.from(sheetView)
        behavior.peekHeight      = miniH
        behavior.isHideable      = false
        behavior.state           = BottomSheetBehavior.STATE_EXPANDED
        behavior.isFitToContents = true
        behavior.skipCollapsed   = false

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // slideOffset: 1.0 = expandido, 0.0 = colapsado (mini bar)
                val p = slideOffset.coerceIn(0f, 1f)

                // Full content fade out ao descer
                fullContent.alpha = p

                // Mini bar fade in ao descer
                miniBar.alpha = 1f - p
                miniBar.isClickable = p < 0.5f

                // Thumbnail: encolhe e migra para canto esquerdo da mini bar
                val thumbScale = 0.22f + (p * 0.78f)
                fullThumbIv.scaleX = thumbScale
                fullThumbIv.scaleY = thumbScale

                // Recycler scroll offset para que o conteúdo acompanhe
                // (o sheet cobre o recycler, por isso não é necessário mover)
                isExpanded = p > 0.5f
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    webView.evaluateJavascript("window.playerPause&&window.playerPause()", null)
                }
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    webView.evaluateJavascript("window.playerPlay&&window.playerPlay()", null)
                }
            }
        })

        return coordinator
    }

    // ── Sheet View (full player + mini bar) ───────────────────────────────────
    private fun buildSheetView(playerH: Int, miniH: Int, screenW: Int): View {
        val sheetRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
        }

        // ── Full player layout ─────────────────────────────────────────────
        fullContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
            isClickable = true
            isFocusable = true
        }

        val statusH = run {
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) resources.getDimensionPixelSize(resId) else dp(24)
        }

        // Status bar spacer
        fullContent.addView(View(this).apply { setBackgroundColor(Color.BLACK) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, statusH))

        // Thumbnail grande (o mesmo playerContainer do WebView)
        fullThumbIv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
        }
        Glide.with(this).load(
            GlideUrl(video.thumb, LazyHeaders.Builder()
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "https://www.google.com/").build())
        ).override(screenW, (screenW * 9f / 16f).toInt()).centerCrop().into(fullThumbIv)

        // Botão back → colapsa
        val backBtn = FrameLayout(this).apply {
            isClickable = true; isFocusable = true
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        val backIv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        loadSvgInto(backIv, "icons/svg/settings/settings_back.svg", dp(22), Color.WHITE)
        backBtn.addView(backIv, FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })

        // Player container com WebView
        val playerFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerFrame.addView(playerContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        playerFrame.addView(backBtn, FrameLayout.LayoutParams(dp(42), dp(42)).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.topMargin = dp(6); it.leftMargin = dp(4)
        })

        fullContent.addView(playerFrame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, playerH))

        // Info (título, artista, meta)
        fullContent.addView(buildFullInfo(),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))

        sheetRoot.addView(fullContent, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // ── Mini bar ───────────────────────────────────────────────────────
        miniBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#242424"))
            setPadding(dp(12), 0, dp(8), 0)
            alpha = 0f
            isClickable = false
            isFocusable = false
            // tap na mini bar expande
            setOnClickListener {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        // Borda superior mini bar
        val topBorder = View(this).apply { setBackgroundColor(Color.parseColor("#2e2e2e")) }

        // Thumbnail mini
        miniThumbIv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#333"))
            }
            clipToOutline = true
        }
        Glide.with(this).load(
            GlideUrl(video.thumb, LazyHeaders.Builder()
                .addHeader("User-Agent", UA).build())
        ).override(dp(44), dp(44)).centerCrop().into(miniThumbIv)

        // Info mini
        val miniInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        miniTitleTv = TextView(this).apply {
            text = fixEncoding(video.title)
            setTextColor(Color.WHITE); textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val miniArtistTv = TextView(this).apply {
            text = video.source.label
            setTextColor(Color.parseColor("#888")); textSize = 11f; maxLines = 1
        }
        miniInfo.addView(miniTitleTv)
        miniInfo.addView(miniArtistTv)

        // Play/pause mini
        miniPlayBtn = FrameLayout(this).apply {
            isClickable = true; isFocusable = true
            setOnClickListener {
                webView.evaluateJavascript(
                    "window.playerIsPlaying&&window.playerIsPlaying()?window.playerPause():window.playerPlay();"
                , null)
            }
        }
        val miniPlayIv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        loadSvgInto(miniPlayIv, "icons/svg/pause.svg", dp(24), Color.WHITE)
        miniPlayBtn.addView(miniPlayIv, FrameLayout.LayoutParams(dp(44), dp(44)).also { it.gravity = Gravity.CENTER })

        // Skip mini
        val miniSkipBtn = FrameLayout(this).apply { isClickable = true; isFocusable = true }
        val miniSkipIv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        loadSvgInto(miniSkipIv, "icons/svg/forward_10.svg", dp(22), Color.WHITE)
        miniSkipBtn.addView(miniSkipIv, FrameLayout.LayoutParams(dp(44), dp(44)).also { it.gravity = Gravity.CENTER })
        miniSkipBtn.setOnClickListener {
            webView.evaluateJavascript("var v=document.getElementById('vid');if(v)v.currentTime=Math.min(v.duration||0,v.currentTime+10);", null)
        }

        // Progress mini
        miniProgress = View(this).apply {
            setBackgroundColor(Color.parseColor("#4fc3f7"))
        }

        miniBar.addView(miniThumbIv, LinearLayout.LayoutParams(dp(44), dp(44)))
        miniBar.addView(View(this), LinearLayout.LayoutParams(dp(10), 0))
        miniBar.addView(miniInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        miniBar.addView(miniPlayBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        miniBar.addView(miniSkipBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

        val miniBarFrame = FrameLayout(this)
        miniBarFrame.addView(topBorder, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(1)))
        miniBarFrame.addView(miniBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, miniH).also { it.topMargin = dp(1) })
        miniBarFrame.addView(miniProgress, FrameLayout.LayoutParams(0, dp(2)).also {
            it.gravity = Gravity.BOTTOM or Gravity.START
        })

        sheetRoot.addView(miniBarFrame, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, miniH + dp(1)).also {
            it.gravity = Gravity.BOTTOM
        })

        // Sync progress na mini bar
        startMiniProgressSync()

        return sheetRoot
    }

    // ── Full info (título, meta, ações) ───────────────────────────────────────
    private fun buildFullInfo(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            setBackgroundColor(AppTheme.bg)
            isClickable = true
            isFocusable = true
        }

        fullTitleTv = TextView(this).apply {
            text = fixEncoding(video.title)
            setTextColor(AppTheme.text); textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD); maxLines = 3
        }
        box.addView(fullTitleTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(View(this), LinearLayout.LayoutParams(1, dp(6)))

        // Meta row
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val faviconIv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        Glide.with(this).load(faviconUrl(video.source)).override(dp(16), dp(16)).circleCrop().into(faviconIv)
        metaRow.addView(faviconIv, LinearLayout.LayoutParams(dp(16), dp(16)))
        metaRow.addView(View(this), LinearLayout.LayoutParams(dp(6), 0))
        metaRow.addView(TextView(this).apply {
            setTextColor(AppTheme.textSecondary); textSize = 11.5f
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
        })
        box.addView(metaRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        return box
    }

    // ── buildInfoBox (no recycler header) ─────────────────────────────────────
    private fun buildInfoBox(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            setBackgroundColor(AppTheme.bg)
            isClickable = true
            isFocusable = true
        }
        val tv = TextView(this).apply {
            text = fixEncoding(video.title)
            setTextColor(AppTheme.text); textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD); maxLines = 3
        }
        box.addView(tv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(View(this), LinearLayout.LayoutParams(1, dp(6)))
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val fi = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        Glide.with(this).load(faviconUrl(video.source)).override(dp(16), dp(16)).circleCrop().into(fi)
        metaRow.addView(fi, LinearLayout.LayoutParams(dp(16), dp(16)))
        metaRow.addView(View(this), LinearLayout.LayoutParams(dp(6), 0))
        metaRow.addView(TextView(this).apply {
            setTextColor(AppTheme.textSecondary); textSize = 11.5f
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
        })
        box.addView(metaRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return box
    }

    // ── Mini progress sync ────────────────────────────────────────────────────
    private fun startMiniProgressSync() {
        val r = object : Runnable {
            override fun run() {
                webView.evaluateJavascript(
                    "(function(){var v=document.getElementById('vid');return v&&v.duration?v.currentTime+'/'+v.duration:'0/1';})()"
                ) { res ->
                    try {
                        val parts = res?.trim('"')?.split('/') ?: return@evaluateJavascript
                        val cur = parts[0].toFloatOrNull() ?: 0f
                        val dur = parts[1].toFloatOrNull() ?: 1f
                        if (dur > 0) {
                            val pct = (cur / dur).coerceIn(0f, 1f)
                            val lp = miniProgress.layoutParams as FrameLayout.LayoutParams
                            lp.width = (resources.displayMetrics.widthPixels * pct).toInt()
                            miniProgress.layoutParams = lp
                        }
                    } catch (_: Exception) {}
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(r)
    }

    // ── extractAndPlay ────────────────────────────────────────────────────────
    private fun extractAndPlay(videoUrl: String) {
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
                    val encoded = java.net.URLEncoder.encode(videoUrl, "UTF-8")
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
                                    "https://nuxxx.app",
                                    buildPlayerHtml(link),
                                    "text/html", "UTF-8", null
                                )
                            }
                        }
                    } else {
                        if (failed.incrementAndGet() == total && !done.get()
                            && errDone.compareAndSet(false, true))
                            handler.post { showError() }
                    }
                } catch (_: Exception) {
                    if (failed.incrementAndGet() == total && !done.get()
                        && errDone.compareAndSet(false, true))
                        handler.post { showError() }
                } finally { conn?.disconnect() }
            }
        }
    }

    private fun showError() {
        if (!extracting) return
        extracting = false
        spinnerView.visibility = View.GONE
        errorView.visibility   = View.VISIBLE
    }

    // ── loadRelated ───────────────────────────────────────────────────────────
    private fun loadRelated() {
        thread {
            try {
                val result = FeedFetcher.fetchAll(Random.nextInt(1, 30))
                    .filter { it.videoUrl != video.videoUrl }.take(40)
                handler.post {
                    relatedAdapter.setItems(result)
                }
            } catch (_: Exception) {}
        }
    }

    // ── showVideoBottomSheet ──────────────────────────────────────────────────
    private fun showVideoBottomSheet(v: FeedVideo) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(
            this, com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog)
        val sheetView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        sheetView.addView(TextView(this).apply {
            text = fixEncoding(v.title)
            setTextColor(AppTheme.text); textSize = 13.5f
            setTypeface(null, Typeface.BOLD); maxLines = 2
            setPadding(dp(20), dp(20), dp(20), dp(2))
        })
        sheetView.addView(TextView(this).apply {
            text = buildString {
                append(v.source.label)
                if (v.views.isNotEmpty()) append("  ·  ${v.views} vis.")
                if (v.duration.isNotEmpty()) append("  ·  ${v.duration}")
            }
            setTextColor(AppTheme.textSecondary); textSize = 11.5f
            setPadding(dp(20), 0, dp(20), dp(14))
        })
        sheetView.addView(View(this).apply { setBackgroundColor(AppTheme.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        data class SI(val icon: String, val label: String, val action: () -> Unit)
        listOf(
            SI("icons/svg/open_in_browser.svg", "Abrir vídeo") {
                dialog.dismiss()
                ExibicaoPage.start(this, v)
            }
        ).forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
                isClickable = true; isFocusable = true
                setOnClickListener { item.action() }
            }
            row.addView(View(this), LinearLayout.LayoutParams(dp(22), dp(22)))
            row.addView(View(this), LinearLayout.LayoutParams(dp(16), 1))
            row.addView(TextView(this).apply {
                text = item.label; setTextColor(AppTheme.text); textSize = 15f
            })
            sheetView.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        sheetView.addView(View(this), LinearLayout.LayoutParams(1, dp(24)))
        dialog.setContentView(sheetView); dialog.show()
    }

    // ── buildWebView ──────────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView() = WebView(this).apply {
        setBackgroundColor(Color.BLACK)
        settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true; loadWithOverviewMode = true; setSupportZoom(false)
            userAgentString = UA
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webChromeClient = WebChromeClient()
        webViewClient   = object : WebViewClient() {}
    }

    // ── buildSpinner ──────────────────────────────────────────────────────────
    private fun buildSpinner() = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
        var running = true
        val spinner = object : View(this@ExibicaoPage) {
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
        addView(spinner, FrameLayout.LayoutParams(dp(44), dp(44)).also { it.gravity = Gravity.CENTER })
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { spinner.stop() }
        })
    }

    // ── buildErrorView ────────────────────────────────────────────────────────
    private fun buildErrorView() = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
        val col = LinearLayout(this@ExibicaoPage).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        col.addView(TextView(this@ExibicaoPage).apply {
            text = "Não foi possível obter o vídeo."
            setTextColor(Color.parseColor("#99FFFFFF")); textSize = 12f; gravity = Gravity.CENTER
        })
        col.addView(View(this@ExibicaoPage), LinearLayout.LayoutParams(1, dp(12)))
        col.addView(TextView(this@ExibicaoPage).apply {
            text = "Tentar novamente"
            setTextColor(Color.parseColor("#B3FFFFFF")); textSize = 12f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#80FFFFFF"))
            }
            setPadding(dp(20), dp(8), dp(20), dp(8))
            isClickable = true; isFocusable = true
            setOnClickListener { extractAndPlay(video.videoUrl) }
        })
        addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
    }

    // ── loadSvgInto ───────────────────────────────────────────────────────────
    private fun loadSvgInto(iv: ImageView, path: String, sizePx: Int, tint: Int) {
        try {
            val svg = com.caverock.androidsvg.SVG.getFromAsset(assets, path)
            svg.documentWidth = sizePx.toFloat(); svg.documentHeight = sizePx.toFloat()
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
    }

    // ── RelatedAdapter ────────────────────────────────────────────────────────
    private inner class RelatedAdapter(
        private val header: View,
        private var items: List<FeedVideo> = emptyList(),
        private var showSkeleton: Boolean = true,
        private val onTap: (FeedVideo) -> Unit,
        private val onMenuTap: (FeedVideo) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER   = 0
        private val TYPE_SKELETON = 1
        private val TYPE_ITEM     = 2

        fun setItems(newItems: List<FeedVideo>) {
            showSkeleton = false
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int) = when {
            position == 0          -> TYPE_HEADER
            showSkeleton           -> TYPE_SKELETON
            else                   -> TYPE_ITEM
        }

        override fun getItemCount(): Int {
            return if (showSkeleton) 1 + 6 else 1 + items.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_HEADER   -> object : RecyclerView.ViewHolder(header) {}
                TYPE_SKELETON -> object : RecyclerView.ViewHolder(buildSkeletonItem()) {}
                else          -> ItemVH(buildItemView())
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ItemVH) {
                val v = items[position - 1]
                holder.bind(v)
            }
        }

        private fun buildSkeletonItem(): View {
            val row = LinearLayout(this@ExibicaoPage).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), 0, dp(8), dp(14))
                isClickable = false
            }
            row.addView(View(this@ExibicaoPage).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(AppTheme.thumbShimmer1)
                }
            }, LinearLayout.LayoutParams(dp(160), dp(90)))
            row.addView(View(this@ExibicaoPage), LinearLayout.LayoutParams(dp(10), 0))
            val col = LinearLayout(this@ExibicaoPage).apply { orientation = LinearLayout.VERTICAL }
            repeat(3) { i ->
                col.addView(View(this@ExibicaoPage).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        setColor(AppTheme.thumbShimmer1)
                    }
                }, LinearLayout.LayoutParams(if (i == 0) LinearLayout.LayoutParams.MATCH_PARENT else dp(120 - i * 20), dp(11)))
                if (i < 2) col.addView(View(this@ExibicaoPage), LinearLayout.LayoutParams(1, dp(5)))
            }
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }

        private fun buildItemView(): LinearLayout {
            val row = LinearLayout(this@ExibicaoPage).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), 0, dp(4), dp(14))
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                val ok = theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                if (ok) background = getDrawable(tv.resourceId)
            }
            val thumbFrame = FrameLayout(this@ExibicaoPage).apply {
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(AppTheme.thumbBg)
                }
            }
            val thumb = ImageView(this@ExibicaoPage).apply { scaleType = ImageView.ScaleType.CENTER_CROP; tag = "thumb" }
            val durBadge = TextView(this@ExibicaoPage).apply {
                setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#CC000000"))
                }
                setPadding(dp(4), dp(1), dp(4), dp(1)); visibility = View.GONE; tag = "dur"
            }
            thumbFrame.addView(thumb, FrameLayout.LayoutParams(dp(160), dp(90)))
            thumbFrame.addView(durBadge, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.BOTTOM or Gravity.END
                it.bottomMargin = dp(4); it.rightMargin = dp(4)
            })
            row.addView(thumbFrame, LinearLayout.LayoutParams(dp(160), dp(90)))
            row.addView(View(this@ExibicaoPage), LinearLayout.LayoutParams(dp(10), 0))
            val infoCol = LinearLayout(this@ExibicaoPage).apply { orientation = LinearLayout.VERTICAL }
            val titleRow = LinearLayout(this@ExibicaoPage).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
            }
            val title = TextView(this@ExibicaoPage).apply {
                setTextColor(AppTheme.text); textSize = 13f; maxLines = 2; tag = "title"
            }
            val menuBtn = ImageView(this@ExibicaoPage).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(6), dp(2), dp(2), dp(2))
                isClickable = true; isFocusable = true; tag = "menu"
            }
            titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            titleRow.addView(menuBtn, LinearLayout.LayoutParams(dp(32), dp(32)))
            val meta = TextView(this@ExibicaoPage).apply {
                setTextColor(AppTheme.textSecondary); textSize = 11f; maxLines = 1; tag = "meta"
            }
            infoCol.addView(titleRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            infoCol.addView(View(this@ExibicaoPage), LinearLayout.LayoutParams(1, dp(4)))
            infoCol.addView(meta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(infoCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }

        inner class ItemVH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
            fun bind(v: FeedVideo) {
                val thumb   = root.findViewWithTag<ImageView>("thumb")
                val title   = root.findViewWithTag<TextView>("title")
                val meta    = root.findViewWithTag<TextView>("meta")
                val dur     = root.findViewWithTag<TextView>("dur")
                val menuBtn = root.findViewWithTag<ImageView>("menu")

                title.text = fixEncoding(v.title)
                meta.text  = buildString {
                    append(v.source.label)
                    if (v.views.isNotEmpty()) append("  ·  ${v.views} vis.")
                    if (v.duration.isNotEmpty()) append("  ·  ${v.duration}")
                }
                if (v.duration.isNotEmpty()) { dur.text = v.duration; dur.visibility = View.VISIBLE }
                else dur.visibility = View.GONE

                if (v.thumb.isNotEmpty()) {
                    Glide.with(this@ExibicaoPage).load(
                        GlideUrl(v.thumb, LazyHeaders.Builder()
                            .addHeader("User-Agent", UA)
                            .addHeader("Referer", "https://www.google.com/").build())
                    ).override(320, 180).centerCrop().into(thumb)
                }

                loadSvgInto(menuBtn, "icons/svg/more_vert.svg", dp(18), AppTheme.iconSub)
                menuBtn.setOnClickListener { onMenuTap(v) }
                root.setOnClickListener { onTap(v) }
            }
        }
    }
}