package dev.horizon.android.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable configuration representing active UI surface theme and background settings.
 */
data class ThemeConfig(
    val accentKey: String = "cyan",
    val panelOpacity: Float = 0.72f,
    val blurRadiusDp: Float = 16f,
    val backgroundType: String = "mc", // "mc", "shader", "custom", "oled"
    val customBgUri: String? = null,
    val customBgType: String? = null
)

/**
 * Shared singleton managing reactive state updates for theme, opacity, blur, and background.
 */
object ThemeState {
    private val _state = MutableStateFlow(ThemeConfig())
    val state: StateFlow<ThemeConfig> = _state.asStateFlow()

    fun update(
        accentKey: String? = null,
        panelOpacity: Float? = null,
        blurRadiusDp: Float? = null,
        backgroundType: String? = null,
        customBgUri: String? = null,
        customBgType: String? = null
    ) {
        val curr = _state.value
        _state.value = curr.copy(
            accentKey = accentKey ?: curr.accentKey,
            panelOpacity = panelOpacity ?: curr.panelOpacity,
            blurRadiusDp = blurRadiusDp ?: curr.blurRadiusDp,
            backgroundType = backgroundType ?: curr.backgroundType,
            customBgUri = customBgUri ?: curr.customBgUri,
            customBgType = customBgType ?: curr.customBgType
        )
    }

    fun set(config: ThemeConfig) {
        _state.value = config
    }
}
