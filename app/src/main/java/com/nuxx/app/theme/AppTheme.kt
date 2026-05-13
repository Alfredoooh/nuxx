// AppTheme.kt
package com.nuxx.app.theme

import android.graphics.Color

object AppTheme {

    object Dark {
        val bg              = Color.parseColor("#0F0F0F")
        val bgSecondary     = Color.parseColor("#181818")
        val surface         = Color.parseColor("#212121")
        val text            = Color.WHITE
        val textSecondary   = Color.parseColor("#AAAAAA")
        val icon            = Color.WHITE
        val iconSub         = Color.parseColor("#AAAAAA")
        val navBg           = Color.parseColor("#0F0F0F")
        val navBorder       = Color.parseColor("#303030")
        val navActive       = Color.WHITE
        val navInactive     = Color.parseColor("#888888")
        val divider         = Color.parseColor("#303030")
        val appBar          = Color.parseColor("#0F0F0F")
        val card            = Color.parseColor("#1F1F1F")
        val inputBg         = Color.parseColor("#121212")
        val inputBorder     = Color.parseColor("#303030")
        val chipBg          = Color.parseColor("#272727")
        val chipBgActive    = Color.WHITE
        val chipText        = Color.WHITE
        val chipTextActive  = Color.parseColor("#0F0F0F")
        val sheet           = Color.parseColor("#212121")
        val sheetHandle     = Color.parseColor("#535353")
        val popup           = Color.parseColor("#212121")
        val thumbBg         = Color.parseColor("#272727")
    }

    // ── Cor primária rosa ────────────────────────────────────────────────────
    val primary             = Color.parseColor("#E01462")
    val primaryDark         = Color.parseColor("#B8104F")
    val primaryLight        = Color.parseColor("#FF4D8D")

    val ytRed               = Color.parseColor("#E01462")
    val ytRedDark           = Color.parseColor("#B8104F")
    val ytRedLight          = Color.parseColor("#FF4D8D")
    val ytRedDeep           = Color.parseColor("#9C0E44")
    val ytWhite             = Color.WHITE
    val ytBlack             = Color.BLACK

    val live                = Color.parseColor("#E01462")
    val liveText            = Color.WHITE
    val shortsRed           = Color.parseColor("#E01462")
    val shortsRedDark       = Color.parseColor("#B8104F")

    val membership          = Color.parseColor("#1565C0")
    val membershipLight     = Color.parseColor("#1976D2")
    val sponsor             = Color.parseColor("#00695C")

    val superChatBlue       = Color.parseColor("#1565C0")
    val superChatCyan       = Color.parseColor("#0097A7")
    val superChatGreen      = Color.parseColor("#00897B")
    val superChatYellow     = Color.parseColor("#F9A825")
    val superChatOrange     = Color.parseColor("#EF6C00")
    val superChatPink       = Color.parseColor("#C62828")
    val superChatRed        = Color.parseColor("#B71C1C")

    val progressPlayed      = Color.parseColor("#E01462")
    val progressBuffer      = Color.parseColor("#909090")
    val progressBg          = Color.parseColor("#535353")
    val progressThumb       = Color.parseColor("#E01462")

    val badge4K             = Color.parseColor("#4CAF50")
    val badgeHD             = Color.parseColor("#2196F3")
    val badgeSDR            = Color.parseColor("#9E9E9E")
    val badgeHDR            = Color.parseColor("#FFC107")
    val badge360            = Color.parseColor("#9C27B0")
    val badgeCC             = Color.WHITE

    val link                = Color.parseColor("#065FD4")
    val linkVisited         = Color.parseColor("#9575CD")

    val verified            = Color.parseColor("#AAAAAA")
    val verifiedPremium     = Color.parseColor("#FFD600")

    val notifBadge          = Color.parseColor("#E01462")
    val notifBadgeText      = Color.WHITE

    val adBadge             = Color.parseColor("#FFD700")
    val adBadgeText         = Color.BLACK
    val adSkipBtn           = Color.parseColor("#212121")
    val adSkipText          = Color.WHITE

    val captionBg           = Color.parseColor("#BF000000")
    val captionText         = Color.WHITE

    val error               = Color.parseColor("#FF4444")
    val errorDark           = Color.parseColor("#CC0000")
    val warning             = Color.parseColor("#FFC107")
    val warningDark         = Color.parseColor("#F57F17")
    val success             = Color.parseColor("#4CAF50")
    val successDark         = Color.parseColor("#2E7D32")
    val info                = Color.parseColor("#065FD4")

    val btnPrimary          = Color.parseColor("#E01462")
    val btnPrimaryHover     = Color.parseColor("#B8104F")
    val btnPrimaryText      = Color.WHITE
    val btnPrimaryPressed   = Color.parseColor("#9C0E44")

    val toastBg             = Color.parseColor("#323232")
    val toastText           = Color.WHITE
    val toastAction         = Color.parseColor("#E01462")

    val playerBg            = Color.BLACK
    val playerControls      = Color.WHITE
    val playerControlsBg    = Color.argb(153, 0, 0, 0)
    val playerProgressPlayed = Color.parseColor("#E01462")
    val playerProgressBuffer = Color.parseColor("#909090")
    val playerProgressBg    = Color.parseColor("#535353")
    val playerProgressThumb = Color.parseColor("#E01462")
    val playerTimestamp     = Color.WHITE
    val playerTimestampBg   = Color.argb(153, 0, 0, 0)
    val playerQualityBadge  = Color.parseColor("#212121")
    val playerQualityText   = Color.WHITE
    val playerEndscreenBg   = Color.argb(178, 0, 0, 0)

    val shortsBg            = Color.BLACK
    val shortsText          = Color.WHITE
    val shortsIcon          = Color.WHITE
    val shortsProgress      = Color.parseColor("#E01462")

    val inputCursor         = Color.parseColor("#E01462")
    val inputSelection      = Color.argb(76, 224, 20, 98)

    val overlay             = Color.argb(178, 0, 0, 0)
    val overlayLight        = Color.argb(102, 0, 0, 0)
    val scrim               = Color.argb(128, 0, 0, 0)
    val popupScrim          = Color.argb(153, 0, 0, 0)
    val dialogBarrier       = Color.argb(153, 0, 0, 0)

    val iconOnDark          = Color.WHITE
    val textOnAccent        = Color.WHITE

    val shadowHard          = Color.argb(204, 0, 0, 0)
    val tooltipText         = Color.WHITE

    val thumbOverlay        = Color.argb(76, 0, 0, 0)
    val thumbDuration       = Color.BLACK
    val thumbDurationText   = Color.WHITE

    val avatarText          = Color.WHITE

    val studioAccent        = Color.parseColor("#E01462")
    val studioPublished     = Color.parseColor("#4CAF50")
    val studioDraft         = Color.parseColor("#FFC107")

    val chartLine           = Color.parseColor("#E01462")
    val chartBar            = Color.parseColor("#E01462")

    val btnSubscribe        = Color.parseColor("#E01462")
    val btnSubscribeText    = Color.WHITE

    val borderFocus         = Color.parseColor("#E01462")
    val inputBorderFocus    = Color.parseColor("#E01462")
    val chipBorderActive    = Color.TRANSPARENT

    // ── Fundos principais ────────────────────────────────────────────────────
    val bg                  = Color.parseColor("#FFFFFF")
    val bgSecondary         = Color.parseColor("#F2F2F2")
    val bgTertiary          = Color.parseColor("#E5E5E5")
    val bgQuaternary        = Color.parseColor("#D9D9D9")

    val surface             = Color.WHITE
    val surfaceAlt          = Color.parseColor("#F9F9F9")
    val card                = Color.WHITE
    val cardHover           = Color.parseColor("#F0F0F0")
    val cardPressed         = Color.parseColor("#E8E8E8")
    val cardAlt             = Color.parseColor("#F2F2F2")
    val cardSelected        = Color.parseColor("#FCE4EC")

    val appBar              = Color.WHITE
    val appBarBg            = Color.WHITE
    val appBarBorder        = Color.parseColor("#E0E0E0")

    val navBg               = Color.WHITE
    val navBorder           = Color.parseColor("#E0E0E0")
    val navActive           = Color.parseColor("#0F0F0F")
    val navInactive         = Color.parseColor("#606060")
    val navIndicator        = Color.parseColor("#EEEEEE")

    val drawerBg            = Color.WHITE
    val drawerItemBg        = Color.TRANSPARENT
    val drawerItemHover     = Color.parseColor("#F2F2F2")
    val drawerItemActive    = Color.parseColor("#FCE4EC")
    val drawerDivider       = Color.parseColor("#E0E0E0")
    val drawerText          = Color.parseColor("#0F0F0F")
    val drawerHeader        = Color.parseColor("#F9F9F9")

    val text                = Color.parseColor("#0F0F0F")
    val textSecondary       = Color.parseColor("#606060")
    val textTertiary        = Color.parseColor("#909090")
    val textHint            = Color.parseColor("#BDBDBD")
    val textDisabled        = Color.parseColor("#CCCCCC")
    val textInvert          = Color.WHITE
    val textSub             = Color.parseColor("#606060")
    val textHintAlt         = Color.parseColor("#909090")

    val emptyIcon           = Color.parseColor("#CCCCCC")
    val emptyText           = Color.parseColor("#909090")
    val emptyLinkText       = Color.parseColor("#065FD4")

    val icon                = Color.parseColor("#0F0F0F")
    val iconSub             = Color.parseColor("#606060")
    val iconTertiary        = Color.parseColor("#909090")
    val iconDisabled        = Color.parseColor("#CCCCCC")

    val divider             = Color.parseColor("#E0E0E0")
    val dividerSoft         = Color.parseColor("#EEEEEE")
    val border              = Color.parseColor("#CCCCCC")
    val borderSoft          = Color.argb(15, 0, 0, 0)

    val inputBg             = Color.WHITE
    val inputBorder         = Color.parseColor("#CCCCCC")
    val inputText           = Color.parseColor("#0F0F0F")
    val inputHint           = Color.parseColor("#BDBDBD")
    val inputIconBg         = Color.parseColor("#F2F2F2")

    val chipBg              = Color.parseColor("#E8E8E8")
    val chipBgActive        = Color.parseColor("#E01462")
    val chipText            = Color.parseColor("#0F0F0F")
    val chipTextActive      = Color.WHITE
    val chipBorder          = Color.parseColor("#CCCCCC")

    val btnSecondary        = Color.parseColor("#0F0F0F")
    val btnSecondaryText    = Color.WHITE
    val btnSecondaryHover   = Color.parseColor("#272727")
    val btnGhost            = Color.argb(15, 0, 0, 0)
    val btnGhostHover       = Color.argb(25, 0, 0, 0)
    val btnGhostText        = Color.parseColor("#0F0F0F")
    val btnLike             = Color.parseColor("#EEEEEE")
    val btnLikeActive       = Color.parseColor("#E01462")
    val btnLikeActiveText   = Color.parseColor("#E01462")
    val btnLikeText         = Color.parseColor("#0F0F0F")
    val btnSubscribed       = Color.parseColor("#EEEEEE")
    val btnSubscribedText   = Color.parseColor("#0F0F0F")
    val btnBell             = Color.parseColor("#EEEEEE")
    val btnBellText         = Color.parseColor("#0F0F0F")

    val popup               = Color.WHITE
    val popupBorder         = Color.parseColor("#E0E0E0")
    val sheet               = Color.WHITE
    val sheetHandle         = Color.parseColor("#CCCCCC")
    val dialogBg            = Color.WHITE

    val tooltipBg           = Color.parseColor("#212121")

    val thumbBg             = Color.parseColor("#E8E8E8")
    val thumbIcon           = Color.argb(66, 0, 0, 0)
    val thumbShimmer1       = Color.parseColor("#E8E8E8")
    val thumbShimmer2       = Color.parseColor("#F2F2F2")

    val avatarBg            = Color.parseColor("#CCCCCC")
    val avatarBorder        = Color.WHITE
    val channelBannerBg     = Color.parseColor("#E8E8E8")

    val miniPlayerBg        = Color.WHITE
    val miniPlayerProgress  = Color.parseColor("#E01462")
    val miniPlayerDivider   = Color.parseColor("#E0E0E0")

    val commentBg           = Color.WHITE
    val commentHighlight    = Color.parseColor("#FCE4EC")
    val commentPinned       = Color.parseColor("#E8F5E9")
    val commentHeart        = Color.parseColor("#E01462")
    val commentAuthorBg     = Color.parseColor("#EEEEEE")

    val hashtagText         = Color.parseColor("#065FD4")

    val chapterBg           = Color.parseColor("#F2F2F2")
    val chapterActive       = Color.parseColor("#E0E0E0")
    val chapterText         = Color.parseColor("#0F0F0F")
    val chapterTime         = Color.parseColor("#606060")

    val playlistBg          = Color.parseColor("#F9F9F9")
    val playlistHeader      = Color.parseColor("#EEEEEE")
    val playlistActive      = Color.parseColor("#E8E8E8")
    val playlistNumber      = Color.parseColor("#606060")

    val studioBg            = Color.parseColor("#F9F9F9")
    val studioCard          = Color.WHITE
    val studioHeader        = Color.parseColor("#F2F2F2")
    val studioPrivate       = Color.parseColor("#909090")

    val chartFill           = Color.argb(38, 224, 20, 98)
    val chartGrid           = Color.parseColor("#E0E0E0")
    val chartLabel          = Color.parseColor("#606060")
    val chartTooltipBg      = Color.parseColor("#212121")
    val chartBarAlt         = Color.parseColor("#065FD4")
    val chartBg             = Color.parseColor("#E0E0E0")

    val shimmer             = intArrayOf(
        Color.parseColor("#E8E8E8"),
        Color.parseColor("#F5F5F5"),
        Color.parseColor("#E8E8E8")
    )

    val shadow              = Color.argb(38, 0, 0, 0)
    val shadowSoft          = Color.argb(20, 0, 0, 0)

    private val listeners = mutableListOf<() -> Unit>()

    fun addThemeListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeThemeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyThemeChanged() {
        listeners.forEach { it.invoke() }
    }
}