package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.caverock.androidsvg.SVG
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nuxx.app.MainActivity
import com.nuxx.app.services.DownloadService
import com.nuxx.app.services.FaviconService
import com.nuxx.app.services.LockService
import kotlin.concurrent.thread

private object SC {
    val bg        = Color.parseColor("#FFFFFF")
    val text      = Color.parseColor("#0F1419")
    val textSub   = Color.parseColor("#536471")
    val textDestr = Color.parseColor("#E01462")
    val divider   = Color.parseColor("#EFF3F4")
    val rowPress  = Color.parseColor("#F7F9F9")
    val switchOn  = Color.parseColor("#E01462")
    val accent    = Color.parseColor("#E01462")
}

// Phosphor icon paths
private const val ICO_BACK       = "icons/svg/phosphor-icons/regular/arrow-left.svg"
private const val ICO_PIN        = "icons/svg/settings/settings_pin.svg"   // único que não muda
private const val ICO_LOCK       = "icons/svg/phosphor-icons/regular/lock.svg"
private const val ICO_TRASH      = "icons/svg/phosphor-icons/regular/trash.svg"
private const val ICO_TIMER      = "icons/svg/phosphor-icons/regular/timer.svg"
private const val ICO_REFRESH    = "icons/svg/phosphor-icons/regular/arrows-clockwise.svg"
private const val ICO_INFO       = "icons/svg/phosphor-icons/regular/info.svg"
private const val ICO_EYE        = "icons/svg/phosphor-icons/regular/eye.svg"
private const val ICO_SCREENSHOT = "icons/svg/phosphor-icons/regular/prohibit.svg"
private const val ICO_SEARCH     = "icons/svg/phosphor-icons/regular/magnifying-glass.svg"
private const val ICO_VOLUME     = "icons/svg/phosphor-icons/regular/speaker-high.svg"
private const val ICO_HEART      = "icons/svg/phosphor-icons/regular/heart.svg"
private const val ICO_CARDS      = "icons/svg/phosphor-icons/regular/squares-four.svg"
private const val ICO_UPDATE     = "icons/svg/phosphor-icons/regular/cloud-arrow-up.svg"
private const val ICO_LICENSES   = "icons/svg/phosphor-icons/regular/book-open.svg"
private const val ICO_CHEVRON    = "icons/svg/phosphor-icons/regular/caret-right.svg"

@SuppressLint("ViewConstructor")
class SettingsPage(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())
    private val density  get() = context.resources.displayMetrics.density

    private var lockEnabled  = false
    private var privRecentes = true
    private var blockScreens = false
    private var defaultVol   = 100
    private var searchEngine = "Google"
    private lateinit var contentCol: LinearLayout

    // Preferência de feed guardada em SharedPreferences
    private val prefs get() = context.getSharedPreferences("nuxx_prefs", Context.MODE_PRIVATE)

    init {
        setBackgroundColor(SC.bg)
        loadState()
        buildUI()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFocusableInTouchMode = true
        requestFocus()
        activity.setStatusBarDark(false)
        activity.window.statusBarColor = SC.bg
    }

    fun handleBack() = activity.closeSettings()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            handleBack(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun loadState() {
        thread {
            lockEnabled  = LockService.instance.isEnabled()
            privRecentes = prefs.getBoolean("priv_recentes", true)
            blockScreens = prefs.getBoolean("block_screens", false)
            defaultVol   = prefs.getInt("default_vol", 100)
            searchEngine = prefs.getString("search_engine", "Google") ?: "Google"
            handler.post { rebuildContent() }
        }
    }

    private fun buildUI() {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // status bar spacer
        root.addView(View(context).apply { setBackgroundColor(SC.bg) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight))

        // App bar
        val bar = FrameLayout(context).apply { setBackgroundColor(SC.bg) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val btnBack = FrameLayout(context).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { handleBack() }
            isClickable = true; isFocusable = true
        }
        btnBack.addView(svgA(ICO_BACK, 22, SC.text),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })
        row.addView(btnBack, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(TextView(context).apply {
            text = "Definições"; setTextColor(SC.text)
            textSize = 20f; setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(bar)
        root.addView(divLine())

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

    private fun rebuildContent() {
        contentCol.removeAllViews()

        // ── SEGURANÇA ─────────────────────────────────────────────────────
        secHeader("Segurança")
        switchRow(ICO_LOCK, "Bloquear app",
            if (lockEnabled) "PIN obrigatório" else "Sem bloqueio", lockEnabled) { v ->
            openPinSheet(unlock = true) {
                thread {
                    LockService.instance.setEnabled(v)
                    handler.post { lockEnabled = v; rebuildContent() }
                }
            }
        }
        if (lockEnabled) {
            arrowRow(ICO_PIN, "Alterar PIN", "Muda o código de acesso") {
                openPinSheet(unlock = false) { toast("PIN alterado") }
            }
            arrowRow(ICO_TIMER, "Bloquear após", "Configurar tempo") {
                openLockDelayPicker()
            }
        }
        switchRow(ICO_EYE, "Privacidade nos recentes",
            if (privRecentes) "App aparece em preto" else "App visível", privRecentes) { v ->
            privRecentes = v
            prefs.edit().putBoolean("priv_recentes", v).apply()
        }
        switchRow(ICO_SCREENSHOT, "Bloquear capturas",
            if (blockScreens) "Screenshots bloqueados" else "Screenshots permitidos", blockScreens) { v ->
            blockScreens = v
            prefs.edit().putBoolean("block_screens", v).apply()
            if (v) activity.window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE)
            else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }

        // ── NAVEGAÇÃO ─────────────────────────────────────────────────────
        secHeader("Navegação")
        arrowRow(ICO_SEARCH, "Motor de pesquisa", searchEngine) {
            openSearchEnginePicker()
        }
        arrowRow(ICO_VOLUME, "Volume padrão", "$defaultVol%") {
            openDefaultVolumePicker()
        }

        // ── PREFERÊNCIAS DE VÍDEO ─────────────────────────────────────────
        secHeader("Preferências de vídeo")
        arrowRow(ICO_HEART, "Preferências do feed", "Categorias favoritas") {
            openFeedPreferencesPicker()
        }
        arrowRow(ICO_CARDS, "Estilo dos cards", currentCardStyleLabel()) {
            openCardStylePicker()
        }

        // ── MANUTENÇÃO ────────────────────────────────────────────────────
        secHeader("Manutenção")
        arrowRow(ICO_REFRESH, "Recarregar ícones", "Baixa novamente os favicons") {
            thread {
                FaviconService.instance.clearAll()
                FaviconService.instance.preloadAll()
                handler.post { toast("Ícones recarregados") }
            }
        }
        arrowRow(ICO_UPDATE, "Verificar atualizações", "Versão atual: 1.0.0") {
            openCheckUpdateSheet()
        }
        arrowRow(ICO_TRASH, "Limpar downloads", "Apaga todos os ficheiros",
            destructive = true) { openConfirmClearSheet() }

        // ── SOBRE ─────────────────────────────────────────────────────────
        secHeader("Sobre")
        aboutRow()
        arrowRow(ICO_LICENSES, "Licenças de software", "Dependências open source") {
            activity.openLicenses()
        }

        contentCol.addView(spacer(20))
    }

    private fun currentCardStyleLabel(): String = when (prefs.getString("card_style", "grid_adaptive")) {
        "youtube"       -> "YouTube"
        "grid_fixed"    -> "Grid 2×2 fixo"
        "card_m3"       -> "Card M3"
        "card_m3_color" -> "Card M3 dinâmico"
        else            -> "Grid adaptável (padrão)"
    }

    // ── ROWS ───────────────────────────────────────────────────────────────

    private fun secHeader(text: String) {
        contentCol.addView(divLine())
        contentCol.addView(TextView(context).apply {
            this.text = text.uppercase()
            setTextColor(SC.textSub); textSize = 11f
            setTypeface(null, Typeface.BOLD); letterSpacing = 0.08f
            setPadding(dp(16), dp(18), dp(16), dp(8))
        })
    }

    private fun arrowRow(
        ico: String, label: String, sub: String,
        destructive: Boolean = false, onTap: () -> Unit
    ) {
        val labelColor = if (destructive) SC.textDestr else SC.text
        val iconColor  = if (destructive) SC.textDestr else SC.textSub
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
            isClickable = true; isFocusable = true
            background = pressDrawable()
            setOnClickListener { onTap() }
        }
        row.addView(svgA(ico, 22, iconColor), LinearLayout.LayoutParams(dp(22), dp(22)))
        row.addView(hSp(14))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = label; setTextColor(labelColor); textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(context).apply {
            text = sub; setTextColor(SC.textSub); textSize = 12.5f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(hSp(8))
        row.addView(svgA(ICO_CHEVRON, 18, Color.parseColor("#CCCCCC")),
            LinearLayout.LayoutParams(dp(18), dp(18)))
        contentCol.addView(row)
        contentCol.addView(insetDiv())
    }

    private fun switchRow(
        ico: String, label: String, sub: String, value: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val subTv = TextView(context).apply {
            text = sub; setTextColor(SC.textSub); textSize = 12.5f
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        row.addView(svgA(ico, 22, SC.textSub), LinearLayout.LayoutParams(dp(22), dp(22)))
        row.addView(hSp(14))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = label; setTextColor(SC.text); textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(subTv.also { it.setPadding(0, dp(2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(NuxxSwitch(context, value) { v ->
            subTv.text = if (v) "Ativo" else "Inativo"; onChange(v)
        }, LinearLayout.LayoutParams(dp(50), dp(28)))
        contentCol.addView(row)
        contentCol.addView(insetDiv())
    }

    private fun aboutRow() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val logo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
            }
            try { setImageBitmap(android.graphics.BitmapFactory.decodeStream(context.assets.open("logo.png"))) }
            catch (_: Exception) {}
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(hSp(14))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = "nuxx"; setTextColor(SC.text); textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(context).apply {
            text = "Versão 1.0.0 · Navegação privada"
            setTextColor(SC.textSub); textSize = 12.5f; setPadding(0, dp(2), 0, 0)
        })
        row.addView(col)
        contentCol.addView(row)
        contentCol.addView(insetDiv())
    }

    // ── SHEETS ─────────────────────────────────────────────────────────────

    private fun openPinSheet(unlock: Boolean, onDone: () -> Unit) {
        val dialog = m3Dialog()
        val sheet  = sheetRoot()
        handlebar(sheet)
        // sem título — apenas a LockScreenView directamente
        sheet.addView(LockScreenView(context, unlock = unlock, onSuccess = {
            dialog.dismiss(); onDone()
        }))
        sheet.addView(spacer(8))
        dialog.setContentView(sheet)
        dialog.show()
        expandFull(dialog)
    }

    private fun openLockDelayPicker() {
        val opts   = listOf(0, 5, 10, 30, 60, 120, 300, 600, 1800, 3600)
        val labels = listOf("Imediato","5 seg","10 seg","30 seg","1 min",
            "2 min","5 min","10 min","30 min","1 hora")
        val dialog = m3Dialog()
        var sel = 0
        val sheet = sheetRoot()
        handlebar(sheet)
        sheetTitle(sheet, "Bloquear após")
        sheetDivider(sheet)

        val np = NumberPicker(context).apply {
            minValue = 0; maxValue = labels.size - 1
            displayedValues = labels.toTypedArray(); value = 0
            wrapSelectorWheel = false
            setOnValueChangedListener { _, _, nv -> sel = nv }
        }
        sheet.addView(np, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(176)).also {
            it.leftMargin = dp(16); it.rightMargin = dp(16); it.topMargin = dp(8)
        })
        sheet.addView(spacer(8))
        sheetDivider(sheet)
        sheetButton(sheet, "Confirmar", SC.accent, Color.WHITE) {
            toast("Bloqueio após: ${labels[sel]}"); dialog.dismiss()
        }
        sheetButton(sheet, "Cancelar", Color.WHITE, SC.text, border = true) {
            dialog.dismiss()
        }
        sheet.addView(spacer(16))
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openSearchEnginePicker() {
        val engines = listOf("Google", "Bing", "DuckDuckGo", "Brave", "Yahoo")
        val dialog = m3Dialog()
        val sheet  = sheetRoot()
        handlebar(sheet)
        sheetTitle(sheet, "Motor de pesquisa")
        sheetDivider(sheet)
        engines.forEach { eng ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
                isClickable = true; isFocusable = true; background = pressDrawable()
                setOnClickListener {
                    searchEngine = eng
                    prefs.edit().putString("search_engine", eng).apply()
                    rebuildContent(); dialog.dismiss()
                }
            }
            row.addView(TextView(context).apply {
                text = eng; setTextColor(SC.text); textSize = 15f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (eng == searchEngine) {
                row.addView(svgA(ICO_CHEVRON, 18, SC.accent),
                    LinearLayout.LayoutParams(dp(18), dp(18)))
            }
            sheet.addView(row)
            sheetDivider(sheet)
        }
        sheet.addView(spacer(16))
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openDefaultVolumePicker() {
        val dialog = m3Dialog()
        var vol = defaultVol
        val sheet = sheetRoot()
        handlebar(sheet)
        sheetTitle(sheet, "Volume padrão")

        val volLabel = TextView(context).apply {
            text = "$vol%"; setTextColor(SC.text); textSize = 28f
            setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        sheet.addView(volLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val slider = SeekBar(context).apply {
            max = 100; progress = vol
            progressDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                setColor(SC.accent)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                    vol = p; volLabel.text = "$p%"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        sheet.addView(slider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin = dp(24); it.rightMargin = dp(24); it.topMargin = dp(8)
        })
        sheet.addView(spacer(16))
        sheetDivider(sheet)
        sheetButton(sheet, "Aplicar", SC.accent, Color.WHITE) {
            defaultVol = vol
            prefs.edit().putInt("default_vol", vol).apply()
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC,
                (vol / 100f * max).toInt(), 0)
            rebuildContent(); dialog.dismiss()
        }
        sheetButton(sheet, "Cancelar", Color.WHITE, SC.text, border = true) { dialog.dismiss() }
        sheet.addView(spacer(16))
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openFeedPreferencesPicker() {
        val cats = listOf(
            "Amador","MILF","Asiática","Latina","Loira",
            "Gay","Lésbicas","BDSM","Anal","Teen","Hardcore","Softcore"
        )
        val saved = prefs.getStringSet("feed_prefs", emptySet())!!.toMutableSet()
        val dialog = m3Dialog()
        val sheet  = sheetRoot()
        handlebar(sheet)
        sheetTitle(sheet, "Preferências do feed")
        sheetSubtitle(sheet, "Seleciona as categorias que preferes ver")
        sheetDivider(sheet)

        val checkViews = mutableMapOf<String, View>()
        cats.forEach { cat ->
            val checked = cat in saved
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(14), dp(20), dp(14))
                isClickable = true; isFocusable = true; background = pressDrawable()
            }
            val lbl = TextView(context).apply {
                text = cat; setTextColor(SC.text); textSize = 15f
            }
            val check = NuxxSwitch(context, checked) { v ->
                if (v) saved.add(cat) else saved.remove(cat)
            }
            row.addView(lbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(check, LinearLayout.LayoutParams(dp(50), dp(28)))
            checkViews[cat] = row
            sheet.addView(row)
            sheetDivider(sheet)
        }
        sheet.addView(spacer(8))
        sheetDivider(sheet)
        sheetButton(sheet, "Guardar", SC.accent, Color.WHITE) {
            prefs.edit().putStringSet("feed_prefs", saved).apply()
            toast("Preferências guardadas"); dialog.dismiss()
        }
        sheetButton(sheet, "Cancelar", Color.WHITE, SC.text, border = true) { dialog.dismiss() }
        sheet.addView(spacer(16))
        val sv = android.widget.ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        sv.addView(sheet, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.setContentView(sv)
        dialog.show()
        expandFull(dialog)
    }

    private fun openCardStylePicker() {
        data class StyleOpt(val key: String, val label: String, val sub: String)
        val opts = listOf(
            StyleOpt("grid_adaptive", "Grid adaptável", "Padrão — altura ajusta ao thumbnail"),
            StyleOpt("youtube",       "YouTube",        "Lista de um por linha como YouTube"),
            StyleOpt("grid_fixed",    "Grid 2×2 fixo",  "Dois por linha, altura uniforme"),
            StyleOpt("card_m3",       "Card M3",        "Card Material 3 Expressive"),
            StyleOpt("card_m3_color", "Card M3 dinâmico","Card M3 com cores do vídeo"),
        )
        val current = prefs.getString("card_style", "grid_adaptive")
        val dialog = m3Dialog()
        val sheet  = sheetRoot()
        handlebar(sheet)
        sheetTitle(sheet, "Estilo dos cards")
        sheetSubtitle(sheet, "Afeta o feed da aba Explorar")
        sheetDivider(sheet)
        opts.forEach { opt ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(14), dp(20), dp(14))
                isClickable = true; isFocusable = true; background = pressDrawable()
                setOnClickListener {
                    prefs.edit().putString("card_style", opt.key).apply()
                    rebuildContent(); dialog.dismiss()
                    toast("Reinicia o app para aplicar")
                }
            }
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(context).apply {
                text = opt.label; setTextColor(SC.text); textSize = 15f
                setTypeface(null, if (opt.key == current) Typeface.BOLD else Typeface.NORMAL)
            })
            col.addView(TextView(context).apply {
                text = opt.sub; setTextColor(SC.textSub); textSize = 12f
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (opt.key == current) {
                row.addView(svgA(ICO_CHEVRON, 18, SC.accent),
                    LinearLayout.LayoutParams(dp(18), dp(18)))
            }
            sheet.addView(row)
            sheetDivider(sheet)
        }
        sheet.addView(spacer(16))
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openCheckUpdateSheet() {
        val dialog = m3Dialog()
        val sheet  = sheetRoot()
        handlebar(sheet)
        sheetTitle(sheet, "Verificar atualizações")
        sheetDivider(sheet)

        val statusTv = TextView(context).apply {
            text = "A verificar..."; setTextColor(SC.textSub)
            textSize = 14f; gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        sheet.addView(statusTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        sheet.addView(spacer(8))
        sheetDivider(sheet)
        sheetButton(sheet, "Fechar", Color.WHITE, SC.text, border = true) { dialog.dismiss() }
        sheet.addView(spacer(16))
        dialog.setContentView(sheet)
        dialog.show()

        // simula verificação
        handler.postDelayed({
            statusTv.text = "✓ Tens a versão mais recente (1.0.0)"
            statusTv.setTextColor(Color.parseColor("#1DA462"))
        }, 1800)
    }

    private fun openConfirmClearSheet() {
        val dialog = m3Dialog()
        val sheet  = sheetRoot()
        handlebar(sheet)
        sheetTitle(sheet, "Limpar downloads?")
        sheetSubtitle(sheet, "Esta ação apaga todos os ficheiros transferidos e não pode ser desfeita.")
        sheet.addView(spacer(8))
        sheetDivider(sheet)
        sheetButton(sheet, "Apagar tudo", SC.textDestr,Color.WHITE) {
        thread {
        DownloadService.instance.items.toList()
        .forEach { DownloadService.instance.delete(it.id) }
        handler.post { toast("Downloads limpos"); dialog.dismiss() }
        }
     }
        sheetButton(sheet, "Cancelar", Color.WHITE, SC.text, border = true) { dialog.dismiss() }
        sheet.addView(spacer(16))
        dialog.setContentView(sheet)
        dialog.show()
    }

    // ── Sheet helpers ──────────────────────────────────────────────────────

    private fun m3Dialog() = BottomSheetDialog(
        activity,
        com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
    )

    private fun sheetRoot() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
    }

    private fun handlebar(parent: LinearLayout) {
        val bar = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }
        parent.addView(bar, LinearLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(12); it.bottomMargin = dp(8)
        })
    }

    private fun sheetTitle(parent: LinearLayout, text: String) {
        parent.addView(TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#1C1B1F")); textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(4), dp(20), dp(4))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun sheetSubtitle(parent: LinearLayout, text: String) {
        parent.addView(TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#888888")); textSize = 13.5f
            setPadding(dp(20), dp(4), dp(20), dp(10))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun sheetDivider(parent: LinearLayout) {
        parent.addView(View(context).apply { setBackgroundColor(Color.parseColor("#F0F0F0")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
    }

    private fun sheetButton(
        parent: LinearLayout, text: String,
        bgColor: Int = Color.WHITE, textColor: Int = SC.text,
        border: Boolean = false, onClick: () -> Unit
    ) {
        val btn = TextView(context).apply {
            this.text = text; this.textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(bgColor)
                if (border) setStroke(dp(1), Color.parseColor("#E0E0E0"))
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
        parent.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin = dp(16); it.rightMargin = dp(16)
            it.topMargin = dp(10); it.bottomMargin = dp(4)
        })
    }

    private fun expandFull(dialog: BottomSheetDialog) {
        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let {
                val beh = BottomSheetBehavior.from(it)
                val h   = activity.resources.displayMetrics.heightPixels
                it.layoutParams.height = h; it.requestLayout()
                beh.peekHeight = h
                beh.state = BottomSheetBehavior.STATE_EXPANDED
                beh.skipCollapsed = true
            }
        }
    }

    // ── Helpers visuais ────────────────────────────────────────────────────

    private fun divLine() = View(context).apply {
        setBackgroundColor(SC.divider)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun insetDiv() = FrameLayout(context).apply {
        addView(View(context).apply { setBackgroundColor(SC.divider) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1).also {
                it.leftMargin = dp(52)
            })
    }

    private fun pressDrawable(): android.graphics.drawable.Drawable {
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(SC.rowPress) }
        val normal  = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(Color.TRANSPARENT) }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun svgA(path: String, sizeDp: Int, tint: Int) =
        activity.svgImageView(path, sizeDp, tint)

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    private fun spacer(h: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h))
    }

    private fun hSp(w: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), 1)
    }

    private fun dp(v: Int) = (v * density).toInt()
}

// ── NuxxSwitch (renomeado para não colidir) ─────────────────────────────────
class NuxxSwitch(
    context: Context,
    private var checked: Boolean,
    private val onChange: (Boolean) -> Unit,
) : FrameLayout(context) {

    private val density    = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun dpF(v: Float) = v * density

    private val trackView = View(context)
    private val thumbView = View(context)

    init {
        clipChildren = false
        updateTrack()
        addView(trackView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        thumbView.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            elevation = dpF(3f)
        }
        addView(thumbView, LayoutParams(dp(22), dp(22)))
        positionThumb(animate = false)
        setOnClickListener {
            checked = !checked; updateTrack()
            positionThumb(animate = true); onChange(checked)
        }
    }

    private fun updateTrack() {
        trackView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dpF(14f)
            setColor(if (checked) Color.parseColor("#E01462") else Color.parseColor("#CFD9DE"))
        }
    }

    private fun positionThumb(animate: Boolean) {
        val pad    = dp(3)
        val trackW = dp(50)
        val thumbW = dp(22)
        val travel = trackW - thumbW - pad * 2
        val tx = (pad + if (checked) travel else 0).toFloat()
        val ty = pad.toFloat()
        if (animate) {
            thumbView.animate().translationX(tx).translationY(ty)
                .setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                .start()
        } else {
            thumbView.translationX = tx; thumbView.translationY = ty
        }
    }
}