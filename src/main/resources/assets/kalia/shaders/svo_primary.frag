// Traced terrain: primary visibility comes straight out of the octree.
//
// No chunk meshes, no draw calls, no vertex memory. One ray per pixel finds the surface, samples
// the block atlas where it crossed the face, shades it the way vanilla would, and multiplies in the
// traced lighting that the earlier passes prepared. Depth is written so entities, particles and the
// hand still rasterise on top with correct occlusion.
//
// The lighting is applied here rather than as a pass over the finished frame, because only this
// shader knows which pixels are traced terrain. Modulating the whole screen instead lit every
// entity with the lighting of whatever happened to be behind it.

layout(binding = 0) uniform sampler2D svoLight;
layout(binding = 1) uniform sampler2D svoGeometry;

layout(location = 0) in vec2 uv;

layout(location = 0) out vec4 fragColor;

#include "kalia:svo_common.glsl"
#include "kalia:svo_sampling.glsl"

/**
 * Reads the half-resolution lighting for a surface at [distance].
 *
 * Taps that found no surface, or found one at a very different distance, are dropped; without that
 * the lighting of a near wall smears out over the sky behind its silhouette.
 */
vec3 svoLightAt(vec2 coord, float distance, out float specular) {
    vec2 size = vec2(textureSize(svoLight, 0));
    vec2 base = coord * size - 0.5;
    vec2 corner = floor(base);
    vec2 fraction = base - corner;

    vec4 total = vec4(0.0);
    float weightSum = 0.0;
    for (int y = 0; y < 2; ++y) {
        for (int x = 0; x < 2; ++x) {
            vec2 tapUv = (corner + vec2(x, y) + 0.5) / size;
            float tapDistance = texture(svoGeometry, tapUv).w;
            if (tapDistance < 0.0) {
                continue;
            }
            float bilinear = (x == 0 ? 1.0 - fraction.x : fraction.x) *
                (y == 0 ? 1.0 - fraction.y : fraction.y);
            float agreement = exp(-abs(tapDistance - distance) / max(distance * 0.05, 0.25));
            float weight = bilinear * agreement + 1.0e-4;
            total += texture(svoLight, tapUv) * weight;
            weightSum += weight;
        }
    }

    if (weightSum <= 1.0e-3) {
        // Falling back to neutral here puts a hard edge wherever the traced surface and the drawn
        // one disagree, and that edge follows the camera around. Taking the light as-is keeps the
        // transition smooth; being slightly wrong is far less visible than a moving boundary.
        vec4 direct = texture(svoLight, coord);
        specular = direct.a;
        return direct.rgb;
    }
    total /= weightSum;
    specular = total.a;
    return total.rgb;
}

void main() {
    vec3 near = svoWorldFromDepth(uv, 0.0);
    vec3 far = svoWorldFromDepth(uv, 1.0);
    vec3 direction = normalize(far - near);

    SvoHit hit;
    if (!svoTrace(near, direction, 0.0, svoFogEnd, svoFootprint, hit)) {
        // Leave the sky the sky pass already drew.
        discard;
    }

    vec3 point = near + direction * hit.t;
    float distance = length(point);

    // Vanilla shading first: the block-and-sky lightmap and the per-face dimming.
    vec3 shaded = hit.coarse
        ? hit.albedo * svoSkyAmbient
        : hit.albedo * svoLightmapColor(hit.light) * hit.shade;

    // Red marks terrain the lighting pass never found a surface for. A red region that follows the
    // camera means the traced lighting is running out of range before the drawn terrain does.
    if (svoDebugView == SVO_DEBUG_COVERAGE) {
        float covered = texture(svoGeometry, uv).w >= 0.0 ? 1.0 : 0.0;
        fragColor = vec4(1.0 - covered, covered, 0.0, 1.0);
        gl_FragDepth = 0.0;
        return;
    }

    if (svoIntensity > 0.0 && !hit.coarse) {
        float specular = 0.0;
        vec3 light = svoLightAt(uv, distance, specular);
        if (svoDebugView == SVO_DEBUG_LIGHT) {
            shaded = light;
        } else {
            shaded = shaded * mix(vec3(1.0), light, svoIntensity) + svoSkyColor * (specular * svoIntensity);
        }
    }

    // Fog is applied here rather than later because the rasterised entities drawn over this already
    // carry Minecraft's own fog, and applying it twice to them would be worse than once to each.
    float fogStart = svoFogStart;
    float fogEnd = max(svoFogEnd, fogStart + 1.0);
    float fog = clamp((distance - fogStart) / (fogEnd - fogStart), 0.0, 1.0);

    vec4 clip = svoViewProjection * vec4(point, 1.0);
    gl_FragDepth = clamp(clip.z / max(clip.w, 1.0e-6), 0.0, 1.0);
    fragColor = vec4(mix(shaded, svoFogColor.rgb, fog), 1.0);
}
