package com.doction.webviewapp.ui

import com.doction.webviewapp.theme.AppTheme
import com.doction.webviewapp.services.DownloadService
import com.doction.webviewapp.services.FaviconService
import com.doction.webviewapp.services.LockService
import com.doction.webviewapp.services.ThemeService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.MainActivity
import kotlin.concurrent.thread

private const val SVG_BACK       = "icons/svg/settings/settings_back.svg"
private const val SVG_WALLPAPER  = "icons/svg/settings/settings_wallpaper.svg"
private const val SVG_ENGINE     = "icons/svg/settings/settings_engine.svg"
private const val SVG_SCREENSHOT = "icons/svg/settings/settings_screenshot.svg"
private const val SVG_PIN        = "icons/svg/settings/settings_pin.svg"
private const val SVG_VOLUME     = "icons/svg/settings/settings_volume.svg"
private const val SVG_LOCK       = "icons/svg/settings/settings_lock.svg"
private const val SVG_TRASH      = "icons/svg/settings/settings_trash.svg"
private const val SVG_CHEVRON    = "icons/svg/settings/settings_chevron.svg"

private const val SVG_EYE = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg>"""
private const val SVG_REFRESH_CW = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/></svg>"""
private const val SVG_SCROLL_TEXT = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 12h-5"/><path d="M15 8h-5"/><path d="M19 17V5a2 2 0 0 0-2-2H4"/><path d="M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3"/></svg>"""
private const val SVG_TIMER = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="10" x2="14" y1="2" y2="2"/><line x1="12" x2="15" y1="14" y2="11"/><circle cx="12" cy="14" r="8"/></svg>"""

private data class IconData(val id: String, val label: String, val sub: String, val asset: String)
private val K_ICONS = listOf(
    IconData("classic",  "Classic",  "Fundo vermelho",  "icons/ic_classic.png"),
    IconData("light",    "Light",    "Fundo branco",    "icons/ic_light.png"),
    IconData("original", "Original", "Ícone original",  "icons/ic_original.png"),
)

@SuppressLint("ViewConstructor")
class SettingsPage(context: Context) : FrameLayout(context) {

    private val activity    = context as MainActivity
    private val ts          = ThemeService.instance
    private val handler     = Handler(Looper.getMainLooper())
    private val density     get() = context.resources.displayMetrics.density
    private val statusBarH  get() = activity.statusBarHeight

    private var lockEnabled  = false
    private var activeIconId = "classic"

    private lateinit var rootScroll: NestedScrollView
    private lateinit var contentCol: LinearLayout
    private lateinit var appBarView: FrameLayout

    private val sheetStack = mutableListOf<FrameLayout>()

    init {
        setBackgroundColor(AppTheme.bg)
        applyLightStatusBar()
        loadState()
        buildUI()
    }

    private fun applyLightStatusBar() {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = true
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK
            && event.action == android.view.KeyEvent.ACTION_UP) {
            if (sheetStack.isNotEmpty()) { dismissTopSheet(); return true }
            activity.closeSettings(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFocusableInTouchMode = true
        requestFocus()
        applyLightStatusBar()
    }

    fun handleBack() {
        if (sheetStack.isNotEmpty()) dismissTopSheet()
        else activity.closeSettings()
    }

    private fun loadState() {
        thread {
            lockEnabled  = LockService.instance.isEnabled()
            activeIconId = activity.getPreferences(Context.MODE_PRIVATE)
                .getString("active_icon", "classic") ?: "classic"
            handler.post { rebuildContent() }
        }
    }

    private fun buildUI() {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val spacer = View(context).apply { setBackgroundColor(AppTheme.bg) }
        root.addView(spacer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, statusBarH))

        appBarView = buildAppBar()
        root.addView(appBarView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        rootScroll = NestedScrollView(context).apply { isFillViewport = true }
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(32))
        }
        rootScroll.addView(contentCol, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(rootScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        rebuildContent()
    }

    private fun buildAppBar(): FrameLayout {
        val bar = FrameLayout(context).apply { setBackgroundColor(AppTheme.bg) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        val btnBack = FrameLayout(context).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener {
                if (sheetStack.isNotEmpty()) dismissTopSheet()
                else activity.closeSettings()
            }
        }
        btnBack.addView(svgAsset(SVG_BACK, 22, AppTheme.text),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })
        row.addView(btnBack, LinearLayout.LayoutParams(dp(46), dp(44)))
        row.addView(TextView(context).apply {
            text = "Definições"; setTextColor(AppTheme.text)
            textSize = 17f; setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))
        return bar
    }

    private fun rebuildContent() {
        setBackgroundColor(AppTheme.bg)
        appBarView.setBackgroundColor(AppTheme.bg)
        ((appBarView.getChildAt(0) as? LinearLayout)?.getChildAt(0) as? FrameLayout)
            ?.let { (it.getChildAt(0) as? android.widget.ImageView)?.setColorFilter(AppTheme.text) }
        ((appBarView.getChildAt(0) as? LinearLayout)?.getChildAt(1) as? TextView)
            ?.setTextColor(AppTheme.text)

        contentCol.removeAllViews()

        contentCol.addView(sectionLabel("Aparência"))
        contentCol.addView(sectionCard(listOf(
            tapRow(SVG_WALLPAPER, null, "Fundo de ecrã", wallpaperLabel()) { openWallpaperSheet() },
            iconPreviewRow("Ícone do app",
                K_ICONS.firstOrNull { it.id == activeIconId }?.label ?: "Classic",
                K_ICONS.firstOrNull { it.id == activeIconId }?.asset ?: K_ICONS[0].asset
            ) { openIconSheet() },
        )))
        contentCol.addView(spacer(20))

        contentCol.addView(sectionLabel("Segurança"))
        contentCol.addView(sectionCard(buildLockRows()))
        contentCol.addView(spacer(20))

        contentCol.addView(sectionLabel("Privacidade"))
        contentCol.addView(sectionCard(listOf(
            switchRow(null, SVG_EYE, "Privacidade nos recentes",
                if (ts.privacyRecent) "App aparece em preto" else "Conteúdo visível",
                ts.privacyRecent) { v -> ts.setPrivacyRecent(v); rebuildContent() },
            switchRow(SVG_SCREENSHOT, null, "Bloquear capturas",
                if (ts.noScreenshot) "Screenshots bloqueados" else "Screenshots permitidos",
                ts.noScreenshot) { v -> ts.setNoScreenshot(v); rebuildContent() },
        )))
        contentCol.addView(spacer(20))

        contentCol.addView(sectionLabel("Navegação"))
        contentCol.addView(sectionCard(listOf(
            tapRow(SVG_ENGINE, null, "Motor de pesquisa",
                ThemeService.engines[ts.engine] ?: "Google") { openEnginePicker() },
            tapRow(SVG_VOLUME, null, "Volume máximo", "${ts.maxVolume}%") { openVolumePicker() },
        )))
        contentCol.addView(spacer(20))

        contentCol.addView(sectionLabel("Manutenção"))
        contentCol.addView(sectionCard(listOf(
            tapRow(null, SVG_REFRESH_CW, "Recarregar ícones", "Baixa novamente os favicons") {
                thread {
                    FaviconService.instance.clearAll()
                    FaviconService.instance.preloadAll()
                    handler.post { snack("Ícones recarregados") }
                }
            },
            tapRow(SVG_TRASH, null, "Limpar downloads", "Apaga todos os ficheiros",
                destructive = true) { openConfirmClearSheet() },
        )))
        contentCol.addView(spacer(20))

        contentCol.addView(sectionLabel("Sobre"))
        contentCol.addView(aboutCard())
        contentCol.addView(spacer(10))
        contentCol.addView(sectionCard(listOf(
            tapRow(null, SVG_SCROLL_TEXT, "Licenças de software",
                "Dependências open source") { activity.openLicenses() },
        )))
        contentCol.addView(spacer(20))
    }

    private fun buildLockRows(): List<View> = buildList {
        add(switchRow(SVG_LOCK, null, "Bloquear app",
            if (lockEnabled) "PIN obrigatório" else "Sem bloqueio", lockEnabled) { v ->
            openPinSheet(unlock = true) {
                thread {
                    LockService.instance.setEnabled(v)
                    handler.post { lockEnabled = v; rebuildContent() }
                }
            }
        })
        if (lockEnabled) {
            add(tapRow(SVG_PIN, null, "Alterar PIN", "Muda o código de acesso") {
                openPinSheet(unlock = false) { snack("PIN alterado") }
            })
            add(tapRow(null, SVG_TIMER, "Bloquear após", ts.lockDelayLabel) {
                openLockDelayPicker()
            })
        }
    }

    private fun sectionCard(rows: List<View>): View {
        val wrapper = FrameLayout(context)
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val total = rows.size
        rows.forEachIndexed { i, row ->
            val isOnly  = total == 1
            val isFirst = i == 0
            val isLast  = i == total - 1
            val bigR   = dp(14).toFloat()
            val smallR = dp(6).toFloat()
            val radii = when {
                isOnly  -> floatArrayOf(bigR,bigR,bigR,bigR,bigR,bigR,bigR,bigR)
                isFirst -> floatArrayOf(bigR,bigR,bigR,bigR,smallR,smallR,smallR,smallR)
                isLast  -> floatArrayOf(smallR,smallR,smallR,smallR,bigR,bigR,bigR,bigR)
                else    -> floatArrayOf(smallR,smallR,smallR,smallR,smallR,smallR,smallR,smallR)
            }
            val card = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = radii
                    setColor(AppTheme.card)
                }
            }
            card.addView(row, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (!isLast) lp.bottomMargin = dp(2)
            col.addView(card, lp)
        }
        wrapper.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin = dp(16); it.rightMargin = dp(16)
        })
        return wrapper
    }

    private fun tapRow(
        svgAssetPath: String?, inlineSvg: String?,
        label: String, sub: String,
        destructive: Boolean = false,
        onTap: () -> Unit,
    ): View {
        val iconColor = if (destructive) AppTheme.error else AppTheme.textSecondary
        val textColor = if (destructive) AppTheme.error else AppTheme.text
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onTap() }
            isClickable = true; isFocusable = true
        }
        row.addView(svgView(svgAssetPath, inlineSvg, 20, iconColor))
        row.addView(hSpacer(14))
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(context).apply {
            text = label; setTextColor(textColor); textSize = 14f; setTypeface(null, Typeface.BOLD)
        })
        tc.addView(spacer(2))
        tc.addView(TextView(context).apply {
            text = sub; setTextColor(AppTheme.textSecondary); textSize = 12f
        })
        row.addView(tc, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(svgView(SVG_CHEVRON, null, 16, Color.argb(100,
            Color.red(AppTheme.textSecondary), Color.green(AppTheme.textSecondary),
            Color.blue(AppTheme.textSecondary))))
        return row
    }

    private fun switchRow(
        svgAssetPath: String?, inlineSvg: String?,
        label: String, sub: String,
        value: Boolean,
        onChange: (Boolean) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        row.addView(svgView(svgAssetPath, inlineSvg, 20, AppTheme.textSecondary))
        row.addView(hSpacer(14))
        val subTv = TextView(context).apply { text = sub; setTextColor(AppTheme.textSecondary); textSize = 12f }
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(context).apply {
            text = label; setTextColor(AppTheme.text); textSize = 14f; setTypeface(null, Typeface.BOLD)
        })
        tc.addView(spacer(2))
        tc.addView(subTv)
        row.addView(tc, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(SettingsSwitch(context, value) { v ->
            subTv.text = if (v) "Ativo" else "Inativo"
            onChange(v)
        }, LinearLayout.LayoutParams(dp(46), dp(26)))
        return row
    }

    private fun iconPreviewRow(label: String, sub: String, asset: String, onTap: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onTap() }
            isClickable = true; isFocusable = true
        }
        val img = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(5).toFloat()
            }
            try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open(asset))) }
            catch (_: Exception) {}
        }
        row.addView(img, LinearLayout.LayoutParams(dp(20), dp(20)))
        row.addView(hSpacer(14))
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(context).apply {
            text = label; setTextColor(AppTheme.text); textSize = 14f; setTypeface(null, Typeface.BOLD)
        })
        tc.addView(spacer(2))
        tc.addView(TextView(context).apply { text = sub; setTextColor(AppTheme.textSecondary); textSize = 12f })
        row.addView(tc, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(svgView(SVG_CHEVRON, null, 16, Color.argb(100,
            Color.red(AppTheme.textSecondary), Color.green(AppTheme.textSecondary),
            Color.blue(AppTheme.textSecondary))))
        return row
    }

    private fun aboutCard(): View {
        val wrapper = FrameLayout(context)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background  = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(AppTheme.card)
            }
        }
        val logo = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
            }
            try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open("logo.png"))) }
            catch (_: Exception) {}
        }
        card.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
        card.addView(hSpacer(14))
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(context).apply {
            text = "nuxxx"; setTextColor(AppTheme.text); textSize = 15f; setTypeface(null, Typeface.BOLD)
        })
        tc.addView(spacer(2))
        tc.addView(TextView(context).apply {
            text = "Versão 1.0.0 · Navegação privada"; setTextColor(AppTheme.textSecondary); textSize = 12f
        })
        card.addView(tc)
        wrapper.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin = dp(16); it.rightMargin = dp(16)
        })
        return wrapper
    }

    private fun openWallpaperSheet() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sheetHandle())
        content.addView(spacer(12))
        content.addView(TextView(context).apply {
            text = "Fundo de ecrã"; setTextColor(AppTheme.text)
            textSize = 17f; setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), 0, dp(20), 0)
        })
        content.addView(spacer(12))

        val switchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        switchRow.addView(svgAsset(SVG_WALLPAPER, 20, AppTheme.textSecondary))
        switchRow.addView(hSpacer(14))
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(context).apply {
            text = "Usar imagem como fundo"; setTextColor(AppTheme.text)
            textSize = 14f; setTypeface(null, Typeface.BOLD)
        })
        tc.addView(spacer(2))
        val wpSub = TextView(context).apply {
            text = if (ts.useWallpaper) "Imagem ativa" else "Fundo sólido"
            setTextColor(AppTheme.textSecondary); textSize = 12f
        }
        tc.addView(wpSub)
        switchRow.addView(tc, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        switchRow.addView(SettingsSwitch(context, ts.useWallpaper) { v ->
            thread { ts.setUseWallpaper(v) }
            wpSub.text = if (v) "Imagem ativa" else "Fundo sólido"
            if (v) { dismissTopSheet(); rebuildContent() }
        }, LinearLayout.LayoutParams(dp(46), dp(26)))
        content.addView(switchRow)

        if (ts.useWallpaper) {
            content.addView(spacer(16))
            val gallery = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
            }
            val galleryRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), 0, dp(16), 0)
            }
            ThemeService.wallpapers.forEach { wp ->
                val sel = wp == ts.bg
                val thumb = FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(14).toFloat()
                        setStroke(dp(2), if (sel) AppTheme.ytRed else Color.TRANSPARENT)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(90), dp(168)).also {
                        it.rightMargin = dp(10)
                    }
                    setOnClickListener { thread { ts.setBg(wp) }; rebuildContent(); dismissTopSheet() }
                }
                val img = android.widget.ImageView(context).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
                    }
                    val assetPath = wp.removePrefix("assets/")
                    try {
                        setImageBitmap(BitmapFactory.decodeStream(context.assets.open(assetPath)))
                    } catch (_: Exception) {}
                }
                thumb.addView(img, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                galleryRow.addView(thumb)
            }
            gallery.addView(galleryRow)
            content.addView(gallery, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        content.addView(spacer(24))
        showSheet(content)
    }

    private fun openIconSheet() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sheetHandle())
        content.addView(spacer(14))
        val hRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }
        hRow.addView(TextView(context).apply {
            text = "Ícone do app"; setTextColor(AppTheme.text)
            textSize = 17f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        hRow.addView(TextView(context).apply {
            text = "Fechar"; setTextColor(AppTheme.textSecondary); textSize = 15f
            setOnClickListener { dismissTopSheet() }
        })
        content.addView(hRow)
        content.addView(spacer(6))
        content.addView(TextView(context).apply {
            text = "O app irá fechar e reabrir ao alterar o ícone."
            setTextColor(AppTheme.textSecondary); textSize = 12f
            setPadding(dp(20), 0, dp(20), 0)
        })
        content.addView(spacer(16))
        val iconsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
        }
        K_ICONS.forEach { iconData ->
            val sel = activeIconId == iconData.id
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background  = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(18).toFloat()
                    setColor(if (sel) Color.argb(31, 255, 0, 0) else Color.TRANSPARENT)
                    setStroke(dp(2), if (sel) AppTheme.ytRed else Color.TRANSPARENT)
                }
                layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    activeIconId = iconData.id
                    activity.getPreferences(Context.MODE_PRIVATE).edit()
                        .putString("active_icon", iconData.id).apply()
                    rebuildContent(); dismissTopSheet()
                }
            }
            val img = android.widget.ImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(18).toFloat()
                }
                try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open(iconData.asset))) }
                catch (_: Exception) {}
            }
            cell.addView(img, LinearLayout.LayoutParams(dp(64), dp(64)))
            cell.addView(spacer(8))
            cell.addView(TextView(context).apply {
                text = iconData.label
                setTextColor(if (sel) AppTheme.ytRed else AppTheme.text)
                textSize = 13f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            })
            cell.addView(spacer(2))
            cell.addView(TextView(context).apply {
                text = iconData.sub; setTextColor(AppTheme.textSecondary)
                textSize = 10f; gravity = Gravity.CENTER
            })
            iconsRow.addView(cell)
        }
        content.addView(iconsRow)
        content.addView(spacer(24))
        showSheet(content)
    }

    private fun openPinSheet(unlock: Boolean, onDone: () -> Unit) {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sheetHandle())
        content.addView(spacer(14))
        content.addView(LockScreenView(context, unlock = unlock, onSuccess = {
            dismissTopSheet(); onDone()
        }))
        showSheet(content)
    }

    private fun openLockDelayPicker() {
        val opts   = listOf(0, 5, 10, 30, 60, 120, 300, 600, 1800, 3600, 7200, 14400)
        val labels = listOf("Imediato","5 seg","10 seg","30 seg","1 min","2 min",
            "5 min","10 min","30 min","1 hora","2 horas","4 horas")
        val idx = opts.indexOf(ts.lockDelay).coerceAtLeast(0)
        openPickerSheet("Bloquear após", labels, idx) { i ->
            thread { ts.setLockDelay(opts[i]) }; handler.post { rebuildContent() }
        }
    }

    private fun openEnginePicker() {
        val keys   = ThemeService.engines.keys.toList()
        val values = ThemeService.engines.values.toList()
        val idx    = keys.indexOf(ts.engine).coerceAtLeast(0)
        openPickerSheet("Motor de pesquisa", values, idx) { i ->
            thread { ts.setEngine(keys[i]) }; handler.post { rebuildContent() }
        }
    }

    private fun openVolumePicker() {
        val items = List(10) { "${(it + 1) * 10}%" }
        val idx   = ((ts.maxVolume / 10) - 1).coerceIn(0, 9)
        openPickerSheet("Volume máximo", items, idx) { i ->
            thread { ts.setMaxVolume((i + 1) * 10) }; handler.post { rebuildContent() }
        }
    }

    private fun openPickerSheet(title: String, items: List<String>, initial: Int, onOk: (Int) -> Unit) {
        var selectedIdx = initial
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sheetHandle())
        val hRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        hRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        hRow.addView(TextView(context).apply {
            text = title; setTextColor(AppTheme.text)
            textSize = 15f; setTypeface(null, Typeface.BOLD)
        })
        hRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        hRow.addView(TextView(context).apply {
            text = "OK"; setTextColor(AppTheme.ytRed)
            textSize = 15f; setTypeface(null, Typeface.BOLD)
            setOnClickListener { onOk(selectedIdx); dismissTopSheet() }
        })
        content.addView(hRow)
        val np = NumberPicker(context).apply {
            minValue = 0; maxValue = items.size - 1
            displayedValues = items.toTypedArray(); value = initial
            wrapSelectorWheel = false
            setOnValueChangedListener { _, _, nv -> selectedIdx = nv }
            try {
                val f = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                f.isAccessible = true
                (f.get(this) as? android.graphics.Paint)?.color = AppTheme.text
            } catch (_: Exception) {}
        }
        content.addView(np, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(176)).also {
            it.leftMargin = dp(16); it.rightMargin = dp(16)
        })
        content.addView(spacer(8))
        showSheet(content)
    }

    private fun openConfirmClearSheet() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sheetHandle())
        content.addView(spacer(14))
        content.addView(TextView(context).apply {
            text = "Limpar downloads?"; setTextColor(AppTheme.text)
            textSize = 16f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        content.addView(spacer(16))
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        btnRow.addView(TextView(context).apply {
            text = "Cancelar"; setTextColor(AppTheme.text); textSize = 14f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
                setStroke(dp(1), AppTheme.divider)
            }
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { dismissTopSheet() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(hSpacer(12))
        btnRow.addView(TextView(context).apply {
            text = "Apagar tudo"; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
                setColor(AppTheme.error)
            }
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                thread {
                    DownloadService.instance.items.toList()
                        .forEach { DownloadService.instance.delete(it.id) }
                    handler.post { snack("Downloads limpos"); dismissTopSheet() }
                }
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(btnRow)
        content.addView(spacer(36))
        showSheet(content)
    }

    private fun showSheet(content: LinearLayout) {
        val screenH = context.resources.displayMetrics.heightPixels
        val overlay = FrameLayout(context).apply { setBackgroundColor(Color.TRANSPARENT) }
        val sheetBg = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(dp(20f), dp(20f), dp(20f), dp(20f), 0f, 0f, 0f, 0f)
                setColor(AppTheme.sheet)
            }
            setOnClickListener { }
        }
        val scroll = NestedScrollView(context).apply { isFillViewport = true }
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sheetBg.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val scrim = View(context).apply {
            setBackgroundColor(Color.BLACK); alpha = 0f
            setOnClickListener { dismissTopSheet() }
        }
        overlay.addView(scrim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlay.addView(sheetBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (screenH * 0.75f).toInt()).also { it.gravity = Gravity.BOTTOM })
        sheetBg.translationY = screenH.toFloat()
        activity.addContentOverlay(overlay)
        sheetStack.add(overlay)
        sheetBg.animate().translationY(0f).setDuration(380)
            .setInterpolator(DecelerateInterpolator(2.5f)).start()
        scrim.animate().alpha(0.55f).setDuration(380).start()
        this.animate().translationY(-dp(28).toFloat()).scaleX(0.94f).scaleY(0.94f)
            .setDuration(380).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun dismissTopSheet() {
        val overlay = sheetStack.removeLastOrNull() ?: return
        val sheetBg = overlay.getChildAt(1) as? FrameLayout ?: return
        val scrim   = overlay.getChildAt(0)
        val screenH = context.resources.displayMetrics.heightPixels.toFloat()
        sheetBg.animate().translationY(screenH).setDuration(300)
            .setInterpolator(AccelerateInterpolator(2f)).start()
        scrim.animate().alpha(0f).setDuration(300).start()
        if (sheetStack.isEmpty()) {
            this.animate().translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(300).setInterpolator(DecelerateInterpolator(1.5f)).start()
        }
        handler.postDelayed({ activity.removeContentOverlay(overlay) }, 310)
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text.uppercase()
        setTextColor(AppTheme.textSecondary)
        textSize = 11f; letterSpacing = 0.08f
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(20), dp(8), dp(16), dp(6))
    }

    private fun sheetHandle() = FrameLayout(context).apply {
        setPadding(0, dp(10), 0, 0)
        val handle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(AppTheme.sheetHandle)
            }
        }
        addView(handle, FrameLayout.LayoutParams(dp(36), dp(4)).also { it.gravity = Gravity.CENTER })
    }

    private fun svgAsset(path: String, sizeDp: Int, tint: Int) =
        activity.svgImageView(path, sizeDp, tint)

    private fun svgInline(svgStr: String, sizeDp: Int, tint: Int) =
        android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            try {
                val px  = dp(sizeDp)
                val hex = String.format("#%06X", 0xFFFFFF and tint)
                val colored = svgStr.replace("currentColor", hex)
                val svg = SVG.getFromString(colored)
                svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bmp)); setImageBitmap(bmp)
            } catch (_: Exception) {}
        }

    private fun svgView(assetPath: String?, inlineSvg: String?, sizeDp: Int, tint: Int) =
        when {
            assetPath != null  -> svgAsset(assetPath, sizeDp, tint)
            inlineSvg != null  -> svgInline(inlineSvg, sizeDp, tint)
            else -> android.widget.ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            }
        }

    private fun wallpaperLabel() =
        if (ts.useWallpaper) ts.bg.split("/").last() else "Fundo claro"

    private fun snack(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    private fun spacer(h: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }
    private fun hSpacer(w: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), 1)
    }
    private fun dp(v: Int)   = (v * density).toInt()
    private fun dp(v: Float) = v * density
}

class SettingsSwitch(
    context: Context,
    private var checked: Boolean,
    private val onChange: (Boolean) -> Unit,
) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun dp(v: Float) = v * density

    private val trackView = View(context)
    private val thumbView = View(context)

    init {
        updateTrack()
        addView(trackView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        thumbView.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(3f)
        }
        addView(thumbView, LayoutParams(dp(20), dp(20)))
        positionThumb(animate = false)
        setOnClickListener {
            checked = !checked
            updateTrack()
            positionThumb(animate = true)
            onChange(checked)
        }
    }

    private fun updateTrack() {
        trackView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(13f)
            setColor(if (checked) AppTheme.ytRed else AppTheme.cardAlt)
        }
    }

    private fun positionThumb(animate: Boolean) {
        val pad    = dp(3)
        val trackW = dp(46)
        val thumbW = dp(20)
        val travel = trackW - thumbW - pad * 2
        val targetX = (pad + if (checked) travel else 0).toFloat()
        val targetY = pad.toFloat()
        if (animate) {
            thumbView.animate()
                .translationX(targetX).translationY(targetY)
                .setDuration(260)
                .setInterpolator(OvershootInterpolator(2.5f))
                .start()
            thumbView.animate().scaleX(1.15f).scaleY(1.15f)
                .setDuration(120).withEndAction {
                    thumbView.animate().scaleX(1f).scaleY(1f).setDuration(160)
                        .setInterpolator(OvershootInterpolator(3f)).start()
                }.start()
        } else {
            thumbView.translationX = targetX
            thumbView.translationY = targetY
        }
    }
}