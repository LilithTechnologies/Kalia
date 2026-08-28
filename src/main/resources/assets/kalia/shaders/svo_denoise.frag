// Edge-avoiding a-trous wavelet filter, run a few times with a widening step.
//
// Each pass reaches twice as far as the last for the same nine taps, so three passes cover a 17x17
// neighbourhood at the cost of 27 samples. Weights fall off with normal and depth disagreement,
// which is what keeps the blur from crossing a wall corner or a silhouette.

layout(binding = 0) uniform sampler2D kaliaLight;
layout(binding = 1) uniform sampler2D kaliaGeometry;

layout(location = 0) in vec2 uv;

// One target only. The geometry buffer is read but never rewritten, so declaring an output for it
// would leave the pipeline with more fragment outputs than the pass has attachments.
layout(location = 0) out vec4 outLight;

#include "kalia:svo_scene.glsl"

const float KERNEL[3] = float[3](0.375, 0.25, 0.0625);

/** How sharply the filter refuses to average across a brightness step. */
const float LUMA_SHARPNESS = 1.5;

void main() {
    vec4 centre = texture(kaliaLight, uv);
    vec4 geometry = texture(kaliaGeometry, uv);

    if (geometry.w < 0.0) {
        outLight = centre;
        return;
    }

    vec2 texel = svoFilterStep / svoTargetSize;
    float centreLuma = dot(centre.rgb, vec3(0.2126, 0.7152, 0.0722));

    vec3 sum = vec3(0.0);
    float alphaSum = 0.0;
    float weightSum = 0.0;

    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 tapUv = uv + vec2(x, y) * texel;
            vec4 tapGeometry = texture(kaliaGeometry, tapUv);
            if (tapGeometry.w < 0.0) {
                continue;
            }

            float kernel = KERNEL[abs(x)] * KERNEL[abs(y)];
            float normalWeight = pow(max(dot(tapGeometry.xyz, geometry.xyz), 0.0), 32.0);
            float depthWeight = exp(-abs(tapGeometry.w - geometry.w) / max(geometry.w * 0.02, 0.05));

            vec4 tap = texture(kaliaLight, tapUv);
            // Without this the filter happily averages across a shadow edge, and on flat ground -
            // where the normal and depth guides agree everywhere - that turns every shadow and
            // every contact darkening into a uniform grey wash.
            float tapLuma = dot(tap.rgb, vec3(0.2126, 0.7152, 0.0722));
            float lumaWeight = exp(-abs(tapLuma - centreLuma) * LUMA_SHARPNESS);

            float weight = kernel * normalWeight * depthWeight * lumaWeight;
            sum += tap.rgb * weight;
            alphaSum += tap.a * weight;
            weightSum += weight;
        }
    }

    outLight = weightSum > 1.0e-4
        ? vec4(sum / weightSum, alphaSum / weightSum)
        : centre;
}
