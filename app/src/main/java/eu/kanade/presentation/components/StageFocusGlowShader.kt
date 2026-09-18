package eu.kanade.presentation.components

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Isolates [RuntimeShader] (AGSL) usage for the home hero «Cinematic focus» glow.
 *
 * [RuntimeShader] exists only on API 33+. Keeping every reference inside this
 * [@RequiresApi] class prevents ART from resolving a missing class when the home hub
 * composes on older devices (NoClassDefFoundError).
 *
 * Свечение рисуется отдельным слоем ПОД карточками карусели: RenderEffect на поверхности
 * с пятью `AsyncImage` и ken-burns означал бы offscreen-перерисовку всей сцены каждый кадр.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class StageFocusGlowShader {

    private val shader: RuntimeShader? = try {
        RuntimeShader(STAGE_FOCUS_GLOW_AGSL)
    } catch (t: Throwable) {
        // Компиляция шейдера может упасть и на API 33+: без этого catch приложение крашится.
        Log.e("StageFocusGlow", "Failed to compile STAGE_FOCUS_GLOW_AGSL", t)
        null
    }

    val isAvailable: Boolean get() = shader != null

    fun DrawScope.drawStageGlow(accent: Color, intensity: Float) {
        val active = shader ?: return
        synchronized(active) {
            active.setFloatUniform("resolution", size.width, size.height)
            active.setFloatUniform("accent", accent.red, accent.green, accent.blue)
            active.setFloatUniform("intensity", intensity)
            drawRect(brush = ShaderBrush(active))
        }
    }
}

// Keep in sync with assets/shaders/stage_focus_glow.agsl (design-time copy)
private const val STAGE_FOCUS_GLOW_AGSL = """
uniform float2 resolution;
uniform float3 accent;
uniform float intensity;

half4 main(float2 fragCoord) {
    // Свечение прижато к нижней кромке слоя — там, где стоит фокус-постер.
    float2 center = float2(resolution.x * 0.5, resolution.y * 1.08);
    float2 d = (fragCoord - center) / max(resolution.y, 1.0);
    float falloff = exp(-dot(d, d) * 9.0);
    float alpha = clamp(falloff * intensity, 0.0, 1.0);
    return half4(half3(accent) * alpha, alpha);
}
"""
