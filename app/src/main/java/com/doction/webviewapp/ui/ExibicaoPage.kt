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
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
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
// Constantes
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

// ─────────────────────────────────────────────────────────────────────────────
// HTML do player
// Alterações:
//  • Removido botão settings e overlay de qualidade
//  • Volume movido para top-bar (onde estava settings)
//  • Controlos realinhados sem espaço vazio
//  • Legendas em tempo real via polling de VTT externo + injeção de TextTrack
//  • fullscreen API substituído por postMessage para o Kotlin gerir
//  • Escape de URL reforçado
// ─────────────────────────────────────────────────────────────────────────────

private fun escapeHtmlAttr(s: String): String =
    s.replace("&", "&amp;")
     .replace("\"", "&quot;")
     .replace("'", "&#39;")
     .replace("<", "&lt;")
     .replace(">", "&gt;")

private fun buildPlayerHtml(videoUrl: String): String {
    val escaped = escapeHtmlAttr(videoUrl)
    return """<!DOCTYPE html>
<html lang="pt">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no"/>
<title>Player</title>
<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
*{-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent;}
*:focus{outline:none;}
html,body{width:100%;height:100%;background:#000;overflow:hidden;font-family:-apple-system,Roboto,Arial,sans-serif;}
video{position:absolute;inset:0;width:100%;height:100%;display:block;object-fit:contain;transition:filter .3s;pointer-events:none;}
body.ui video{filter:brightness(.6);}
body.overlay-open video{filter:brightness(.35);}
.spinner-wrap{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;pointer-events:none;transition:opacity .3s;z-index:5;}
.spinner-wrap.hidden{opacity:0;}
.spinner{width:36px;height:36px;border-radius:50%;border:3px solid rgba(255,255,255,.18);border-top-color:rgba(255,255,255,.85);animation:spin .8s linear infinite;}
@keyframes spin{to{transform:rotate(360deg);}}
.flash{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:72px;height:72px;border-radius:50%;background:rgba(0,0,0,.45);display:flex;align-items:center;justify-content:center;opacity:0;pointer-events:none;z-index:6;}
.flash img{width:36px;height:36px;filter:invert(1);}
.flash.pop{animation:flashpop .38s ease forwards;}
@keyframes flashpop{0%{opacity:1;transform:translate(-50%,-50%) scale(.75);}55%{opacity:.85;transform:translate(-50%,-50%) scale(1.1);}100%{opacity:0;transform:translate(-50%,-50%) scale(1.25);}}
/* top-bar: back + subtitles + volume */
.top-bar{position:absolute;top:0;left:0;right:0;display:flex;align-items:center;justify-content:flex-end;padding:12px 16px;gap:4px;opacity:0;transition:opacity .25s;pointer-events:none;z-index:20;}
body.ui .top-bar{opacity:1;pointer-events:all;}
/* ctrl-slot */
.ctrl-slot{position:absolute;bottom:0;left:0;right:0;opacity:0;transition:opacity .25s;pointer-events:none;z-index:20;}
body.ui .ctrl-slot{opacity:1;pointer-events:all;}
.card-controls{width:100%;padding:4px 14px 18px;transition:opacity .22s,transform .22s;}
.card-controls.hidden{opacity:0;pointer-events:none;transform:translateY(6px);}
.prog-wrap{width:100%;padding:10px 0 4px;cursor:pointer;position:relative;}
.prog-track{width:100%;height:3px;background:rgba(255,255,255,.35);border-radius:2px;position:relative;transition:height .13s;}
.prog-wrap:hover .prog-track{height:5px;}
.prog-wrap:hover .prog-thumb{transform:translate(-50%,-50%) scale(1);}
.prog-buf{position:absolute;left:0;top:0;height:100%;background:rgba(255,255,255,.42);border-radius:2px;pointer-events:none;}
.prog-fill{position:absolute;left:0;top:0;height:100%;background:#fff;border-radius:2px;pointer-events:none;}
.prog-thumb{position:absolute;top:50%;transform:translate(-50%,-50%) scale(0);width:14px;height:14px;border-radius:50%;background:#fff;pointer-events:none;transition:transform .13s;}
.prog-tip{position:absolute;bottom:22px;background:rgba(28,28,28,.92);color:#fff;font-size:12px;font-weight:500;padding:2px 7px;border-radius:2px;pointer-events:none;display:none;transform:translateX(-50%);white-space:nowrap;}
.prog-wrap:hover .prog-tip{display:block;}
/* brow: left=time | center=back/play/fwd | right=speed+fs */
.brow{display:flex;align-items:center;padding:0 2px;}
.brow-left{display:flex;align-items:center;flex-shrink:0;}
.brow-center{display:flex;align-items:center;flex:1;justify-content:center;}
.brow-right{display:flex;align-items:center;flex-shrink:0;}
.time-display{font-size:13px;font-weight:500;color:rgba(255,255,255,.85);white-space:nowrap;padding:0 4px;line-height:1;}
.time-display .sep{color:rgba(255,255,255,.4);}
.ib{background:none;border:none;color:#fff;width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;cursor:pointer;flex-shrink:0;transition:background .15s;padding:0;}
.ib:hover{background:rgba(255,255,255,.15);}
.ib img{width:24px;height:24px;filter:invert(1);}
.ib.play-btn img{width:32px;height:32px;}
.ib.lg img{width:28px;height:28px;}
.ib.sub-active img{filter:invert(1) sepia(1) saturate(5) hue-rotate(80deg);}
.spd{background:none;border:none;color:#fff;font:600 13px/1 -apple-system,Roboto,Arial,sans-serif;padding:8px 10px;border-radius:8px;cursor:pointer;transition:background .15s;white-space:nowrap;display:flex;align-items:center;}
.spd:hover{background:rgba(255,255,255,.15);}
/* overlay de volume */
.overlay{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;pointer-events:none;opacity:0;transition:opacity .22s;z-index:30;}
.overlay.active{opacity:1;pointer-events:all;}
.ov-label{font-size:12px;font-weight:600;color:rgba(255,255,255,.6);letter-spacing:.08em;text-transform:uppercase;margin-bottom:8px;}
.ov-value{font-size:44px;font-weight:700;color:#fff;line-height:1;margin-bottom:28px;text-shadow:0 2px 16px rgba(0,0,0,.7);}
.ov-slider{-webkit-appearance:none;appearance:none;width:min(340px,72%);height:4px;border-radius:2px;outline:none;cursor:pointer;background:rgba(255,255,255,.22);}
.ov-slider::-webkit-slider-thumb{-webkit-appearance:none;width:22px;height:22px;border-radius:50%;background:#fff;margin-top:-9px;box-shadow:0 2px 10px rgba(0,0,0,.5);}
.ov-slider::-moz-range-thumb{width:22px;height:22px;border-radius:50%;background:#fff;border:none;box-shadow:0 2px 10px rgba(0,0,0,.5);}
.ov-close{position:absolute;top:14px;right:16px;background:none;border:none;cursor:pointer;display:flex;align-items:center;justify-content:center;padding:6px;border-radius:50%;transition:background .15s;}
.ov-close:hover{background:rgba(255,255,255,.12);}
.ov-close img{width:28px;height:28px;filter:invert(1);opacity:.75;}
.ov-close:hover img{opacity:1;}
/* overlay de velocidade */
.ov-spd-list{display:flex;flex-direction:column;gap:4px;width:min(280px,75%);}
.ov-opt{display:flex;align-items:center;justify-content:space-between;padding:13px 18px;border-radius:12px;cursor:pointer;transition:background .15s;}
.ov-opt:hover{background:rgba(255,255,255,.1);}
.ov-opt-label{font-size:15px;font-weight:500;color:#fff;}
.ov-opt-check{width:20px;height:20px;opacity:0;transition:opacity .15s;}
.ov-opt-check img{width:20px;height:20px;filter:invert(1) sepia(1) saturate(5) hue-rotate(80deg);}
.ov-opt.sel .ov-opt-check{opacity:1;}
/* legendas */
::cue{background:rgba(0,0,0,.7);color:#fff;font-size:16px;font-family:-apple-system,Roboto,Arial,sans-serif;}
</style>
</head>
<body>
<video id="vid" src="$escaped" autoplay playsinline webkit-playsinline preload="auto"></video>
<div class="spinner-wrap" id="spinnerWrap"><div class="spinner"></div></div>
<div class="flash" id="flash"><img id="flashIcon" src="file:///android_asset/icons/svg/play_arrow.svg" onerror="this.style.display='none'"/></div>

<!-- top-bar: legendas + volume -->
<div class="top-bar" id="topBar">
  <button class="ib lg" id="subsBtn" title="Legendas">
    <img id="subsIcon" src="file:///android_asset/icons/svg/subtitles.svg" onerror="this.style.display='none'"/>
  </button>
  <button class="ib lg" id="volBtn" title="Volume">
    <img id="volIcon" src="file:///android_asset/icons/svg/volume_up.svg" onerror="this.style.display='none'"/>
  </button>
</div>

<!-- overlay volume -->
<div class="overlay" id="ovVol">
  <button class="ov-close" id="ovVolClose"><img src="file:///android_asset/icons/svg/close.svg" onerror="this.style.display='none'"/></button>
  <div class="ov-label">Volume</div>
  <div class="ov-value" id="ovVolVal">100%</div>
  <input class="ov-slider" id="ovVolSlider" type="range" min="0" max="100" step="1" value="100"/>
</div>

<!-- overlay velocidade -->
<div class="overlay" id="ovSpd">
  <button class="ov-close" id="ovSpdClose"><img src="file:///android_asset/icons/svg/close.svg" onerror="this.style.display='none'"/></button>
  <div class="ov-label">Velocidade</div>
  <div class="ov-value" id="ovSpdVal">1×</div>
  <div class="ov-spd-list" id="spdList"></div>
</div>

<!-- controlos -->
<div class="ctrl-slot" id="ctrlSlot">
  <div class="card-controls" id="cardControls">
    <div class="prog-wrap" id="progWrap">
      <div class="prog-tip" id="progTip">0:00</div>
      <div class="prog-track">
        <div class="prog-buf" id="progBuf" style="width:0%"></div>
        <div class="prog-fill" id="progFill" style="width:0%"></div>
        <div class="prog-thumb" id="progThumb" style="left:0%"></div>
      </div>
    </div>
    <div class="brow">
      <div class="brow-left">
        <div class="time-display"><span id="curT">0:00</span>&nbsp;<span class="sep">/</span>&nbsp;<span id="durT">0:00</span></div>
      </div>
      <div class="brow-center">
        <button class="ib" id="backBtn"><img src="file:///android_asset/icons/svg/replay_10.svg" onerror="this.style.display='none'"/></button>
        <button class="ib play-btn" id="playBtn"><img id="playIcon" src="file:///android_asset/icons/svg/play_arrow.svg" onerror="this.style.display='none'"/></button>
        <button class="ib" id="fwdBtn"><img src="file:///android_asset/icons/svg/forward_10.svg" onerror="this.style.display='none'"/></button>
      </div>
      <div class="brow-right">
        <button class="spd" id="spdBtn">1×</button>
        <button class="ib" id="fsBtn" title="Ecrã inteiro">
          <img id="fsIcon" src="file:///android_asset/icons/svg/fullscreen.svg" onerror="this.style.display='none'"/>
        </button>
      </div>
    </div>
  </div>
</div>

<script>
(function(){
'use strict';
const $=id=>document.getElementById(id);
const body=document.body,vid=$('vid'),spinnerWrap=$('spinnerWrap'),
  cardControls=$('cardControls'),playBtn=$('playBtn'),playIcon=$('playIcon'),
  progWrap=$('progWrap'),progFill=$('progFill'),progBuf=$('progBuf'),
  progThumb=$('progThumb'),progTip=$('progTip'),
  curTEl=$('curT'),durTEl=$('durT'),
  volBtn=$('volBtn'),volIcon=$('volIcon'),
  backBtn=$('backBtn'),fwdBtn=$('fwdBtn'),
  spdBtn=$('spdBtn'),fsBtn=$('fsBtn'),fsIcon=$('fsIcon'),
  flash=$('flash'),flashIcon=$('flashIcon'),
  ovVol=$('ovVol'),ovVolClose=$('ovVolClose'),ovVolVal=$('ovVolVal'),ovVolSlider=$('ovVolSlider'),
  ovSpd=$('ovSpd'),ovSpdClose=$('ovSpdClose'),ovSpdVal=$('ovSpdVal'),spdList=$('spdList'),
  subsBtn=$('subsBtn'),subsIcon=$('subsIcon');

const ICO={
  play:'file:///android_asset/icons/svg/play_arrow.svg',
  pause:'file:///android_asset/icons/svg/pause.svg',
  vol_up:'file:///android_asset/icons/svg/volume_up.svg',
  vol_down:'file:///android_asset/icons/svg/volume_down.svg',
  vol_off:'file:///android_asset/icons/svg/volume_off.svg',
  fs:'file:///android_asset/icons/svg/fullscreen.svg',
  fs_exit:'file:///android_asset/icons/svg/fullscreen_exit.svg',
  check:'file:///android_asset/icons/svg/check.svg',
  subs_on:'file:///android_asset/icons/svg/subtitles.svg',
  subs_off:'file:///android_asset/icons/svg/subtitles_off.svg',
};

const SPEEDS=[0.25,0.5,0.75,1,1.25,1.5,1.75,2,2.5,3];
let curSpeed=1,curVol=100,subsActive=false,subsTrack=null;

// ── formato de tempo ──────────────────────────────────────────────────────
function fmt(s){
  if(!isFinite(s)||s<0)return'0:00';
  s=Math.floor(s);
  const h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sec=String(s%60).padStart(2,'0');
  return h?h+':'+String(m).padStart(2,'0')+':'+sec:m+':'+sec;
}

// ── slider gradient ───────────────────────────────────────────────────────
function sliderGrad(el){
  const p=((+el.value-+el.min)/(+el.max-+el.min))*100;
  el.style.background='linear-gradient(to right,rgba(255,255,255,.95) '+p+'%,rgba(255,255,255,.22) '+p+'%)';
}

// ── spinner ───────────────────────────────────────────────────────────────
vid.addEventListener('waiting',()=>spinnerWrap.classList.remove('hidden'));
vid.addEventListener('playing',()=>spinnerWrap.classList.add('hidden'));
vid.addEventListener('canplay',()=>spinnerWrap.classList.add('hidden'));

// ── overlays ──────────────────────────────────────────────────────────────
const allOverlays=[ovVol,ovSpd];
let uiVisible=false,hideTimer=null,isDragging=false;

function anyOverlayOpen(){return allOverlays.some(o=>o.classList.contains('active'));}
function openOverlay(which){
  allOverlays.forEach(o=>o.classList.remove('active'));
  body.classList.add('overlay-open');
  which.classList.add('active');
  clearTimeout(hideTimer);
}
function closeOverlay(){
  allOverlays.forEach(o=>o.classList.remove('active'));
  body.classList.remove('overlay-open');
  if(uiVisible)resetHideTimer();
}
ovVolClose.addEventListener('click',e=>{e.stopPropagation();closeOverlay();});
ovSpdClose.addEventListener('click',e=>{e.stopPropagation();closeOverlay();});

function showUI(){uiVisible=true;body.classList.add('ui');resetHideTimer();}
function resetHideTimer(){
  clearTimeout(hideTimer);
  if(!anyOverlayOpen()&&!isDragging)
    hideTimer=setTimeout(()=>{uiVisible=false;body.classList.remove('ui');closeOverlay();},3500);
}
function hideUIFully(){uiVisible=false;body.classList.remove('ui');closeOverlay();}

document.addEventListener('click',e=>{
  if(e.target.closest('.ctrl-slot')||e.target.closest('.overlay')||e.target.closest('.top-bar'))return;
  if(uiVisible){clearTimeout(hideTimer);hideUIFully();}else showUI();
});

// ── play/pause ────────────────────────────────────────────────────────────
function setPlaying(p){
  playIcon.src=p?ICO.pause:ICO.play;
  if(!p){clearTimeout(hideTimer);showUI();}
}
function togglePlay(){vid.paused?vid.play():vid.pause();}
playBtn.addEventListener('click',e=>{e.stopPropagation();togglePlay();});
vid.addEventListener('play',()=>setPlaying(true));
vid.addEventListener('pause',()=>setPlaying(false));
vid.addEventListener('ended',()=>setPlaying(false));

function doFlash(src){flashIcon.src=src;flash.classList.remove('pop');void flash.offsetWidth;flash.classList.add('pop');}

// ── progress bar ──────────────────────────────────────────────────────────
function setPct(p){progFill.style.width=p+'%';progThumb.style.left=p+'%';}
vid.addEventListener('loadedmetadata',()=>durTEl.textContent=fmt(vid.duration));
vid.addEventListener('timeupdate',()=>{
  if(!seeking){const p=vid.duration?(vid.currentTime/vid.duration)*100:0;setPct(p);}
  curTEl.textContent=fmt(vid.currentTime);
});
vid.addEventListener('progress',()=>{
  try{if(vid.buffered.length&&vid.duration)progBuf.style.width=(vid.buffered.end(vid.buffered.length-1)/vid.duration*100)+'%';}catch(_){}
});

progWrap.addEventListener('mousemove',e=>{
  const r=progWrap.getBoundingClientRect(),p=Math.min(1,Math.max(0,(e.clientX-r.left)/r.width));
  progTip.textContent=fmt(p*(vid.duration||0));
  progTip.style.left=(p*100)+'%';
});

let seeking=false;
function seekTo(cx){
  const r=progWrap.getBoundingClientRect(),p=Math.min(1,Math.max(0,(cx-r.left)/r.width));
  if(vid.duration)vid.currentTime=p*vid.duration;
  setPct(p*100);
}

// touch seek (Android WebView — usar touch events, não mouse)
progWrap.addEventListener('touchstart',e=>{
  e.stopPropagation();seeking=true;isDragging=true;clearTimeout(hideTimer);
  seekTo(e.touches[0].clientX);
},{passive:true});
window.addEventListener('touchmove',e=>{if(seeking)seekTo(e.touches[0].clientX);},{passive:true});
window.addEventListener('touchend',()=>{if(seeking){seeking=false;isDragging=false;resetHideTimer();}});

// mouse seek (para emulador/desktop)
progWrap.addEventListener('mousedown',e=>{e.stopPropagation();seeking=true;isDragging=true;clearTimeout(hideTimer);seekTo(e.clientX);});
window.addEventListener('mousemove',e=>{if(seeking)seekTo(e.clientX);});
window.addEventListener('mouseup',()=>{if(seeking){seeking=false;isDragging=false;resetHideTimer();}});

// ── volume ────────────────────────────────────────────────────────────────
function vpApply(pct){
  pct=Math.min(100,Math.max(0,Math.round(pct)));
  curVol=pct;vid.volume=pct/100;vid.muted=(pct===0);
  ovVolVal.textContent=pct+'%';
  ovVolSlider.value=pct;sliderGrad(ovVolSlider);
  volIcon.src=pct===0?ICO.vol_off:pct<50?ICO.vol_down:ICO.vol_up;
}
volBtn.addEventListener('click',e=>{e.stopPropagation();openOverlay(ovVol);sliderGrad(ovVolSlider);});
ovVolSlider.addEventListener('input',()=>{vpApply(+ovVolSlider.value);});
ovVolSlider.addEventListener('touchstart',e=>e.stopPropagation(),{passive:true});
vpApply(100);

// ── velocidade ────────────────────────────────────────────────────────────
function buildSpdList(){
  spdList.innerHTML='';
  SPEEDS.forEach(s=>{
    const lbl=(Number.isInteger(s)?s:s.toFixed(2))+'×';
    const el=document.createElement('div');
    el.className='ov-opt'+(s===curSpeed?' sel':'');
    el.innerHTML='<span class="ov-opt-label">'+lbl+'</span><div class="ov-opt-check"><img src="'+ICO.check+'" onerror="this.style.display=\'none\'"/></div>';
    el.addEventListener('click',e=>{e.stopPropagation();spApply(s);buildSpdList();closeOverlay();});
    spdList.appendChild(el);
  });
}
function spApply(s){
  curSpeed=s;vid.playbackRate=s;
  const lbl=(Number.isInteger(s)?s:s.toFixed(2))+'×';
  ovSpdVal.textContent=lbl;spdBtn.textContent=lbl;
}
spdBtn.addEventListener('click',e=>{e.stopPropagation();buildSpdList();openOverlay(ovSpd);});
spApply(1);

// ── legendas em tempo real ────────────────────────────────────────────────
// Estratégia:
//  1. Se o vídeo tiver TextTracks embebidos, usa-os.
//  2. Caso contrário, o Kotlin pode chamar window.loadSubtitles(vttUrl)
//     que faz fetch do VTT e injeta cues dinamicamente via addCue().
//  3. Polling a cada 5s para recarregar legendas se a URL mudar.

let _subUrl=null,_subPollTimer=null;

function applyEmbeddedTracks(){
  for(let i=0;i<vid.textTracks.length;i++)
    vid.textTracks[i].mode=subsActive?'showing':'hidden';
}

function injectVttCues(vttText){
  // Remove track anterior se existir
  if(subsTrack){
    try{while(subsTrack.cues&&subsTrack.cues.length)subsTrack.removeCue(subsTrack.cues[0]);}catch(_){}
  }
  if(!subsActive)return;
  if(!subsTrack){
    const t=vid.addTextTrack('subtitles','Legendas','pt');
    t.mode='showing';
    subsTrack=t;
  }
  subsTrack.mode='showing';
  // Parse VTT simples
  const blocks=vttText.replace(/\r\n/g,'\n').split('\n\n');
  blocks.forEach(block=>{
    const lines=block.trim().split('\n');
    const timeLine=lines.find(l=>l.includes('-->'));
    if(!timeLine)return;
    const [startStr,endStr]=timeLine.split('-->').map(s=>s.trim());
    function toSec(t){const p=t.replace(',','.').split(':');if(p.length===3)return +p[0]*3600+(+p[1])*60+(+p[2]);return(+p[0])*60+(+p[1]);}
    const start=toSec(startStr),end=toSec(endStr);
    const text=lines.slice(lines.indexOf(timeLine)+1).join('\n');
    if(!isFinite(start)||!isFinite(end)||start>=end)return;
    try{subsTrack.addCue(new VTTCue(start,end,text));}catch(_){}
  });
}

function fetchAndInjectSubs(url){
  if(!url||!subsActive)return;
  fetch(url,{cache:'no-store'}).then(r=>r.text()).then(t=>injectVttCues(t)).catch(_=>{});
}

window.loadSubtitles=function(vttUrl){
  _subUrl=vttUrl;
  fetchAndInjectSubs(vttUrl);
  clearInterval(_subPollTimer);
  _subPollTimer=setInterval(()=>fetchAndInjectSubs(_subUrl),5000);
};

function applySubtitles(){
  subsIcon.src=subsActive?ICO.subs_on:ICO.subs_off;
  subsBtn.classList.toggle('sub-active',subsActive);
  if(vid.textTracks.length>0){
    applyEmbeddedTracks();
  } else if(_subUrl){
    if(subsActive){fetchAndInjectSubs(_subUrl);}
    else if(subsTrack){
      try{while(subsTrack.cues&&subsTrack.cues.length)subsTrack.removeCue(subsTrack.cues[0]);}catch(_){}
      subsTrack.mode='hidden';
    }
  }
}

vid.addEventListener('loadedmetadata',()=>{
  if(vid.textTracks.length>0)applyEmbeddedTracks();
});
subsBtn.addEventListener('click',e=>{e.stopPropagation();subsActive=!subsActive;applySubtitles();showUI();});

// ── back / fwd ────────────────────────────────────────────────────────────
backBtn.addEventListener('click',e=>{e.stopPropagation();vid.currentTime=Math.max(0,vid.currentTime-10);});
fwdBtn.addEventListener('click',e=>{e.stopPropagation();vid.currentTime=Math.min(vid.duration||0,vid.currentTime+10);});

// ── fullscreen — via postMessage para o Kotlin tratar ────────────────────
fsBtn.addEventListener('click',e=>{
  e.stopPropagation();
  window.NuxxxBridge&&window.NuxxxBridge.onFullscreenToggle?
    window.NuxxxBridge.onFullscreenToggle():
    (document.fullscreenElement?document.exitFullscreen().catch(_=>{}):document.documentElement.requestFullscreen().catch(_=>{}));
});
document.addEventListener('fullscreenchange',()=>{
  fsIcon.src=document.fullscreenElement?ICO.fs_exit:ICO.fs;
});

// ── teclado ───────────────────────────────────────────────────────────────
document.addEventListener('keydown',e=>{
  if(e.code==='Space'){e.preventDefault();togglePlay();doFlash(vid.paused?ICO.play:ICO.pause);}
  if(e.code==='ArrowLeft')vid.currentTime=Math.max(0,vid.currentTime-5);
  if(e.code==='ArrowRight')vid.currentTime=Math.min(vid.duration||0,vid.currentTime+5);
  if(e.code==='ArrowUp')vpApply(curVol+5);
  if(e.code==='ArrowDown')vpApply(curVol-5);
  if(e.code==='Escape')closeOverlay();
});

// API pública para Kotlin
window.setSystemVolume=pct=>vpApply(pct);
window.playerPause=()=>vid.pause();
window.playerPlay=()=>vid.play();
window.playerIsPlaying=()=>!vid.paused;

showUI();
})();
</script>
</body>
</html>"""
}

// ─────────────────────────────────────────────────────────────────────────────
// Mini player flutuante (estilo YouTube)
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class FloatingMiniPlayer(
    context: Context,
    private val webView: WebView,
    private val onExpand: () -> Unit,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler = Handler(Looper.getMainLooper())

    // dimensões do mini player (16:9)
    private val miniW get() = (resources.displayMetrics.widthPixels * 0.45f).toInt()
    private val miniH get() = (miniW * 9f / 16f).toInt()
    private val margin get() = activity.dp(12)

    // posição corrente
    private var posX = 0f
    private var posY = 0f

    // drag
    private var dStartX = 0f; private var dStartY = 0f
    private var vStartX = 0f; private var vStartY = 0f
    private var isDragging = false
    private var velX = 0f; private var velY = 0f
    private var lastMoveTime = 0L

    // barra de progresso
    private val progressBar: View

    // botão de pausa/play
    private val pauseBtn: FrameLayout

    init {
        elevation = activity.dp(8).toFloat()
        clipToOutline = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = activity.dp(10).toFloat()
            setColor(Color.BLACK)
        }

        // WebView (continua a tocar — apenas reparentada)
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // barra de progresso (fina no fundo)
        progressBar = View(context).apply {
            setBackgroundColor(Color.parseColor("#E6143C"))
        }
        addView(progressBar, LayoutParams(0, activity.dp(2)).also { it.gravity = Gravity.BOTTOM or Gravity.START })

        // overlay escuro semi-transparente + controlos
        val overlay = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#55000000")) }

        pauseBtn = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val js = "window.playerIsPlaying&&window.playerIsPlaying()?" +
                    "window.playerPause():" +
                    "window.playerPlay();"
                webView.evaluateJavascript(js, null)
                updatePauseIcon()
            }
        }
        try {
            val px = activity.dp(28)
            val svg = com.caverock.androidsvg.SVG.getFromAsset(context.assets, "icons/svg/pause.svg")
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            val iv = ImageView(context).apply {
                setImageBitmap(bmp)
                setColorFilter(Color.WHITE)
                tag = "pause_icon"
            }
            pauseBtn.addView(iv, LayoutParams(px, px).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}

        // botão fechar
        val closeBtn = FrameLayout(context).apply {
            isClickable = true; isFocusable = true
            setOnClickListener { dismissMini() }
        }
        try {
            val px = activity.dp(20)
            val svg = com.caverock.androidsvg.SVG.getFromAsset(context.assets, "icons/svg/close.svg")
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            val iv = ImageView(context).apply { setImageBitmap(bmp); setColorFilter(Color.WHITE) }
            closeBtn.addView(iv, LayoutParams(px, px).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}

        overlay.addView(pauseBtn, LayoutParams(activity.dp(52), activity.dp(52)).also { it.gravity = Gravity.CENTER })
        overlay.addView(closeBtn, LayoutParams(activity.dp(36), activity.dp(36)).also {
            it.gravity = Gravity.TOP or Gravity.END
            it.topMargin = activity.dp(4); it.rightMargin = activity.dp(4)
        })

        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // toque para expandir (desde que não seja drag)
        overlay.setOnClickListener { onExpand() }

        setupDrag()
        startProgressSync()
    }

    private fun updatePauseIcon() {
        handler.postDelayed({
            webView.evaluateJavascript("window.playerIsPlaying&&window.playerIsPlaying()") { result ->
                val isPlaying = result?.trim() == "true"
                val iconName = if (isPlaying) "pause.svg" else "play_arrow.svg"
                (findViewWithTag<ImageView>("pause_icon"))?.let { iv ->
                    try {
                        val px = activity.dp(28)
                        val svg = com.caverock.androidsvg.SVG.getFromAsset(context.assets, "icons/svg/$iconName")
                        svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                        svg.renderToCanvas(Canvas(bmp))
                        iv.setImageBitmap(bmp)
                    } catch (_: Exception) {}
                }
            }
        }, 100)
    }

    private fun startProgressSync() {
        val r = object : Runnable {
            override fun run() {
                if (!isAttachedToWindow) return
                webView.evaluateJavascript(
                    "(function(){var v=document.getElementById('vid');return v?v.currentTime+'/'+v.duration:'0/0';})()"
                ) { result ->
                    try {
                        val s = result?.trim('"') ?: "0/0"
                        val parts = s.split('/')
                        val cur = parts[0].toFloatOrNull() ?: 0f
                        val dur = parts[1].toFloatOrNull() ?: 0f
                        if (dur > 0) {
                            val pct = (cur / dur).coerceIn(0f, 1f)
                            val totalW = width
                            val lp = progressBar.layoutParams as LayoutParams
                            lp.width = (totalW * pct).toInt()
                            progressBar.layoutParams = lp
                        }
                    } catch (_: Exception) {}
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(r)
    }

    // ── drag & snap ──────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dStartX = ev.rawX; dStartY = ev.rawY
                    vStartX = x; vStartY = y
                    isDragging = false
                    velX = 0f; velY = 0f
                    lastMoveTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - dStartX; val dy = ev.rawY - dStartY
                    if (!isDragging && (abs(dx) > 8f || abs(dy) > 8f)) isDragging = true
                    if (isDragging) {
                        val now = System.currentTimeMillis()
                        val dt = (now - lastMoveTime).coerceAtLeast(1L)
                        velX = (dx) / dt * 16f; velY = (dy) / dt * 16f
                        lastMoveTime = now
                        x = vStartX + dx; y = vStartY + dy
                        clampPosition()
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) snapToCorner() else performClick()
                    isDragging = false
                    false
                }
                else -> false
            }
        }
    }

    private fun clampPosition() {
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        x = x.coerceIn(0f, screenW - miniW)
        y = y.coerceIn(0f, screenH - miniH - activity.dp(60))
    }

    private fun snapToCorner() {
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        val targetX = if (x + miniW / 2f < screenW / 2f) margin.toFloat()
                      else screenW - miniW - margin
        val targetY = y.coerceIn(margin.toFloat(), screenH - miniH - activity.dp(72))
        animate().x(targetX).y(targetY).setDuration(220)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    fun placeAtBottomRight() {
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        x = screenW - miniW - margin
        y = screenH - miniH - activity.dp(72)
    }

    fun animateIn() {
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        y = screenH
        animate().y(y - miniH - activity.dp(72) - margin)
            .setDuration(320).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun dismissMini() {
        webView.evaluateJavascript("window.playerPause&&window.playerPause()", null)
        animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(200)
            .withEndAction { onClose() }.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(miniW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(miniH, MeasureSpec.EXACTLY)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExibicaoPage
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("ViewConstructor")
class ExibicaoPage(
    context: Context,
    private val video: FeedVideo,
    private val onVideoTap: (FeedVideo, View) -> Unit,
) : FrameLayout(context) {

    private val activity = context as MainActivity
    // FIX #29: @Volatile garante visibilidade entre threads
    @Volatile private var isDestroyed = false

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var webView: WebView
    private lateinit var spinnerView: FrameLayout
    private lateinit var errorView: FrameLayout
    private lateinit var recycler: RecyclerView
    private lateinit var btnDownload: FrameLayout

    private val relatedList = mutableListOf<FeedVideo>()
    private lateinit var relatedAdapter: RelatedAdapter

    private var directUrl: String? = null
    // FIX #33: extracting gerido apenas na main thread com flag extra para evitar showError duplo
    private var extracting = false
    private var miniPlayer: FloatingMiniPlayer? = null

    // FIX #48: referências aos Runnable do handler para poder cancelar
    private val pendingRunnables = mutableListOf<Runnable>()

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        animateIn()       // FIX: entrada suave de baixo para cima
        extractAndPlay(video.videoUrl)
        loadRelated()
    }

    // ── Animação de entrada: sobe de baixo ───────────────────────────────────
    private fun animateIn() {
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        translationY = screenH
        alpha = 1f
        animate()
            .translationY(0f)
            .setDuration(380)
            .setInterpolator(DecelerateInterpolator(2.2f))
            .start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        activity.setStatusBarDark(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isDestroyed = true
        // FIX #48: cancela todos os postDelayed pendentes
        pendingRunnables.forEach { handler.removeCallbacks(it) }
        pendingRunnables.clear()
    }

    // ── Minimizar para mini player flutuante ─────────────────────────────────
    fun minimizeToFloat() {
        if (miniPlayer != null) return

        // Retira o WebView da hierarquia actual
        (webView.parent as? ViewGroup)?.removeView(webView)

        val mp = FloatingMiniPlayer(
            context = context,
            webView = webView,
            onExpand = { expandFromFloat() },
            onClose = {
                // fecha mini player e destrói tudo
                miniPlayer = null
                activity.closeVideoPlayer()
            }
        )
        miniPlayer = mp

        // Adiciona ao root da Activity (DecorView ou rootView)
        val root = activity.window.decorView as FrameLayout
        mp.placeAtBottomRight()
        root.addView(mp, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        mp.animateIn()

        // Esconde a ExibicaoPage mas mantém-na viva
        animate().alpha(0f).translationY(50f.dp()).setDuration(250)
            .withEndAction { visibility = View.GONE }.start()
    }

    fun expandFromFloat() {
        val mp = miniPlayer ?: return
        val root = activity.window.decorView as FrameLayout

        // Anima o mini player a sair
        mp.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(200)
            .withEndAction {
                root.removeView(mp)
                miniPlayer = null

                // Reparenta o WebView de volta ao playerContainer
                (webView.parent as? ViewGroup)?.removeView(webView)
                playerContainer?.addView(webView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))

                // Mostra ExibicaoPage
                visibility = View.VISIBLE
                alpha = 0f; translationY = 50f.dp()
                animate().alpha(1f).translationY(0f).setDuration(280)
                    .setInterpolator(DecelerateInterpolator(2f)).start()
            }.start()
    }

    private fun Float.dp() = this * resources.displayMetrics.density

    // referência ao container do player para reparenting
    private var playerContainer: FrameLayout? = null

    // ── buildUI ──────────────────────────────────────────────────────────────
    private fun buildUI() {
        val screenW = context.resources.displayMetrics.widthPixels
        val playerH = (screenW * 9f / 16f).toInt()

        // FIX #1, #5: rootCol sem translationZ; relatedScroll tem weight=1
        val rootCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
        }

        rootCol.addView(
            View(context).apply { setBackgroundColor(Color.BLACK) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight)
        )

        val pContainer = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        playerContainer = pContainer

        webView = buildWebView()
        pContainer.addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        spinnerView = buildSpinner()
        pContainer.addView(spinnerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        errorView = buildErrorView()
        errorView.visibility = View.GONE
        pContainer.addView(errorView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // botão back
        val btnBack = FrameLayout(context).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true; isFocusable = true
            setOnClickListener {
                // minimiza em vez de fechar diretamente
                minimizeToFloat()
            }
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

        // infoBox — FIX #1: sem translationZ para não bloquear toques abaixo
        val infoBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(8))
            setBackgroundColor(AppTheme.bg)
        }

        val titleTv = TextView(context).apply {
            text = video.title
            setTextColor(AppTheme.text)
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 3
        }
        infoBox.addView(titleTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(5)))

        val metaTv = TextView(context).apply {
            setTextColor(AppTheme.textSecondary)
            textSize = 11.5f
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
        }
        infoBox.addView(metaTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(12)))

        // FIX #9, #10: btnDownload com isClickable/isFocusable correctos
        btnDownload = FrameLayout(context).apply {
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        val dlPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(50).toFloat()
                setColor(Color.parseColor("#F2F2F2"))
            }
            setPadding(dp(16), dp(10), dp(20), dp(10))
            isClickable = true; isFocusable = true
            setOnClickListener { showSnackbar("Download ainda não disponível nesta versão") }
        }
        dlPill.addView(activity.svgImageView("icons/svg/download.svg", 18, AppTheme.text), LinearLayout.LayoutParams(dp(18), dp(18)))
        dlPill.addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
        dlPill.addView(TextView(context).apply {
            text = "Descarregar"
            setTextColor(AppTheme.text); textSize = 13f; setTypeface(null, Typeface.BOLD)
        })
        btnDownload.addView(dlPill, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(btnDownload, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))
        infoBox.addView(
            View(context).apply { setBackgroundColor(AppTheme.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        )

        rootCol.addView(infoBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // FIX #2, #3, #4: NestedScrollView sem isFillViewport (deixa RecyclerView medir livremente)
        val relatedScroll = NestedScrollView(context).apply {
            isFillViewport = false
            clipChildren = true
            setBackgroundColor(AppTheme.bg)
        }
        val relatedCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
        }

        relatedCol.addView(
            TextView(context).apply {
                text = "Relacionados"
                setTextColor(AppTheme.text); textSize = 13.5f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(12), dp(10), dp(12), dp(4))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        // FIX #6: skeleton com tag para remover correctamente
        val skeletonBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; tag = "skeleton"
        }
        repeat(5) { skeletonBox.addView(buildRelatedSkeleton()) }
        relatedCol.addView(skeletonBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        relatedAdapter = RelatedAdapter(
            items = relatedList,
            onTap = { v, thumb -> onVideoTap(v, thumb) },
            onMenuTap = { v -> showVideoBottomSheet(v) }
        )

        // FIX #3: RecyclerView com altura WRAP e nested disabled dentro de NestedScrollView
        recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
            adapter = relatedAdapter
            visibility = View.GONE
            itemAnimator = null
            // FIX #38: outlineProvider para clipToOutline funcionar correctamente
            clipToPadding = true
        }

        relatedCol.addView(recycler, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // FIX #50: espaçador com MATCH_PARENT
        relatedCol.addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)))

        relatedScroll.addView(relatedCol, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rootCol.addView(relatedScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(rootCol, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // ── Bottom sheet ─────────────────────────────────────────────────────────

    private fun showVideoBottomSheet(video: FeedVideo) {
        val dialog = BottomSheetDialog(
            activity, // FIX #11: usar activity, não context genérico
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )
        val sheetView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        sheetView.addView(TextView(context).apply {
            text = video.title
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

        data class SheetItem(val iconPath: String, val label: String, val action: () -> Unit)

        val items = listOf(
            SheetItem("icons/svg/bookmark.svg", "Guardar para ver mais tarde") {
                dialog.dismiss(); showSnackbar("Guardado")
            },
            SheetItem("icons/svg/playlist_add.svg", "Adicionar à playlist") {
                dialog.dismiss(); showSnackbar("Adicionado à playlist")
            },
            SheetItem("icons/svg/open_in_browser.svg", "Ver no browser") {
                dialog.dismiss()
                activity.addContentOverlay(BrowserPage(context, freeNavigation = true, externalUrl = video.videoUrl))
            }
        )

        items.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
                isClickable = true; isFocusable = true
                // FIX #12: background com null-check
                val tv = android.util.TypedValue()
                val resolved = activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                if (resolved) background = activity.getDrawable(tv.resourceId)
                setOnClickListener { item.action() }
            }
            row.addView(activity.svgImageView(item.iconPath, 22, AppTheme.iconSub), LinearLayout.LayoutParams(dp(22), dp(22)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(16), 1))
            row.addView(TextView(context).apply {
                text = item.label
                setTextColor(AppTheme.text); textSize = 15f
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            sheetView.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        sheetView.addView(View(context), LinearLayout.LayoutParams(1, dp(24)))
        dialog.setContentView(sheetView)
        dialog.show()
    }

    // ── Snackbar ─────────────────────────────────────────────────────────────

    private fun showSnackbar(message: String) {
        (parent as? ViewGroup)?.findViewWithTag<View>("snackbar_m3")?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        val snack = FrameLayout(context).apply {
            tag = "snackbar_m3"
            elevation = dp(6).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#1C1B1F"))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(context).apply {
            text = message
            setTextColor(Color.parseColor("#F4EFF4")); textSize = 14f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        snack.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER_VERTICAL
        })
        addView(snack, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
            it.bottomMargin = dp(24); it.leftMargin = dp(16); it.rightMargin = dp(16)
        })
        snack.alpha = 0f; snack.translationY = dp(20).toFloat()
        snack.animate().alpha(1f).translationY(0f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()

        // FIX #43: guarda referência do Runnable para cancelar se necessário
        val dismissRunnable = Runnable {
            if (snack.isAttachedToWindow) {
                snack.animate().alpha(0f).translationY(dp(20).toFloat()).setDuration(200)
                    .withEndAction { (snack.parent as? ViewGroup)?.removeView(snack) }.start()
            }
        }
        pendingRunnables.add(dismissRunnable)
        handler.postDelayed(dismissRunnable, 3000)
    }

    // ── Extracção de vídeo ───────────────────────────────────────────────────

    private fun extractAndPlay(videoUrl: String) {
        if (extracting) return
        extracting = true
        spinnerView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        btnDownload.visibility = View.GONE

        // FIX #30, #31: usa AtomicBoolean para done E para error, evitando race conditions
        val done = AtomicBoolean(false)
        val errorDone = AtomicBoolean(false)
        val failed = AtomicInteger(0)
        val total = CONVERT_APIS.size

        CONVERT_APIS.forEach { api ->
            thread {
                // FIX #32: disconnect sempre no finally
                var conn: java.net.HttpURLConnection? = null
                try {
                    val encoded = java.net.URLEncoder.encode(videoUrl, "UTF-8")
                    conn = (java.net.URL("$api/extract?url=$encoded").openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 90_000
                        requestMethod = "GET"
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val link = org.json.JSONObject(body).optString("link", "")
                        if (link.isNotEmpty() && done.compareAndSet(false, true)) {
                            handler.post {
                                if (isDestroyed) return@post
                                extracting = false
                                directUrl = link
                                spinnerView.visibility = View.GONE
                                btnDownload.visibility = View.VISIBLE
                                webView.loadDataWithBaseURL(
                                    "https://nuxxx.app",
                                    buildPlayerHtml(link),
                                    "text/html", "UTF-8", null
                                )
                            }
                        }
                    } else {
                        val f = failed.incrementAndGet()
                        if (f == total && !done.get() && errorDone.compareAndSet(false, true)) {
                            handler.post { if (!isDestroyed) showError() }
                        }
                    }
                } catch (_: Exception) {
                    // FIX #32
                    val f = failed.incrementAndGet()
                    if (f == total && !done.get() && errorDone.compareAndSet(false, true)) {
                        handler.post { if (!isDestroyed) showError() }
                    }
                } finally {
                    conn?.disconnect()
                }
            }
        }
    }

    private fun showError() {
        // FIX #33: só executa se ainda em extracção
        if (!extracting) return
        extracting = false
        spinnerView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    // ── Related ──────────────────────────────────────────────────────────────

    private fun loadRelated() {
        thread {
            try {
                val result = FeedFetcher.fetchAll(Random.nextInt(1, 30))
                    .filter { it.videoUrl != video.videoUrl }
                    .take(40)
                handler.post {
                    if (isDestroyed) return@post
                    // FIX #6: remove skeleton correctamente
                    findViewWithTag<LinearLayout>("skeleton")?.visibility = View.GONE
                    relatedList.clear()
                    relatedList.addAll(result)
                    relatedAdapter.notifyDataSetChanged()
                    recycler.visibility = View.VISIBLE
                }
            } catch (_: Exception) {}
        }
    }

    // ── WebView ──────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView() = WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // FIX #24: allowUniversalAccessFromFileURLs apenas para assets locais
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = false // FIX #24 — não necessário com baseURL
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

    // ── Spinner ──────────────────────────────────────────────────────────────

    private fun buildSpinner() = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        // FIX #49: spinner com flag de cancelamento
        var running = true
        val spinner = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private var phase = 0f
            private val runner = object : Runnable {
                override fun run() {
                    if (!running) return
                    phase = (phase + 3f) % 360f
                    invalidate()
                    postDelayed(this, 16)
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
        tag = spinner // guarda referência para parar depois
        addView(spinner, FrameLayout.LayoutParams(dp(44), dp(44)).also { it.gravity = Gravity.CENTER })
        // Para o spinner quando a visibilidade muda para GONE
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { spinner.stop() }
        })
    }

    // ── Error view ───────────────────────────────────────────────────────────

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
        addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER
        })
    }

    // ── Skeleton ─────────────────────────────────────────────────────────────

    private fun buildRelatedSkeleton() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(12), 0, dp(8), dp(14))
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
        }, LinearLayout.LayoutParams(dp(160), dp(90)))
        addView(View(context), LinearLayout.LayoutParams(dp(10), 0))
        val infoCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        infoCol.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(13)))
        infoCol.addView(View(context), LinearLayout.LayoutParams(1, dp(5)))
        infoCol.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
        }, LinearLayout.LayoutParams(dp(120), dp(11)))
        addView(infoCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    // ── Destroy ──────────────────────────────────────────────────────────────

    fun destroy() {
        isDestroyed = true
        // FIX #47, #48
        handler.removeCallbacksAndMessages(null)
        pendingRunnables.clear()
        miniPlayer?.let { mp ->
            (mp.parent as? ViewGroup)?.removeView(mp)
            miniPlayer = null
        }
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
    }

    private fun dp(v: Int) = activity.dp(v)
}

// ─────────────────────────────────────────────────────────────────────────────
// RelatedAdapter
// ─────────────────────────────────────────────────────────────────────────────

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
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(4), dp(14))
            isClickable = true; isFocusable = true
            // FIX #39: ripple feedback
            val tv = android.util.TypedValue()
            val resolved = ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            if (resolved) background = ctx.getDrawable(tv.resourceId)
        }

        // FIX #38: thumbFrame com outlineProvider correcto para clip funcionar
        val thumbFrame = FrameLayout(ctx).apply {
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(AppTheme.thumbBg)
            }
        }
        val thumb = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        thumbFrame.addView(thumb, FrameLayout.LayoutParams(dp(160), dp(90)))

        val durationBadge = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#CC000000"))
            }
            setPadding(dp(4), dp(1), dp(4), dp(1)); visibility = View.GONE
        }
        thumbFrame.addView(durationBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM or Gravity.END; it.bottomMargin = dp(4); it.rightMargin = dp(4)
        })

        row.addView(thumbFrame, LinearLayout.LayoutParams(dp(160), dp(90)))
        row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 0))

        val infoCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP
        }

        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
        }
        val title = TextView(ctx).apply {
            setTextColor(AppTheme.text); textSize = 13f; maxLines = 2
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // FIX #9, #10: menuBtn explicitamente clicável e focável
        val menuBtn = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(6), dp(2), dp(2), dp(2))
            isClickable = true; isFocusable = true
            // FIX #39: ripple no menuBtn
            val tv = android.util.TypedValue()
            val resolved = ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
            if (resolved) background = ctx.getDrawable(tv.resourceId)
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
        infoCol.addView(View(ctx), LinearLayout.LayoutParams(1, dp(5)))

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
        val v = items[position]
        val ctx = holder.root.context
        fun dp(i: Int) = (i * ctx.resources.displayMetrics.density).toInt()

        holder.title.text = v.title
        holder.title.setTextColor(AppTheme.text)
        holder.sourceLabel.text = v.source.label
        holder.sourceLabel.setTextColor(AppTheme.textSecondary)

        // FIX #37: placeholder no favicon
        Glide.with(ctx).load(faviconUrl(v.source))
            .placeholder(android.R.drawable.ic_menu_gallery)
            .override(dp(14), dp(14)).circleCrop().into(holder.favicon)

        holder.meta.text = buildString {
            if (v.views.isNotEmpty()) append("${v.views} vis.")
            if (v.duration.isNotEmpty()) append("  ·  ${v.duration}")
        }
        holder.meta.setTextColor(AppTheme.textSecondary)

        if (v.duration.isNotEmpty()) {
            holder.duration.text = v.duration; holder.duration.visibility = View.VISIBLE
        } else holder.duration.visibility = View.GONE

        (holder.thumb.parent as? FrameLayout)?.background?.let {
            (it as? GradientDrawable)?.setColor(AppTheme.thumbBg)
        }

        if (v.thumb.isNotEmpty()) {
            // FIX #37: placeholder na thumb
            Glide.with(ctx).load(
                GlideUrl(v.thumb, LazyHeaders.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", referer(v.source)).build())
            ).placeholder(android.R.color.darker_gray)
             .override(320, 180).centerCrop().into(holder.thumb)
        }

        // FIX #8: menuBtn.setOnClickListener antes de row.setOnClickListener
        // e o menuBtn consome o evento sem propagar ao row
        holder.menuBtn.setOnClickListener { onMenuTap(v) }
        holder.root.setOnClickListener { onTap(v, holder.thumb) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        // FIX #36: usa lifecycle-safe clear
        try { Glide.with(holder.thumb.context).clear(holder.thumb) } catch (_: Exception) {}
        try { Glide.with(holder.favicon.context).clear(holder.favicon) } catch (_: Exception) {}
    }

    override fun getItemCount() = items.size
}