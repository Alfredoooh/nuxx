package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.*
import androidx.recyclerview.widget.ConcatAdapter
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
import kotlin.concurrent.thread
import kotlin.random.Random

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

private fun buildPlayerHtml(videoUrl: String): String {
    val escaped = videoUrl.replace("\"", "&quot;")
    return """<!DOCTYPE html>
<html lang="pt">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<title>Player</title>
<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
*{-webkit-user-select:none;-moz-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent;}
*:focus{outline:none;}
html,body{width:100%;height:100%;background:#000;overflow:hidden;font-family:-apple-system,Roboto,Arial,sans-serif;}
video{position:absolute;inset:0;width:100%;height:100%;display:block;object-fit:contain;transition:filter .3s;pointer-events:none;}
body.ui video{filter:brightness(.6);}
body.overlay-open video{filter:brightness(.35);}
.spinner-wrap{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;pointer-events:none;transition:opacity .3s;}
.spinner-wrap.hidden{opacity:0;}
.spinner{width:36px;height:36px;border-radius:50%;border:3px solid rgba(255,255,255,.18);border-top-color:rgba(255,255,255,.85);animation:spin .8s linear infinite;}
@keyframes spin{to{transform:rotate(360deg);}}
.flash{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:72px;height:72px;border-radius:50%;background:rgba(0,0,0,.45);display:flex;align-items:center;justify-content:center;opacity:0;pointer-events:none;}
.flash img{width:36px;height:36px;filter:invert(1);}
.flash.pop{animation:flashpop .38s ease forwards;}
@keyframes flashpop{0%{opacity:1;transform:translate(-50%,-50%) scale(.75);}55%{opacity:.85;transform:translate(-50%,-50%) scale(1.1);}100%{opacity:0;transform:translate(-50%,-50%) scale(1.25);}}
.top-bar{position:absolute;top:0;left:0;right:0;display:flex;align-items:center;justify-content:flex-end;padding:12px 16px;gap:4px;opacity:0;transition:opacity .25s;pointer-events:none;}
body.ui .top-bar{opacity:1;pointer-events:all;}
.ctrl-slot{position:absolute;bottom:0;left:0;right:0;opacity:0;transition:opacity .25s;pointer-events:none;}
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
.brow{display:flex;align-items:center;padding:0 2px;}
.brow-left{display:flex;align-items:center;flex-shrink:0;}
.brow-center{display:flex;align-items:center;flex:1;justify-content:center;}
.brow-right{display:flex;align-items:center;flex-shrink:0;}
.time-display{font-size:13px;font-weight:500;color:rgba(255,255,255,.85);white-space:nowrap;padding:0 4px 0 2px;line-height:1;}
.time-display .sep{color:rgba(255,255,255,.4);}
.ib{background:none;border:none;color:#fff;width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;cursor:pointer;flex-shrink:0;transition:background .15s;padding:0;}
.ib:hover{background:rgba(255,255,255,.15);}
.ib img{width:24px;height:24px;filter:invert(1);}
.ib.play-btn img{width:32px;height:32px;}
.ib.lg img{width:28px;height:28px;}
.ib.sub-active img{filter:invert(1) sepia(1) saturate(5) hue-rotate(80deg);}
.spd{background:none;border:none;color:#fff;font:600 13px/1 -apple-system,Roboto,Arial,sans-serif;padding:8px 10px;border-radius:8px;cursor:pointer;transition:background .15s;white-space:nowrap;display:flex;align-items:center;}
.spd:hover{background:rgba(255,255,255,.15);}
.overlay{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;pointer-events:none;opacity:0;transition:opacity .22s;z-index:10;}
.overlay.active{opacity:1;pointer-events:all;}
.ov-label{font-size:12px;font-weight:600;color:rgba(255,255,255,.6);letter-spacing:.08em;text-transform:uppercase;margin-bottom:8px;}
.ov-value{font-size:44px;font-weight:700;color:#fff;line-height:1;margin-bottom:28px;text-shadow:0 2px 16px rgba(0,0,0,.7);}
.ov-slider{-webkit-appearance:none;appearance:none;width:min(340px,72%);height:4px;border-radius:2px;outline:none;cursor:pointer;background:rgba(255,255,255,.22);}
.ov-slider::-webkit-slider-runnable-track{height:4px;background:transparent;border-radius:2px;}
.ov-slider::-webkit-slider-thumb{-webkit-appearance:none;width:22px;height:22px;border-radius:50%;background:#fff;margin-top:-9px;box-shadow:0 2px 10px rgba(0,0,0,.5);}
.ov-slider::-moz-range-thumb{width:22px;height:22px;border-radius:50%;background:#fff;border:none;box-shadow:0 2px 10px rgba(0,0,0,.5);}
.ov-close{position:absolute;top:14px;right:16px;background:none;border:none;cursor:pointer;display:flex;align-items:center;justify-content:center;padding:6px;border-radius:50%;transition:background .15s;}
.ov-close:hover{background:rgba(255,255,255,.12);}
.ov-close img{width:28px;height:28px;filter:invert(1);opacity:.75;transition:opacity .12s;}
.ov-close:hover img{opacity:1;}
.ov-settings-list{display:flex;flex-direction:column;gap:4px;width:min(320px,80%);}
.ov-opt{display:flex;align-items:center;justify-content:space-between;padding:13px 18px;border-radius:12px;cursor:pointer;transition:background .15s;}
.ov-opt:hover{background:rgba(255,255,255,.1);}
.ov-opt-label{font-size:15px;font-weight:500;color:#fff;}
.ov-opt-check{width:20px;height:20px;opacity:0;transition:opacity .15s;}
.ov-opt-check img{width:20px;height:20px;filter:invert(1) sepia(1) saturate(5) hue-rotate(80deg);}
.ov-opt.active .ov-opt-check{opacity:1;}
</style>
</head>
<body>
<video id="vid" src="$escaped" autoplay playsinline webkit-playsinline preload="auto"></video>
<div class="spinner-wrap" id="spinnerWrap"><div class="spinner"></div></div>
<div class="flash" id="flash"><img id="flashIcon" src="file:///android_asset/icons/svg/play_arrow.svg"/></div>
<div class="top-bar" id="topBar">
  <button class="ib lg" id="subsBtn"><img id="subsIcon" src="file:///android_asset/icons/svg/subtitles.svg"/></button>
  <button class="ib lg" id="settingsBtn"><img src="file:///android_asset/icons/svg/settings.svg"/></button>
</div>
<div class="overlay" id="ovVol">
  <button class="ov-close" id="ovVolClose"><img src="file:///android_asset/icons/svg/close.svg"/></button>
  <div class="ov-label">Volume</div><div class="ov-value" id="ovVolVal">100%</div>
  <input class="ov-slider" id="ovVolSlider" type="range" min="0" max="100" step="1" value="100"/>
</div>
<div class="overlay" id="ovSpd">
  <button class="ov-close" id="ovSpdClose"><img src="file:///android_asset/icons/svg/close.svg"/></button>
  <div class="ov-label">Velocidade</div><div class="ov-value" id="ovSpdVal">1×</div>
  <input class="ov-slider" id="ovSpdSlider" type="range" min="0" max="100" step="1" value="50"/>
</div>
<div class="overlay" id="ovSettings">
  <button class="ov-close" id="ovSettingsClose"><img src="file:///android_asset/icons/svg/close.svg"/></button>
  <div class="ov-label">Qualidade</div><div class="ov-value" id="ovSettingsVal">Auto</div>
  <div class="ov-settings-list" id="settingsList"></div>
</div>
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
        <button class="ib" id="muteBtn"><img id="volIcon" src="file:///android_asset/icons/svg/volume_up.svg"/></button>
        <div class="time-display"><span id="curT">0:00</span>&nbsp;<span class="sep">/</span>&nbsp;<span id="durT">0:00</span></div>
      </div>
      <div class="brow-center">
        <button class="ib" id="backBtn"><img src="file:///android_asset/icons/svg/replay_10.svg"/></button>
        <button class="ib play-btn" id="playBtn"><img id="playIcon" src="file:///android_asset/icons/svg/play_arrow.svg"/></button>
        <button class="ib" id="fwdBtn"><img src="file:///android_asset/icons/svg/forward_10.svg"/></button>
      </div>
      <div class="brow-right">
        <button class="spd" id="spdBtn">1×</button>
        <button class="ib" id="fsBtn"><img id="fsIcon" src="file:///android_asset/icons/svg/fullscreen.svg"/></button>
      </div>
    </div>
  </div>
</div>
<script>
const body=document.body,vid=document.getElementById('vid'),spinnerWrap=document.getElementById('spinnerWrap'),
cardControls=document.getElementById('cardControls'),playBtn=document.getElementById('playBtn'),
playIcon=document.getElementById('playIcon'),progWrap=document.getElementById('progWrap'),
progFill=document.getElementById('progFill'),progBuf=document.getElementById('progBuf'),
progThumb=document.getElementById('progThumb'),progTip=document.getElementById('progTip'),
curTEl=document.getElementById('curT'),durTEl=document.getElementById('durT'),
muteBtn=document.getElementById('muteBtn'),volIcon=document.getElementById('volIcon'),
backBtn=document.getElementById('backBtn'),fwdBtn=document.getElementById('fwdBtn'),
spdBtn=document.getElementById('spdBtn'),fsBtn=document.getElementById('fsBtn'),
fsIcon=document.getElementById('fsIcon'),flash=document.getElementById('flash'),
flashIcon=document.getElementById('flashIcon'),ovVol=document.getElementById('ovVol'),
ovVolClose=document.getElementById('ovVolClose'),ovVolVal=document.getElementById('ovVolVal'),
ovVolSlider=document.getElementById('ovVolSlider'),ovSpd=document.getElementById('ovSpd'),
ovSpdClose=document.getElementById('ovSpdClose'),ovSpdVal=document.getElementById('ovSpdVal'),
ovSpdSlider=document.getElementById('ovSpdSlider'),ovSettings=document.getElementById('ovSettings'),
ovSettingsClose=document.getElementById('ovSettingsClose'),ovSettingsVal=document.getElementById('ovSettingsVal'),
settingsBtn=document.getElementById('settingsBtn'),settingsList=document.getElementById('settingsList'),
subsBtn=document.getElementById('subsBtn'),subsIcon=document.getElementById('subsIcon');
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
const QUALITIES=[{id:'auto',label:'Automático'},{id:'1080',label:'1080p · Full HD'},{id:'720',label:'720p · HD'},{id:'480',label:'480p'},{id:'360',label:'360p'}];
let curQuality='auto',curSpeed=1,curVol=100,subsActive=false;
function fmt(s){if(!isFinite(s)||s<0)return'0:00';s=Math.floor(s);const h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sec=String(s%60).padStart(2,'0');if(h){return h+':'+String(m).padStart(2,'0')+':'+sec;}return m+':'+sec;}
function sliderGrad(el){const pct=((+el.value-+el.min)/(+el.max-+el.min))*100;el.style.background='linear-gradient(to right,rgba(255,255,255,.95) '+pct+'%,rgba(255,255,255,.22) '+pct+'%)';}
function sliderToSpeed(v){v=+v;return v<=50?0.25+(v/50)*.75:1+((v-50)/50)*2;}
function speedToSlider(s){s=+s;return s<=1?((s-.25)/.75)*50:50+((s-1)/2)*50;}
vid.addEventListener('waiting',()=>spinnerWrap.classList.remove('hidden'));
vid.addEventListener('playing',()=>spinnerWrap.classList.add('hidden'));
vid.addEventListener('canplay',()=>spinnerWrap.classList.add('hidden'));
const allOverlays=[ovVol,ovSpd,ovSettings];
let uiVisible=false,hideTimer=null,isDragging=false;
function openOverlay(which){allOverlays.forEach(o=>o.classList.remove('active'));body.classList.remove('ui');body.classList.add('overlay-open');which.classList.add('active');clearTimeout(hideTimer);}
function closeOverlay(){allOverlays.forEach(o=>o.classList.remove('active'));body.classList.remove('overlay-open');if(uiVisible){body.classList.add('ui');resetHideTimer();}}
ovVolClose.addEventListener('click',e=>{e.stopPropagation();closeOverlay();});
ovSpdClose.addEventListener('click',e=>{e.stopPropagation();closeOverlay();});
ovSettingsClose.addEventListener('click',e=>{e.stopPropagation();closeOverlay();});
function showUI(){uiVisible=true;body.classList.add('ui');resetHideTimer();}
function resetHideTimer(){clearTimeout(hideTimer);const open=allOverlays.some(o=>o.classList.contains('active'));if(!open&&!isDragging)hideTimer=setTimeout(hideUIFully,3500);}
function hideUIFully(){uiVisible=false;body.classList.remove('ui');closeOverlay();}
document.addEventListener('click',e=>{
  if(e.target.closest('.ctrl-slot')||e.target.closest('.overlay')||e.target.closest('.top-bar'))return;
  if(uiVisible){clearTimeout(hideTimer);hideUIFully();}else showUI();
});
function setPlaying(p){playIcon.src=p?ICO.pause:ICO.play;if(!p){clearTimeout(hideTimer);showUI();}}
function togglePlay(){vid.paused?vid.play():vid.pause();}
playBtn.addEventListener('click',e=>{e.stopPropagation();togglePlay();});
vid.addEventListener('play',()=>setPlaying(true));
vid.addEventListener('pause',()=>setPlaying(false));
vid.addEventListener('ended',()=>setPlaying(false));
function doFlash(src){flashIcon.src=src;flash.classList.remove('pop');void flash.offsetWidth;flash.classList.add('pop');}
function setPct(p){progFill.style.width=p+'%';progThumb.style.left=p+'%';}
vid.addEventListener('loadedmetadata',()=>{durTEl.textContent=fmt(vid.duration);});
vid.addEventListener('timeupdate',()=>{if(!seeking){const p=vid.duration?(vid.currentTime/vid.duration)*100:0;setPct(p);}curTEl.textContent=fmt(vid.currentTime);});
vid.addEventListener('progress',()=>{if(vid.buffered.length)progBuf.style.width=(vid.buffered.end(vid.buffered.length-1)/vid.duration*100)+'%';});
progWrap.addEventListener('mousemove',e=>{const r=progWrap.getBoundingClientRect();const p=Math.min(1,Math.max(0,(e.clientX-r.left)/r.width));progTip.textContent=fmt(p*(vid.duration||0));progTip.style.left=(p*100)+'%';});
let seeking=false;
function seekTo(cx){const r=progWrap.getBoundingClientRect();const p=Math.min(1,Math.max(0,(cx-r.left)/r.width));vid.currentTime=p*(vid.duration||0);setPct(p*100);}
progWrap.addEventListener('mousedown',e=>{e.stopPropagation();seeking=true;isDragging=true;clearTimeout(hideTimer);seekTo(e.clientX);});
window.addEventListener('mousemove',e=>{if(seeking)seekTo(e.clientX);});
window.addEventListener('mouseup',()=>{if(seeking){seeking=false;isDragging=false;resetHideTimer();}});
progWrap.addEventListener('touchstart',e=>{e.stopPropagation();seeking=true;isDragging=true;clearTimeout(hideTimer);seekTo(e.touches[0].clientX);},{passive:true});
window.addEventListener('touchmove',e=>{if(seeking)seekTo(e.touches[0].clientX);},{passive:true});
window.addEventListener('touchend',()=>{if(seeking){seeking=false;isDragging=false;resetHideTimer();}});
function vpApply(pct){pct=Math.min(100,Math.max(0,Math.round(pct)));curVol=pct;vid.volume=pct/100;vid.muted=(pct===0);ovVolVal.textContent=pct+'%';ovVolSlider.value=pct;sliderGrad(ovVolSlider);volIcon.src=pct===0?ICO.vol_off:pct<50?ICO.vol_down:ICO.vol_up;}
muteBtn.addEventListener('click',e=>{e.stopPropagation();openOverlay(ovVol);});
ovVolSlider.addEventListener('input',e=>{e.stopPropagation();vpApply(+ovVolSlider.value);});
ovVolSlider.addEventListener('mousedown',e=>e.stopPropagation());
ovVolSlider.addEventListener('touchstart',e=>e.stopPropagation(),{passive:true});
vpApply(100);
function spApply(speed){speed=Math.round(speed*20)/20;speed=Math.min(3,Math.max(0.25,speed));curSpeed=speed;vid.playbackRate=speed;const lbl=(Number.isInteger(speed)?speed:+speed.toFixed(2))+'x';ovSpdVal.textContent=lbl;spdBtn.textContent=lbl;ovSpdSlider.value=speedToSlider(speed);sliderGrad(ovSpdSlider);}
spdBtn.addEventListener('click',e=>{e.stopPropagation();openOverlay(ovSpd);});
ovSpdSlider.addEventListener('input',e=>{e.stopPropagation();spApply(sliderToSpeed(+ovSpdSlider.value));});
ovSpdSlider.addEventListener('mousedown',e=>e.stopPropagation());
ovSpdSlider.addEventListener('touchstart',e=>e.stopPropagation(),{passive:true});
spApply(1);
function applySubtitles(){for(let i=0;i<vid.textTracks.length;i++){vid.textTracks[i].mode=subsActive?'showing':'hidden';}subsIcon.src=subsActive?ICO.subs_on:ICO.subs_off;subsBtn.classList.toggle('sub-active',subsActive);}
vid.addEventListener('loadedmetadata',()=>{for(let i=0;i<vid.textTracks.length;i++){vid.textTracks[i].mode=subsActive?'showing':'hidden';}});
subsBtn.addEventListener('click',e=>{e.stopPropagation();subsActive=!subsActive;applySubtitles();showUI();});
settingsBtn.addEventListener('click',e=>{e.stopPropagation();renderQualityList();openOverlay(ovSettings);});
function renderQualityList(){
  settingsList.innerHTML='';
  ovSettingsVal.textContent=QUALITIES.find(q=>q.id===curQuality)?.label||'Auto';
  QUALITIES.forEach(q=>{
    const active=q.id===curQuality;
    const el=document.createElement('div');
    el.className='ov-opt'+(active?' active':'');
    el.innerHTML='<span class="ov-opt-label">'+q.label+'</span><div class="ov-opt-check"><img src="'+ICO.check+'"/></div>';
    el.addEventListener('click',e=>{
      e.stopPropagation();curQuality=q.id;ovSettingsVal.textContent=q.label;
      if(window._hls){if(q.id==='auto'){window._hls.currentLevel=-1;}else{const lvl=window._hls.levels.findIndex(l=>String(l.height)===q.id);if(lvl>=0)window._hls.currentLevel=lvl;}}
      renderQualityList();
    });
    settingsList.appendChild(el);
  });
}
window.setSystemVolume=function(pct){vpApply(pct);};
window.setQualities=function(list){QUALITIES.length=0;list.forEach(q=>QUALITIES.push(q));};
backBtn.addEventListener('click',e=>{e.stopPropagation();vid.currentTime=Math.max(0,vid.currentTime-10);});
fwdBtn.addEventListener('click',e=>{e.stopPropagation();vid.currentTime=Math.min(vid.duration||0,vid.currentTime+10);});
fsBtn.addEventListener('click',e=>{e.stopPropagation();document.fullscreenElement?document.exitFullscreen():document.documentElement.requestFullscreen();});
document.addEventListener('fullscreenchange',()=>{fsIcon.src=document.fullscreenElement?ICO.fs_exit:ICO.fs;});
document.addEventListener('keydown',e=>{
  if(e.code==='Space'){e.preventDefault();togglePlay();doFlash(vid.paused?ICO.play:ICO.pause);}
  if(e.code==='ArrowLeft')vid.currentTime=Math.max(0,vid.currentTime-5);
  if(e.code==='ArrowRight')vid.currentTime=Math.min(vid.duration||0,vid.currentTime+5);
  if(e.code==='ArrowUp')vpApply(curVol+5);
  if(e.code==='ArrowDown')vpApply(curVol-5);
  if(e.code==='KeyM')muteBtn.click();
  if(e.code==='Escape')closeOverlay();
});
showUI();
</script>
</body>
</html>"""
}

@SuppressLint("ViewConstructor")
class ExibicaoPage(
    context: Context,
    private val video: FeedVideo,
    private val onVideoTap: (FeedVideo, View) -> Unit,
    private val originThumb: View? = null
) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var webView: WebView
    private lateinit var spinnerView: FrameLayout
    private lateinit var errorView: FrameLayout

    // Header adapter refs para actualizar sem recriar
    private lateinit var headerAdapter: HeaderAdapter
    private lateinit var relatedAdapter: RelatedAdapter
    private val relatedList = mutableListOf<FeedVideo>()

    private var directUrl: String? = null
    private var extracting = false
    private var isDestroyed = false

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        animateIn()
        extractAndPlay(video.videoUrl)
        loadRelated()
    }

    private fun animateIn() {
        if (originThumb == null) {
            alpha = 0f; translationY = dp(40).toFloat()
            animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(DecelerateInterpolator(2f)).start()
            return
        }
        val loc = IntArray(2); originThumb.getLocationOnScreen(loc)
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        pivotX = loc[0] + originThumb.width / 2f; pivotY = loc[1] + originThumb.height / 2f
        scaleX = originThumb.width / screenW; scaleY = originThumb.height / screenH; alpha = 0f
        animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(360).setInterpolator(DecelerateInterpolator(2.4f)).start()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); activity.setStatusBarDark(true) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); isDestroyed = true }

    private fun buildUI() {
        val screenW = context.resources.displayMetrics.widthPixels
        val playerH = (screenW * 9f / 16f).toInt()

        // ── Player container ──────────────────────────────────────
        val playerContainer = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        webView = buildWebView()
        playerContainer.addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        spinnerView = buildSpinner()
        playerContainer.addView(spinnerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        errorView = buildErrorView(); errorView.visibility = View.GONE
        playerContainer.addView(errorView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val btnBack = FrameLayout(context).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { activity.closeVideoPlayer() }
        }
        btnBack.addView(activity.svgImageView("icons/svg/settings/settings_back.svg", 22, Color.WHITE), FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })
        playerContainer.addView(btnBack, FrameLayout.LayoutParams(dp(42), dp(42)).also { it.gravity = Gravity.TOP or Gravity.START; it.topMargin = dp(6); it.leftMargin = dp(4) })

        // ── Status bar spacer ─────────────────────────────────────
        val statusSpacer = View(context).apply { setBackgroundColor(Color.BLACK) }

        // ── Header adapter (infoBox + skeleton/related label) ─────
        headerAdapter = HeaderAdapter(
            context = context,
            activity = activity,
            video = video,
            onDownloadClick = { showSnackbar("Download ainda não disponível nesta versão") }
        )

        // ── Related adapter ───────────────────────────────────────
        relatedAdapter = RelatedAdapter(
            items = relatedList,
            onTap = { v, thumb -> onVideoTap(v, thumb) },
            onMenuTap = { v -> showVideoBottomSheet(v) }
        )

        // ── Single RecyclerView that owns everything below player ──
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(false)
            itemAnimator = null
            setBackgroundColor(AppTheme.bg)
            adapter = ConcatAdapter(
                ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(),
                headerAdapter,
                relatedAdapter
            )
        }

        // ── Root: statusBar + player + recycler ───────────────────
        val rootCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        rootCol.addView(statusSpacer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight))
        rootCol.addView(playerContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, playerH))
        rootCol.addView(recycler, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(rootCol, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // ── BottomSheet nativo Material3 ──────────────────────────────
    private fun showVideoBottomSheet(video: FeedVideo) {
        val dialog = BottomSheetDialog(context, com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog)
        val sheetView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        sheetView.addView(TextView(context).apply {
            text = video.title; setTextColor(AppTheme.text); textSize = 13.5f
            setTypeface(null, Typeface.BOLD); maxLines = 2; setPadding(dp(20), dp(20), dp(20), dp(2))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        sheetView.addView(TextView(context).apply {
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
            setTextColor(AppTheme.textSecondary); textSize = 11.5f; setPadding(dp(20), 0, dp(20), dp(14))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        sheetView.addView(View(context).apply { setBackgroundColor(AppTheme.divider) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        data class SheetItem(val iconPath: String, val label: String, val action: () -> Unit)
        listOf(
            SheetItem("icons/svg/bookmark.svg", "Guardar para ver mais tarde") { dialog.dismiss(); showSnackbar("Guardado") },
            SheetItem("icons/svg/playlist_add.svg", "Adicionar à playlist") { dialog.dismiss(); showSnackbar("Adicionado à playlist") },
            SheetItem("icons/svg/open_in_browser.svg", "Ver no browser") {
                dialog.dismiss()
                activity.addContentOverlay(BrowserPage(context, freeNavigation = true, externalUrl = video.videoUrl))
            }
        ).forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16)); isClickable = true; isFocusable = true
                background = android.util.TypedValue().let { tv ->
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                    context.getDrawable(tv.resourceId)
                }
                setOnClickListener { item.action() }
            }
            row.addView(activity.svgImageView(item.iconPath, 22, AppTheme.iconSub), LinearLayout.LayoutParams(dp(22), dp(22)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(16), 1))
            row.addView(TextView(context).apply { text = item.label; setTextColor(AppTheme.text); textSize = 15f },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            sheetView.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        sheetView.addView(View(context), LinearLayout.LayoutParams(1, dp(24)))
        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun showSnackbar(message: String) {
        (parent as? ViewGroup)?.findViewWithTag<View>("snackbar_m3")?.let { (parent as ViewGroup).removeView(it) }
        val snack = FrameLayout(context).apply {
            tag = "snackbar_m3"; elevation = dp(6).toFloat()
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#1C1B1F")) }
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(context).apply { text = message; setTextColor(Color.parseColor("#F4EFF4")); textSize = 14f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        snack.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_VERTICAL })
        addView(snack, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM; it.bottomMargin = dp(24); it.leftMargin = dp(16); it.rightMargin = dp(16)
        })
        snack.alpha = 0f; snack.translationY = dp(20).toFloat()
        snack.animate().alpha(1f).translationY(0f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
        handler.postDelayed({
            snack.animate().alpha(0f).translationY(dp(20).toFloat()).setDuration(200)
                .withEndAction { (snack.parent as? ViewGroup)?.removeView(snack) }.start()
        }, 3000)
    }

    private fun extractAndPlay(videoUrl: String) {
        if (extracting) return
        extracting = true
        spinnerView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        handler.post { headerAdapter.setDownloadVisible(false) }

        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val failed = java.util.concurrent.atomic.AtomicInteger(0)
        val total = CONVERT_APIS.size

        CONVERT_APIS.forEach { api ->
            thread {
                try {
                    val encoded = java.net.URLEncoder.encode(videoUrl, "UTF-8")
                    val conn = (java.net.URL("$api/extract?url=$encoded").openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15_000; readTimeout = 90_000; requestMethod = "GET"
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText(); conn.disconnect()
                        val link = org.json.JSONObject(body).optString("link", "")
                        if (link.isNotEmpty() && done.compareAndSet(false, true)) {
                            handler.post {
                                if (isDestroyed) return@post
                                extracting = false; directUrl = link
                                spinnerView.visibility = View.GONE
                                headerAdapter.setDownloadVisible(true)
                                webView.loadDataWithBaseURL("https://nuxxx.app", buildPlayerHtml(link), "text/html", "UTF-8", null)
                            }
                        }
                    } else {
                        conn.disconnect()
                        if (failed.incrementAndGet() == total && !done.get()) handler.post { if (!isDestroyed) showError() }
                    }
                } catch (_: Exception) {
                    if (failed.incrementAndGet() == total && !done.get()) handler.post { if (!isDestroyed) showError() }
                }
            }
        }
    }

    private fun showError() {
        extracting = false
        spinnerView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    private fun loadRelated() {
        thread {
            try {
                val result = FeedFetcher.fetchAll(Random.nextInt(1, 30))
                    .filter { it.videoUrl != video.videoUrl }.take(40)
                handler.post {
                    if (isDestroyed) return@post
                    headerAdapter.setSkeletonVisible(false)
                    relatedList.clear(); relatedList.addAll(result)
                    relatedAdapter.notifyDataSetChanged()
                }
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView() = WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; mediaPlaybackRequiresUserGesture = false
            allowFileAccessFromFileURLs = true; allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true; loadWithOverviewMode = true; setSupportZoom(false); userAgentString = UA
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {}
    }

    private fun buildSpinner() = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        val spinner = object : View(context) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL }
            private var phase = 0f
            private val runner = object : Runnable { override fun run() { phase = (phase + 3f) % 360f; invalidate(); postDelayed(this, 16) } }
            init { post(runner) }
            override fun onDraw(c: android.graphics.Canvas) {
                val cx = width / 2f; val cy = height / 2f; val em = width / 2.5f
                val a1 = Math.toRadians(phase.toDouble()); val a2 = Math.toRadians((phase + 180f).toDouble())
                val a3 = Math.toRadians((phase * 0.7f).toDouble()); val a4 = Math.toRadians((phase * 0.7f + 180f).toDouble())
                paint.color = Color.argb(220, 225, 20, 98)
                c.drawCircle(cx+(em*Math.cos(a1)).toFloat(), cy+(em*0.5f*Math.sin(a1*0.5f)).toFloat(), em*0.22f, paint)
                paint.color = Color.argb(220, 111, 202, 220)
                c.drawCircle(cx+(em*Math.cos(a2)).toFloat(), cy+(em*0.5f*Math.sin(a2*0.5f)).toFloat(), em*0.22f, paint)
                paint.color = Color.argb(220, 61, 184, 143)
                c.drawCircle(cx+(em*0.5f*Math.cos(a3*0.5f)).toFloat(), cy+(em*Math.sin(a3)).toFloat(), em*0.22f, paint)
                paint.color = Color.argb(220, 233, 169, 32)
                c.drawCircle(cx+(em*0.5f*Math.cos(a4*0.5f)).toFloat(), cy+(em*Math.sin(a4)).toFloat(), em*0.22f, paint)
            }
        }
        addView(spinner, FrameLayout.LayoutParams(dp(44), dp(44)).also { it.gravity = Gravity.CENTER })
    }

    private fun buildErrorView() = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(activity.svgImageView("icons/svg/error.svg", 36, Color.parseColor("#99FFFFFF")), LinearLayout.LayoutParams(dp(36), dp(36)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))
        col.addView(TextView(context).apply { text = "Não foi possível obter o vídeo."; setTextColor(Color.parseColor("#99FFFFFF")); textSize = 12f; gravity = Gravity.CENTER })
        col.addView(View(context), LinearLayout.LayoutParams(1, dp(12)))
        col.addView(TextView(context).apply {
            text = "Tentar novamente"; setTextColor(Color.parseColor("#B3FFFFFF")); textSize = 12f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat(); setStroke(dp(1), Color.parseColor("#80FFFFFF")) }
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setOnClickListener { extractAndPlay(video.videoUrl) }
        })
        addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
    }

    fun destroy() { webView.stopLoading(); webView.destroy() }
    private fun dp(v: Int) = activity.dp(v)
}

// ── HeaderAdapter ─────────────────────────────────────────────────────────────
// Contém: infoBox (título, meta, download) + label "Relacionados" + skeletons
private class HeaderAdapter(
    private val context: Context,
    private val activity: MainActivity,
    private val video: FeedVideo,
    private val onDownloadClick: () -> Unit
) : RecyclerView.Adapter<HeaderAdapter.VH>() {

    private var downloadVisible = false
    private var skeletonVisible = true

    fun setDownloadVisible(v: Boolean) { downloadVisible = v; notifyItemChanged(0) }
    fun setSkeletonVisible(v: Boolean) { skeletonVisible = v; notifyItemChanged(1) }

    private fun dp(v: Int) = activity.dp(v)

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        var btnDownload: FrameLayout? = null
        var skeletonBox: LinearLayout? = null
    }

    override fun getItemCount() = 2  // 0 = infoBox, 1 = label+skeleton

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return when (viewType) {
            0 -> createInfoBox()
            else -> createSkeletonHeader()
        }
    }

    override fun getItemViewType(position: Int) = position

    override fun onBindViewHolder(holder: VH, position: Int) {
        when (position) {
            0 -> holder.btnDownload?.visibility = if (downloadVisible) View.VISIBLE else View.GONE
            1 -> holder.skeletonBox?.visibility = if (skeletonVisible) View.VISIBLE else View.GONE
        }
    }

    private fun createInfoBox(): VH {
        val screenW = context.resources.displayMetrics.widthPixels
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
        }

        val infoBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; setColor(AppTheme.bg)
                val r = screenW * 0.04f
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }

        infoBox.addView(TextView(context).apply {
            text = video.title; setTextColor(AppTheme.text); textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD); maxLines = 3
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(5)))
        infoBox.addView(TextView(context).apply {
            setTextColor(AppTheme.textSecondary); textSize = 11.5f
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(12)))

        val btnDownload = FrameLayout(context).apply {
            visibility = View.GONE; isClickable = true; isFocusable = true
        }
        val dlPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(50).toFloat(); setColor(Color.parseColor("#F2F2F2")) }
            setPadding(dp(16), dp(10), dp(20), dp(10)); isClickable = true; isFocusable = true
            setOnClickListener { onDownloadClick() }
        }
        dlPill.addView(activity.svgImageView("icons/svg/download.svg", 18, AppTheme.text), LinearLayout.LayoutParams(dp(18), dp(18)))
        dlPill.addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
        dlPill.addView(TextView(context).apply { text = "Descarregar"; setTextColor(AppTheme.text); textSize = 13f; setTypeface(null, Typeface.BOLD) })
        btnDownload.addView(dlPill, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(btnDownload, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))
        infoBox.addView(View(context).apply { setBackgroundColor(AppTheme.divider) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        col.addView(infoBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val vh = VH(col)
        vh.btnDownload = btnDownload
        return vh
    }

    private fun createSkeletonHeader(): VH {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
        }

        col.addView(TextView(context).apply {
            text = "Relacionados"; setTextColor(AppTheme.text); textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD); setPadding(dp(12), dp(10), dp(12), dp(4))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val skeletonBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        repeat(5) { skeletonBox.addView(buildSkeleton()) }
        col.addView(skeletonBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val vh = VH(col)
        vh.skeletonBox = skeletonBox
        return vh
    }

    private fun buildSkeleton() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), 0, dp(8), dp(14))
        addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat(); setColor(AppTheme.thumbShimmer1) }
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
        addView(infoCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
}

// ── RelatedAdapter ────────────────────────────────────────────────────────────
private class RelatedAdapter(
    private val items: List<FeedVideo>,
    private val onTap: (FeedVideo, View) -> Unit,
    private val onMenuTap: (FeedVideo) -> Unit,
) : RecyclerView.Adapter<RelatedAdapter.VH>() {

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        lateinit var thumb: android.widget.ImageView
        lateinit var favicon: android.widget.ImageView
        lateinit var title: TextView
        lateinit var sourceLabel: TextView
        lateinit var meta: TextView
        lateinit var duration: TextView
        lateinit var menuBtn: android.widget.ImageView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(8), dp(14))
            isClickable = true; isFocusable = true
        }

        // Thumb
        val thumbFrame = FrameLayout(ctx).apply {
            clipToOutline = true
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat(); setColor(AppTheme.thumbBg) }
        }
        val thumb = android.widget.ImageView(ctx).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP }
        thumbFrame.addView(thumb, FrameLayout.LayoutParams(dp(160), dp(90)))
        val durationBadge = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat(); setColor(Color.parseColor("#CC000000")) }
            setPadding(dp(4), dp(1), dp(4), dp(1)); visibility = View.GONE
        }
        thumbFrame.addView(durationBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM or Gravity.END; it.bottomMargin = dp(4); it.rightMargin = dp(4)
        })
        row.addView(thumbFrame, LinearLayout.LayoutParams(dp(160), dp(90)))
        row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 0))

        // Info column
        val infoCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP }

        // Título + menu no mesmo row
        val titleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val title = TextView(ctx).apply { setTextColor(AppTheme.text); textSize = 13f; maxLines = 2 }
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val menuBtn = android.widget.ImageView(ctx).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(4), dp(0), dp(0), dp(0))
            try {
                val px = dp(18)
                val svg = com.caverock.androidsvg.SVG.getFromAsset(ctx.assets, "icons/svg/more_vert.svg")
                svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(android.graphics.Canvas(bmp))
                setImageBitmap(bmp); setColorFilter(AppTheme.iconSub)
            } catch (_: Exception) {}
        }
        titleRow.addView(menuBtn, LinearLayout.LayoutParams(dp(26), dp(26)))
        infoCol.addView(titleRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoCol.addView(View(ctx), LinearLayout.LayoutParams(1, dp(5)))

        // Source row
        val sourceRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val favicon = android.widget.ImageView(ctx).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
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
        vh.thumb = thumb; vh.favicon = favicon; vh.title = title
        vh.sourceLabel = sourceLabel; vh.meta = meta; vh.duration = durationBadge; vh.menuBtn = menuBtn
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = items[position]
        val ctx = holder.root.context
        fun dp(i: Int) = (i * ctx.resources.displayMetrics.density).toInt()

        holder.title.text = v.title; holder.title.setTextColor(AppTheme.text)
        holder.sourceLabel.text = v.source.label; holder.sourceLabel.setTextColor(AppTheme.textSecondary)
        Glide.with(ctx).load(faviconUrl(v.source)).override(dp(14), dp(14)).circleCrop().into(holder.favicon)
        holder.meta.text = buildString {
            if (v.views.isNotEmpty()) append("${v.views} vis.")
            if (v.duration.isNotEmpty()) append("  ·  ${v.duration}")
        }
        holder.meta.setTextColor(AppTheme.textSecondary)
        if (v.duration.isNotEmpty()) { holder.duration.text = v.duration; holder.duration.visibility = View.VISIBLE }
        else holder.duration.visibility = View.GONE
        (holder.thumb.parent as? FrameLayout)?.background?.let { (it as? GradientDrawable)?.setColor(AppTheme.thumbBg) }
        if (v.thumb.isNotEmpty()) {
            Glide.with(ctx).load(GlideUrl(v.thumb, LazyHeaders.Builder().addHeader("User-Agent", UA).addHeader("Referer", referer(v.source)).build()))
                .override(320, 180).centerCrop().into(holder.thumb)
        }
        holder.root.setOnClickListener { onTap(v, holder.thumb) }
        holder.menuBtn.setOnClickListener { onMenuTap(v) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        Glide.with(holder.thumb.context).clear(holder.thumb)
        Glide.with(holder.favicon.context).clear(holder.favicon)
    }

    override fun getItemCount() = items.size
}