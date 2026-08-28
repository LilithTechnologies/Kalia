// Temporal half of the denoiser. Reprojects last frame's accumulation into the
// current view, rejects anything the reprojection cannot justify, and folds the
// new trace in with an exponential moving average whose rate is driven by how
// long the pixel has actually been converging.
//
// Target 0: rgb = accumulated irradiance, a = accumulated occlusion
// Target 1: r = first luma moment, g = second luma moment, b = history length,
//           a = variance (consumed by the spatial pass)
// Target 2: rgb = accumulated reflection, a = accumulated confidence

layout(binding = 0) uniform sampler2D kaliaIndirect;
layout(binding = 1) uniform sampler2D kaliaReflection;
layout(binding = 2) uniform sampler2D kaliaSurface;
layout(binding = 3) uniform sampler2D kaliaHistoryIndirect;
layout(binding = 4) uniform sampler2D kaliaHistoryMoments;
layout(binding = 5) uniform sampler2D kaliaHistorySurface;
layout(binding = 6) uniform sampler2D kaliaHistoryReflection;
layout(binding = 7) uniform sampler2D kaliaDepth;

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 outIndirect;
layout(location = 1) out vec4 outMoments;
layout(location = 2) out vec4 outReflection;

layout(push_constant) uniform KaliaRtTemporal {
    mat4 kaliaReprojection;
    vec4 kaliaParams[3];
};

#define KALIA_TEXEL          kaliaParams[0].xy
#define KALIA_HAS_HISTORY    (kaliaParams[0].z > 0.5)
#define KALIA_MAX_FRAMES     kaliaParams[1].x
#define KALIA_MIN_ALPHA      kaliaParams[1].y
#define KALIA_CLAMP_GAMMA    kaliaParams[1].z
#define KALIA_DEPTH_TOLERANCE kaliaParams[2].x
#define KALIA_NORMAL_TOLERANCE kaliaParams[2].y
#define KALIA_REFLECT_FRAMES kaliaParams[2].z

#include "kalia:rt/rt_common.glsl"

// A 2x2 tap is accepted per-corner rather than as a block, so a pixel next to a
// disocclusion keeps whichever corners are still valid instead of dropping all
// of its history.
bool tapValid(vec2 tapUv, vec3 normal, float expectedLinear) {
    if (!kaliaRtOnScreen(tapUv)) {
        return false;
    }
    vec4 previous = texture(kaliaHistorySurface, tapUv);
    if (previous.w < 0.0) {
        return false;
    }
    float relative = abs(previous.w - expectedLinear) / max(expectedLinear, 1.0);
    if (relative > KALIA_DEPTH_TOLERANCE) {
        return false;
    }
    return dot(normalize(previous.xyz), normal) > KALIA_NORMAL_TOLERANCE;
}

void main() {
    vec4 raw = texture(kaliaIndirect, uv);
    vec4 rawReflection = texture(kaliaReflection, uv);
    vec4 surface = texture(kaliaSurface, uv);

    if (surface.w < 0.0) {
        outIndirect = raw;
        outMoments = vec4(0.0, 0.0, 1.0, 0.0);
        outReflection = rawReflection;
        return;
    }

    vec3 normal = normalize(surface.xyz);

    // The reprojection matrix already folds the camera translation in, so a
    // static world only needs the current clip position pushed through it. Its
    // w falls out as the linear depth this surface had last frame, which is a
    // far tighter disocclusion test than comparing raw depths.
    vec4 clip = vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, texture(kaliaDepth, uv).r, 1.0);
    vec4 previousClip = kaliaReprojection * clip;

    float historyLength = 1.0;
    vec3 history = raw.rgb;
    float historyAo = raw.a;
    vec4 historyReflection = rawReflection;
    vec2 historyMoments = vec2(0.0);
    bool reprojected = false;

    if (KALIA_HAS_HISTORY && previousClip.w > 1e-5) {
        vec3 previousNdc = previousClip.xyz / previousClip.w;
        vec2 previousUv = vec2(previousNdc.x * 0.5 + 0.5, 0.5 - previousNdc.y * 0.5);
        float expectedLinear = previousClip.w;

        vec2 texel = KALIA_TEXEL;
        vec2 pixel = previousUv / texel - 0.5;
        vec2 base = floor(pixel);
        vec2 fraction = pixel - base;

        float weights[4];
        weights[0] = (1.0 - fraction.x) * (1.0 - fraction.y);
        weights[1] = fraction.x * (1.0 - fraction.y);
        weights[2] = (1.0 - fraction.x) * fraction.y;
        weights[3] = fraction.x * fraction.y;

        vec2 offsets[4];
        offsets[0] = vec2(0.0, 0.0);
        offsets[1] = vec2(1.0, 0.0);
        offsets[2] = vec2(0.0, 1.0);
        offsets[3] = vec2(1.0, 1.0);

        vec3 colour = vec3(0.0);
        float ao = 0.0;
        vec4 reflectionSum = vec4(0.0);
        vec2 moments = vec2(0.0);
        float lengthSum = 0.0;
        float total = 0.0;

        for (int index = 0; index < 4; ++index) {
            vec2 tapUv = (base + offsets[index] + 0.5) * texel;
            if (!tapValid(tapUv, normal, expectedLinear)) {
                continue;
            }
            float weight = weights[index];
            if (weight <= 0.0) {
                continue;
            }
            vec4 tap = texture(kaliaHistoryIndirect, tapUv);
            vec4 tapMoments = texture(kaliaHistoryMoments, tapUv);
            colour += tap.rgb * weight;
            ao += tap.a * weight;
            reflectionSum += texture(kaliaHistoryReflection, tapUv) * weight;
            moments += tapMoments.rg * weight;
            lengthSum += tapMoments.b * weight;
            total += weight;
        }

        if (total > 1e-4) {
            float inverse = 1.0 / total;
            history = colour * inverse;
            historyAo = ao * inverse;
            historyReflection = reflectionSum * inverse;
            historyMoments = moments * inverse;
            historyLength = min(lengthSum * inverse + 1.0, KALIA_MAX_FRAMES);
            reprojected = true;
        }
    }

    // Neighbourhood statistics of the fresh trace. They serve twice: as the
    // clamp that keeps stale history from ghosting through a lighting change,
    // and as the variance estimate while the pixel is too young for the
    // temporal moments to mean anything.
    vec3 mean = vec3(0.0);
    vec3 meanSquared = vec3(0.0);
    float lumaMean = 0.0;
    float lumaSquared = 0.0;
    float samples = 0.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 tapUv = uv + vec2(x, y) * KALIA_TEXEL;
            vec3 tap = texture(kaliaIndirect, tapUv).rgb;
            mean += tap;
            meanSquared += tap * tap;
            float luma = kaliaRtLuminance(tap);
            lumaMean += luma;
            lumaSquared += luma * luma;
            samples += 1.0;
        }
    }
    float inverseSamples = 1.0 / samples;
    mean *= inverseSamples;
    meanSquared *= inverseSamples;
    lumaMean *= inverseSamples;
    lumaSquared *= inverseSamples;
    vec3 deviation = sqrt(max(meanSquared - mean * mean, vec3(0.0)));

    if (reprojected) {
        vec3 low = mean - deviation * KALIA_CLAMP_GAMMA;
        vec3 high = mean + deviation * KALIA_CLAMP_GAMMA;
        history = clamp(history, low, high);
    }

    float alpha = max(1.0 / historyLength, KALIA_MIN_ALPHA);
    vec3 accumulated = mix(history, raw.rgb, alpha);
    float accumulatedAo = mix(historyAo, raw.a, alpha);

    float luma = kaliaRtLuminance(raw.rgb);
    vec2 moments = mix(historyMoments, vec2(luma, luma * luma), alpha);

    float temporalVariance = max(moments.y - moments.x * moments.x, 0.0);
    float spatialVariance = max(lumaSquared - lumaMean * lumaMean, 0.0);
    // Below a handful of frames the temporal moments are still mostly noise, so
    // the spatial estimate carries the weight and hands over as history builds.
    float blend = clamp((historyLength - 1.0) * 0.25, 0.0, 1.0);
    float variance = mix(spatialVariance, temporalVariance, blend);

    // Reflections converge much faster than the diffuse gather but ghost far
    // more visibly, so they run on a shorter, separately tunable history.
    float reflectionAlpha = max(1.0 / min(historyLength, KALIA_REFLECT_FRAMES), KALIA_MIN_ALPHA);
    vec4 accumulatedReflection = mix(historyReflection, rawReflection, reflectionAlpha);

    outIndirect = vec4(accumulated, accumulatedAo);
    outMoments = vec4(moments, historyLength, variance);
    outReflection = accumulatedReflection;
}
