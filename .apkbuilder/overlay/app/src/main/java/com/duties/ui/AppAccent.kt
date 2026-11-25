package com.duties.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * "App color": ONE color that replaces the main colors of the whole app (the selected tab, the sliders, the
 * confirm buttons, the orange buttons, the glowing dots, the chat send button...).
 *
 * [index] = -1 means "original": nothing is replaced and the app looks exactly as before. Any other value is
 * the position in [swatches]. It is a Compose state, so every place that asks for the color through [pick] /
 * [deep] changes live when the person taps another color, and it is saved on the phone.
 */
object AppAccent {
    class Swatch(val name: String, val color: Color)

    /** The 20 colors of the App theme page. */
    val swatches: List<Swatch> = listOf(
        Swatch("أزرق", Color(0xFF0A84FF)),
        Swatch("سماوي", Color(0xFF32ADE6)),
        Swatch("فيروزي", Color(0xFF00C7BE)),
        Swatch("نعناعي", Color(0xFF2BD9A0)),
        Swatch("أخضر", Color(0xFF30D158)),
        Swatch("ليموني", Color(0xFF9BD800)),
        Swatch("أصفر", Color(0xFFFFD60A)),
        Swatch("ذهبي", Color(0xFFFFB000)),
        Swatch("برتقالي", Color(0xFFFF8A1F)),
        Swatch("مرجاني", Color(0xFFFF5F45)),
        Swatch("أحمر", Color(0xFFFF3B30)),
        Swatch("وردي", Color(0xFFFF375F)),
        Swatch("فوشي", Color(0xFFFF2D95)),
        Swatch("عنابي", Color(0xFFB4234E)),
        Swatch("بنفسجي فاتح", Color(0xFFBF5AF2)),
        Swatch("بنفسجي", Color(0xFF8A5CFF)),
        Swatch("نيلي", Color(0xFF5E5CE6)),
        Swatch("أزرق ملكي", Color(0xFF2F54EB)),
        Swatch("بني", Color(0xFFA2845E)),
        Swatch("رمادي", Color(0xFF8E9AAF))
    )

    /** -1 = original look of the app. */
    var index by mutableIntStateOf(-1)

    val active: Boolean get() = index in swatches.indices

    val color: Color get() = swatches[index].color

    /** For bright colors (tabs, sliders, glows, icons): the chosen color, or [original] when none is chosen. */
    fun pick(original: Color): Color = if (active) color else original

    /**
     * For colors that carry white text or icons (the orange buttons): the chosen color a bit darker so the
     * white on top stays readable. [extra] makes it darker still (second color of a gradient).
     */
    fun deep(original: Color, extra: Float = 0f): Color {
        if (!active) return original
        val k = (0.82f - extra).coerceIn(0.3f, 1f)
        val c = color
        return Color(c.red * k, c.green * k, c.blue * k, 1f)
    }

    private const val PREFS = "duties_state"
    private const val KEY = "app_accent_index"
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val v = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, -1)
        index = if (v in swatches.indices) v else -1
    }

    fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY, index)
            .apply()
    }
}
