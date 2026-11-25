package com.duties.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * One setting for ALL the liquid glass of the app (bottom bar, cards, dialogs, buttons, the create window...).
 * Every value goes from 0 to 1 and 0.5 is the original look of the app:
 *
 *  - [transparency]: 1 = the glass has no tint at all (clear), 0 = the tint is twice as strong.
 *  - [blur]        : 0 = nothing behind the glass is blurred, 1 = twice the original blur.
 *  - [refraction]  : 0 = flat glass, 1 = twice the original edge bending (the lens).
 *  - [dispersion]  : the rainbow colors at the edge of the refraction (chromatic aberration).
 *                    0 = no rainbow anywhere, 0.5 = original (only the moving pill of the bottom bar and the
 *                    slider thumb have it), 1 = twice as wide there AND the rainbow also shows on every other
 *                    glass (cards, dialogs, buttons, the bottom bar).
 *
 * They are Compose states and the glass reads them while drawing, so moving a slider changes every glass
 * on the screen at once, live. They are saved on the phone and restored when the app starts.
 */
object GlassTheme {
    var transparency by mutableFloatStateOf(0.5f)
    var blur by mutableFloatStateOf(0.5f)
    var refraction by mutableFloatStateOf(0.5f)
    var dispersion by mutableFloatStateOf(0.5f)

    /** Multiplier of the alpha of the glass tint (1 at 0.5, 0 at full transparency). */
    val fillScale: Float get() = (1f - transparency) * 2f

    /** Multiplier of the blur radius. */
    val blurScale: Float get() = blur * 2f

    /** Multiplier of the lens (height and amount). */
    val lensScale: Float get() = refraction * 2f

    /** Rainbow strength of the glass that always had it (bottom bar pill, slider thumb): 1 at 0.5. */
    val dispersionScale: Float get() = dispersion * 2f

    /**
     * Rainbow strength of every other glass. Nothing at 0.5 or less (the original look), then it grows
     * up to a soft rainbow at 1.
     */
    val surfaceDispersion: Float get() = ((dispersion - 0.5f) * 2f).coerceAtLeast(0f) * 0.35f

    /**
     * The tint of a glass. Neutral tints (black / white / grey) follow the transparency setting;
     * colored tints (orange buttons...) keep their color so a button never disappears.
     */
    fun fill(c: Color): Color {
        val spread = max(c.red, max(c.green, c.blue)) - min(c.red, min(c.green, c.blue))
        if (spread > 0.12f) return c
        return c.copy(alpha = (c.alpha * fillScale).coerceIn(0f, 1f))
    }

    private const val PREFS = "duties_state"
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        transparency = p.getFloat("glass_transparency", 0.5f).coerceIn(0f, 1f)
        blur = p.getFloat("glass_blur", 0.5f).coerceIn(0f, 1f)
        refraction = p.getFloat("glass_refraction", 0.5f).coerceIn(0f, 1f)
        dispersion = p.getFloat("glass_dispersion", 0.5f).coerceIn(0f, 1f)
    }

    fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("glass_transparency", transparency)
            .putFloat("glass_blur", blur)
            .putFloat("glass_refraction", refraction)
            .putFloat("glass_dispersion", dispersion)
            .apply()
    }
}
