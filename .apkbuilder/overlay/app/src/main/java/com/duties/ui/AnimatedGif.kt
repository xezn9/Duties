package com.duties.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** GIF frames are decoded at most this big (the longest side), so a big GIF does not eat the memory. */
private const val GIF_MAX_SIDE = 1080

/**
 * The banner picture. A normal picture is shown as before; a GIF ([ImageSlot.gif]) is shown animated and
 * repeats forever. The still first frame ([ImageSlot.cropped]) is shown while the GIF loads, and on
 * Android 8 (API 26-27), which cannot play GIFs in this way.
 *
 * The position / zoom the user chose on the "move the banner" page is applied to the GIF too, so it shows
 * the same part as the still picture.
 *
 * @param fit true = the whole banner is visible (full screen viewer), false = it fills the space (crop)
 * @param playing false pauses the GIF (when the page is hidden behind another page)
 */
@Composable
fun BannerPicture(
    slot: ImageSlot,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
    playing: Boolean = true
) {
    val bmp = slot.cropped ?: return
    val gif = slot.gif
    val drawable = if (gif != null && Build.VERSION.SDK_INT >= 28) rememberGifDrawable(gif) else null
    if (drawable != null && Build.VERSION.SDK_INT >= 28) {
        GifCanvas(drawable, slot.tf, modifier, fit, playing)
    } else {
        val img = remember(bmp) { bmp.asImageBitmap() }
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            modifier = modifier
        )
    }
}

/** Decodes the GIF off the main thread. null = not ready yet (or it has only one frame / it is broken). */
@RequiresApi(28)
@Composable
private fun rememberGifDrawable(file: File): AnimatedImageDrawable? {
    val state = produceState<AnimatedImageDrawable?>(null, file) {
        value = withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > GIF_MAX_SIDE) {
                        val k = GIF_MAX_SIDE.toFloat() / longest
                        decoder.setTargetSize(
                            (w * k).roundToInt().coerceAtLeast(1),
                            (h * k).roundToInt().coerceAtLeast(1)
                        )
                    }
                } as? AnimatedImageDrawable
            } catch (e: Exception) {
                null
            }
        }
    }
    return state.value
}

@RequiresApi(28)
@Composable
private fun GifCanvas(
    d: AnimatedImageDrawable,
    tf: CropTransform?,
    modifier: Modifier,
    fit: Boolean,
    playing: Boolean
) {
    var tick by remember(d) { mutableIntStateOf(0) }

    DisposableEffect(d) {
        d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE      // it repeats by itself, forever
        d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
        val handler = Handler(Looper.getMainLooper())
        // a Drawable keeps its callback weakly, so this object is held by onDispose below
        val callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) { tick++ }          // a new frame: draw again
            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                handler.postAtTime(what, who, `when`)
            }
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                handler.removeCallbacks(what, who)
            }
        }
        d.callback = callback
        onDispose {
            d.stop()
            if (d.callback === callback) d.callback = null
        }
    }
    LaunchedEffect(d, playing) { if (playing) d.start() else d.stop() }

    Canvas(modifier) {
        if (tick < 0) return@Canvas                       // only reads [tick], so a new frame redraws this
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (iw <= 0f || ih <= 0f || size.width <= 0f || size.height <= 0f) return@Canvas

        // The part of the GIF that the 2:1 banner frame shows (same maths as the "move the banner" page).
        val fw = 1000f
        val fh = fw / BANNER_ASPECT
        val base = max(fw / iw, fh / ih)
        val zoom = (tf?.zoom ?: 1f).coerceIn(1f, 8f)
        val s = base * zoom
        val dw = iw * s
        val dh = ih * s
        val limX = max(0f, (dw - fw) / 2f)
        val limY = max(0f, (dh - fh) / 2f)
        val ox = ((tf?.ox ?: 0f) * fw).coerceIn(-limX, limX)
        val oy = ((tf?.oy ?: 0f) * fh).coerceIn(-limY, limY)
        val srcW = fw / s
        val srcH = fh / s
        val srcL = (dw / 2f - fw / 2f - ox) / s
        val srcT = (dh / 2f - fh / 2f - oy) / s

        // that part goes into this space (fill = crop, or fit = all of it visible)
        val k = if (fit) min(size.width / srcW, size.height / srcH) else max(size.width / srcW, size.height / srcH)

        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            c.save()
            c.clipRect(0f, 0f, size.width, size.height)
            c.translate(size.width / 2f - (srcL + srcW / 2f) * k, size.height / 2f - (srcT + srcH / 2f) * k)
            c.scale(k, k)
            d.draw(c)
            c.restore()
        }
    }
}
