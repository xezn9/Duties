package com.duties.ui

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.shapes.RoundedRectangularShape

/**
 * Same as the library's `lens(...)`, but the rainbow (chromatic aberration) at the edge of the glass is a
 * NUMBER and not just on/off:
 *
 *  - [chromaticAberration] = 0   -> exactly the library lens (no rainbow, cheapest).
 *  - [chromaticAberration] = 1   -> the rainbow the library draws when `chromaticAberration = true`.
 *  - bigger than 1               -> a wider rainbow.
 *
 * The shader below is the library's dispersion shader (backdrop 2.0.1, Apache 2.0); the only difference is
 * that the strength uniform is given by us instead of the fixed 1.0.
 */
fun BackdropEffectScope.glassLens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f
) {
    if (chromaticAberration <= 0.001f) {
        lens(refractionHeight, refractionAmount, depthEffect, false)
        return
    }
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

    runtimeShaderEffect("DutiesRefractionDispersion", DispersionShader, "content") {
        setFloatUniform("size", w, h)
        setFloatUniform("offset", -pad, -pad)
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        setFloatUniform("chromaticAberration", chromaticAberration)
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

private const val DispersionShader = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

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
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

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
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
