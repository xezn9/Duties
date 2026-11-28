package com.duties.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.cos
import kotlin.math.sin

/*
 * StickyLens.kt — the "sticky" (metaball) liquid glass of the StickyGlass demo, moved into Duties.
 *
 * It is the library's `lens` effect, but for the SMOOTH UNION of up to 3 rounded rects: where two shapes are close
 * they melt into each other with a viscous neck, the neck stretches while they move apart and finally snaps.
 * The merged shape is refracted (with radial chromatic dispersion), tinted and rim-lit in ONE pass.
 *
 * It only uses the public API of the backdrop library (runtimeShaderEffect), so the dependency of the app
 * (io.github.kyant0:backdrop) stays exactly as it was.
 */

/**
 * A rounded rectangle that takes part in a sticky merge.
 * All values are in pixels, relative to the top-left corner of the element that draws the backdrop.
 */
@Immutable
class StickyRoundRect(
    val centerX: Float,
    val centerY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val cornerRadius: Float = minOf(halfWidth, halfHeight)
)

private const val StickyMaxShapes = 3
private const val StickyFar = -100000f

/** A shape that is nowhere: with only this one the effect draws NOTHING (fully transparent). */
internal val StickyNowhere = StickyRoundRect(StickyFar, StickyFar, 1f, 1f, 1f)

/**
 * Sticky version of the lens. Draws ONE glass surface for the smooth union of up to 3 [StickyRoundRect]s.
 * Everything outside the shapes is transparent, so the element that draws the backdrop can be bigger than them
 * (it must contain every shape).
 *
 * @param smoothness blend distance in px. Bigger = stickier (the neck forms from a larger gap).
 * @param refractionHeight how deep (px) from the edge the refraction reaches.
 * @param refractionAmount how strong (px) the refraction bends at the very edge.
 * @param chromaticAberration radial dispersion strength (0 = off).
 * @param tint color laid over the glass (alpha = how strong).
 */
fun BackdropEffectScope.stickyLens(
    shapes: List<StickyRoundRect>,
    smoothness: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    chromaticAberration: Float = 0f,
    tint: Color = Color.Transparent,
    rimWidth: Float = 2f,
    rimAlpha: Float = 0.9f,
    lightAngle: Float = 45f
) {
    if (!isRuntimeShaderSupported()) return

    // the glass must still be drawn (without any bend) when the lens setting of the app is 0
    val height = refractionHeight.coerceAtLeast(0.001f)
    val amount = if (refractionHeight <= 0.001f) 0f else refractionAmount

    if (padding > 0f) {
        padding = (padding - height).coerceAtLeast(0f)
    }
    val pad = padding
    val list = if (shapes.isEmpty()) listOf(StickyNowhere) else shapes
    val count = minOf(list.size, StickyMaxShapes)
    val angle = lightAngle * (3.1415927f / 180f)
    val argb = tint.toArgb()
    val tintA = ((argb shr 24) and 0xFF) / 255f
    val tintR = ((argb shr 16) and 0xFF) / 255f
    val tintG = ((argb shr 8) and 0xFF) / 255f
    val tintB = (argb and 0xFF) / 255f

    runtimeShaderEffect("DutiesStickyLens", StickyLensShader, "content") {
        setFloatUniform("offset", -pad, -pad)
        setFloatUniform("shapeCount", count.toFloat())
        setFloatUniform("smoothness", smoothness.coerceAtLeast(0.001f))
        setFloatUniform("refractionHeight", height)
        setFloatUniform("refractionAmount", amount)
        setFloatUniform("chromaticAberration", chromaticAberration)
        setFloatUniform("lightDir", cos(angle), sin(angle))
        setFloatUniform("tint", floatArrayOf(tintR, tintG, tintB, tintA))
        setFloatUniform("rimWidth", rimWidth.coerceAtLeast(0.001f))
        setFloatUniform("rimAlpha", rimAlpha)

        val r = FloatArray(3) { 1f }
        for (i in 0 until StickyMaxShapes) {
            val s = if (i < count) list[i] else null
            val name = "shape$i"
            if (s != null) {
                setFloatUniform(name, floatArrayOf(s.centerX, s.centerY, s.halfWidth, s.halfHeight))
                r[i] = s.cornerRadius
            } else {
                setFloatUniform(name, floatArrayOf(StickyFar, StickyFar, 1f, 1f))
            }
        }
        setFloatUniform("radii", r)
    }
}

private const val StickyLensShader = """
uniform shader content;

uniform float2 offset;
uniform float4 shape0;
uniform float4 shape1;
uniform float4 shape2;
uniform float3 radii;
uniform float shapeCount;
uniform float smoothness;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float chromaticAberration;
uniform float2 lightDir;
uniform float4 tint;
uniform float rimWidth;
uniform float rimAlpha;

float sdRoundedBox(float2 p, float4 box, float radius) {
    float2 q = abs(p - box.xy) - (box.zw - float2(radius));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float smoothUnion(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float sceneField(float2 p) {
    float d = sdRoundedBox(p, shape0, radii.x);
    if (shapeCount > 1.5) {
        d = smoothUnion(d, sdRoundedBox(p, shape1, radii.y), smoothness);
    }
    if (shapeCount > 2.5) {
        d = smoothUnion(d, sdRoundedBox(p, shape2, radii.z), smoothness);
    }
    return d;
}

half4 main(float2 coord) {
    float2 p = coord + offset;
    float sd = sceneField(p);
    if (sd > 1.0) {
        return half4(0.0);
    }
    float alpha = clamp(0.5 - sd, 0.0, 1.0);

    float2 gradient = float2(
        sceneField(p + float2(1.0, 0.0)) - sceneField(p - float2(1.0, 0.0)),
        sceneField(p + float2(0.0, 1.0)) - sceneField(p - float2(0.0, 1.0))
    );
    float2 normal = gradient / max(length(gradient), 0.0001);

    float depth = max(-sd, 0.0);
    half4 color;
    if (depth >= refractionHeight) {
        color = content.eval(coord);
    } else {
        float x = 1.0 - depth / refractionHeight;
        float bend = (1.0 - sqrt(max(1.0 - x * x, 0.0))) * refractionAmount;
        float2 refractedCoord = coord - bend * normal;
        float2 dispersedCoord = -bend * normal * chromaticAberration;

        half4 c = half4(0.0);

        half4 red = content.eval(refractedCoord + dispersedCoord);
        c.r += red.r / 3.5;
        c.a += red.a / 7.0;

        half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
        c.r += orange.r / 3.5;
        c.g += orange.g / 7.0;
        c.a += orange.a / 7.0;

        half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
        c.r += yellow.r / 3.5;
        c.g += yellow.g / 3.5;
        c.a += yellow.a / 7.0;

        half4 green = content.eval(refractedCoord);
        c.g += green.g / 3.5;
        c.a += green.a / 7.0;

        half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
        c.g += cyan.g / 3.5;
        c.b += cyan.b / 3.0;
        c.a += cyan.a / 7.0;

        half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
        c.b += blue.b / 3.0;
        c.a += blue.a / 7.0;

        half4 purple = content.eval(refractedCoord - dispersedCoord);
        c.r += purple.r / 7.0;
        c.b += purple.b / 3.0;
        c.a += purple.a / 7.0;

        color = c;
    }

    color.rgb = mix(color.rgb, half3(tint.rgb), half(tint.a));

    float edge = 1.0 - smoothstep(0.0, rimWidth, depth);
    float facing = abs(dot(normal, lightDir));
    float rim = edge * (0.25 + 0.75 * pow(facing, 1.5)) * rimAlpha;
    color.rgb = clamp(color.rgb + half3(rim), half3(0.0), half3(1.0));

    return color * half(alpha);
}"""
