// Temporal accumulation for the traced lighting.
//
// Two rays a pixel is nowhere near enough on its own; reprojecting the previous frame and blending
// is what turns that into a stable image. Samples are rejected when the reprojected geometry no
// longer matches, so the filter tightens up around silhouettes and disocclusions instead of
// smearing them.

layout(binding = 0) uniform sampler2D kaliaLight;
layout(binding = 1) uniform sampler2D kaliaGeometry;
layout(binding = 2) uniform sampler2D kaliaHistoryLight;
layout(binding = 3) uniform sampler2D kaliaHistoryGeometry;

layout(location = 0) in vec2 uv;

layout(location = 0) out vec4 outLight;
layout(location = 1) out vec4 outGeometry;

#include "kalia:svo_scene.glsl"
#include "kalia:svo_sampling.glsl"

void main() {
    vec4 current = texture(kaliaLight, uv);
    vec4 geometry = texture(kaliaGeometry, uv);

    outGeometry = geometry;

    // Sky pixels carry a negative distance and need no history at all.
    if (geometry.w < 0.0 || !svoFeature(SVO_FEATURE_HISTORY)) {
        outLight = current;
        return;
    }

    // Reproject through the pixel's actual position. Using a fixed depth here instead reprojects
    // everything as though it sat on one plane, and the geometry test below then throws almost
    // every sample away, which leaves the accumulation doing nothing at all.
    vec3 far = svoWorldFromDepth(uv, 1.0);
    vec3 position = normalize(far) * geometry.w;
    vec4 reprojected = svoReprojection * (svoViewProjection * vec4(position, 1.0));
    if (reprojected.w <= 0.0) {
        outLight = current;
        return;
    }
    vec2 previousUv = reprojected.xy / reprojected.w;
    previousUv = vec2(previousUv.x * 0.5 + 0.5, 0.5 - previousUv.y * 0.5);

    if (any(lessThan(previousUv, vec2(0.0))) || any(greaterThan(previousUv, vec2(1.0)))) {
        outLight = current;
        return;
    }

    vec4 historyGeometry = texture(kaliaHistoryGeometry, previousUv);
    if (historyGeometry.w < 0.0) {
        outLight = current;
        return;
    }

    // Reject when the surface under the reprojected sample is a different one: either it has
    // turned away, or it sits at a noticeably different distance.
    float facing = dot(historyGeometry.xyz, geometry.xyz);
    float depthRatio = abs(historyGeometry.w - geometry.w) / max(geometry.w, 1.0);
    if (facing < 0.9 || depthRatio > 0.06) {
        outLight = current;
        return;
    }

    vec4 history = texture(kaliaHistoryLight, previousUv);

    // Clamp the history into the neighbourhood of what was just traced, so a sample that survived
    // rejection but is still stale converges instead of ghosting.
    vec2 texel = 1.0 / svoTargetSize;
    vec3 low = current.rgb;
    vec3 high = current.rgb;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec3 tap = texture(kaliaLight, uv + vec2(x, y) * texel).rgb;
            low = min(low, tap);
            high = max(high, tap);
        }
    }
    // A generous margin: with only a couple of rays a pixel the neighbourhood is itself noisy, and
    // clamping tightly to it would cap the very convergence the accumulation is there to provide.
    vec3 margin = vec3(0.25);
    vec3 clamped = clamp(history.rgb, low - margin, high + margin);

    float alpha = clamp(svoTemporalAlpha, 0.0, 1.0);
    outLight = vec4(mix(clamped, current.rgb, alpha), mix(history.a, current.a, alpha));
}
