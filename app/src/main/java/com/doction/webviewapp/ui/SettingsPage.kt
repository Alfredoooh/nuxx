package com.doction.webviewapp.ui

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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.services.DownloadService
import com.doction.webviewapp.services.FaviconService
import com.doction.webviewapp.services.LockService
import com.doction.webviewapp.services.ThemeService
import kotlin.concurrent.thread

// ── Paleta fixa clara (estilo X/Twitter) ─────────────────────────────────────
private object XColors {
    val bg           = Color.parseColor("#FFFFFF")
    val surface      = Color.parseColor("#FFFFFF")
    val text         = Color.parseColor("#0F1419")
    val textSub      = Color.parseColor("#536471")
    val textDestr    = Color.parseColor("#F4212E")
    val divider      = Color.parseColor("#EFF3F4")
    val handle       = Color.parseColor("#CFD9DE")
    val scrim        = Color.argb(102, 0, 0, 0)
    val red          = Color.parseColor("#F4212E")
    val rowPress     = Color.parseColor("#F7F9F9")
    val switchOn     = Color.parseColor("#1D9BF0")
    val switchOff    = Color.parseColor("#CFD9DE")
}

private const val SVG_BACK       = "icons/svg/settings/settings_back.svg"
private const val SVG_WALLPAPER  = "icons/svg/settings/settings_wallpaper.svg"
private const val SVG_ENGINE     = "icons/svg/settings/settings_engine.svg"
private const val SVG_PIN        = "icons/svg/settings/settings_pin.svg"
private const val SVG_VOLUME     = "icons/svg/settings/settings_volume.svg"
private const val SVG_LOCK       = "icons/svg/settings/settings_lock.svg"
private const val SVG_TRASH      = "icons/svg/settings/settings_trash.svg"
private const val SVG_SCREENSHOT = "icons/svg/settings/settings_screenshot.svg"

private const val SVG_EYE = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg>"""
private const val SVG_REFRESH_CW = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/></svg>"""
private const val SVG_SCROLL_TEXT = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 12h-5"/><path d="M15 8h-5"/><path d="M19 17V5a2 2 0 0 0-2-2H4"/><path d="M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3"/></svg>"""
private const val SVG_TIMER = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="10" x2="14" y1="2" y2="2"/><line x1="12" x2="15" y1="14" y2="11"/><circle cx="12" cy="14" r="8"/></svg>"""

private data class IconData(val id: String, val label: String, val sub: String, val asset: String)
private val K_ICONS = listOf(
    IconData("classic",  "Classic",  "Fundo vermelho", "icons/ic_classic.png"),
    IconData("light",    "Light",    "Fundo branco",   "icons/ic_light.png"),
    IconData("original", "Original", "Ícone original", "icons/ic_original.png"),
)

@SuppressLint("ViewConstructor")
class SettingsPage(context: Context) : FrameLayout(context) {

    private val activity   = context as MainActivity
    private val ts         = ThemeService.instance
    private val handler    = Handler(Looper.getMainLooper())
    private val density    get() = context.resources.displayMetrics.density

    private var lockEnabled  = false
    private var activeIconId = "classic"

    private lateinit var contentCol: LinearLayout
    private lateinit var appBarView: FrameLayout

    private val sheetStack = mutableListOf<FrameLayout>()

    init {
        setBackgroundColor(XColors.bg)
        loadState()
        buildUI()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFocusableInTouchMode = true
        requestFocus()
        // Statusbar clara para settings
        activity.setStatusBarDark(false)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = true
        activity.window.statusBarColor = XColors.bg
    }

    fun handleBack() {
        if (sheetStack.isNotEmpty()) dismissTopSheet()
        else activity.closeSettings()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            handleBack(); return true
        }
        return super.dispatchKeyEvent(event)
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
        root.addView(View(context).apply { setBackgroundColor(XColors.bg) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight))
        appBarView = buildAppBar()
        root.addView(appBarView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(dividerLine(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        val scroll = NestedScrollView(context).apply { isFillViewport = true }
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(32))
        }
        scroll.addView(contentCol, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        rebuildContent()
    }

    private fun buildAppBar(): FrameLayout {
        val bar = FrameLayout(context).apply { setBackgroundColor(XColors.bg) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnBack = FrameLayout(context).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { handleBack() }
            isClickable = true; isFocusable = true
        }
        btnBack.addView(svgAsset(SVG_BACK, 20, XColors.text),
            FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER })
        row.addView(btnBack, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(TextView(context).apply {
            text = "Definições"
            setTextColor(XColors.text)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.01f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))
        return bar
    }

    private fun rebuildContent() {
        contentCol.removeAllViews()

        sectionHeader("Aparência")
        menuRow(SVG_WALLPAPER, null, "Fundo de ecrã", wallpaperLabel()) { openWallpaperSheet() }
        menuRow(null, null, "Ícone do app",
            K_ICONS.firstOrNull { it.id == activeIconId }?.label ?: "Classic",
            iconAsset = K_ICONS.firstOrNull { it.id == activeIconId }?.asset
        ) { openIconSheet() }

        sectionHeader("Segurança")
        buildLockRows()

        sectionHeader("Privacidade")
        switchMenuRow(null, SVG_EYE, "Privacidade nos recentes",
            if (ts.privacyRecent) "App aparece em preto" else "Conteúdo visível",
            ts.privacyRecent) { v -> ts.setPrivacyRecent(v); rebuildContent() }
        switchMenuRow(SVG_SCREENSHOT, null, "Bloquear capturas",
            if (ts.noScreenshot) "Screenshots bloqueados" else "Screenshots permitidos",
            ts.noScreenshot) { v -> ts.setNoScreenshot(v); rebuildContent() }

        sectionHeader("Navegação")
        menuRow(SVG_ENGINE, null, "Motor de pesquisa",
            ThemeService.engines[ts.engine] ?: "Google") { openEnginePicker() }
        menuRow(SVG_VOLUME, null, "Volume máximo", "${ts.maxVolume}%") { openVolumePicker() }

        sectionHeader("Manutenção")
        menuRow(null, SVG_REFRESH_CW, "Recarregar ícones", "Baixa novamente os favicons") {
            thread {
                FaviconService.instance.clearAll()
                FaviconService.instance.preloadAll()
                handler.post { toast("Ícones recarregados") }
            }
        }
        menuRow(SVG_TRASH, null, "Limpar downloads", "Apaga todos os ficheiros",
            destructive = true) { openConfirmClearSheet() }

        sectionHeader("Sobre")
        aboutRow()
        menuRow(null, SVG_SCROLL_TEXT, "Licenças de software", "Dependências open source") {
            activity.openLicenses()
        }

        contentCol.addView(spacer(20))
    }

    private fun buildLockRows() {
        switchMenuRow(SVG_LOCK, null, "Bloquear app",
            if (lockEnabled) "PIN obrigatório" else "Sem bloqueio", lockEnabled) { v ->
            openPinSheet(unlock = true) {
                thread {
                    LockService.instance.setEnabled(v)
                    handler.post { lockEnabled = v; rebuildContent() }
                }
            }
        }
        if (lockEnabled) {
            menuRow(SVG_PIN, null, "Alterar PIN", "Muda o código de acesso") {
                openPinSheet(unlock = false) { toast("PIN alterado") }
            }
            menuRow(null, SVG_TIMER, "Bloquear após", ts.lockDelayLabel) {
                openLockDelayPicker()
            }
        }
    }

    private fun sectionHeader(text: String) {
        contentCol.addView(dividerLine())
        contentCol.addView(TextView(context).apply {
            this.text = text
            setTextColor(XColors.text)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(16), dp(16), dp(8))
        })
    }

    private fun menuRow(
        svgPath: String?, inlineSvg: String?,
        label: String, sub: String,
        destructive: Boolean = false,
        iconAsset: String? = null,
        onTap: () -> Unit,
    ) {
        val labelColor = if (destructive) XColors.textDestr else XColors.text
        val iconColor  = if (destructive) XColors.textDestr else XColors.textSub
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true; isFocusable = true
            setOnClickListener { onTap() }
            background = rippleOrPress()
        }
        val iconView: View = when {
            iconAsset != null -> ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(5).toFloat()
                }
                try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open(iconAsset))) }
                catch (_: Exception) {}
            }
            svgPath != null   -> svgAsset(svgPath, 22, iconColor)
            inlineSvg != null -> svgInline(inlineSvg, 22, iconColor)
            else              -> View(context)
        }
        row.addView(iconView, LinearLayout.LayoutParams(dp(22), dp(22)))
        row.addView(hSpacer(14))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = label; setTextColor(labelColor); textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(context).apply {
            text = sub; setTextColor(XColors.textSub); textSize = 13f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        contentCol.addView(row)
        contentCol.addView(insetDivider())
    }

    private fun switchMenuRow(
        svgPath: String?, inlineSvg: String?,
        label: String, sub: String, value: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val iconView: View = when {
            svgPath   != null -> svgAsset(svgPath, 22, XColors.textSub)
            inlineSvg != null -> svgInline(inlineSvg, 22, XColors.textSub)
            else              -> View(context)
        }
        row.addView(iconView, LinearLayout.LayoutParams(dp(22), dp(22)))
        row.addView(hSpacer(14))
        val subTv = TextView(context).apply {
            text = sub; setTextColor(XColors.textSub); textSize = 13f
        }
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = label; setTextColor(XColors.text); textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(subTv.also { it.setPadding(0, dp(2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(XSwitch(context, value) { v ->
            subTv.text = if (v) "Ativo" else "Inativo"; onChange(v)
        }, LinearLayout.LayoutParams(dp(50), dp(28)))
        contentCol.addView(row)
        contentCol.addView(insetDivider())
    }

    private fun aboutRow() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val logo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
            }
            try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open("logo.png"))) }
            catch (_: Exception) {}
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(hSpacer(14))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = "nuxxx"; setTextColor(XColors.text); textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(context).apply {
            text = "Versão 1.0.0 · Navegação privada"
            setTextColor(XColors.textSub); textSize = 13f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col)
        contentCol.addView(row)
        contentCol.addView(insetDivider())
    }

    // ── Sheets ────────────────────────────────────────────────────────────────

    private fun sheetParent(): ViewGroup {
        // Usa sempre o decorView para garantir que o overlay cobre tudo
        return activity.window.decorView as ViewGroup
    }

    private fun showSheet(content: LinearLayout) {
        val container = sheetParent()
        val overlay   = FrameLayout(context).apply { isClickable = true }

        val scrim = View(context).apply {
            setBackgroundColor(XColors.scrim); alpha = 0f
            setOnClickListener { dismissTopSheet() }
        }

        val sheet = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape       = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(dp(16f), dp(16f), dp(16f), dp(16f), 0f, 0f, 0f, 0f)
                setColor(XColors.surface)
            }
            isClickable = true
        }

        val scroll = NestedScrollView(context).apply { isFillViewport = true }
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sheet.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val screenH = context.resources.displayMetrics.heightPixels
        overlay.addView(scrim,  FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlay.addView(sheet,  FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            (screenH * 0.72f).toInt()).also { it.gravity = Gravity.BOTTOM })

        container.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        sheetStack.add(overlay)

        sheet.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                sheet.viewTreeObserver.removeOnGlobalLayoutListener(this)
                sheet.translationY = sheet.height.toFloat()
                sheet.animate().translationY(0f).setDuration(340)
                    .setInterpolator(DecelerateInterpolator(2.2f)).start()
            }
        })
        scrim.animate().alpha(1f).setDuration(280).start()
    }

    private fun dismissTopSheet() {
        val overlay   = sheetStack.removeLastOrNull() ?: return
        val container = sheetParent()
        val sheet     = overlay.getChildAt(1) as? FrameLayout
        val scrim     = overlay.getChildAt(0)

        sheet?.animate()?.translationY(sheet.height.toFloat())?.setDuration(260)
            ?.setInterpolator(AccelerateInterpolator(2f))?.start()
        scrim?.animate()?.alpha(0f)?.setDuration(260)?.start()
        handler.postDelayed({ container.removeView(overlay) }, 270)
    }

    private fun openWallpaperSheet() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sheetHandle()); content.addView(spacer(4))
        content.addView(sheetTitleView("Fundo de ecrã"))
        content.addView(spacer(4))

        val swRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        swRow.addView(svgAsset(SVG_WALLPAPER, 22, XColors.textSub), LinearLayout.LayoutParams(dp(22), dp(22)))
        swRow.addView(hSpacer(14))
        val wpSub = TextView(context).apply {
            text = if (ts.useWallpaper) "Imagem ativa" else "Fundo sólido"
            setTextColor(XColors.textSub); textSize = 13f
        }
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = "Usar imagem como fundo"; setTextColor(XColors.text)
            textSize = 15f; setTypeface(null, Typeface.BOLD)
        })
        col.addView(wpSub.also { it.setPadding(0, dp(2), 0, 0) })
        swRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        swRow.addView(XSwitch(context, ts.useWallpaper) { v ->
            thread { ts.setUseWallpaper(v) }
            wpSub.text = if (v) "Imagem ativa" else "Fundo sólido"
            if (v) { dismissTopSheet(); rebuildContent() }
        }, LinearLayout.LayoutParams(dp(50), dp(28)))
        content.addView(swRow)

        if (ts.useWallpaper) {
            content.addView(dividerLine()); content.addView(spacer(12))
            val gallery    = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
            val galleryRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), 0, dp(16), 0)
            }
            ThemeService.wallpapers.forEach { wp ->
                val sel   = wp == ts.bg
                val thumb = FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(14).toFloat()
                        setStroke(dp(2), if (sel) XColors.red else Color.TRANSPARENT)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(90), dp(168)).also { it.rightMargin = dp(10) }
                    setOnClickListener { thread { ts.setBg(wp) }; rebuildContent(); dismissTopSheet() }
                }
                val img = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
                    }
                    val assetPath = wp.removePrefix("assets/")
                    try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open(assetPath))) }
                    catch (_: Exception) {}
                }
                thumb.addView(img, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                galleryRow.addView(thumb)
            }
            gallery.addView(galleryRow)
            content.addView(gallery, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        content.addView(spacer(32))
        showSheet(content)
    }

    private fun openIconSheet() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sheetHandle()); content.addView(spacer(4))
        val hRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        hRow.addView(TextView(context).apply {
            text = "Ícone do app"; setTextColor(XColors.text)
            textSize = 20f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        hRow.addView(TextView(context).apply {
            text = "Fechar"; setTextColor(XColors.textSub); textSize = 15f
            setOnClickListener { dismissTopSheet() }
        })
        content.addView(hRow)
        content.addView(TextView(context).apply {
            text = "O app irá fechar e reabrir ao alterar o ícone."
            setTextColor(XColors.textSub); textSize = 13f
            setPadding(dp(16), dp(4), dp(16), dp(4))
        })
        content.addView(spacer(12))
        val iconsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
        }
        K_ICONS.forEach { iconData ->
            val sel  = activeIconId == iconData.id
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(18).toFloat()
                    setColor(if (sel) Color.argb(20, 29, 155, 240) else Color.TRANSPARENT)
                    setStroke(dp(2), if (sel) XColors.switchOn else Color.TRANSPARENT)
                }
                layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    activeIconId = iconData.id
                    activity.getPreferences(Context.MODE_PRIVATE).edit()
                        .putString("active_icon", iconData.id).apply()
                    rebuildContent(); dismissTopSheet()
                }
            }
            val img = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
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
                setTextColor(if (sel) XColors.switchOn else XColors.text)
                textSize = 13f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            })
            cell.addView(spacer(2))
            cell.addView(TextView(context).apply {
                text = iconData.sub; setTextColor(XColors.textSub)
                textSize = 11f; gravity = Gravity.CENTER
            })
            iconsRow.addView(cell)
        }
        content.addView(iconsRow); content.addView(spacer(32))
        showSheet(content)
    }

    private fun openPinSheet(unlock: Boolean, onDone: () -> Unit) {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sheetHandle()); content.addView(spacer(4))
        content.addView(sheetTitleView(if (unlock) "Verificar PIN" else "Alterar PIN"))
        content.addView(LockScreenView(context, unlock = unlock, onSuccess = {
            dismissTopSheet(); onDone()
        }))
        showSheet(content)
    }

    private fun openLockDelayPicker() {
        val opts   = listOf(0, 5, 10, 30, 60, 120, 300, 600, 1800, 3600, 7200, 14400)
        val labels = listOf("Imediato","5 seg","10 seg","30 seg","1 min","2 min",
            "5 min","10 min","30 min","1 hora","2 horas","4 horas")
        openPickerSheet("Bloquear após", labels, opts.indexOf(ts.lockDelay).coerceAtLeast(0)) { i ->
            thread { ts.setLockDelay(opts[i]) }; handler.post { rebuildContent() }
        }
    }

    private fun openEnginePicker() {
        val keys   = ThemeService.engines.keys.toList()
        val values = ThemeService.engines.values.toList()
        openPickerSheet("Motor de pesquisa", values, keys.indexOf(ts.engine).coerceAtLeast(0)) { i ->
            thread { ts.setEngine(keys[i]) }; handler.post { rebuildContent() }
        }
    }

    private fun openVolumePicker() {
        val items = List(10) { "${(it + 1) * 10}%" }
        openPickerSheet("Volume máximo", items, ((ts.maxVolume / 10) - 1).coerceIn(0, 9)) { i ->
            thread { ts.setMaxVolume((i + 1) * 10) }; handler.post { rebuildContent() }
        }
    }

    private fun openPickerSheet(title: String, items: List<String>, initial: Int, onOk: (Int) -> Unit) {
        var selectedIdx = initial
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(sheetHandle()); content.addView(spacer(4))
        val hRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        hRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        hRow.addView(TextView(context).apply {
            text = title; setTextColor(XColors.text); textSize = 17f; setTypeface(null, Typeface.BOLD)
        })
        hRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        hRow.addView(TextView(context).apply {
            text = "OK"; setTextColor(XColors.switchOn); textSize = 15f; setTypeface(null, Typeface.BOLD)
            setOnClickListener { onOk(selectedIdx); dismissTopSheet() }
        })
        content.addView(hRow)
        content.addView(dividerLine()); content.addView(spacer(8))
        val np = NumberPicker(context).apply {
            minValue = 0; maxValue = items.size - 1
            displayedValues = items.toTypedArray(); value = initial
            wrapSelectorWheel = false
            setOnValueChangedListener { _, _, nv -> selectedIdx = nv }
            try {
                val f = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                f.isAccessible = true
                (f.get(this) as? android.graphics.Paint)?.color = XColors.text
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
        content.addView(sheetHandle()); content.addView(spacer(4))
        content.addView(sheetTitleView("Limpar downloads"))
        content.addView(TextView(context).apply {
            text = "Esta ação apaga todos os ficheiros transferidos e não pode ser desfeita."
            setTextColor(XColors.textSub); textSize = 14f
            setPadding(dp(16), dp(4), dp(16), dp(16))
        })
        content.addView(dividerLine())
        xSheetAction(content, "Apagar tudo", XColors.textDestr) {
            thread {
                DownloadService.instance.items.toList()
                    .forEach { DownloadService.instance.delete(it.id) }
                handler.post { toast("Downloads limpos"); dismissTopSheet() }
            }
        }
        content.addView(dividerLine())
        xSheetAction(content, "Cancelar", XColors.text) { dismissTopSheet() }
        content.addView(spacer(8))
        showSheet(content)
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun sheetTitleView(text: String) = TextView(context).apply {
        this.text = text; setTextColor(XColors.text)
        textSize = 20f; setTypeface(null, Typeface.BOLD)
        setPadding(dp(16), dp(12), dp(16), dp(4))
    }

    private fun xSheetAction(parent: LinearLayout, label: String, color: Int, onClick: () -> Unit) {
        parent.addView(TextView(context).apply {
            text = label; setTextColor(color)
            textSize = 16f; setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
            background = rippleOrPress()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun sheetHandle() = FrameLayout(context).apply {
        setPadding(0, dp(12), 0, dp(4))
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3f)
                setColor(XColors.handle)
            }
        }, FrameLayout.LayoutParams(dp(32), dp(4)).also { it.gravity = Gravity.CENTER })
    }

    private fun dividerLine() = View(context).apply {
        setBackgroundColor(XColors.divider)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun insetDivider() = FrameLayout(context).apply {
        val line = View(context).apply { setBackgroundColor(XColors.divider) }
        addView(line, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.leftMargin = dp(52)
        })
    }

    private fun rippleOrPress(): android.graphics.drawable.Drawable {
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(XColors.rowPress)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(Color.TRANSPARENT)
        }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun svgAsset(path: String, sizeDp: Int, tint: Int) = activity.svgImageView(path, sizeDp, tint)

    private fun svgInline(svgStr: String, sizeDp: Int, tint: Int) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        try {
            val px      = dp(sizeDp)
            val hex     = String.format("#%06X", 0xFFFFFF and tint)
            val colored = svgStr.replace("currentColor", hex)
            val svg     = SVG.getFromString(colored)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp)); setImageBitmap(bmp)
        } catch (_: Exception) {}
    }

    private fun wallpaperLabel() = if (ts.useWallpaper) ts.bg.split("/").last() else "Fundo claro"
    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    private fun spacer(h: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h))
    }
    private fun hSpacer(w: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), 1)
    }
    private fun dp(v: Int)   = (v * density).toInt()
    private fun dp(v: Float) = v * density
}

// ── XSwitch (estilo iOS/X — azul) ────────────────────────────────────────────
class XSwitch(
    context: Context,
    private var checked: Boolean,
    private val onChange: (Boolean) -> Unit,
) : FrameLayout(context) {

    private val density   = context.resources.displayMetrics.density
    private fun dp(v: Int)   = (v * density).toInt()
    private fun dp(v: Float) = v * density

    private val trackView = View(context)
    private val thumbView = View(context)

    init {
        clipChildren = false
        updateTrack()
        addView(trackView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        thumbView.apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            elevation = dp(3f)
        }
        addView(thumbView, LayoutParams(dp(22), dp(22)))
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
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(14f)
            setColor(if (checked) Color.parseColor("#1D9BF0") else Color.parseColor("#CFD9DE"))
        }
    }

    private fun positionThumb(animate: Boolean) {
        val pad    = dp(3)
        val trackW = dp(50)
        val thumbW = dp(22)
        val travel = trackW - thumbW - pad * 2
        val targetX = (pad + if (checked) travel else 0).toFloat()
        val targetY = pad.toFloat()
        if (animate) {
            thumbView.animate().translationX(targetX).translationY(targetY)
                .setDuration(220).setInterpolator(DecelerateInterpolator(1.5f)).start()
        } else {
            thumbView.translationX = targetX
            thumbView.translationY = targetY
        }
    }
}