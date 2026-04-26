package com.doction.webviewapp.theme

import android.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// AppTheme — espelho fiel do app_theme.dart Flutter
// Uso: AppTheme.current.bg  /  AppTheme.isDark = true/false
// ─────────────────────────────────────────────────────────────────────────────
object AppTheme {

    var isDark: Boolean = true

    /** Instância actual — compatível com o padrão AppTheme.current do Flutter */
    val current get() = this

    // ═════════════════════════════════════════════════════════════════════════
    // ESTÁTICAS — iguais em dark e light
    // ═════════════════════════════════════════════════════════════════════════

    // ── YouTube Brand ────────────────────────────────────────────────────────
    val ytRed           = Color.parseColor("#FF0000")
    val ytRedDark       = Color.parseColor("#CC0000")
    val ytRedLight      = Color.parseColor("#FF4444")
    val ytRedDeep       = Color.parseColor("#BF0000")
    val ytWhite         = Color.WHITE
    val ytBlack         = Color.BLACK

    // ── Live / Shorts ────────────────────────────────────────────────────────
    val live            = Color.parseColor("#FF0000")
    val liveText        = Color.WHITE
    val shortsRed       = Color.parseColor("#FF0033")
    val shortsRedDark   = Color.parseColor("#CC0029")

    // ── Membership / Sponsor ─────────────────────────────────────────────────
    val membership      = Color.parseColor("#1565C0")
    val membershipLight = Color.parseColor("#1976D2")
    val sponsor         = Color.parseColor("#00695C")

    // ── Super Chat / Sticker ─────────────────────────────────────────────────
    val superChatBlue   = Color.parseColor("#1565C0")
    val superChatCyan   = Color.parseColor("#0097A7")
    val superChatGreen  = Color.parseColor("#00897B")
    val superChatYellow = Color.parseColor("#F9A825")
    val superChatOrange = Color.parseColor("#EF6C00")
    val superChatPink   = Color.parseColor("#C62828")
    val superChatRed    = Color.parseColor("#B71C1C")

    // ── Progress / Seek Bar ──────────────────────────────────────────────────
    val progressPlayed  = Color.parseColor("#FF0000")
    val progressBuffer  = Color.parseColor("#909090")
    val progressBg      = Color.parseColor("#535353")
    val progressThumb   = Color.parseColor("#FF0000")

    // ── Badges de qualidade ──────────────────────────────────────────────────
    val badge4K         = Color.parseColor("#4CAF50")
    val badgeHD         = Color.parseColor("#2196F3")
    val badgeSDR        = Color.parseColor("#9E9E9E")
    val badgeHDR        = Color.parseColor("#FFC107")
    val badge360        = Color.parseColor("#9C27B0")
    val badgeCC         = Color.WHITE

    // ── Links ────────────────────────────────────────────────────────────────
    val link            = Color.parseColor("#3EA6FF")
    val linkVisited     = Color.parseColor("#9575CD")

    // ── Verificado ───────────────────────────────────────────────────────────
    val verified        = Color.parseColor("#AAAAAA")
    val verifiedPremium = Color.parseColor("#FFD600")

    // ── Notificação ──────────────────────────────────────────────────────────
    val notifBadge      = Color.parseColor("#FF0000")
    val notifBadgeText  = Color.WHITE

    // ── Anúncio / Ad ─────────────────────────────────────────────────────────
    val adBadge         = Color.parseColor("#FFD700")
    val adBadgeText     = Color.BLACK
    val adSkipBtn       = Color.parseColor("#212121")
    val adSkipText      = Color.WHITE

    // ── Legendas ─────────────────────────────────────────────────────────────
    val captionBg       = Color.parseColor("#BF000000")
    val captionText     = Color.WHITE

    // ── Erro / Aviso / Sucesso ───────────────────────────────────────────────
    val error           = Color.parseColor("#FF4444")
    val errorDark       = Color.parseColor("#CC0000")
    val warning         = Color.parseColor("#FFC107")
    val warningDark     = Color.parseColor("#F57F17")
    val success         = Color.parseColor("#4CAF50")
    val successDark     = Color.parseColor("#2E7D32")
    val info            = Color.parseColor("#3EA6FF")

    // ── Botão primário (estático) ─────────────────────────────────────────────
    val btnPrimary      = Color.parseColor("#FF0000")
    val btnPrimaryHover = Color.parseColor("#CC0000")
    val btnPrimaryText  = Color.WHITE
    val btnPrimaryPressed = Color.parseColor("#BF0000")

    // ── Toast (estático) ─────────────────────────────────────────────────────
    val toastBg         = Color.parseColor("#323232")
    val toastText       = Color.WHITE
    val toastAction     = Color.parseColor("#FF0000")

    // ── Player (estático) ────────────────────────────────────────────────────
    val playerBg            = Color.BLACK
    val playerControls      = Color.WHITE

    // ── Shorts (estático) ────────────────────────────────────────────────────
    val shortsBg        = Color.BLACK
    val shortsText      = Color.WHITE
    val shortsIcon      = Color.WHITE
    val shortsProgress  get() = shortsRed

    // ── Input cursor / selection (estático) ──────────────────────────────────
    val inputCursor     = Color.parseColor("#FF0000")

    // ═════════════════════════════════════════════════════════════════════════
    // DINÂMICAS — mudam com isDark
    // ═════════════════════════════════════════════════════════════════════════

    // ── Fundos principais ────────────────────────────────────────────────────
    val bg              get() = if (isDark) Color.parseColor("#0F0F0F") else Color.parseColor("#FFFFFF")
    val bgSecondary     get() = if (isDark) Color.parseColor("#181818") else Color.parseColor("#F2F2F2")
    val bgTertiary      get() = if (isDark) Color.parseColor("#212121") else Color.parseColor("#E5E5E5")
    val bgQuaternary    get() = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#D9D9D9")

    // ── Superfícies / Cards ──────────────────────────────────────────────────
    val surface         get() = if (isDark) Color.parseColor("#212121") else Color.WHITE
    val surfaceAlt      get() = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#F9F9F9")
    val card            get() = if (isDark) Color.parseColor("#1F1F1F") else Color.WHITE
    val cardHover       get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#F0F0F0")
    val cardPressed     get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#E8E8E8")
    val cardAlt         get() = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#F2F2F2")
    val cardSelected    get() = if (isDark) Color.parseColor("#263238") else Color.parseColor("#E3F2FD")

    // ── AppBar / TopBar ──────────────────────────────────────────────────────
    val appBar          get() = if (isDark) Color.parseColor("#0F0F0F") else Color.WHITE
    val appBarBg        get() = appBar   // alias
    val appBarBorder    get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#E0E0E0")

    // ── Bottom Navigation ────────────────────────────────────────────────────
    val navBg           get() = if (isDark) Color.parseColor("#0F0F0F") else Color.WHITE
    val navBorder       get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#E0E0E0")
    val navActive       get() = if (isDark) Color.WHITE                 else Color.parseColor("#0F0F0F")
    val navInactive     get() = if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#606060")
    val navIndicator    get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#EEEEEE")

    // ── Side Navigation / Drawer ─────────────────────────────────────────────
    val drawerBg            get() = if (isDark) Color.parseColor("#0F0F0F") else Color.WHITE
    val drawerItemBg        get() = Color.TRANSPARENT
    val drawerItemHover     get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#F2F2F2")
    val drawerItemActive    get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#E8E8E8")
    val drawerDivider       get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#E0E0E0")
    val drawerText          get() = text
    val drawerHeader        get() = if (isDark) Color.parseColor("#181818") else Color.parseColor("#F9F9F9")

    // ── Texto ────────────────────────────────────────────────────────────────
    val text            get() = if (isDark) Color.WHITE                 else Color.parseColor("#0F0F0F")
    val textSecondary   get() = if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#606060")
    val textTertiary    get() = if (isDark) Color.parseColor("#717171") else Color.parseColor("#909090")
    val textHint        get() = if (isDark) Color.parseColor("#535353") else Color.parseColor("#BDBDBD")
    val textDisabled    get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#CCCCCC")
    val textOnAccent    = Color.WHITE
    val textInvert      get() = if (isDark) Color.parseColor("#0F0F0F") else Color.WHITE
    val textSub         get() = textSecondary   // alias Flutter
    val textHintAlt     get() = textTertiary    // alias Flutter

    // ── Estado vazio / placeholder ───────────────────────────────────────────
    val emptyIcon       get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#CCCCCC")
    val emptyText       get() = if (isDark) Color.parseColor("#717171") else Color.parseColor("#909090")
    val emptyLinkText   get() = if (isDark) Color.parseColor("#3EA6FF") else Color.parseColor("#065FD4")

    // ── Ícones ───────────────────────────────────────────────────────────────
    val icon            get() = if (isDark) Color.WHITE                 else Color.parseColor("#0F0F0F")
    val iconSub         get() = if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#606060")
    val iconTertiary    get() = if (isDark) Color.parseColor("#717171") else Color.parseColor("#909090")
    val iconDisabled    get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#CCCCCC")
    val iconOnDark      = Color.WHITE

    // ── Bordas / Divisores ───────────────────────────────────────────────────
    val divider         get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#E0E0E0")
    val dividerSoft     get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#EEEEEE")
    val border          get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#CCCCCC")
    val borderSoft      get() = if (isDark) Color.argb(20, 255, 255, 255) else Color.argb(15, 0, 0, 0)
    val borderFocus     = Color.parseColor("#FF0000")

    // ── Input / Search Bar ───────────────────────────────────────────────────
    val inputBg             get() = if (isDark) Color.parseColor("#121212") else Color.WHITE
    val inputBorder         get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#CCCCCC")
    val inputBorderFocus    = Color.parseColor("#FF0000")
    val inputText           get() = text
    val inputHint           get() = textHint
    val inputIconBg         get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#F2F2F2")
    val inputSelection      get() = Color.argb(76, 255, 0, 0)   // ytRed a 30%

    // ── Chip / Filtro / Pill ─────────────────────────────────────────────────
    val chipBg          get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#E8E8E8")
    val chipBgActive    get() = if (isDark) Color.WHITE                 else Color.parseColor("#0F0F0F")
    val chipText        get() = if (isDark) Color.WHITE                 else Color.parseColor("#0F0F0F")
    val chipTextActive  get() = if (isDark) Color.parseColor("#0F0F0F") else Color.WHITE
    val chipBorder      get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#CCCCCC")
    val chipBorderActive = Color.TRANSPARENT

    // ── Botões secundário / ghost ─────────────────────────────────────────────
    val btnSecondary        get() = if (isDark) Color.WHITE else Color.parseColor("#0F0F0F")
    val btnSecondaryText    get() = if (isDark) Color.parseColor("#0F0F0F") else Color.WHITE
    val btnSecondaryHover   get() = if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#272727")
    val btnGhost            get() = if (isDark) Color.argb(25, 255, 255, 255) else Color.argb(15, 0, 0, 0)
    val btnGhostHover       get() = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(25, 0, 0, 0)
    val btnGhostText        get() = text
    val btnLike             get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#EEEEEE")
    val btnLikeActive       get() = if (isDark) Color.parseColor("#3EA6FF") else Color.parseColor("#065FD4")
    val btnLikeActiveText   get() = if (isDark) Color.parseColor("#3EA6FF") else Color.parseColor("#065FD4")
    val btnLikeText         get() = text
    val btnSubscribe        = Color.parseColor("#FF0000")
    val btnSubscribeText    = Color.WHITE
    val btnSubscribed       get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#EEEEEE")
    val btnSubscribedText   get() = text
    val btnBell             get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#EEEEEE")
    val btnBellText         get() = text

    // ── Overlay / Scrim ──────────────────────────────────────────────────────
    val overlay         get() = Color.argb(178, 0, 0, 0)   // 70%
    val overlayLight    get() = Color.argb(102, 0, 0, 0)   // 40%
    val scrim           get() = Color.argb(128, 0, 0, 0)   // 50%
    val popupScrim      get() = Color.argb(153, 0, 0, 0)   // 60%

    // ── Popup / BottomSheet / Dialog ─────────────────────────────────────────
    val popup           get() = if (isDark) Color.parseColor("#212121") else Color.WHITE
    val popupBorder     get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#E0E0E0")
    val sheet           get() = if (isDark) Color.parseColor("#212121") else Color.WHITE
    val sheetHandle     get() = if (isDark) Color.parseColor("#535353") else Color.parseColor("#CCCCCC")
    val dialogBg        get() = if (isDark) Color.parseColor("#212121") else Color.WHITE
    val dialogBarrier   get() = Color.argb(153, 0, 0, 0)   // 60%

    // ── Tooltip ──────────────────────────────────────────────────────────────
    val tooltipBg       get() = if (isDark) Color.parseColor("#616161") else Color.parseColor("#212121")
    val tooltipText     = Color.WHITE

    // ── Thumbnail / Feed ─────────────────────────────────────────────────────
    val thumbBg         get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#E8E8E8")
    val thumbIcon       get() = if (isDark) Color.argb(63, 255, 255, 255) else Color.argb(66, 0, 0, 0)
    val thumbOverlay    get() = Color.argb(76, 0, 0, 0)    // 30%
    val thumbDuration   = Color.BLACK
    val thumbDurationText = Color.WHITE
    val thumbShimmer1   get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#E8E8E8")
    val thumbShimmer2   get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#F2F2F2")

    // ── Avatar ───────────────────────────────────────────────────────────────
    val avatarBg        get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#CCCCCC")
    val avatarText      = Color.WHITE
    val avatarBorder    get() = if (isDark) Color.parseColor("#0F0F0F") else Color.WHITE
    val channelBannerBg get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#E8E8E8")

    // ── Player ───────────────────────────────────────────────────────────────
    val playerControlsBg        get() = Color.argb(153, 0, 0, 0)   // 60%
    val playerProgressPlayed    = Color.parseColor("#FF0000")
    val playerProgressBuffer    = Color.parseColor("#909090")
    val playerProgressBg        = Color.parseColor("#535353")
    val playerProgressThumb     = Color.parseColor("#FF0000")
    val playerTimestamp         = Color.WHITE
    val playerTimestampBg       get() = Color.argb(153, 0, 0, 0)
    val playerQualityBadge      = Color.parseColor("#212121")
    val playerQualityText       = Color.WHITE
    val playerEndscreenBg       get() = Color.argb(178, 0, 0, 0)

    // ── Mini Player ──────────────────────────────────────────────────────────
    val miniPlayerBg        get() = if (isDark) Color.parseColor("#212121") else Color.WHITE
    val miniPlayerProgress  = Color.parseColor("#FF0000")
    val miniPlayerDivider   get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#E0E0E0")

    // ── Comments ─────────────────────────────────────────────────────────────
    val commentBg           get() = if (isDark) Color.parseColor("#0F0F0F") else Color.WHITE
    val commentHighlight    get() = if (isDark) Color.parseColor("#1A2733") else Color.parseColor("#E8F4FE")
    val commentPinned       get() = if (isDark) Color.parseColor("#1F2820") else Color.parseColor("#E8F5E9")
    val commentHeart        = Color.parseColor("#FF0000")
    val commentAuthorBg     get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#EEEEEE")

    // ── Hashtag / Tags ───────────────────────────────────────────────────────
    val hashtagText     get() = if (isDark) Color.parseColor("#3EA6FF") else Color.parseColor("#065FD4")

    // ── Chapter / Seção ──────────────────────────────────────────────────────
    val chapterBg       get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#F2F2F2")
    val chapterActive   get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#E0E0E0")
    val chapterText     get() = text
    val chapterTime     get() = textSecondary

    // ── Playlist ─────────────────────────────────────────────────────────────
    val playlistBg      get() = if (isDark) Color.parseColor("#181818") else Color.parseColor("#F9F9F9")
    val playlistHeader  get() = if (isDark) Color.parseColor("#212121") else Color.parseColor("#EEEEEE")
    val playlistActive  get() = if (isDark) Color.parseColor("#272727") else Color.parseColor("#E8E8E8")
    val playlistNumber  get() = textSecondary

    // ── Studio / Creator ─────────────────────────────────────────────────────
    val studioBg        get() = if (isDark) Color.parseColor("#0F0F0F") else Color.parseColor("#F9F9F9")
    val studioCard      get() = if (isDark) Color.parseColor("#212121") else Color.WHITE
    val studioHeader    get() = if (isDark) Color.parseColor("#181818") else Color.parseColor("#F2F2F2")
    val studioAccent    = Color.parseColor("#FF0000")
    val studioPublished = Color.parseColor("#4CAF50")
    val studioDraft     = Color.parseColor("#FFC107")
    val studioPrivate   get() = if (isDark) Color.parseColor("#717171") else Color.parseColor("#909090")

    // ── Analytics / Gráficos ─────────────────────────────────────────────────
    val chartLine       = Color.parseColor("#FF0000")
    val chartFill       get() = Color.argb(38, 255, 0, 0)   // ytRed a 15%
    val chartGrid       get() = if (isDark) Color.parseColor("#303030") else Color.parseColor("#E0E0E0")
    val chartLabel      get() = textSecondary
    val chartTooltipBg  get() = if (isDark) Color.parseColor("#3D3D3D") else Color.parseColor("#212121")
    val chartBar        = Color.parseColor("#FF0000")
    val chartBarAlt     get() = if (isDark) Color.parseColor("#3EA6FF") else Color.parseColor("#065FD4")
    val chartBg         get() = chartGrid  // alias

    // ── Skeleton / Shimmer ───────────────────────────────────────────────────
    val shimmer         get() = if (isDark)
        intArrayOf(Color.parseColor("#272727"), Color.parseColor("#3D3D3D"), Color.parseColor("#272727"))
    else
        intArrayOf(Color.parseColor("#E8E8E8"), Color.parseColor("#F5F5F5"), Color.parseColor("#E8E8E8"))

    // ── Sombras ──────────────────────────────────────────────────────────────
    val shadow          get() = if (isDark) Color.argb(153, 0, 0, 0) else Color.argb(38, 0, 0, 0)
    val shadowSoft      get() = if (isDark) Color.argb(102, 0, 0, 0) else Color.argb(20, 0, 0, 0)
    val shadowHard      get() = Color.argb(204, 0, 0, 0)

    // ─────────────────────────────────────────────────────────────────────────
    // Listeners de tema — as views registam-se aqui para redesenhar-se
    // ─────────────────────────────────────────────────────────────────────────
    private val listeners = mutableListOf<() -> Unit>()

    fun addThemeListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeThemeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** Chama isto depois de alterar isDark para notificar todas as views */
    fun notifyThemeChanged() {
        listeners.forEach { it.invoke() }
    }
}