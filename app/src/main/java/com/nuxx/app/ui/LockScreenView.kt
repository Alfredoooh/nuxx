package com.nuxx.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.*
import com.nuxx.app.MainActivity
import com.nuxx.app.services.LockService
import com.nuxx.app.theme.AppTheme
import kotlin.concurrent.thread

class LockScreenView(
    context: Context,
    private val unlock:    Boolean,
    private val onSuccess: () -> Unit,
) : FrameLayout(context) {

    private val activity  = context as MainActivity
    private val handler   = Handler(Looper.getMainLooper())

    private var input      = ""
    private var firstPin:  String? = null
    private var processing = false
    private var visible    = false

    private val PIN_LEN = 4

    // ── Cores fixas — tema claro ──────────────────────────────────────────────
    private val headerBg      = Color.parseColor("#EEEFF4")
    private val keypadBg      = Color.WHITE
    private val keyBg         = Color.WHITE
    private val actionKeyBg   = Color.WHITE
    private val keyText       = Color.BLACK
    private val dividerColor  = Color.parseColor("#D1D1D6")
    private val digitBoxBg    = Color.parseColor("#DDDDE3")
    private val titleColor    = Color.BLACK
    private val subtitleColor = Color.argb(115, 0, 0, 0)

    // Views que precisam de referenciar
    private lateinit var titleTv:    TextView
    private lateinit var subtitleTv: TextView
    private lateinit var dotsRow:    LinearLayout
    private lateinit var keypadRoot: LinearLayout
    private lateinit var toastView:  FrameLayout

    init {
        setBackgroundColor(headerBg)
        forceStatusBarLight()
        buildUI()
    }

    // ── Status bar sempre claro ───────────────────────────────────────────────
    private fun forceStatusBarLight() {
        activity.window.statusBarColor = headerBg
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility =
                activity.window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    private fun buildUI() {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // Zona superior
        val topArea = LinearLayout(context).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // Ícone cadeado
        val lockIcon = activity.svgImageView(
            "icons/svg/settings/settings_lock.svg", 56,
            Color.argb(64, 0, 0, 0)
        )
        topArea.addView(lockIcon, LinearLayout.LayoutParams(dp(56), dp(56)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
        })
        topArea.addView(spacer(18))

        titleTv = TextView(context).apply {
            text          = titleText()
            setTextColor(titleColor)
            textSize      = 22f
            setTypeface(typeface, Typeface.BOLD)
            gravity       = Gravity.CENTER
            letterSpacing = -0.01f
        }
        topArea.addView(titleTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        topArea.addView(spacer(6))

        subtitleTv = TextView(context).apply {
            text     = subtitleText(error = false)
            setTextColor(subtitleColor)
            textSize = 14f
            gravity  = Gravity.CENTER
        }
        topArea.addView(subtitleTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin  = dp(24)
            it.rightMargin = dp(24)
        })

        topArea.addView(spacer(32))

        // 4 digit boxes
        dotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
        }
        for (i in 0 until PIN_LEN) {
            dotsRow.addView(buildDigitBox(i), LinearLayout.LayoutParams(0, dp(52), 1f).also {
                it.leftMargin  = dp(4)
                it.rightMargin = dp(4)
            })
        }
        topArea.addView(dotsRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin  = dp(24)
            it.rightMargin = dp(24)
        })

        root.addView(topArea, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Teclado
        keypadRoot = buildKeypad()
        root.addView(keypadRoot, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Toast
        toastView = FrameLayout(context).apply { visibility = View.GONE }
        addView(toastView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity   = Gravity.TOP
            it.topMargin = dp(16)
        })
    }

    private fun buildDigitBox(index: Int): FrameLayout {
        val filled = index < input.length
        val box = FrameLayout(context).apply {
            background = GradientDrawable().also {
                it.shape        = GradientDrawable.RECTANGLE
                it.cornerRadius = dp(10).toFloat()
                it.setColor(
                    if (filled) digitBoxBg
                    else Color.argb(
                        (0.55 * 255).toInt(),
                        Color.red(digitBoxBg),
                        Color.green(digitBoxBg),
                        Color.blue(digitBoxBg)
                    )
                )
            }
        }
        if (filled) {
            val ch = if (visible) input[index].toString() else null
            if (ch != null) {
                box.addView(TextView(context).apply {
                    text = ch; setTextColor(titleColor); textSize = 22f
                    setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
                }, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT))
            } else {
                val dot = View(context).apply {
                    background = GradientDrawable().also {
                        it.shape = GradientDrawable.OVAL; it.setColor(titleColor)
                    }
                }
                box.addView(dot, FrameLayout.LayoutParams(dp(10), dp(10)).also {
                    it.gravity = Gravity.CENTER
                })
            }
        }
        return box
    }

    private fun refreshDots(error: Boolean = false) {
        dotsRow.removeAllViews()
        for (i in 0 until PIN_LEN) {
            val filled = i < input.length
            val box = FrameLayout(context).apply {
                background = GradientDrawable().also {
                    it.shape        = GradientDrawable.RECTANGLE
                    it.cornerRadius = dp(10).toFloat()
                    it.setColor(when {
                        error  -> Color.argb(38, 255, 68, 68)
                        filled -> digitBoxBg
                        else   -> Color.argb(
                            (0.55 * 255).toInt(),
                            Color.red(digitBoxBg),
                            Color.green(digitBoxBg),
                            Color.blue(digitBoxBg)
                        )
                    })
                    if (error) it.setStroke(dp(1), Color.argb(102, 255, 68, 68))
                }
            }
            if (filled) {
                if (visible) {
                    box.addView(TextView(context).apply {
                        text = input[i].toString(); setTextColor(titleColor)
                        textSize = 22f; gravity = Gravity.CENTER
                    }, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT))
                } else {
                    val dot = View(context).apply {
                        background = GradientDrawable().also {
                            it.shape = GradientDrawable.OVAL
                            it.setColor(if (error) Color.parseColor("#FF4444") else titleColor)
                        }
                    }
                    box.addView(dot, FrameLayout.LayoutParams(dp(10), dp(10)).also {
                        it.gravity = Gravity.CENTER
                    })
                }
            }
            dotsRow.addView(box, LinearLayout.LayoutParams(0, dp(52), 1f).also {
                it.leftMargin  = dp(4)
                it.rightMargin = dp(4)
            })
        }
    }

    // ── Teclado ───────────────────────────────────────────────────────────────
    private fun buildKeypad(): LinearLayout {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(keypadBg)
        }

        col.addView(View(context).apply { setBackgroundColor(dividerColor) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"))
            .forEach { keys ->
                col.addView(buildKeyRow(keys))
                col.addView(View(context).apply { setBackgroundColor(dividerColor) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            }

        // Última linha: visibility | 0 | backspace
        val lastRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        lastRow.addView(
            buildActionKey(
                svgPath = if (visible) "icons/svg/visibility_off.svg" else "icons/svg/visibility.svg",
                bg      = actionKeyBg,
            ) { onToggleVisible() },
            LinearLayout.LayoutParams(0, dp(64), 1f)
        )
        lastRow.addView(View(context).apply { setBackgroundColor(dividerColor) },
            LinearLayout.LayoutParams(1, dp(64)))
        lastRow.addView(buildNumKey("0", keyBg), LinearLayout.LayoutParams(0, dp(64), 1f))
        lastRow.addView(View(context).apply { setBackgroundColor(dividerColor) },
            LinearLayout.LayoutParams(1, dp(64)))
        lastRow.addView(
            buildActionKey(
                svgPath = "icons/svg/backspace.svg",
                bg      = actionKeyBg,
            ) { onDel() },
            LinearLayout.LayoutParams(0, dp(64), 1f)
        )

        col.addView(lastRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        return col
    }

    private fun buildKeyRow(keys: List<String>): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        keys.forEachIndexed { i, k ->
            if (i > 0) row.addView(View(context).apply { setBackgroundColor(dividerColor) },
                LinearLayout.LayoutParams(1, dp(64)))
            row.addView(buildNumKey(k, keyBg), LinearLayout.LayoutParams(0, dp(64), 1f))
        }
        return row
    }

    private fun buildNumKey(label: String, bg: Int): FrameLayout {
        val btn = FrameLayout(context).apply {
            setBackgroundColor(bg)
            isClickable = true; isFocusable = true
            setOnClickListener { if (!processing) onKey(label) }
        }
        btn.addView(TextView(context).apply {
            text = label; setTextColor(keyText); textSize = 26f
            setTypeface(typeface, Typeface.NORMAL); gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        return btn
    }

    private fun buildActionKey(svgPath: String, bg: Int, onClick: () -> Unit): FrameLayout {
        val btn = FrameLayout(context).apply {
            setBackgroundColor(bg)
            isClickable = true; isFocusable = true
            setOnClickListener { if (!processing) onClick() }
            tag = "action_$svgPath"
        }
        val iconColor = Color.argb((0.65 * 255).toInt(),
            Color.red(keyText), Color.green(keyText), Color.blue(keyText))
        btn.addView(
            activity.svgImageView(svgPath, 22, iconColor),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER }
        )
        return btn
    }

    // ── Lógica ────────────────────────────────────────────────────────────────
    private fun onKey(k: String) {
        if (input.length >= PIN_LEN) return
        input += k
        refreshDots()
        if (input.length == PIN_LEN) {
            handler.postDelayed({ if (!processing) onConfirm() }, 120)
        }
    }

    private fun onDel() {
        if (input.isEmpty()) return
        input = input.dropLast(1)
        refreshDots()
    }

    private fun onToggleVisible() {
        visible = !visible
        // Reconstrói teclado para atualizar ícone
        keypadRoot.removeAllViews()
        buildKeypad().let { newKp ->
            for (i in 0 until newKp.childCount) keypadRoot.addView(newKp.getChildAt(i))
        }
        refreshDots()
    }

    private fun onConfirm() {
        if (processing || input.length < PIN_LEN) { triggerError(); return }
        processing = true

        if (unlock) {
            thread {
                val ok = LockService.instance.verify(input)
                handler.post {
                    if (ok) {
                        showToast(success = true, msg = "Bem-vindo de volta!")
                        handler.postDelayed({ onSuccess() }, 1800)
                    } else {
                        showToast(success = false, msg = "PIN incorreto. Tenta novamente.")
                        triggerError()
                        processing = false
                    }
                }
            }
        } else {
            if (firstPin == null) {
                firstPin        = input
                input           = ""
                processing      = false
                titleTv.text    = titleText()
                subtitleTv.text = subtitleText(error = false)
                refreshDots()
            } else {
                if (input == firstPin) {
                    thread {
                        LockService.instance.setPin(input)
                        handler.post {
                            showToast(success = true, msg = "PIN definido com sucesso!")
                            handler.postDelayed({ onSuccess() }, 1800)
                        }
                    }
                } else {
                    firstPin = null
                    showToast(success = false, msg = "PINs não coincidem. Recomeça.")
                    triggerError()
                    processing = false
                }
            }
        }
    }

    private fun triggerError() {
        input = ""
        refreshDots(error = true)
        subtitleTv.text = subtitleText(error = true)
        subtitleTv.setTextColor(Color.parseColor("#FF4444"))
        shakeView(dotsRow) {
            subtitleTv.setTextColor(subtitleColor)
            subtitleTv.text = subtitleText(error = false)
            refreshDots(error = false)
        }
    }

    private fun shakeView(v: View, onEnd: () -> Unit) {
        ValueAnimator.ofFloat(0f, -12f, 12f, -8f, 8f, -4f, 0f).apply {
            duration = 420
            addUpdateListener { v.translationX = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { onEnd() }
            })
        }.start()
    }

    // ── Toast ─────────────────────────────────────────────────────────────────
    private fun showToast(success: Boolean, msg: String) {
        toastView.removeAllViews()
        toastView.visibility = View.VISIBLE

        val bg = GradientDrawable().also {
            it.shape        = GradientDrawable.RECTANGLE
            it.cornerRadius = dp(100).toFloat()
            it.setColor(Color.argb((0.72 * 255).toInt(), 255, 255, 255))
            it.setStroke(1, Color.argb((0.5 * 255).toInt(), 255, 255, 255))
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background  = bg
        }

        val circle = FrameLayout(context).apply {
            background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.parseColor("#EEEEEE"))
            }
        }
        val iconPath = if (success) "icons/svg/settings/settings_chevron.svg" else "icons/svg/close.svg"
        circle.addView(
            activity.svgImageView(iconPath, 16,
                if (success) Color.parseColor("#4CAF50") else Color.parseColor("#FF4444")),
            FrameLayout.LayoutParams(dp(16), dp(16)).also { it.gravity = Gravity.CENTER }
        )
        row.addView(circle, LinearLayout.LayoutParams(dp(34), dp(34)))
        row.addView(hSpacer(10))
        row.addView(TextView(context).apply {
            text = msg
            setTextColor(Color.argb((0.80 * 255).toInt(), 0, 0, 0))
            textSize = 13f; setTypeface(typeface, Typeface.BOLD); maxLines = 1
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        toastView.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin  = dp(20)
            it.rightMargin = dp(20)
        })

        toastView.translationY = -dp(80).toFloat()
        toastView.animate().translationY(0f).setDuration(420)
            .setInterpolator(OvershootInterpolator(1.2f)).start()

        handler.postDelayed({
            toastView.animate().translationY(-dp(80).toFloat()).setDuration(300)
                .withEndAction { toastView.visibility = View.GONE }.start()
        }, 2600)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun titleText() = when {
        unlock           -> "Inserir Senha"
        firstPin == null -> "Novo PIN"
        else             -> "Confirmar PIN"
    }

    private fun subtitleText(error: Boolean) = when {
        error && unlock  -> "PIN incorreto. Tenta novamente."
        error            -> "PINs não coincidem. Começa de novo."
        unlock           -> "Digite sua senha de acesso."
        firstPin == null -> "Define um PIN de 4 dígitos."
        else             -> "Digite novamente para confirmar."
    }

    private fun spacer(h: Int)  = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }
    private fun hSpacer(w: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), 1)
    }
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}