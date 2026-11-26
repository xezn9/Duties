package com.duties.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.duties.R
import kotlin.math.roundToInt

/**
 * The "app color" picker as the HyperSpring feature wheel ([FeatureWheel], copied from HyperSpring as it is):
 * the 20 colors are cards on a vertical drum. Drag / fling to spin it, tap the card in the middle to apply
 * that color. The center card of the wheel shows a check when it is the active color.
 */
@Composable
fun AppColorWheel(dark: Boolean, fg: Color, modifier: Modifier = Modifier) {
    val tiles = remember { ArrayList<ColorTile>() }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            val fgInt = fg.toArgb()
            AppAccent.swatches.forEachIndexed { i, s ->
                tiles += buildTile(ctx, s, dark, fgInt) { AppAccent.index = i }
            }
            val bgInt = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            FeatureWheel(ctx, tiles.map { it.view }, 60, bgInt, fgInt).also {
                it.setCenter(AppAccent.index.coerceAtLeast(0))
            }
        },
        update = {
            // reading AppAccent.index here makes this block run again whenever the color changes
            val selected = AppAccent.index
            tiles.forEachIndexed { i, t -> t.setSelected(i == selected) }
        }
    )
}

private class ColorTile(
    val view: View,
    private val box: GradientDrawable,
    private val check: ImageView,
    private val idle: Int,
    private val ring: Int,
    private val ringPx: Int
) {
    fun setSelected(on: Boolean) {
        check.visibility = if (on) View.VISIBLE else View.INVISIBLE
        box.setStroke(ringPx, if (on) ring else idle)
    }
}

private fun buildTile(ctx: Context, swatch: AppAccent.Swatch, dark: Boolean, fg: Int, onClick: () -> Unit): ColorTile {
    val d = ctx.resources.displayMetrics.density
    fun px(v: Float) = (v * d).roundToInt()

    val fill = if (dark) 0x14FFFFFF else 0x0F000000
    val idle = if (dark) 0x2EFFFFFF else 0x1F000000
    val box = GradientDrawable().apply {
        cornerRadius = 16f * d
        setColor(fill)
        setStroke(px(1.2f), idle)
    }

    val dot = View(ctx).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(swatch.color.toArgb())
        }
    }
    val name = TextView(ctx).apply {
        text = swatch.name
        setTextColor(fg)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        maxLines = 1
    }
    val check = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_check)
        setColorFilter(fg)
        visibility = View.INVISIBLE
    }

    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(16f), 0, px(16f), 0)
        background = box
        isClickable = true
        setOnClickListener { onClick() }
        addView(dot, LinearLayout.LayoutParams(px(34f), px(34f)).apply { marginEnd = px(14f) })
        addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(check, LinearLayout.LayoutParams(px(22f), px(22f)))
    }
    return ColorTile(row, box, check, idle, fg, px(2.2f))
}
