package com.xcode.app.home

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xcode.app.editor.EditorActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
        val root = buildRoot()
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun buildRoot(): FrameLayout {
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.parseColor("#0d0d0f"))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        col.addView(buildHeader())
        col.addView(buildDivider())
        col.addView(buildSectionLabel("APLICACOES"))
        col.addView(buildAppsList())
        col.addView(buildFooter())

        frame.addView(col)
        return frame
    }

    private fun buildHeader(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(28))
        }

        val logoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Logo icon — monospace bracket style
        val logoMark = TextView(this).apply {
            text = "<>"
            textSize = 24f
            setTextColor(Color.parseColor("#0e7af0"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, dp(10), 0)
        }
        val logoTitle = TextView(this).apply {
            text = "XCode"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val logoSub = TextView(this).apply {
            text = "  Studio"
            textSize = 24f
            setTextColor(Color.parseColor("#555558"))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        logoRow.addView(logoMark)
        logoRow.addView(logoTitle)
        logoRow.addView(logoSub)

        val subtitle = TextView(this).apply {
            text = "Seleciona uma aplicacao para comecar"
            textSize = 13f
            setTextColor(Color.parseColor("#555558"))
            setPadding(0, dp(8), 0, 0)
        }

        col.addView(logoRow)
        col.addView(subtitle)
        return col
    }

    private fun buildDivider(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#1a1a1e"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        )
    }

    private fun buildSectionLabel(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 10f
        letterSpacing = 0.15f
        setTextColor(Color.parseColor("#444448"))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(24), dp(24), dp(24), dp(10))
    }

    private fun buildAppsList(): ScrollView {
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }

        col.addView(buildAppCard(
            iconLetters = "{ }",
            iconColor = "#0e7af0",
            iconBg = "#0a1628",
            name = "Code Editor",
            description = "Editor de codigo com Git integrado, syntax highlight para 15+ linguagens, terminal e preview nativo",
            tag = "v2.0",
            tagColor = "#0e7af0",
            tagBg = "#0a1628"
        ) { openEditor() })

        col.addView(buildComingSoon(
            iconLetters = "[ ]",
            name = "Terminal Pro",
            description = "Em breve"
        ))

        col.addView(buildComingSoon(
            iconLetters = "< />",
            name = "Design Studio",
            description = "Em breve"
        ))

        scroll.addView(col)
        return scroll
    }

    private fun buildAppCard(
        iconLetters: String,
        iconColor: String,
        iconBg: String,
        name: String,
        description: String,
        tag: String,
        tagColor: String,
        tagBg: String,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(10)
            layoutParams = lp
            isClickable = true
            isFocusable = true
        }
        card.background = makeCardBg("#13131a", "#1e1e26", 12)
        val ripple = RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.parseColor("#1a0e7af0")), null, null
        )
        card.foreground = ripple
        card.setOnClickListener { onClick() }

        // Icon box
        val iconBox = FrameLayout(this).apply {
            val sz = dp(50)
            layoutParams = LinearLayout.LayoutParams(sz, sz)
        }
        iconBox.background = makeRoundBg(iconBg, 10)
        val iconLabel = TextView(this).apply {
            text = iconLetters
            textSize = 16f
            setTextColor(Color.parseColor(iconColor))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        iconBox.addView(iconLabel)
        card.addView(iconBox)

        // Text column
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(14)
            layoutParams = lp
        }

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameLabel = TextView(this).apply {
            text = name
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val tagView = TextView(this).apply {
            text = tag
            textSize = 9f
            setTextColor(Color.parseColor(tagColor))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(5), dp(2), dp(5), dp(2))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = dp(8)
            layoutParams = lp
        }
        tagView.background = makeRoundBg(tagBg, 4)

        nameRow.addView(nameLabel)
        nameRow.addView(tagView)

        val desc = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#555558"))
            setPadding(0, dp(5), 0, 0)
            maxLines = 2
            lineSpacingMultiplier = 1.3f
        }

        textCol.addView(nameRow)
        textCol.addView(desc)
        card.addView(textCol)

        // Arrow
        val arrow = TextView(this).apply {
            text = ">"
            textSize = 16f
            setTextColor(Color.parseColor("#2a2a30"))
            setPadding(dp(10), 0, 0, 0)
            typeface = Typeface.MONOSPACE
        }
        card.addView(arrow)

        return card
    }

    private fun buildComingSoon(iconLetters: String, name: String, description: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            alpha = 0.3f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(10)
            layoutParams = lp
        }
        card.background = makeCardBg("#13131a", "#1a1a20", 12)

        val iconBox = FrameLayout(this).apply {
            val sz = dp(50)
            layoutParams = LinearLayout.LayoutParams(sz, sz)
        }
        iconBox.background = makeRoundBg("#1a1a20", 10)
        val iconLabel = TextView(this).apply {
            text = iconLetters
            textSize = 14f
            setTextColor(Color.parseColor("#333336"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        iconBox.addView(iconLabel)
        card.addView(iconBox)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(14)
            layoutParams = lp
        }
        val nameLabel = TextView(this).apply {
            text = name
            textSize = 14f
            setTextColor(Color.parseColor("#333336"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val desc = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#2a2a2e"))
            setPadding(0, dp(4), 0, 0)
        }
        textCol.addView(nameLabel)
        textCol.addView(desc)
        card.addView(textCol)
        return card
    }

    private fun buildFooter(): TextView = TextView(this).apply {
        text = "XCode Studio  v2.0.0"
        textSize = 11f
        setTextColor(Color.parseColor("#222226"))
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun openEditor() {
        val intent = Intent(this, EditorActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun makeCardBg(fill: String, stroke: String, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fill))
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor(stroke))
        }

    private fun makeRoundBg(fill: String, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fill))
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}