package com.doction.webviewapp.ui

import com.doction.webviewapp.theme.AppTheme
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
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
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.AppTheme
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.services.DownloadService
import com.doction.webviewapp.services.FaviconService
import com.doction.webviewapp.services.LockService
import com.doction.webviewapp.services.ThemeService
import kotlin.concurrent.thread

// ─── Caminhos SVG em assets/ ─────────────────────────────────────────────────
private const val SVG_BACK       = "icons/svg/settings/settings_back.svg"
private const val SVG_DARK       = "icons/svg/settings/settings_dark.svg"
private const val SVG_SUN        = "icons/svg/settings/settings_sun.svg"
private const val SVG_AUTO_THEME = "icons/svg/settings/settings_auto_theme.svg"
private const val SVG_WALLPAPER  = "icons/svg/settings/settings_wallpaper.svg"
private const val SVG_ENGINE     = "icons/svg/settings/settings_engine.svg"
private const val SVG_SCREENSHOT = "icons/svg/settings/settings_screenshot.svg"
private const val SVG_PIN        = "icons/svg/settings/settings_pin.svg"
private const val SVG_VOLUME     = "icons/svg/settings/settings_volume.svg"
private const val SVG_LOCK       = "icons/svg/settings/settings_lock.svg"
private const val SVG_TRASH      = "icons/svg/settings/settings_trash.svg"
private const val SVG_CHEVRON    = "icons/svg/settings/settings_chevron.svg"

// ─── SVGs Lucide inline ───────────────────────────────────────────────────────
private const val SVG_EYE = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg>"""
private const val SVG_REFRESH_CW = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/></svg>"""
private const val SVG_SCROLL_TEXT = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 12h-5"/><path d="M15 8h-5"/><path d="M19 17V5a2 2 0 0 0-2-2H4"/><path d="M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3"/></svg>"""
private const val SVG_TIMER = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="10" x2="14" y1="2" y2="2"/><line x1="12" x2="15" y1="14" y2="11"/><circle cx="12" cy="14" r="8"/></svg>"""

// ─── Dados dos ícones do app ──────────────────────────────────────────────────
private data class IconData(val id: String, val label: String, val sub: String, val asset: String)
private val K_ICONS = listOf(
    IconData("classic",  "Classic",  "Fundo vermelho • predefinido", "icons/ic_classic.png"),
    IconData("light",    "Light",    "Fundo branco",                  "icons/ic_light.png"),
    IconData("original", "Original", "Ícone original",                "icons/ic_original.png"),
)

@SuppressLint("ViewConstructor")
class SettingsPage(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val ts       = ThemeService.instance
    private val handler  = Handler(Looper.getMainLooper())

    // Estado
    private var lockEnabled   = false
    private var activeIconId  = "classic"
    private var themeMode     = if (ts.isDark) "dark" else "light"   // "dark"|"light"|"system"

    // Views que precisam de recolorir ao mudar tema
    private lateinit var rootScroll:   NestedScrollView
    private lateinit var contentCol:   LinearLayout
    private lateinit var appBarView:   FrameLayout

    init {
        setBackgroundColor(AppTheme.bg)
        loadState()
        buildUI()
    }

    // ─── Estado inicial ────────────────────────────────────────────────────────

    private fun loadState() {
        thread {
            lockEnabled  = LockService.instance.isEnabled()
            activeIconId = activity.getPreferences(Context.MODE_PRIVATE)
                .getString("active_icon", "classic") ?: "classic"
            handler.post { rebuildContent() }
        }
    }

    // ─── UI principal ──────────────────────────────────────────────────────────

    private fun buildUI() {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // AppBar
        appBarView = buildAppBar()
        root.addView(appBarView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        // Scroll
        rootScroll = NestedScrollView(context).apply { isFillViewport = true }
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(32))
        }
        rootScroll.addView(contentCol, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(rootScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        rebuildContent()
    }

    // ─── AppBar ────────────────────────────────────────────────────────────────

    private fun buildAppBar(): FrameLayout {
        val bar = FrameLayout(context).apply { setBackgroundColor(AppTheme.bg) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        val btnBack = FrameLayout(context).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { activity.closeSettings() }
        }
        btnBack.addView(
            svgAsset(SVG_BACK, 22, AppTheme.text),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER }
        )
        row.addView(btnBack, LinearLayout.LayoutParams(dp(46), dp(44)))
        row.addView(TextView(context).apply {
            text      = "Definições"
            setTextColor(AppTheme.text)
            textSize  = 17f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))
        return bar
    }

    // ─── Reconstrói todo o conteúdo (chamado ao mudar tema ou estado) ─────────

    private fun rebuildContent() {
        setBackgroundColor(AppTheme.bg)
        appBarView.setBackgroundColor(AppTheme.bg)
        // Atualiza cor do título e botão voltar
        (appBarView.getChildAt(0) as? LinearLayout)?.let { row ->
            (row.getChildAt(0) as? FrameLayout)?.let { btn ->
                (btn.getChildAt(0) as? ImageView)?.setColorFilter(AppTheme.text)
            }
            (row.getChildAt(1) as? TextView)?.setTextColor(AppTheme.text)
        }

        contentCol.removeAllViews()

        // ── APARÊNCIA ──────────────────────────────────────────────────────────
        contentCol.addView(sectionLabel("Aparência"))
        contentCol.addView(sectionCard(buildList {
            add(tapRow(SVG_BACK    /* placeholder */,
                svgAssetPath = themeSvgPath(),
                label        = "Tema",
                sub          = themeModeLabel(),
                onTap        = { openThemeSheet() }))
            add(divider())
            add(tapRow(svgAssetPath = SVG_WALLPAPER,
                label = "Fundo de ecrã",
                sub   = wallpaperLabel(),
                onTap = { openWallpaperSheet() }))
            add(divider())
            add(iconPreviewRow(
                label = "Ícone do app",
                sub   = K_ICONS.firstOrNull { it.id == activeIconId }?.label ?: "Classic",
                asset = K_ICONS.firstOrNull { it.id == activeIconId }?.asset ?: K_ICONS[0].asset,
                onTap = { openIconSheet() }))
        }))
        contentCol.addView(spacer(16))

        // ── SEGURANÇA ──────────────────────────────────────────────────────────
        contentCol.addView(sectionLabel("Segurança"))
        contentCol.addView(sectionCard(buildLockRows()))
        contentCol.addView(spacer(16))

        // ── PRIVACIDADE ────────────────────────────────────────────────────────
        contentCol.addView(sectionLabel("Privacidade"))
        contentCol.addView(sectionCard(buildList {
            add(switchRow(
                inlineSvg = SVG_EYE,
                label     = "Privacidade nos recentes",
                sub       = if (ts.privacyRecent) "App aparece em preto" else "Conteúdo visível",
                value     = ts.privacyRecent,
                onChange  = { v -> ts.setPrivacyRecent(v); rebuildContent() }))
            add(divider())
            add(switchRow(
                svgAssetPath = SVG_SCREENSHOT,
                label        = "Bloquear capturas",
                sub          = if (ts.noScreenshot) "Screenshots bloqueados" else "Screenshots permitidos",
                value        = ts.noScreenshot,
                onChange     = { v -> ts.setNoScreenshot(v); rebuildContent() }))
        }))
        contentCol.addView(spacer(16))

        // ── NAVEGAÇÃO ──────────────────────────────────────────────────────────
        contentCol.addView(sectionLabel("Navegação"))
        contentCol.addView(sectionCard(buildList {
            add(tapRow(
                svgAssetPath = SVG_ENGINE,
                label        = "Motor de pesquisa",
                sub          = ThemeService.engines[ts.engine] ?: "Google",
                onTap        = { openEnginePicker() }))
            add(divider())
            add(tapRow(
                svgAssetPath = SVG_VOLUME,
                label        = "Volume máximo",
                sub          = "${ts.maxVolume}%",
                onTap        = { openVolumePicker() }))
        }))
        contentCol.addView(spacer(16))

        // ── MANUTENÇÃO ─────────────────────────────────────────────────────────
        contentCol.addView(sectionLabel("Manutenção"))
        contentCol.addView(sectionCard(buildList {
            add(tapRow(
                inlineSvg = SVG_REFRESH_CW,
                label     = "Recarregar ícones",
                sub       = "Baixa novamente os favicons",
                onTap     = {
                    thread {
                        FaviconService.instance.clearAll()
                        FaviconService.instance.preloadAll()
                        handler.post { snack("Ícones recarregados") }
                    }
                }))
            add(divider())
            add(tapRow(
                svgAssetPath = SVG_TRASH,
                label        = "Limpar downloads",
                sub          = "Apaga todos os ficheiros",
                destructive  = true,
                onTap        = { openConfirmClearSheet() }))
        }))
        contentCol.addView(spacer(16))

        // ── SOBRE ──────────────────────────────────────────────────────────────
        contentCol.addView(sectionLabel("Sobre"))
        contentCol.addView(aboutCard())
        contentCol.addView(spacer(8))
        contentCol.addView(sectionCard(listOf(
            tapRow(
                inlineSvg = SVG_SCROLL_TEXT,
                label     = "Licenças de software",
                sub       = "Dependências open source",
                onTap     = { activity.openLicenses() })
        )))
        contentCol.addView(spacer(16))
    }

    // ─── Secção de segurança (condicional ao lockEnabled) ─────────────────────

    private fun buildLockRows(): List<View> = buildList {
        add(switchRow(
            svgAssetPath = SVG_LOCK,
            label        = "Bloquear app",
            sub          = if (lockEnabled) "PIN obrigatório" else "Sem bloqueio",
            value        = lockEnabled,
            onChange     = { v ->
                openPinSheet(unlock = true) {
                    thread {
                        LockService.instance.setEnabled(v)
                        handler.post { lockEnabled = v; rebuildContent() }
                    }
                }
            }))
        if (lockEnabled) {
            add(divider())
            add(tapRow(
                svgAssetPath = SVG_PIN,
                label        = "Alterar PIN",
                sub          = "Muda o código de acesso",
                onTap        = { openPinSheet(unlock = false) { snack("PIN alterado") } }))
            add(divider())
            add(tapRow(
                inlineSvg = SVG_TIMER,
                label     = "Bloquear após",
                sub       = ts.lockDelayLabel,
                onTap     = { openLockDelayPicker() }))
        }
    }

    // ─── Bottom Sheets ─────────────────────────────────────────────────────────

    private fun openThemeSheet() {
        val sheet = buildSheet { col ->
            col.addView(sheetHandle())
            col.addView(spacer(14))
            col.addView(TextView(context).apply {
                text     = "Tema"
                setTextColor(AppTheme.text)
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(20), 0, dp(20), 0)
            })
            col.addView(spacer(8))

            fun themeOption(svgPath: String, label: String, sub: String, mode: String) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity     = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    setOnClickListener {
                        themeMode = mode
                        when (mode) {
                            "dark"   -> ts.setDark(true)
                            "light"  -> ts.setDark(false)
                            "system" -> ts.setDark(true) // segue sistema — simplificado
                        }
                        rebuildContent()
                        dismissSheet()
                    }
                    isClickable = true; isFocusable = true
                }
                val sel = themeMode == mode
                row.addView(svgAsset(svgPath, 20, if (sel) AppTheme.ytRed else AppTheme.iconSub))
                row.addView(hSpacer(14))
                val textCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                textCol.addView(TextView(context).apply {
                    text = label; setTextColor(if (sel) AppTheme.ytRed else AppTheme.text)
                    textSize = 14f; setTypeface(typeface, Typeface.BOLD)
                })
                textCol.addView(spacer(2))
                textCol.addView(TextView(context).apply {
                    text = sub; setTextColor(AppTheme.textSecondary); textSize = 12f
                })
                row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                // Círculo radio
                val circle = View(context).apply {
                    background = GradientDrawable().also {
                        it.shape     = GradientDrawable.OVAL
                        it.setColor(if (sel) AppTheme.ytRed else Color.TRANSPARENT)
                        it.setStroke(dp(2), if (sel) AppTheme.ytRed else Color.argb(90,
                            Color.red(AppTheme.textSecondary),
                            Color.green(AppTheme.textSecondary),
                            Color.blue(AppTheme.textSecondary)))
                    }
                }
                row.addView(circle, LinearLayout.LayoutParams(dp(22), dp(22)))
                col.addView(row)
                if (mode != "dark") col.addView(sheetDivider())
            }

            themeOption(SVG_AUTO_THEME, "Automático",  "Segue as definições do sistema", "system")
            themeOption(SVG_SUN,        "Tema claro",  "Interface sempre clara",           "light")
            themeOption(SVG_DARK,       "Tema escuro", "Interface sempre escura",           "dark")
            col.addView(spacer(12))
        }
        showSheet(sheet, 0.42f)
    }

    private fun openWallpaperSheet() {
        val sheet = buildSheet { col ->
            col.addView(sheetHandle())
            col.addView(spacer(12))
            col.addView(TextView(context).apply {
                text = "Fundo de ecrã"; setTextColor(AppTheme.text)
                textSize = 16f; setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            col.addView(spacer(8))

            // Toggle switch
            val switchRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(4), dp(16), dp(4))
            }
            switchRow.addView(svgAsset(SVG_WALLPAPER, 20, AppTheme.textSecondary))
            switchRow.addView(hSpacer(14))
            val textCol2 = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val wpLabel = TextView(context).apply {
                text = "Usar imagem como fundo"; setTextColor(AppTheme.text)
                textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            }
            val wpSub = TextView(context).apply {
                text = if (ts.useWallpaper) "Imagem ativa" else if (ts.isDark) "Fundo escuro sólido" else "Fundo claro sólido"
                setTextColor(AppTheme.textSecondary); textSize = 12f
            }
            textCol2.addView(wpLabel)
            textCol2.addView(spacer(2))
            textCol2.addView(wpSub)
            switchRow.addView(textCol2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val miniSw = MiniSwitch(context, ts.useWallpaper) { v ->
                thread { ts.setUseWallpaper(v) }
                wpSub.text = if (v) "Imagem ativa" else if (ts.isDark) "Fundo escuro sólido" else "Fundo claro sólido"
                if (v) rebuildContent()
            }
            switchRow.addView(miniSw, LinearLayout.LayoutParams(dp(44), dp(25)))
            col.addView(switchRow)

            // Galeria de wallpapers
            if (ts.useWallpaper) {
                col.addView(spacer(14))
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
                        background = GradientDrawable().also {
                            it.shape        = GradientDrawable.RECTANGLE
                            it.cornerRadius = dp(14).toFloat()
                            it.setStroke(dp(2), if (sel) AppTheme.ytRed else Color.TRANSPARENT)
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(90), dp(168)).also {
                            it.rightMargin = dp(10)
                        }
                        setOnClickListener {
                            thread { ts.setBg(wp) }
                            rebuildContent()
                            dismissSheet()
                        }
                    }
                    val img = ImageView(context).apply {
                        scaleType     = ImageView.ScaleType.CENTER_CROP
                        clipToOutline = true
                        background    = GradientDrawable().also {
                            it.shape        = GradientDrawable.RECTANGLE
                            it.cornerRadius = dp(12).toFloat()
                        }
                        try {
                            val bmp = BitmapFactory.decodeStream(context.assets.open(
                                wp.removePrefix("assets/")))
                            setImageBitmap(bmp)
                        } catch (_: Exception) {}
                    }
                    thumb.addView(img, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT))
                    galleryRow.addView(thumb)
                }
                gallery.addView(galleryRow)
                col.addView(gallery, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            col.addView(spacer(20))
        }
        showSheet(sheet, if (ts.useWallpaper) 0.60f else 0.40f)
    }

    private fun openIconSheet() {
        val sheet = buildSheet { col ->
            col.addView(sheetHandle())
            col.addView(spacer(14))
            val hRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(20), 0, dp(20), 0)
            }
            hRow.addView(TextView(context).apply {
                text = "Ícone do app"; setTextColor(AppTheme.text)
                textSize = 17f; setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            hRow.addView(TextView(context).apply {
                text = "Fechar"; setTextColor(AppTheme.textSecondary); textSize = 15f
                setOnClickListener { dismissSheet() }
            })
            col.addView(hRow)
            col.addView(spacer(4))
            col.addView(TextView(context).apply {
                text = "O app irá fechar e reabrir ao alterar o ícone. É o comportamento normal do Android."
                setTextColor(AppTheme.textSecondary); textSize = 12f
                setPadding(dp(20), 0, dp(20), 0)
            })
            col.addView(spacer(14))

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
                    background  = GradientDrawable().also {
                        it.shape        = GradientDrawable.RECTANGLE
                        it.cornerRadius = dp(18).toFloat()
                        it.setColor(if (sel) Color.argb(31, 255, 0, 0) else Color.TRANSPARENT)
                        it.setStroke(dp(2), if (sel) AppTheme.ytRed else Color.TRANSPARENT)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
                    setOnClickListener {
                        activeIconId = iconData.id
                        activity.getPreferences(Context.MODE_PRIVATE).edit()
                            .putString("active_icon", iconData.id).apply()
                        rebuildContent()
                        dismissSheet()
                    }
                }
                val img = ImageView(context).apply {
                    scaleType     = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    background    = GradientDrawable().also {
                        it.shape        = GradientDrawable.RECTANGLE
                        it.cornerRadius = dp(18).toFloat()
                    }
                    try {
                        val bmp = BitmapFactory.decodeStream(context.assets.open(iconData.asset))
                        setImageBitmap(bmp)
                    } catch (_: Exception) {}
                }
                cell.addView(img, LinearLayout.LayoutParams(dp(64), dp(64)))
                cell.addView(spacer(8))
                cell.addView(TextView(context).apply {
                    text = iconData.label
                    setTextColor(if (sel) AppTheme.ytRed else AppTheme.text)
                    textSize = 13f; setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                })
                cell.addView(spacer(2))
                cell.addView(TextView(context).apply {
                    text = iconData.sub; setTextColor(AppTheme.textSecondary)
                    textSize = 10f; gravity = Gravity.CENTER
                })
                iconsRow.addView(cell)
            }
            col.addView(iconsRow)
            col.addView(spacer(20))
        }
        showSheet(sheet, 0.46f)
    }

    private fun openPinSheet(unlock: Boolean, onDone: () -> Unit) {
        val sheet = buildSheet { col ->
            col.addView(sheetHandle())
            col.addView(spacer(14))
            col.addView(LockScreenView(context,
                unlock = unlock,
                onSuccess = { dismissSheet(); onDone() }))
        }
        showSheet(sheet, 0.9f)
    }

    private fun openLockDelayPicker() {
        val opts   = listOf(0,5,10,30,60,120,300,600,1800,3600,7200,14400)
        val labels = listOf("Imediato","5 seg","10 seg","30 seg","1 min","2 min","5 min","10 min","30 min","1 hora","2 horas","4 horas")
        var idx    = opts.indexOf(ts.lockDelay).coerceAtLeast(0)
        openPickerSheet("Bloquear após", labels, idx) { i ->
            idx = i
            thread { ts.setLockDelay(opts[i]) }
            handler.post { rebuildContent() }
        }
    }

    private fun openEnginePicker() {
        val keys   = ThemeService.engines.keys.toList()
        val values = ThemeService.engines.values.toList()
        val idx    = keys.indexOf(ts.engine).coerceAtLeast(0)
        openPickerSheet("Motor de pesquisa", values, idx) { i ->
            thread { ts.setEngine(keys[i]) }
            handler.post { rebuildContent() }
        }
    }

    private fun openVolumePicker() {
        val items = List(10) { "${(it + 1) * 10}%" }
        val idx   = ((ts.maxVolume / 10) - 1).coerceIn(0, 9)
        openPickerSheet("Volume máximo", items, idx) { i ->
            thread { ts.setMaxVolume((i + 1) * 10) }
            handler.post { rebuildContent() }
        }
    }

    private fun openPickerSheet(title: String, items: List<String>, initial: Int, onOk: (Int) -> Unit) {
        var selectedIdx = initial
        val sheet = buildSheet { col ->
            col.addView(sheetHandle())
            val hRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }
            hRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            hRow.addView(TextView(context).apply {
                text = title; setTextColor(AppTheme.text)
                textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            })
            hRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            hRow.addView(TextView(context).apply {
                text = "OK"
                setTextColor(AppTheme.ytRed)
                textSize = 15f; setTypeface(typeface, Typeface.BOLD)
                setOnClickListener { onOk(selectedIdx); dismissSheet() }
            })
            col.addView(hRow)

            // NumberPicker como wheel
            val np = NumberPicker(context).apply {
                minValue     = 0
                maxValue     = items.size - 1
                displayedValues = items.toTypedArray()
                value        = initial
                wrapSelectorWheel = false
                setOnValueChangedListener { _, _, newVal -> selectedIdx = newVal }
                // Cor do texto via reflection
                try {
                    val f = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                    f.isAccessible = true
                    (f.get(this) as? android.graphics.Paint)?.color = AppTheme.text
                } catch (_: Exception) {}
            }
            col.addView(np, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(176)).also {
                it.leftMargin  = dp(16); it.rightMargin = dp(16)
            })
            col.addView(spacer(8))
        }
        showSheet(sheet, 0.42f)
    }

    private fun openConfirmClearSheet() {
        val sheet = buildSheet { col ->
            col.addView(sheetHandle())
            col.addView(spacer(14))
            col.addView(TextView(context).apply {
                text = "Limpar downloads?"; setTextColor(AppTheme.text)
                textSize = 16f; setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            col.addView(spacer(16))
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), 0, dp(16), 0)
            }
            // Cancelar
            val btnCancel = TextView(context).apply {
                text = "Cancelar"; setTextColor(AppTheme.text)
                textSize = 14f; gravity = Gravity.CENTER
                background = GradientDrawable().also {
                    it.shape        = GradientDrawable.RECTANGLE
                    it.cornerRadius = dp(12).toFloat()
                    it.setStroke(dp(1), AppTheme.divider)
                }
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { dismissSheet() }
            }
            btnRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            btnRow.addView(hSpacer(12))
            // Apagar tudo
            val btnDelete = TextView(context).apply {
                text = "Apagar tudo"; setTextColor(Color.WHITE)
                textSize = 14f; gravity = Gravity.CENTER
                background = GradientDrawable().also {
                    it.shape        = GradientDrawable.RECTANGLE
                    it.cornerRadius = dp(12).toFloat()
                    it.setColor(AppTheme.error)
                }
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener {
                    thread {
                        DownloadService.instance.items.toList().forEach {
                            DownloadService.instance.delete(it.id)
                        }
                        handler.post { snack("Downloads limpos"); dismissSheet() }
                    }
                }
            }
            btnRow.addView(btnDelete, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            col.addView(btnRow)
            col.addView(spacer(32))
        }
        showSheet(sheet, 0.36f)
    }

    // ─── Sheet engine (equivalente ao showModalBottomSheet do Flutter) ─────────

    private var currentSheetOverlay: FrameLayout? = null

    private fun buildSheet(build: (LinearLayout) -> Unit): LinearLayout {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        build(col)
        return col
    }

    private fun showSheet(content: LinearLayout, heightFactor: Float) {
        dismissSheet()
        val screenH = context.resources.displayMetrics.heightPixels
        val maxH    = (screenH * heightFactor).toInt()

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(87, 0, 0, 0))
            setOnClickListener { dismissSheet() }
        }

        val sheetBg = FrameLayout(context).apply {
            background = GradientDrawable().also {
                it.shape = GradientDrawable.RECTANGLE
                it.cornerRadii = floatArrayOf(dp(16f),dp(16f),dp(16f),dp(16f),0f,0f,0f,0f)
                it.setColor(AppTheme.sheet)
            }
            setOnClickListener { /* consome toque */ }
        }

        val scroll = NestedScrollView(context).apply { isFillViewport = true }
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        sheetBg.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        overlay.addView(sheetBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, maxH).also {
            it.gravity = Gravity.BOTTOM
        })

        // Anima de baixo para cima
        sheetBg.translationY = maxH.toFloat()
        sheetBg.animate().translationY(0f).setDuration(320)
            .setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()

        activity.addContentOverlay(overlay)
        currentSheetOverlay = overlay
    }

    private fun dismissSheet() {
        val overlay = currentSheetOverlay ?: return
        val sheetBg = overlay.getChildAt(0) as? FrameLayout ?: return
        val h = sheetBg.height.toFloat().coerceAtLeast(100f)
        sheetBg.animate().translationY(h).setDuration(280)
            .setInterpolator(android.view.animation.AccelerateInterpolator(2f))
            .withEndAction {
                activity.removeContentOverlay(overlay)
                currentSheetOverlay = null
            }.start()
    }

    // ─── Widgets helpers ───────────────────────────────────────────────────────

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text.uppercase()
        setTextColor(AppTheme.textSecondary)
        textSize     = 12f
        letterSpacing = 0.08f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(16), dp(6), dp(16), dp(6))
    }

    private fun sectionCard(rows: List<View>): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background  = GradientDrawable().also {
                it.shape        = GradientDrawable.RECTANGLE
                it.cornerRadius = dp(16).toFloat()
                it.setColor(AppTheme.card)
            }
        }
        rows.forEach { col.addView(it) }
        return FrameLayout(context).apply {
            addView(col, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).also {
                it.leftMargin  = dp(16); it.rightMargin = dp(16)
            })
        }
    }

    private fun tapRow(
        svgAssetPath: String?  = null,
        inlineSvg:    String?  = null,
        label:        String,
        sub:          String,
        destructive:  Boolean  = false,
        onTap:        () -> Unit,
    ): View {
        val iconColor = if (destructive) AppTheme.error else AppTheme.textSecondary
        val textColor = if (destructive) AppTheme.error else AppTheme.text
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setOnClickListener { onTap() }
            isClickable = true; isFocusable = true
        }
        row.addView(svgView(svgAssetPath, inlineSvg, 20, iconColor))
        row.addView(hSpacer(14))
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(context).apply {
            text = label; setTextColor(textColor); textSize = 14f
        })
        tc.addView(spacer(2))
        tc.addView(TextView(context).apply {
            text = sub; setTextColor(AppTheme.textSecondary); textSize = 12f
        })
        row.addView(tc, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(svgView(SVG_CHEVRON, null, 16, Color.argb(128,
            Color.red(AppTheme.textSecondary),
            Color.green(AppTheme.textSecondary),
            Color.blue(AppTheme.textSecondary))))
        return row
    }

    private fun switchRow(
        svgAssetPath: String?  = null,
        inlineSvg:    String?  = null,
        label:        String,
        sub:          String,
        value:        Boolean,
        onChange:     (Boolean) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
        }
        row.addView(svgView(svgAssetPath, inlineSvg, 20, AppTheme.textSecondary))
        row.addView(hSpacer(14))
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(context).apply {
            text = label; setTextColor(AppTheme.text); textSize = 14f
        })
        tc.addView(spacer(2))
        tc.addView(TextView(context).apply {
            text = sub; setTextColor(AppTheme.textSecondary); textSize = 12f
        })
        row.addView(tc, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(MiniSwitch(context, value, onChange), LinearLayout.LayoutParams(dp(44), dp(25)))
        return row
    }

    private fun iconPreviewRow(label: String, sub: String, asset: String, onTap: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setOnClickListener { onTap() }
            isClickable = true; isFocusable = true
        }
        val img = ImageView(context).apply {
            scaleType     = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background    = GradientDrawable().also {
                it.shape = GradientDrawable.RECTANGLE; it.cornerRadius = dp(5).toFloat()
            }
            try {
                setImageBitmap(BitmapFactory.decodeStream(context.assets.open(asset)))
            } catch (_: Exception) {}
        }
        row.addView(img, LinearLayout.LayoutParams(dp(20), dp(20)))
        row.addView(hSpacer(14))
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(context).apply { text = label; setTextColor(AppTheme.text); textSize = 14f })
        tc.addView(spacer(2))
        tc.addView(TextView(context).apply { text = sub; setTextColor(AppTheme.textSecondary); textSize = 12f })
        row.addView(tc, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(svgView(SVG_CHEVRON, null, 16, Color.argb(128,
            Color.red(AppTheme.textSecondary),
            Color.green(AppTheme.textSecondary),
            Color.blue(AppTheme.textSecondary))))
        return row
    }

    private fun aboutCard(): View {
        val wrap = FrameLayout(context)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background  = GradientDrawable().also {
                it.shape        = GradientDrawable.RECTANGLE
                it.cornerRadius = dp(16).toFloat()
                it.setColor(AppTheme.card)
            }
        }
        val logo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
            background = GradientDrawable().also {
                it.shape = GradientDrawable.RECTANGLE; it.cornerRadius = dp(12).toFloat()
            }
            try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open("logo.png"))) }
            catch (_: Exception) {}
        }
        card.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
        card.addView(hSpacer(14))
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(TextView(context).apply {
            text = "nuxxx"; setTextColor(AppTheme.text); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
        })
        tc.addView(spacer(2))
        tc.addView(TextView(context).apply {
            text = "Versão 1.0.0 · Navegação privada"; setTextColor(AppTheme.textSecondary); textSize = 12f
        })
        card.addView(tc)
        wrap.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin = dp(16); it.rightMargin = dp(16)
        })
        return wrap
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(AppTheme.divider)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.marginStart = dp(52); it.marginEnd = dp(16)
        }
    }

    private fun sheetDivider() = View(context).apply {
        setBackgroundColor(AppTheme.divider)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.marginStart = dp(52); it.marginEnd = dp(16)
        }
    }

    private fun sheetHandle() = FrameLayout(context).apply {
        setPadding(0, dp(10), 0, 0)
        val handle = View(context).apply {
            background = GradientDrawable().also {
                it.shape        = GradientDrawable.RECTANGLE
                it.cornerRadius = dp(2).toFloat()
                it.setColor(AppTheme.sheetHandle)
            }
        }
        addView(handle, FrameLayout.LayoutParams(dp(36), dp(4)).also { it.gravity = Gravity.CENTER })
    }

    // ─── SVG helpers ──────────────────────────────────────────────────────────

    // SVG de assets/ — usa svgImageView do MainActivity (igual ao resto do app)
    private fun svgAsset(path: String, sizeDp: Int, tint: Int) =
        activity.svgImageView(path, sizeDp, tint)

    // SVG inline (Lucide) — renderiza via AndroidSVG substituindo currentColor
    private fun svgInline(svgStr: String, sizeDp: Int, tint: Int) =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            try {
                val sizePx = dp(sizeDp)
                val hex    = String.format("#%06X", 0xFFFFFF and tint)
                val colored = svgStr.replace("currentColor", hex)
                val svg    = SVG.getFromString(colored)
                svg.documentWidth  = sizePx.toFloat()
                svg.documentHeight = sizePx.toFloat()
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bmp))
                setImageBitmap(bmp)
            } catch (_: Exception) {}
        }

    // Escolhe automaticamente entre asset e inline
    private fun svgView(assetPath: String?, inlineSvg: String?, sizeDp: Int, tint: Int): ImageView =
        when {
            assetPath != null -> svgAsset(assetPath, sizeDp, tint)
            inlineSvg != null -> svgInline(inlineSvg, sizeDp, tint)
            else -> ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            }
        }

    // ─── Tema helpers ──────────────────────────────────────────────────────────

    private fun themeSvgPath() = when (themeMode) {
        "system" -> SVG_AUTO_THEME
        "light"  -> SVG_SUN
        else     -> SVG_DARK
    }

    private fun themeModeLabel() = when (themeMode) {
        "system" -> "Automático (sistema)"
        "light"  -> "Tema claro"
        else     -> "Tema escuro"
    }

    private fun wallpaperLabel() =
        if (ts.useWallpaper) ts.bg.split("/").last()
        else if (ts.isDark) "Fundo escuro" else "Fundo claro"

    // ─── Snackbar ──────────────────────────────────────────────────────────────

    private fun snack(msg: String) {
        val toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
        toast.show()
    }

    // ─── Spacers ───────────────────────────────────────────────────────────────

    private fun spacer(h: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }
    private fun hSpacer(w: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), 1)
    }

    private fun dp(v: Int)   = (v * context.resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * context.resources.displayMetrics.density)
}

// ─────────────────────────────────────────────────────────────────────────────
// MiniSwitch — animado, espelho 1:1 do _MiniSwitch Flutter
// ─────────────────────────────────────────────────────────────────────────────
class MiniSwitch(
    context: Context,
    private var checked: Boolean,
    private val onChange: (Boolean) -> Unit,
) : FrameLayout(context) {

    private val thumbView: View
    private val trackView: View = this

    init {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        fun dp(v: Float) = (v * density)

        // Fundo track
        updateTrack()

        // Thumb branco
        thumbView = View(context).apply {
            background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(android.graphics.Color.WHITE)
            }
            elevation = dp(3f)
        }
        addView(thumbView, LayoutParams(dp(19), dp(19)))
        positionThumb(animate = false)

        setOnClickListener {
            checked = !checked
            updateTrack()
            positionThumb(animate = true)
            onChange(checked)
        }
    }

    private fun updateTrack() {
        background = GradientDrawable().also {
            it.shape        = GradientDrawable.RECTANGLE
            it.cornerRadius = (25 * context.resources.displayMetrics.density)
            it.setColor(if (checked) AppTheme.ytRed else AppTheme.cardAlt)
        }
    }

    private fun positionThumb(animate: Boolean) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val pad    = dp(3)
        val travel = dp(44) - dp(19) - pad * 2
        val targetX = (pad + if (checked) travel else 0).toFloat()
        val targetY = pad.toFloat()
        if (animate) {
            thumbView.animate().translationX(targetX).translationY(targetY)
                .setDuration(220)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                .start()
        } else {
            thumbView.translationX = targetX
            thumbView.translationY = targetY
        }
    }
}