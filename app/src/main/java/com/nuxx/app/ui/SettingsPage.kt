package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
import androidx.core.widget.NestedScrollView
import com.caverock.androidsvg.SVG
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nuxx.app.MainActivity
import com.nuxx.app.services.DownloadService
import com.nuxx.app.services.FaviconService
import com.nuxx.app.services.LockService
import kotlin.concurrent.thread

private object SColors {
    val bg        = Color.parseColor("#FFFFFF")
    val surface   = Color.parseColor("#FFFFFF")
    val text      = Color.parseColor("#0F1419")
    val textSub   = Color.parseColor("#536471")
    val textDestr = Color.parseColor("#F4212E")
    val divider   = Color.parseColor("#EFF3F4")
    val handle    = Color.parseColor("#DDDDDD")
    val scrim     = Color.argb(102, 0, 0, 0)
    val rowPress  = Color.parseColor("#F7F9F9")
    val switchOn  = Color.parseColor("#1D9BF0")
}

private const val SVG_BACK      = "icons/svg/settings/settings_back.svg"
private const val SVG_PIN       = "icons/svg/settings/settings_pin.svg"
private const val SVG_LOCK      = "icons/svg/settings/settings_lock.svg"
private const val SVG_TRASH     = "icons/svg/settings/settings_trash.svg"

private const val SVG_TIMER = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="10" x2="14" y1="2" y2="2"/><line x1="12" x2="15" y1="14" y2="11"/><circle cx="12" cy="14" r="8"/></svg>"""
private const val SVG_REFRESH   = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/></svg>"""
private const val SVG_LICENSES  = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 12h-5"/><path d="M15 8h-5"/><path d="M19 17V5a2 2 0 0 0-2-2H4"/><path d="M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3"/></svg>"""

@SuppressLint("ViewConstructor")
class SettingsPage(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())
    private val density  get() = context.resources.displayMetrics.density

    private var lockEnabled = false
    private lateinit var contentCol: LinearLayout

    init {
        setBackgroundColor(SColors.bg)
        loadState()
        buildUI()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFocusableInTouchMode = true
        requestFocus()
        activity.setStatusBarDark(false)
        activity.window.statusBarColor = SColors.bg
    }

    fun handleBack() {
        activity.closeSettings()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            handleBack(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun loadState() {
        thread {
            lockEnabled = LockService.instance.isEnabled()
            handler.post { rebuildContent() }
        }
    }

    private fun buildUI() {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        root.addView(View(context).apply { setBackgroundColor(SColors.bg) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight))

        val bar = FrameLayout(context).apply { setBackgroundColor(SColors.bg) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val btnBack = FrameLayout(context).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { handleBack() }
            isClickable = true; isFocusable = true
        }
        btnBack.addView(svgAsset(SVG_BACK, 20, SColors.text),
            FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER })
        row.addView(btnBack, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(TextView(context).apply {
            text = "Definições"; setTextColor(SColors.text)
            textSize = 20f; setTypeface(null, Typeface.BOLD); letterSpacing = -0.01f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(dividerLine(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        val scroll = NestedScrollView(context).apply { isFillViewport = true }
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(32))
        }
        scroll.addView(contentCol, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        rebuildContent()
    }

    private fun rebuildContent() {
        contentCol.removeAllViews()

        sectionHeader("Segurança")
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
            menuRow(null, SVG_TIMER, "Bloquear após", "Configurar tempo") {
                openLockDelayPicker()
            }
        }

        sectionHeader("Manutenção")
        menuRow(null, SVG_REFRESH, "Recarregar ícones", "Baixa novamente os favicons") {
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
        menuRow(null, SVG_LICENSES, "Licenças de software", "Dependências open source") {
            activity.openLicenses()
        }

        contentCol.addView(spacer(20))
    }

    // ── Rows ──────────────────────────────────────────────────────────────────

    private fun sectionHeader(text: String) {
        contentCol.addView(dividerLine())
        contentCol.addView(TextView(context).apply {
            this.text = text; setTextColor(SColors.text)
            textSize = 15f; setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(16), dp(16), dp(8))
        })
    }

    private fun menuRow(
        svgPath: String?, inlineSvg: String?,
        label: String, sub: String,
        destructive: Boolean = false,
        onTap: () -> Unit,
    ) {
        val labelColor = if (destructive) SColors.textDestr else SColors.text
        val iconColor  = if (destructive) SColors.textDestr else SColors.textSub
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true; isFocusable = true
            setOnClickListener { onTap() }
            background = rippleOrPress()
        }
        val iconView: View = when {
            svgPath   != null -> svgAsset(svgPath, 22, iconColor)
            inlineSvg != null -> svgInline(inlineSvg, 22, iconColor)
            else              -> View(context)
        }
        row.addView(iconView, LinearLayout.LayoutParams(dp(22), dp(22)))
        row.addView(hSpacer(14))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = label; setTextColor(labelColor); textSize = 15f; setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(context).apply {
            text = sub; setTextColor(SColors.textSub); textSize = 13f; setPadding(0, dp(2), 0, 0)
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
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val iconView: View = when {
            svgPath   != null -> svgAsset(svgPath, 22, SColors.textSub)
            inlineSvg != null -> svgInline(inlineSvg, 22, SColors.textSub)
            else              -> View(context)
        }
        row.addView(iconView, LinearLayout.LayoutParams(dp(22), dp(22)))
        row.addView(hSpacer(14))
        val subTv = TextView(context).apply {
            text = sub; setTextColor(SColors.textSub); textSize = 13f
        }
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = label; setTextColor(SColors.text); textSize = 15f; setTypeface(null, Typeface.BOLD)
        })
        col.addView(subTv.also { it.setPadding(0, dp(2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(SSwitch(context, value) { v ->
            subTv.text = if (v) "Ativo" else "Inativo"; onChange(v)
        }, LinearLayout.LayoutParams(dp(50), dp(28)))
        contentCol.addView(row)
        contentCol.addView(insetDivider())
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
        row.addView(hSpacer(14))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = "nuxxx"; setTextColor(SColors.text); textSize = 15f; setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(context).apply {
            text = "Versão 1.0.0 · Navegação privada"
            setTextColor(SColors.textSub); textSize = 13f; setPadding(0, dp(2), 0, 0)
        })
        row.addView(col)
        contentCol.addView(row)
        contentCol.addView(insetDivider())
    }

    // ── Sheets nativos (Material BottomSheetDialog) ───────────────────────────

    private fun buildSheetRoot(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
    }

    private fun addHandlebar(parent: LinearLayout) {
        val handlebar = View(context).apply {
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(100).toFloat()
                setColor(SColors.handle)
            }
        }
        parent.addView(handlebar, LinearLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity      = Gravity.CENTER_HORIZONTAL
            it.topMargin    = dp(10)
            it.bottomMargin = dp(10)
        })
    }

    private fun openPinSheet(unlock: Boolean, onDone: () -> Unit) {
        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )
        val sheet = buildSheetRoot()
        addHandlebar(sheet)
        sheet.addView(TextView(context).apply {
            text = if (unlock) "Verificar PIN" else "Alterar PIN"
            setTextColor(Color.parseColor("#1C1B1F")); textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(4), dp(16), dp(4))
        })
        sheet.addView(LockScreenView(context, unlock = unlock, onSuccess = {
            dialog.dismiss(); onDone()
        }))
        sheet.addView(spacer(8))
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openLockDelayPicker() {
        val opts   = listOf(0, 5, 10, 30, 60, 120, 300, 600, 1800, 3600)
        val labels = listOf("Imediato","5 seg","10 seg","30 seg","1 min",
            "2 min","5 min","10 min","30 min","1 hora")
        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )
        var selectedIdx = 0
        val sheet = buildSheetRoot()
        addHandlebar(sheet)

        // Título + OK
        val hRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        hRow.addView(TextView(context).apply {
            text = "Bloquear após"; setTextColor(Color.parseColor("#1C1B1F"))
            textSize = 17f; setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        hRow.addView(TextView(context).apply {
            text = "OK"; setTextColor(SColors.switchOn)
            textSize = 15f; setTypeface(null, Typeface.BOLD)
            setOnClickListener { toast("Bloqueio após: ${labels[selectedIdx]}"); dialog.dismiss() }
        })
        sheet.addView(hRow)
        sheet.addView(View(context).apply { setBackgroundColor(Color.parseColor("#EEEEEE")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        sheet.addView(spacer(8))

        val np = NumberPicker(context).apply {
            minValue = 0; maxValue = labels.size - 1
            displayedValues = labels.toTypedArray(); value = 0
            wrapSelectorWheel = false
            setOnValueChangedListener { _, _, nv -> selectedIdx = nv }
        }
        sheet.addView(np, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(176)).also {
            it.leftMargin = dp(16); it.rightMargin = dp(16)
        })
        sheet.addView(spacer(16))
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openConfirmClearSheet() {
        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )
        val sheet = buildSheetRoot()
        addHandlebar(sheet)

        sheet.addView(TextView(context).apply {
            text = "Limpar downloads"
            setTextColor(Color.parseColor("#1C1B1F")); textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(4), dp(16), dp(4))
        })
        sheet.addView(TextView(context).apply {
            text = "Esta ação apaga todos os ficheiros transferidos e não pode ser desfeita."
            setTextColor(Color.parseColor("#888888")); textSize = 14f
            setPadding(dp(16), dp(4), dp(16), dp(16))
        })

        sheet.addView(View(context).apply { setBackgroundColor(Color.parseColor("#EEEEEE")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // Apagar tudo
        sheet.addView(TextView(context).apply {
            text = "Apagar tudo"
            setTextColor(Color.parseColor("#F4212E")); textSize = 16f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            val ok = activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            if (ok) background = activity.getDrawable(tv.resourceId)
            setOnClickListener {
                thread {
                    DownloadService.instance.items.toList()
                        .forEach { DownloadService.instance.delete(it.id) }
                    handler.post { toast("Downloads limpos"); dialog.dismiss() }
                }
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        sheet.addView(View(context).apply { setBackgroundColor(Color.parseColor("#EEEEEE")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // Cancelar
        sheet.addView(TextView(context).apply {
            text = "Cancelar"
            setTextColor(Color.parseColor("#1C1B1F")); textSize = 16f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            val ok = activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            if (ok) background = activity.getDrawable(tv.resourceId)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        sheet.addView(spacer(24))
        dialog.setContentView(sheet)
        dialog.show()
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun dividerLine() = View(context).apply {
        setBackgroundColor(SColors.divider)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun insetDivider() = FrameLayout(context).apply {
        val line = View(context).apply { setBackgroundColor(SColors.divider) }
        addView(line, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.leftMargin = dp(52)
        })
    }

    private fun rippleOrPress(): android.graphics.drawable.Drawable {
        val pressed = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(SColors.rowPress) }
        val normal  = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(Color.TRANSPARENT) }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun svgAsset(path: String, sizeDp: Int, tint: Int) =
        activity.svgImageView(path, sizeDp, tint)

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

// ── SSwitch ───────────────────────────────────────────────────────────────────
class SSwitch(
    context: Context,
    private var checked: Boolean,
    private val onChange: (Boolean) -> Unit,
) : FrameLayout(context) {

    private val density      = context.resources.displayMetrics.density
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