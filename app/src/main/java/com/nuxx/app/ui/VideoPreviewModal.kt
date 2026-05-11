package com.nuxx.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nuxx.app.MainActivity
import com.nuxx.app.models.FeedVideo
import com.nuxx.app.theme.AppTheme

object VideoPreviewModal {

    private fun dp(ctx: Context, v: Int) =
        (v * ctx.resources.displayMetrics.density).toInt()

    private fun fixEnc(raw: String): String {
        return try {
            val bytes   = raw.toByteArray(Charsets.ISO_8859_1)
            val decoded = String(bytes, Charsets.UTF_8)
            if (decoded.any { it.code > 127 } || raw.none { it.code > 127 }) decoded else raw
        } catch (_: Exception) { raw }
    }

    fun show(activity: MainActivity, video: FeedVideo) {
        val ctx = activity as Context

        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )

        val sheetRoot = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // handlebar
        sheetRoot.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 4)).also {
            it.gravity      = Gravity.CENTER_HORIZONTAL
            it.topMargin    = dp(ctx, 10)
            it.bottomMargin = dp(ctx, 10)
        })

        // título
        sheetRoot.addView(TextView(ctx).apply {
            text = fixEnc(video.title)
            setTextColor(Color.parseColor("#1C1B1F"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), dp(ctx, 2))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // meta
        sheetRoot.addView(TextView(ctx).apply {
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty())    append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
            setTextColor(Color.parseColor("#888888"))
            textSize = 11.5f
            setPadding(dp(ctx, 20), 0, dp(ctx, 20), dp(ctx, 14))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // divider
        sheetRoot.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // link label
        sheetRoot.addView(TextView(ctx).apply {
            text = "Link"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.04f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 4))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // link row
        val linkRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            background  = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 10).toFloat()
                setColor(Color.parseColor("#F6F6F6"))
                setStroke(dp(ctx, 1), Color.parseColor("#E0E0E0"))
            }
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
        }

        linkRow.addView(TextView(ctx).apply {
            text      = video.videoUrl
            textSize  = 11.5f
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextColor(Color.parseColor("#1C1B1F"))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val copyBtn = FrameLayout(ctx).apply {
            isClickable = true; isFocusable = true
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 4), dp(ctx, 8))
            setOnClickListener {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("link", video.videoUrl))
                activity.showSnackbarGlobal("Link copiado")
            }
        }
        copyBtn.addView(
            activity.svgImageView("icons/svg/content_copy.svg", 18, Color.parseColor("#555555")),
            FrameLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)).also { it.gravity = Gravity.CENTER }
        )
        linkRow.addView(copyBtn, LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)))

        sheetRoot.addView(linkRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.leftMargin  = dp(ctx, 20)
            it.rightMargin = dp(ctx, 20)
        })

        // tags
        val allTags = (video.tags + video.categories)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12)

        if (allTags.isNotEmpty()) {
            sheetRoot.addView(TextView(ctx).apply {
                text = "Tags"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.04f
                setTextColor(Color.parseColor("#888888"))
                setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 6))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            val tagsScroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setPadding(dp(ctx, 20), 0, dp(ctx, 20), 0)
            }
            val tagsRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
            }
            allTags.forEachIndexed { i, tag ->
                tagsRow.addView(TextView(ctx).apply {
                    text = tag
                    textSize = 11f
                    setTextColor(Color.parseColor("#444444"))
                    background = GradientDrawable().apply {
                        shape        = GradientDrawable.RECTANGLE
                        cornerRadius = dp(ctx, 100).toFloat()
                        setColor(Color.parseColor("#F0F0F0"))
                        setStroke(dp(ctx, 1), Color.parseColor("#DDDDDD"))
                    }
                    setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { if (i > 0) it.leftMargin = dp(ctx, 6) })
            }
            tagsScroll.addView(tagsRow)
            sheetRoot.addView(tagsScroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        // divider
        sheetRoot.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).also { it.topMargin = dp(ctx, 14) })

        // ações
        data class SI(val icon: String, val label: String, val action: () -> Unit)

        listOf(
            SI("icons/svg/open_in_browser.svg", "Ver no browser") {
                dialog.dismiss()
                activity.addContentOverlay(
                    BrowserPage(ctx, freeNavigation = true, externalUrl = video.videoUrl)
                )
            },
            SI("icons/svg/bookmark.svg", "Guardar para ver mais tarde") {
                dialog.dismiss()
                activity.showSnackbarGlobal("Guardado")
            },
            SI("icons/svg/playlist_add.svg", "Adicionar à playlist") {
                dialog.dismiss()
                activity.showSnackbarGlobal("Adicionado à playlist")
            },
        ).forEach { item ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                val ok = activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                if (ok) background = activity.getDrawable(tv.resourceId)
                setOnClickListener { item.action() }
            }
            row.addView(
                activity.svgImageView(item.icon, 22, Color.parseColor("#555555")),
                LinearLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22))
            )
            row.addView(View(ctx), LinearLayout.LayoutParams(dp(ctx, 16), 1))
            row.addView(TextView(ctx).apply {
                text = item.label
                setTextColor(Color.parseColor("#1C1B1F"))
                textSize = 15f
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            sheetRoot.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        sheetRoot.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, 24)))

        // scroll wrapper
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(sheetRoot, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        dialog.setContentView(scroll)

        // expandir até à status bar
        dialog.setOnShowListener {
            val bs = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bs?.let {
                val behavior = BottomSheetBehavior.from(it)
                val screenH  = activity.resources.displayMetrics.heightPixels
                it.layoutParams.height = screenH
                it.requestLayout()
                behavior.peekHeight    = screenH
                behavior.state         = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        dialog.show()
    }
}