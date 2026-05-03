// SUBSTITUIR a função buildM3Loader() completa:

@androidx.annotation.OptIn(com.google.android.material.progressindicator.BaseProgressIndicator::class)
private fun buildM3Loader(): CircularProgressIndicator {
    val indicator = CircularProgressIndicator(context)
    indicator.isIndeterminate   = true
    indicator.indicatorSize     = dp(30)
    indicator.trackThickness    = dp(3)
    indicator.trackCornerRadius = dp(50)
    indicator.setIndicatorColor(AppTheme.ytRed)
    indicator.trackColor = Color.parseColor("#22000000")
    try {
        val cls = indicator.javaClass.superclass
        cls?.getDeclaredMethod("setWavelength", Int::class.java)
            ?.apply { isAccessible = true }
            ?.invoke(indicator, dp(8))
        cls?.getDeclaredMethod("setWaveAmplitude", Int::class.java)
            ?.apply { isAccessible = true }
            ?.invoke(indicator, dp(2))
    } catch (_: Exception) {}
    return indicator
}