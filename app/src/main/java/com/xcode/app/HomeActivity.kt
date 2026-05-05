package com.xcode.app.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xcode.app.editor.EditorActivity
import com.xcode.app.R

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

        val root = buildUI()
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun buildUI(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0d0d0f"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ── Header ────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }

        val logoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val logoIcon = TextView(this).apply {
            text = "<>"
            textSize = 22f
            setTextColor(Color.parseColor("#0e7af0"))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, dp(10), 0)
        }

        val logoText = TextView(this).apply {
            text = "XCode"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }

        val logoSub = TextView(this).apply {
            text = "  Studio"
            textSize = 22f
            setTextColor(Color.parseColor("#858585"))
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }

        logoRow.addView(logoIcon)
        logoRow.addView(logoText)
        logoRow.addView(logoSub)

        val subText = TextView(this).apply {
            text = "Seleciona uma aplicação para começar"
            textSize = 13f
            setTextColor(Color.parseColor("#858585"))
            setPadding(0, dp(4), 0, 0)
        }

        header.addView(logoRow)
        header.addView(subText)
        root.addView(header)

        // ── Divider ───────────────────────────────────────────────────────
        val div = View(this).apply {
            setBackgroundColor(Color.parseColor("#1e1e20"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
        }
        root.addView(div)

        // ── Section label ─────────────────────────────────────────────────
        val sectionLabel = TextView(this).apply {
            text = "APLICAÇÕES"
            textSize = 10f
            letterSpacing = 0.15f
            setTextColor(Color.parseColor("#555558"))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(dp(24), dp(24), dp(24), dp(12))
        }
        root.addView(sectionLabel)

        // ── Apps grid ─────────────────────────────────────────────────────
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val appsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }

        // Editor app card
        appsContainer.addView(buildAppCard(
            iconText = "{ }",
            iconColor = "#0e7af0",
            name = "Code Editor",
            description = "Editor de código com suporte Git, múltiplas linguagens e preview nativo",
            tag = "v2.0",
            tagColor = "#0e7af0"
        ) {
            openEditor()
        })

        // Coming soon placeholder
        appsContainer.addView(buildComingSoonCard(
            iconText = "⬡",
            name = "Terminal Pro",
            description = "Em breve"
        ))

        scroll.addView(appsContainer)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // ── Footer ────────────────────────────────────────────────────────
        val footer = TextView(this).apply {
            text = "XCode Studio · v2.0.0"
            textSize = 11f
            setTextColor(Color.parseColor("#333336"))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(16))
        }
        root.addView(footer)

        return root
    }

    private fun buildAppCard(
        iconText: String,
        iconColor: String,
        name: String,
        description: String,
        tag: String,
        tagColor: String,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#13131a"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(10)
            layoutParams = lp
        }

        // Rounded corners via background drawable
        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#13131a"))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.parseColor("#1e1e2a"))
        }
        card.background = bg

        // Icon box
        val iconBox = FrameLayout(this).apply {
            val sz = dp(48)
            layoutParams = LinearLayout.LayoutParams(sz, sz)
        }
        val iconBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#0e1a2e"))
            cornerRadius = dp(10).toFloat()
        }
        iconBox.background = iconBg

        val iconLabel = TextView(this).apply {
            text = iconText
            textSize = 18f
            setTextColor(Color.parseColor(iconColor))
            typeface = android.graphics.Typeface.MONOSPACE
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
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }

        val tagView = TextView(this).apply {
            text = tag
            textSize = 9f
            setTextColor(Color.parseColor(tagColor))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = dp(8)
            layoutParams = lp
            setPadding(dp(5), dp(2), dp(5), dp(2))
        }
        val tagBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#0e1a2e"))
            cornerRadius = dp(4).toFloat()
        }
        tagView.background = tagBg

        nameRow.addView(nameLabel)
        nameRow.addView(tagView)

        val descLabel = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#666670"))
            setPadding(0, dp(4), 0, 0)
            maxLines = 2
        }

        textCol.addView(nameRow)
        textCol.addView(descLabel)
        card.addView(textCol)

        // Arrow
        val arrow = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(Color.parseColor("#333340"))
            setPadding(dp(8), 0, 0, 0)
        }
        card.addView(arrow)

        card.setOnClickListener { onClick() }
        card.isClickable = true
        card.isFocusable = true

        // Ripple
        val ripple = android.content.res.ColorStateList.valueOf(Color.parseColor("#1a0e7af0"))
        card.foreground = android.graphics.drawable.RippleDrawable(ripple, null, null)

        return card
    }

    private fun buildComingSoonCard(iconText: String, name: String, description: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0.35f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(10)
            layoutParams = lp
        }
        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#13131a"))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.parseColor("#1e1e2a"))
        }
        card.background = bg

        val iconBox = FrameLayout(this).apply {
            val sz = dp(48)
            layoutParams = LinearLayout.LayoutParams(sz, sz)
        }
        val iconBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#1a1a20"))
            cornerRadius = dp(10).toFloat()
        }
        iconBox.background = iconBg
        val iconLabel = TextView(this).apply {
            text = iconText
            textSize = 18f
            setTextColor(Color.parseColor("#555558"))
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
            setTextColor(Color.parseColor("#555558"))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val descLabel = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#333336"))
            setPadding(0, dp(4), 0, 0)
        }
        textCol.addView(nameLabel)
        textCol.addView(descLabel)
        card.addView(textCol)

        return card
    }

    private fun openEditor() {
        val intent = Intent(this, EditorActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}