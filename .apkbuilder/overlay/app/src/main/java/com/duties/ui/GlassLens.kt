package com.duties.ui

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.shapes.RoundedRectangularShape

/**
 * The liquid glass lens of the app = the library's `lens(...)` + the two fixes the reference app has:
 *
 *  1. the color samples are clamped inside the glass image (without it the edge samples fall outside and
 *     show dark / black arcs at the top and bottom of the glass), and
 *  2. the direction of the bend can never be a zero vector (that made a visible split through the glass).
 *
 * The rainbow (chromatic aberration) is a NUMBER, not on/off:
 *
 *  - [chromaticAberration] = 0   -> no rainbow (cheaper shader, one sample).
 *  - [chromaticAberration] = 1   -> exactly the rainbow of the library with `chromaticAberration = true`
 *                                   (7 color samples), which is what the reference app uses.
 *  - bigger than 1               -> a wider rainbow.
 */
fun BackdropEffectScope.glassLens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).coerceAtLeast(0f)
    }

    val radii = glassCornerRadii() ?: throw UnsupportedOperationException(
        "Only RoundedRectangularShape or CornerBasedShape is supported in lens effects."
    )
    val w = size.width
    val h = size.height
    val pad = padding
    val rainbow = chromaticAberration > 0.001f

    runtimeShaderEffect(
        if (rainbow) "DutiesRefractionDispersion" else "DutiesRefraction",
        if (rainbow) DispersionShader else RefractionShader,
        "content"
    ) {
        setFloatUniform("size", w, h)
        setFloatUniform("contentSize", w + 2f * pad, h + 2f * pad)
        setFloatUniform("offset", -pad, -pad)
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (rainbow) setFloatUniform("chromaticAberration", chromaticAberration)
    }
}

private fun BackdropEffectScope.glassCornerRadii(): FloatArray? = when (val s = shape) {
    is RoundedRectangularShape -> {
        val c = s.corners(size, layoutDirection, this)
        floatArrayOf(c.topLeft, c.topRight, c.bottomRight, c.bottomLeft)
    }

    is AbsoluteRoundedCornerShape -> {
        val max = size.minDimension / 2f
        floatArrayOf(
            s.topStart.toPx(size, this).coerceAtMost(max),
            s.topEnd.toPx(size, this).coerceAtMost(max),
            s.bottomEnd.toPx(size, this).coerceAtMost(max),
            s.bottomStart.toPx(size, this).coerceAtMost(max)
        )
    }

    is CornerBasedShape -> {
        val max = size.minDimension / 2f
        val ltr = layoutDirection == LayoutDirection.Ltr
        floatArrayOf(
            (if (ltr) s.topStart else s.topEnd).toPx(size, this).coerceAtMost(max),
            (if (ltr) s.topEnd else s.topStart).toPx(size, this).coerceAtMost(max),
            (if (ltr) s.bottomEnd else s.bottomStart).toPx(size, this).coerceAtMost(max),
            (if (ltr) s.bottomStart else s.bottomEnd).toPx(size, this).coerceAtMost(max)
        )
    }

    else -> null
}

private const val ShaderHead = """
uniform shader content;

uniform float2 size;
uniform float2 contentSize;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        float2 outside = max(cornerCoord, 0.0);
        float outsideLength = length(outside);
        return outsideLength > 0.0001
            ? sign(coord) * (outside / outsideLength)
            : float2(0.0);
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

// the bend direction must never be a zero vector
float2 safeNormalize(float2 value) {
    float lengthSquared = dot(value, value);
    return lengthSquared > 0.000001
        ? value * inversesqrt(lengthSquared)
        : float2(1.0, 0.0);
}

// samples never leave the glass image
float2 clampSampleCoord(float2 coord) {
    return clamp(coord, float2(0.5), max(contentSize - float2(0.5), float2(0.5)));
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
"""

private const val RefractionShader = ShaderHead + """
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = safeNormalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
            + depthEffect * safeNormalize(centeredCoord)
    );

    return content.eval(clampSampleCoord(coord + d * grad));
}
"""

private const val DispersionShader = ShaderHead + """
uniform float chromaticAberration;

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = safeNormalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
            + depthEffect * safeNormalize(centeredCoord)
    );

    float2 refractedCoord = clampSampleCoord(coord + d * grad);
    float dispersionIntensity = chromaticAberration
        * ((centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 0.0001));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(clampSampleCoord(refractedCoord + dispersedCoord));
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(clampSampleCoord(refractedCoord + dispersedCoord * (2.0 / 3.0)));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(clampSampleCoord(refractedCoord + dispersedCoord * (1.0 / 3.0)));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(clampSampleCoord(refractedCoord));
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(clampSampleCoord(refractedCoord - dispersedCoord * (1.0 / 3.0)));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(clampSampleCoord(refractedCoord - dispersedCoord * (2.0 / 3.0)));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(clampSampleCoord(refractedCoord - dispersedCoord));
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
