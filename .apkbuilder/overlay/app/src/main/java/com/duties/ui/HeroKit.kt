package com.duties.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.launch

/*
 * HeroKit.kt — the three pieces of the "About" header of HyperSpring, ported to Compose:
 *
 *   1. FloatingSpinImage   the logo (here: any content) floats slowly up and down; it sinks a little when pressed
 *                          and, when tapped, makes one full turn on a spring (like FloatingSpin in SettingsActivity.java).
 *   2. SplitTitle          the name that enters letter by letter: already in HyperSpringAnimations.kt, it only got the
 *                          new parameters  centered = true  (the hero is centered) and  fontWeight  (the original uses 800).
 *   3. Marquee             the huge band of words that never stops (Marquee.java): filled word, outlined word, accent
 *                          diamond between them; the page scroll pushes it (down = faster, up = the other way for a moment).
 */

// ---------------------------------------------------------------------
// 1. Floating + spinning image
// ---------------------------------------------------------------------

/** Android's OvershootInterpolator(tension) as an Easing (the little pop when the finger is lifted). */
private fun overshoot(tension: Float) = Easing { t ->
    val x = t - 1f
    x * x * ((tension + 1f) * x + tension) + 1f
}

private val FLOAT_EASING = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)   // ≈ AccelerateDecelerateInterpolator

/**
 * [content] floats up and down 5dp (2.6 s, back and forth). Press = it sinks to 96 %; lift = it comes back with an
 * overshoot. Tap = one turn with a spring (response 0.7 s, damping 0.6: it passes 360° a little and settles).
 */
@Composable
fun FloatingSpinImage(
    modifier: Modifier = Modifier,
    floatDp: Float = 5f,
    onTap: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val rotation = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    val transition = rememberInfiniteTransition(label = "heroFloat")
    val dy by transition.animateFloat(
        initialValue = -floatDp,
        targetValue = floatDp,
        animationSpec = infiniteRepeatable(tween(2600, easing = FLOAT_EASING), RepeatMode.Reverse),
        label = "dy"
    )
    // HyperSpring's Spring.apply(response = 0.7 s, damping = 0.6)  ->  stiffness = (2π / response)²
    val stiffness = remember { val w = (2.0 * PI / 0.7).toFloat(); w * w }

    Box(
        modifier
            .graphicsLayer {
                translationY = dy.dp.toPx()
                rotationZ = rotation.value
                scaleX = press.value
                scaleY = press.value
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch { press.animateTo(0.96f, tween(140)) }
                        tryAwaitRelease()
                        scope.launch { press.animateTo(1f, tween(380, easing = overshoot(2.2f))) }
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onTap()
                        scope.launch {
                            val from = rotation.value % 360f
                            rotation.snapTo(from)
                            rotation.animateTo(360f, spring(dampingRatio = 0.6f, stiffness = stiffness))
                            rotation.snapTo(0f)            // 360° and 0° look the same: ready for the next tap
                        }
                    }
                )
            }
    ) { content() }
}

// ---------------------------------------------------------------------
// 3. Marquee (the words that move)
// ---------------------------------------------------------------------

/** Holds the speed of a [Marquee]; [push] is how the page scroll pushes it. [current] = the word that is in the middle. */
class MarqueeState {
    internal var speed = 0f          // px / s
    internal var idle = 0f           // calm speed (42 dp / s), set by Marquee once it knows the density
    internal var viewWidth = 0       // px, set by Marquee
    internal var offset by mutableFloatStateOf(0f)

    /** Index (in the list given to Marquee) of the word that is passing through the middle of the band right now. */
    var current by mutableIntStateOf(0)
        internal set

    /** [dy] in px: positive = the page goes down (faster), negative = up (it goes the other way for a moment). */
    fun push(dy: Float) {
        if (idle == 0f) return
        val max = idle * 22f
        speed = (speed + dy * 9f).coerceIn(-max, max)
    }

    /**
     * Gyroscope push. [angle] = how far the phone turned about its long axis since the last sample (radians):
     * positive = tilted to the RIGHT -> the words slide to the right; negative = LEFT -> they slide to the left.
     * (The calm pace moves the band to the left, which is a positive [speed]; so a right tilt makes the speed negative.)
     */
    fun tilt(angle: Float) {
        if (idle == 0f) return
        val max = idle * 22f
        speed = (speed - angle * idle * TILT_GAIN).coerceIn(-max, max)
    }
}

/** How strong the gyroscope pushes the band: bigger = a small tilt moves the words faster. */
private const val TILT_GAIN = 16f
/** Rotation slower than this (rad/s) is sensor noise / hand tremor and is ignored. */
private const val TILT_DEAD_ZONE = 0.12f
/** Flip to -1f if on some phone the words slide the wrong way. */
private const val TILT_SIGN = 1f

@Composable
fun rememberMarqueeState() = remember { MarqueeState() }

/** Pushes the marquee with a ScrollState (call it next to the Marquee): scrolling down speeds it up, up reverses it. */
@Composable
fun MarqueeState.FollowScroll(scroll: ScrollState) {
    LaunchedEffect(scroll) {
        var last = scroll.value
        snapshotFlow { scroll.value }.collect { v ->
            push((v - last).toFloat())
            last = v
        }
    }
}

/**
 * Pushes the marquee with the gyroscope (call it next to the Marquee): tilt / turn the phone to the right and the words
 * slide to the right, to the left and they slide to the left; then they return to the calm pace by themselves.
 * It listens only while the screen is on top (RESUMED) and does nothing on a phone without a gyroscope.
 */
@Composable
fun MarqueeState.FollowTilt() {
    val context = LocalContext.current
    val state = this
    DisposableEffect(context, state) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (sm == null || gyro == null) return@DisposableEffect onDispose { }

        var lastNs = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (lastNs != 0L) {
                    val dt = ((e.timestamp - lastNs) / 1e9f).coerceIn(0f, 0.1f)
                    // values[1] = rotation speed about the Y axis (the long axis of the phone) in rad/s:
                    // positive when the right edge goes down = tilt to the right
                    val w = e.values[1] * TILT_SIGN
                    if (abs(w) > TILT_DEAD_ZONE) state.tilt(w * dt)
                }
                lastNs = e.timestamp
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        fun start() { lastNs = 0L; sm.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME) }
        fun stop() { sm.unregisterListener(listener) }

        // follow the activity: no sensor while the app is in the background
        var c: Context? = context
        while (c is ContextWrapper && c !is LifecycleOwner) c = c.baseContext
        val owner = c as? LifecycleOwner
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(o: LifecycleOwner) = start()
            override fun onPause(o: LifecycleOwner) = stop()
        }
        if (owner != null) owner.lifecycle.addObserver(observer) else start()   // addObserver calls onResume at once if already resumed

        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            stop()
        }
    }
}

/**
 * Huge band of words that passes from side to side without end: words alternate bright / dim (or filled / only outline
 * when [outlined] is true), separated by a diamond of the accent color. The speed returns by itself to the calm pace after
 * a [MarqueeState.push]. [MarqueeState.current] tells which word is in the middle, so other things can follow it.
 * Give it the full width of the screen (no horizontal padding around it).
 *
 * [outlined] = false is for Arabic: the letters of a joined word overlap where they join, and an outline would show
 * a line at every join, so the second kind of word is drawn dim ([outlineColor]) instead of outlined.
 */
@Composable
fun Marquee(
    words: List<String>,
    modifier: Modifier = Modifier,
    state: MarqueeState = rememberMarqueeState(),
    accent: Color = ACCENT,
    fillColor: Color = Color(0xFFF5F5F7),
    outlineColor: Color = Color.White.copy(alpha = 0.5f),
    outlined: Boolean = true
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // size in dp, not sp: it is decoration (with a big system font it would take half the screen)
    val fontSize = with(density) { 48.dp.toSp() }
    // Arabic: no letter spacing (it would cut the joins between the letters)
    val joined = remember(words) { words.any { w -> w.any { it.code in 0x0590..0x08FF } } }
    val style = remember(fontSize, joined) {
        TextStyle(fontSize = fontSize, fontWeight = FontWeight.ExtraBold, letterSpacing = if (joined) 0.em else (-0.03f).em)
    }
    // With an odd number of words the bright / dim pattern would flip every lap: the list is doubled so it never does
    val seq = remember(words) { if (words.size % 2 == 1) words + words else words }
    val layouts = remember(seq, style) { seq.map { measurer.measure(it, style) } }
    val gap = with(density) { 26.dp.toPx() }
    val diamond = with(density) { 7.dp.toPx() }
    val strokePx = with(density) { 1.2.dp.toPx() }
    val period = remember(layouts, gap) { layouts.sumOf { (it.size.width + gap * 2).toDouble() }.toFloat() }
    val heightDp = with(density) { (layouts.maxOf { it.size.height }).toDp() } + 8.dp

    /** The word whose stretch (from the middle of the gap before it to the middle of the gap after it) holds the centre. */
    fun wordInMiddle(): Int {
        val cx = state.viewWidth / 2f
        var x = -state.offset
        for (i in 0 until seq.size * 4 + 8) {
            val w = layouts[i % layouts.size].size.width
            if (cx < x + w + gap) return (i % layouts.size) % words.size
            x += w + gap * 2
        }
        return 0
    }

    LaunchedEffect(state) {
        state.idle = with(density) { 42.dp.toPx() }
        if (state.speed == 0f) state.speed = state.idle
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    val dt = min(0.05f, (now - last) / 1e9f)
                    // the speed comes back to the calm pace little by little (like GSAP's inertia)
                    state.speed += (state.idle - state.speed) * min(1f, dt * 3.2f)
                    var o = (state.offset + state.speed * dt) % period
                    if (o < 0f) o += period
                    state.offset = o
                }
                last = now
                state.current = wordInMiddle()
            }
        }
    }

    Canvas(modifier.fillMaxWidth().height(heightDp).onSizeChanged { state.viewWidth = it.width }) {
        var x = -state.offset
        var i = 0
        val cy = size.height / 2f
        // the words are repeated until the whole width is covered
        while (x < size.width) {
            val k = i % layouts.size
            val l = layouts[k]
            if (x + l.size.width > 0f) {
                val top = cy - l.size.height / 2f
                when {
                    k % 2 == 0 -> drawText(l, color = fillColor, topLeft = Offset(x, top))
                    outlined -> drawText(l, color = outlineColor, topLeft = Offset(x, top), drawStyle = Stroke(strokePx))
                    else -> drawText(l, color = outlineColor, topLeft = Offset(x, top))
                }
            }
            x += l.size.width + gap
            // accent diamond between one word and the next
            rotate(45f, pivot = Offset(x, cy)) {
                drawRect(accent, topLeft = Offset(x - diamond / 2f, cy - diamond / 2f), size = Size(diamond, diamond))
            }
            x += gap
            i++
        }
    }
}
