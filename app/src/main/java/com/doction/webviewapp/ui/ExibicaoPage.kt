// ─── ExibicaoPage.kt ──────────────────────────────────────────────────────────
package com.doction.webviewapp.ui

import android.annotation.SuppressLint
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.models.FeedFetcher
import com.doction.webviewapp.models.FeedVideo
import com.doction.webviewapp.models.VideoSource
import com.doction.webviewapp.theme.AppTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
private val CONVERT_APIS = listOf(
    "https://nuxxconvert1.onrender.com",
    "https://nuxxconvert2.onrender.com",
    "https://nuxxconvert3.onrender.com",
    "https://nuxxconvert4.onrender.com",
    "https://nuxxconvert5.onrender.com",
)

private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

private fun referer(src: VideoSource) = when (src) {
    VideoSource.EPORNER   -> "https://www.eporner.com/"
    VideoSource.PORNHUB   -> "https://www.pornhub.com/"
    VideoSource.REDTUBE   -> "https://www.redtube.com/"
    VideoSource.YOUPORN   -> "https://www.youporn.com/"
    VideoSource.XVIDEOS   -> "https://www.xvideos.com/"
    VideoSource.XHAMSTER  -> "https://xhamster.com/"
    VideoSource.SPANKBANG -> "https://spankbang.com/"
    VideoSource.BRAVOTUBE -> "https://www.bravotube.net/"
    VideoSource.DRTUBER   -> "https://www.drtuber.com/"
    VideoSource.TXXX      -> "https://www.txxx.com/"
    VideoSource.GOTPORN   -> "https://www.gotporn.com/"
    VideoSource.PORNDIG   -> "https://www.porndig.com/"
    else                  -> "https://www.google.com/"
}

private fun faviconUrl(src: VideoSource): String {
    val domain = referer(src).removePrefix("https://").removePrefix("http://").trimEnd('/')
    return "https://www.google.com/s2/favicons?sz=32&domain=$domain"
}

private fun escapeHtmlAttr(s: String) = s
    .replace("&", "&amp;").replace("\"", "&quot;")
    .replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;")

// FIX encoding: garante UTF-8 correcto nos títulos vindos de RSS/JSON
private fun fixEncoding(raw: String): String {
    return try {
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        val decoded = String(bytes, Charsets.UTF_8)
        if (decoded.any { it.code > 127 } || raw.none { it.code > 127 }) decoded else raw
    } catch (_: Exception) { raw }
}

// ─── Player HTML ──────────────────────────────────────────────────────────────
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

// ─── FloatingMiniPlayer ───────────────────────────────────────────────────────
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class FloatingMiniPlayer(
    context: Context,
    val webView: WebView,
    val sourceView: View,           // card original no ExploreView — para container transform
    private val onExpand: () -> Unit,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler = Handler(Looper.getMainLooper())

    val miniW get() = (resources.displayMetrics.widthPixels * 0.48f).toInt()
    val miniH get() = (miniW * 9f / 16f).toInt()
    private val margin get() = activity.dp(14)
    private val navH get() = activity.dp(72)

    private val progressBar: View
    private val pauseIconIv: ImageView

    // drag state
    private var dStartX = 0f; private var dStartY = 0f
    private var vStartX = 0f; private var vStartY = 0f
    private var isDragging = false
    private var tapThreshold = 12f

    // spring animations
    private val springX: SpringAnimation
    private val springY: SpringAnimation

    init {
        elevation = activity.dp(10).toFloat()
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = activity.dp(12).toFloat()
            setColor(Color.BLACK)
        }

        // WebView
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // barra progresso
        progressBar = View(context).apply { setBackgroundColor(Color.parseColor("#E6143C")) }
        addView(progressBar, LayoutParams(0, activity.dp(3)).also { it.gravity = Gravity.BOTTOM or Gravity.START })

        // overlay
        val overlay = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#66000000")) }

        // pause/play btn
        val pauseFrame = FrameLayout(context).apply { isClickable = true; isFocusable = true }
        pauseIconIv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            tag = "mini_play_icon"
        }
        loadSvgInto(pauseIconIv, "icons/svg/pause.svg", activity.dp(26))
        pauseFrame.addView(pauseIconIv, LayoutParams(activity.dp(44), activity.dp(44)).also { it.gravity = Gravity.CENTER })
        pauseFrame.setOnClickListener {
            webView.evaluateJavascript(
                "window.playerIsPlaying&&window.playerIsPlaying()?window.playerPause():window.playerPlay();"
            ) { updateMiniIcon() }
        }

        // fechar btn
        val closeFrame = FrameLayout(context).apply { isClickable = true; isFocusable = true }
        val closeIv = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        loadSvgInto(closeIv, "icons/svg/close.svg", activity.dp(18))
        closeFrame.addView(closeIv, LayoutParams(activity.dp(32), activity.dp(32)).also { it.gravity = Gravity.CENTER })
        closeFrame.setOnClickListener { dismissWithAnim() }

        overlay.addView(pauseFrame, LayoutParams(activity.dp(52), activity.dp(52)).also { it.gravity = Gravity.CENTER })
        overlay.addView(closeFrame, LayoutParams(activity.dp(36), activity.dp(36)).also {
            it.gravity = Gravity.TOP or Gravity.END
            it.topMargin = activity.dp(4); it.rightMargin = activity.dp(4)
        })
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // toque para expandir (detectado no setupDrag)
        setupDrag()

        // spring animations para snap suave
        springX = SpringAnimation(this, DynamicAnimation.X).apply {
            spring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
        }
        springY = SpringAnimation(this, DynamicAnimation.Y).apply {
            spring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
        }

        startProgressSync()
    }

    private fun loadSvgInto(iv: ImageView, path: String, sizePx: Int) {
        try {
            val svg = com.caverock.androidsvg.SVG.getFromAsset(context.assets, path)
            svg.documentWidth = sizePx.toFloat(); svg.documentHeight = sizePx.toFloat()
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp); iv.setColorFilter(Color.WHITE)
        } catch (_: Exception) {}
    }

    private fun updateMiniIcon() {
        handler.postDelayed({
            webView.evaluateJavascript("window.playerIsPlaying&&window.playerIsPlaying()") { res ->
                val playing = res?.trim() == "true"
                loadSvgInto(pauseIconIv, if (playing) "icons/svg/pause.svg" else "icons/svg/play_arrow.svg", activity.dp(26))
            }
        }, 120)
    }

    private fun startProgressSync() {
        val r = object : Runnable {
            override fun run() {
                if (!isAttachedToWindow) return
                webView.evaluateJavascript(
                    "(function(){var v=document.getElementById('vid');return v&&v.duration?v.currentTime+'/'+v.duration:'0/1';})()"
                ) { res ->
                    try {
                        val parts = res?.trim('"')?.split('/') ?: return@evaluateJavascript
                        val cur = parts[0].toFloatOrNull() ?: 0f
                        val dur = parts[1].toFloatOrNull() ?: 1f
                        if (dur > 0) {
                            val pct = (cur / dur).coerceIn(0f, 1f)
                            val lp = progressBar.layoutParams as LayoutParams
                            lp.width = (width * pct).toInt()
                            progressBar.layoutParams = lp
                        }
                    } catch (_: Exception) {}
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(r)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    springX.cancel(); springY.cancel()
                    dStartX = ev.rawX; dStartY = ev.rawY
                    vStartX = x; vStartY = y
                    isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - dStartX; val dy = ev.rawY - dStartY
                    if (!isDragging && (abs(dx) > tapThreshold || abs(dy) > tapThreshold)) isDragging = true
                    if (isDragging) {
                        x = (vStartX + dx).coerceIn(0f, screenW() - miniW.toFloat())
                        y = (vStartY + dy).coerceIn(0f, screenH() - miniH - navH.toFloat())
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDragging) onExpand()
                    else snapToSide()
                    isDragging = false; true
                }
                else -> false
            }
        }
    }

    private fun screenW() = resources.displayMetrics.widthPixels.toFloat()
    private fun screenH() = resources.displayMetrics.heightPixels.toFloat()

    // snap apenas para esquerda ou direita, nunca para o centro
    private fun snapToSide() {
        val midX = x + miniW / 2f
        val targetX = if (midX < screenW() / 2f) margin.toFloat() else screenW() - miniW - margin
        val targetY = y.coerceIn(margin.toFloat(), screenH() - miniH - navH.toFloat())
        springX.animateToFinalPosition(targetX)
        springY.animateToFinalPosition(targetY)
    }

    fun placeDefault() {
        x = screenW() - miniW - margin
        y = screenH() - miniH - navH.toFloat()
    }

    // animação de entrada: container transform — escala do tamanho do card até mini player
    fun animateInFromCard(cardBounds: android.graphics.Rect) {
        val targetX = screenW() - miniW - margin
        val targetY = screenH() - miniH - navH.toFloat()

        val startScaleX = cardBounds.width().toFloat() / miniW
        val startScaleY = cardBounds.height().toFloat() / miniH

        x = cardBounds.left.toFloat(); y = cardBounds.top.toFloat()
        scaleX = startScaleX; scaleY = startScaleY
        alpha = 0f

        animate()
            .x(targetX).y(targetY)
            .scaleX(1f).scaleY(1f)
            .alpha(1f)
            .setDuration(380)
            .setInterpolator(DecelerateInterpolator(2.2f))
            .start()
    }

    // animação de expansão: container transform — mini player cresce até preencher ecrã
    fun animateExpand(onEnd: () -> Unit) {
        animate()
            .x(0f).y(0f)
            .scaleX(screenW() / miniW).scaleY(screenH() / miniH)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction(onEnd)
            .start()
    }

    private fun dismissWithAnim() {
        webView.evaluateJavascript("window.playerPause&&window.playerPause()", null)
        animate().alpha(0f).scaleX(0.75f).scaleY(0.75f).setDuration(200)
            .withEndAction { onClose() }.start()
    }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(miniW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(miniH, MeasureSpec.EXACTLY)
        )
    }
}

// ─── ExibicaoPage ─────────────────────────────────────────────────────────────
@SuppressLint("ViewConstructor")
class ExibicaoPage(
    context: Context,
    private val video: FeedVideo,
    private val onVideoTap: (FeedVideo, View) -> Unit,
) : FrameLayout(context) {

    private val activity = context as MainActivity
    @Volatile private var isDestroyed = false
    private val handler = Handler(Looper.getMainLooper())
    private val pendingRunnables = mutableListOf<Runnable>()

    private lateinit var webView: WebView
    private lateinit var spinnerView: FrameLayout
    private lateinit var errorView: FrameLayout
    private lateinit var recycler: RecyclerView
    private lateinit var btnDownload: FrameLayout
    private var playerContainer: FrameLayout? = null

    private val relatedList = mutableListOf<FeedVideo>()
    private lateinit var relatedAdapter: RelatedAdapter

    private var directUrl: String? = null
    private var extracting = false

    var miniPlayer: FloatingMiniPlayer? = null

    init {
        setBackgroundColor(AppTheme.bg)
        buildUI()
        animateSlideUp()
        extractAndPlay(video.videoUrl)
        loadRelated()
    }

    // ── Slide-up de baixo ─────────────────────────────────────────────────────
    private fun animateSlideUp() {
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        translationY = screenH
        alpha = 1f
        animate()
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator(2.4f))
            .start()
    }

    // ── Minimizar → FloatingMiniPlayer com container transform ────────────────
    fun minimizeToFloat(originCardBounds: android.graphics.Rect? = null) {
        if (miniPlayer != null) return

        (webView.parent as? ViewGroup)?.removeView(webView)

        val mp = FloatingMiniPlayer(
            context = context,
            webView = webView,
            sourceView = this,
            onExpand = { expandFromFloat() },
            onClose = {
                miniPlayer = null
                activity.closeVideoPlayer()
            }
        )
        miniPlayer = mp

        val root = activity.window.decorView as FrameLayout
        root.addView(mp, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        if (originCardBounds != null) mp.animateInFromCard(originCardBounds)
        else {
            mp.placeDefault()
            mp.alpha = 0f
            mp.animate().alpha(1f).setDuration(250).start()
        }

        // Anima ExibicaoPage para baixo
        animate().translationY(resources.displayMetrics.heightPixels.toFloat())
            .setDuration(320).setInterpolator(AccelerateInterpolator(2f))
            .withEndAction {
                visibility = View.GONE
                translationY = 0f
            }.start()

        activity.setStatusBarDark(false)
    }

    // ── Expandir FloatingMiniPlayer → ExibicaoPage ────────────────────────────
    fun expandFromFloat() {
        val mp = miniPlayer ?: return
        val root = activity.window.decorView as FrameLayout

        mp.animateExpand {
            root.removeView(mp)
            miniPlayer = null

            (webView.parent as? ViewGroup)?.removeView(webView)
            playerContainer?.addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))

            visibility = View.VISIBLE
            translationY = resources.displayMetrics.heightPixels.toFloat()
            animate().translationY(0f).setDuration(350)
                .setInterpolator(DecelerateInterpolator(2.4f)).start()
            activity.setStatusBarDark(true)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        activity.setStatusBarDark(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isDestroyed = true
        pendingRunnables.forEach { handler.removeCallbacks(it) }
        pendingRunnables.clear()
    }

    // ── buildUI ───────────────────────────────────────────────────────────────
    private fun buildUI() {
        val screenW = context.resources.displayMetrics.widthPixels
        val playerH = (screenW * 9f / 16f).toInt()

        val rootCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
        }

        // status bar spacer
        rootCol.addView(
            View(context).apply { setBackgroundColor(Color.BLACK) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight)
        )

        // player container
        val pContainer = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        playerContainer = pContainer
        webView = buildWebView()
        pContainer.addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        spinnerView = buildSpinner()
        pContainer.addView(spinnerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        errorView = buildErrorView()
        errorView.visibility = View.GONE
        pContainer.addView(errorView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // botão back → minimiza
        val btnBack = FrameLayout(context).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true; isFocusable = true
            setOnClickListener { minimizeToFloat() }
        }
        btnBack.addView(
            activity.svgImageView("icons/svg/settings/settings_back.svg", 22, Color.WHITE),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER }
        )
        pContainer.addView(btnBack, FrameLayout.LayoutParams(dp(42), dp(42)).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.topMargin = dp(6); it.leftMargin = dp(4)
        })

        rootCol.addView(pContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, playerH))

        // infoBox — sem translationZ para não bloquear toques
        val infoBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(8))
            setBackgroundColor(AppTheme.bg)
        }

        val titleTv = TextView(context).apply {
            text = fixEncoding(video.title)
            setTextColor(AppTheme.text); textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD); maxLines = 3
        }
        infoBox.addView(titleTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(6)))

        // meta row: favicon + fonte + views + duração
        val metaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val faviconIv = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        Glide.with(context).load(faviconUrl(video.source)).override(dp(16), dp(16)).circleCrop().into(faviconIv)
        metaRow.addView(faviconIv, LinearLayout.LayoutParams(dp(16), dp(16)))
        metaRow.addView(View(context), LinearLayout.LayoutParams(dp(6), 0))
        val metaTv = TextView(context).apply {
            setTextColor(AppTheme.textSecondary); textSize = 11.5f
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
        }
        metaRow.addView(metaTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(metaRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(12)))

        btnDownload = FrameLayout(context).apply { visibility = View.GONE; isClickable = false; isFocusable = false }
        val dlPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(50).toFloat()
                setColor(Color.parseColor("#F2F2F2"))
            }
            setPadding(dp(16), dp(10), dp(20), dp(10))
            isClickable = true; isFocusable = true
            setOnClickListener { showSnackbar("Download ainda não disponível nesta versão") }
        }
        dlPill.addView(activity.svgImageView("icons/svg/download.svg", 18, AppTheme.text), LinearLayout.LayoutParams(dp(18), dp(18)))
        dlPill.addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
        dlPill.addView(TextView(context).apply {
            text = "Descarregar"; setTextColor(AppTheme.text); textSize = 13f; setTypeface(null, Typeface.BOLD)
        })
        btnDownload.addView(dlPill, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(btnDownload, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))
        infoBox.addView(View(context).apply { setBackgroundColor(AppTheme.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        rootCol.addView(infoBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // scroll com relacionados
        val relatedScroll = NestedScrollView(context).apply {
            isFillViewport = false; clipChildren = true
            setBackgroundColor(AppTheme.bg)
        }
        val relatedCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(AppTheme.bg)
        }

        relatedCol.addView(TextView(context).apply {
            text = "Relacionados"; setTextColor(AppTheme.text)
            textSize = 13.5f; setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(4))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val skeletonBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; tag = "skeleton" }
        repeat(5) { skeletonBox.addView(buildRelatedSkeleton()) }
        relatedCol.addView(skeletonBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        relatedAdapter = RelatedAdapter(
            items = relatedList,
            onTap = { v, thumb -> onVideoTap(v, thumb) },
            onMenuTap = { v -> showVideoBottomSheet(v) }
        )

        recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(false); isNestedScrollingEnabled = false
            adapter = relatedAdapter; visibility = View.GONE; itemAnimator = null
        }

        relatedCol.addView(recycler, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        relatedCol.addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)))

        relatedScroll.addView(relatedCol, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rootCol.addView(relatedScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(rootCol, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // ── Bottom sheet ──────────────────────────────────────────────────────────
    private fun showVideoBottomSheet(video: FeedVideo) {
        val dialog = BottomSheetDialog(activity, com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog)
        val sheetView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.TRANSPARENT)
        }
        sheetView.addView(TextView(context).apply {
            text = fixEncoding(video.title)
            setTextColor(AppTheme.text); textSize = 13.5f
            setTypeface(null, Typeface.BOLD); maxLines = 2
            setPadding(dp(20), dp(20), dp(20), dp(2))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        sheetView.addView(TextView(context).apply {
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
            setTextColor(AppTheme.textSecondary); textSize = 11.5f
            setPadding(dp(20), 0, dp(20), dp(14))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        sheetView.addView(View(context).apply { setBackgroundColor(AppTheme.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        data class SI(val icon: String, val label: String, val action: () -> Unit)
        listOf(
            SI("icons/svg/bookmark.svg", "Guardar para ver mais tarde") { dialog.dismiss(); showSnackbar("Guardado") },
            SI("icons/svg/playlist_add.svg", "Adicionar à playlist") { dialog.dismiss(); showSnackbar("Adicionado à playlist") },
            SI("icons/svg/open_in_browser.svg", "Ver no browser") {
                dialog.dismiss()
                activity.addContentOverlay(BrowserPage(context, freeNavigation = true, externalUrl = video.videoUrl))
            }
        ).forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                val ok = activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                if (ok) background = activity.getDrawable(tv.resourceId)
                setOnClickListener { item.action() }
            }
            row.addView(activity.svgImageView(item.icon, 22, AppTheme.iconSub), LinearLayout.LayoutParams(dp(22), dp(22)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(16), 1))
            row.addView(TextView(context).apply {
                text = item.label; setTextColor(AppTheme.text); textSize = 15f
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            sheetView.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        sheetView.addView(View(context), LinearLayout.LayoutParams(1, dp(24)))
        dialog.setContentView(sheetView); dialog.show()
    }

    // ── Snackbar ──────────────────────────────────────────────────────────────
    private fun showSnackbar(message: String) {
        (parent as? ViewGroup)?.findViewWithTag<View>("snackbar_m3")?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val snack = FrameLayout(context).apply {
            tag = "snackbar_m3"; elevation = dp(6).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#1C1B1F"))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(context).apply {
            text = message; setTextColor(Color.parseColor("#F4EFF4")); textSize = 14f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        snack.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_VERTICAL })
        addView(snack, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM; it.bottomMargin = dp(24); it.leftMargin = dp(16); it.rightMargin = dp(16)
        })
        snack.alpha = 0f; snack.translationY = dp(20).toFloat()
        snack.animate().alpha(1f).translationY(0f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
        val r = Runnable {
            if (snack.isAttachedToWindow)
                snack.animate().alpha(0f).translationY(dp(20).toFloat()).setDuration(200)
                    .withEndAction { (snack.parent as? ViewGroup)?.removeView(snack) }.start()
        }
        pendingRunnables.add(r); handler.postDelayed(r, 3000)
    }

    // ── Extracção ─────────────────────────────────────────────────────────────
    private fun extractAndPlay(videoUrl: String) {
        if (extracting) return
        extracting = true
        spinnerView.visibility = View.VISIBLE; errorView.visibility = View.GONE; btnDownload.visibility = View.GONE

        val done = AtomicBoolean(false)
        val errDone = AtomicBoolean(false)
        val failed = AtomicInteger(0)
        val total = CONVERT_APIS.size

        CONVERT_APIS.forEach { api ->
            thread {
                var conn: java.net.HttpURLConnection? = null
                try {
                    val encoded = java.net.URLEncoder.encode(videoUrl, "UTF-8")
                    conn = (java.net.URL("$api/extract?url=$encoded").openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15_000; readTimeout = 90_000; requestMethod = "GET"
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val link = org.json.JSONObject(body).optString("link", "")
                        if (link.isNotEmpty() && done.compareAndSet(false, true)) {
                            handler.post {
                                if (isDestroyed) return@post
                                extracting = false; directUrl = link
                                spinnerView.visibility = View.GONE; btnDownload.visibility = View.VISIBLE
                                webView.loadDataWithBaseURL("https://nuxxx.app", buildPlayerHtml(link), "text/html", "UTF-8", null)
                            }
                        }
                    } else {
                        if (failed.incrementAndGet() == total && !done.get() && errDone.compareAndSet(false, true))
                            handler.post { if (!isDestroyed) showError() }
                    }
                } catch (_: Exception) {
                    if (failed.incrementAndGet() == total && !done.get() && errDone.compareAndSet(false, true))
                        handler.post { if (!isDestroyed) showError() }
                } finally { conn?.disconnect() }
            }
        }
    }

    private fun showError() {
        if (!extracting) return
        extracting = false; spinnerView.visibility = View.GONE; errorView.visibility = View.VISIBLE
    }

    // ── Related ───────────────────────────────────────────────────────────────
    private fun loadRelated() {
        thread {
            try {
                val result = FeedFetcher.fetchAll(Random.nextInt(1, 30))
                    .filter { it.videoUrl != video.videoUrl }.take(40)
                handler.post {
                    if (isDestroyed) return@post
                    findViewWithTag<LinearLayout>("skeleton")?.visibility = View.GONE
                    relatedList.clear(); relatedList.addAll(result)
                    relatedAdapter.notifyDataSetChanged(); recycler.visibility = View.VISIBLE
                }
            } catch (_: Exception) {}
        }
    }

    // ── WebView ───────────────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView() = WebView(context).apply {
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
        webViewClient = object : WebViewClient() {}
    }

    // ── Spinner ───────────────────────────────────────────────────────────────
    private fun buildSpinner() = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        var running = true
        val spinner = object : View(context) {
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
                val a1 = Math.toRadians(phase.toDouble()); val a2 = Math.toRadians((phase + 180f).toDouble())
                val a3 = Math.toRadians((phase * 0.7f).toDouble()); val a4 = Math.toRadians((phase * 0.7f + 180f).toDouble())
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

    // ── Error ─────────────────────────────────────────────────────────────────
    private fun buildErrorView() = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(activity.svgImageView("icons/svg/error.svg", 36, Color.parseColor("#99FFFFFF")),
            LinearLayout.LayoutParams(dp(36), dp(36)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))
        col.addView(TextView(context).apply {
            text = "Não foi possível obter o vídeo."
            setTextColor(Color.parseColor("#99FFFFFF")); textSize = 12f; gravity = Gravity.CENTER
        })
        col.addView(View(context), LinearLayout.LayoutParams(1, dp(12)))
        col.addView(TextView(context).apply {
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
        addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
    }

    // ── Skeleton ──────────────────────────────────────────────────────────────
    private fun buildRelatedSkeleton() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), 0, dp(8), dp(14))
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat(); setColor(AppTheme.thumbShimmer1)
            }
        }, LinearLayout.LayoutParams(dp(160), dp(90)))
        addView(View(context), LinearLayout.LayoutParams(dp(10), 0))
        val infoCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        infoCol.addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat(); setColor(AppTheme.thumbShimmer1) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(13)))
        infoCol.addView(View(context), LinearLayout.LayoutParams(1, dp(5)))
        infoCol.addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat(); setColor(AppTheme.thumbShimmer1) }
        }, LinearLayout.LayoutParams(dp(120), dp(11)))
        infoCol.addView(View(context), LinearLayout.LayoutParams(1, dp(5)))
        infoCol.addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat(); setColor(AppTheme.thumbShimmer1) }
        }, LinearLayout.LayoutParams(dp(80), dp(10)))
        addView(infoCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    // ── Destroy ───────────────────────────────────────────────────────────────
    fun destroy() {
        isDestroyed = true
        handler.removeCallbacksAndMessages(null); pendingRunnables.clear()
        miniPlayer?.let { mp -> (mp.parent as? ViewGroup)?.removeView(mp); miniPlayer = null }
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading(); webView.destroy()
    }

    private fun dp(v: Int) = activity.dp(v)
}

// ─── RelatedAdapter ───────────────────────────────────────────────────────────
private class RelatedAdapter(
    private val items: List<FeedVideo>,
    private val onTap: (FeedVideo, View) -> Unit,
    private val onMenuTap: (FeedVideo) -> Unit,
) : RecyclerView.Adapter<RelatedAdapter.VH>() {

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        lateinit var thumb: ImageView
        lateinit var title: TextView
        lateinit var meta: TextView
        lateinit var duration: TextView
        lateinit var menuBtn: View
        lateinit var favicon: ImageView
        lateinit var sourceLabel: TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), 0, dp(4), dp(14))
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            val ok = ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            if (ok) background = ctx.getDrawable(tv.resourceId)
        }

        val thumbFrame = FrameLayout(ctx).apply {
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat(); setColor(AppTheme.thumbBg)
            }
        }
        val thumb = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        thumbFrame.addView(thumb, FrameLayout.LayoutParams(dp(160), dp(90)))
        val durationBadge = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat(); setColor(Color.parseColor("#CC000000"))
            }
            setPadding(dp(4), dp(1), dp(4), dp(1)); visibility = View.GONE
        }
        thumbFrame.addView(durationBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM or Gravity.END; it.bottomMargin = dp(4); it.rightMargin = dp(4)
        })

        row.addView(thumbFrame, LinearLayout.LayoutParams(dp(160), dp(90)))
        row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 0))

        val infoCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP }

        // título + menu btn na mesma row
        val titleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val title = TextView(ctx).apply { setTextColor(AppTheme.text); textSize = 13f; maxLines = 2 }
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val menuBtn = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE; setPadding(dp(6), dp(2), dp(2), dp(2))
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            val ok = ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
            if (ok) background = ctx.getDrawable(tv.resourceId)
            try {
                val px = dp(18)
                val svg = com.caverock.androidsvg.SVG.getFromAsset(ctx.assets, "icons/svg/more_vert.svg")
                svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bmp))
                setImageBitmap(bmp); setColorFilter(AppTheme.iconSub)
            } catch (_: Exception) {}
        }
        titleRow.addView(menuBtn, LinearLayout.LayoutParams(dp(32), dp(32)))
        infoCol.addView(titleRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoCol.addView(View(ctx), LinearLayout.LayoutParams(1, dp(4)))

        // source row: favicon + label
        val sourceRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val favicon = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        sourceRow.addView(favicon, LinearLayout.LayoutParams(dp(14), dp(14)))
        sourceRow.addView(View(ctx), LinearLayout.LayoutParams(dp(4), 0))
        val sourceLabel = TextView(ctx).apply { setTextColor(AppTheme.textSecondary); textSize = 11f; maxLines = 1 }
        sourceRow.addView(sourceLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoCol.addView(sourceRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoCol.addView(View(ctx), LinearLayout.LayoutParams(1, dp(3)))

        val meta = TextView(ctx).apply { setTextColor(AppTheme.textSecondary); textSize = 11f; maxLines = 1 }
        infoCol.addView(meta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        row.addView(infoCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val vh = VH(row)
        vh.thumb = thumb; vh.title = title; vh.meta = meta
        vh.duration = durationBadge; vh.menuBtn = menuBtn
        vh.favicon = favicon; vh.sourceLabel = sourceLabel
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = items[position]; val ctx = holder.root.context
        fun dp(i: Int) = (i * ctx.resources.displayMetrics.density).toInt()

        holder.title.text = fixEncoding(v.title)
        holder.title.setTextColor(AppTheme.text)
        holder.sourceLabel.text = v.source.label
        holder.sourceLabel.setTextColor(AppTheme.textSecondary)

        Glide.with(ctx).load(faviconUrl(v.source))
            .placeholder(android.R.drawable.ic_menu_gallery)
            .override(dp(14), dp(14)).circleCrop().into(holder.favicon)

        holder.meta.text = buildString {
            if (v.views.isNotEmpty()) append("${v.views} vis.")
            if (v.duration.isNotEmpty()) append("  ·  ${v.duration}")
        }
        holder.meta.setTextColor(AppTheme.textSecondary)

        if (v.duration.isNotEmpty()) { holder.duration.text = v.duration; holder.duration.visibility = View.VISIBLE }
        else holder.duration.visibility = View.GONE

        (holder.thumb.parent as? FrameLayout)?.background?.let { (it as? GradientDrawable)?.setColor(AppTheme.thumbBg) }

        if (v.thumb.isNotEmpty()) {
            Glide.with(ctx).load(
                GlideUrl(v.thumb, LazyHeaders.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", referer(v.source)).build())
            ).placeholder(android.R.color.darker_gray).override(320, 180).centerCrop().into(holder.thumb)
        }

        holder.menuBtn.setOnClickListener { onMenuTap(v) }
        holder.root.setOnClickListener { onTap(v, holder.thumb) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        try { Glide.with(holder.thumb.context).clear(holder.thumb) } catch (_: Exception) {}
        try { Glide.with(holder.favicon.context).clear(holder.favicon) } catch (_: Exception) {}
    }

    override fun getItemCount() = items.size
}