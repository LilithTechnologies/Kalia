// Spatial half of the denoiser: one edge-stopping a-trous wavelet iteration.
//
// Each iteration doubles the spacing between taps, so a handful of 5x5 passes
// reach the footprint of a very wide blur at a fraction of the cost. Weights are
// driven by the estimated variance, which lets converged pixels keep their
// detail while noisy ones are smoothed hard.
//
// Target 0: rgb = filtered irradiance, a = filtered occlusion
// Target 1: a = filtered variance, carried into the next iteration

layout(binding = 0) uniform sampler2D kaliaColour;
layout(binding = 1) uniform sampler2D kaliaVariance;
layout(binding = 2) uniform sampler2D kaliaSurface;

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 outColour;
layout(location = 1) out vec4 outVariance;

layout(push_constant) uniform KaliaRtAtrous {
    vec4 kaliaParams[2];
};

#define KALIA_TEXEL       kaliaParams[0].xy
#define KALIA_STEP        kaliaParams[0].z
#define KALIA_SIGMA_DEPTH kaliaParams[0].w
#define KALIA_SIGMA_NORMAL kaliaParams[1].x
#define KALIA_SIGMA_LUMA  kaliaParams[1].y

#include "kalia:rt/rt_common.glsl"

// The 5x5 separable B3 spline the wavelet is built from.
const float KERNEL[3] = float[](3.0 / 8.0, 1.0 / 4.0, 1.0 / 16.0);

// How many accumulated frames a pixel needs before it is treated as converged.
const float KALIA_YOUNG_FRAMES = 4.0;

void main() {
    vec4 surface = texture(kaliaSurface, uv);
    vec4 centre = texture(kaliaColour, uv);

    if (surface.w < 0.0) {
        // Sky, or a pixel the tracer declined. Nothing to filter against.
        outColour = centre;
        outVariance = texture(kaliaVariance, uv);
        return;
    }

    vec3 normal = normalize(surface.xyz);
    float depth = surface.w;
    vec4 statistics = texture(kaliaVariance, uv);
    float variance = statistics.a;

    // A pixel the camera only just revealed has no history to lean on, so all it
    // has is one frame of a handful of rays. Widening the filter over exactly
    // those pixels is what stops a turn from boiling, and it costs nothing
    // anywhere else because converged pixels leave the weights untouched.
    float historyLength = max(statistics.b, 1.0);
    float youth = clamp(KALIA_YOUNG_FRAMES / historyLength, 1.0, KALIA_YOUNG_FRAMES);

    // Prefiltering the variance over a small window stops a single bright
    // sample from convincing the luminance weight that an edge exists.
    float smoothedVariance = 0.0;
    float smoothedWeight = 0.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            float weight = KERNEL[abs(x)] * KERNEL[abs(y)];
            smoothedVariance += texture(kaliaVariance, uv + vec2(x, y) * KALIA_TEXEL).a * weight;
            smoothedWeight += weight;
        }
    }
    smoothedVariance /= max(smoothedWeight, 1e-6);

    float centreLuma = kaliaRtLuminance(centre.rgb);
    float lumaScale = (KALIA_SIGMA_LUMA * sqrt(max(smoothedVariance, 0.0)) + 1e-4) * youth;
    // Geometry still gates the filter, just less tightly while the pixel is young.
    float depthSigma = KALIA_SIGMA_DEPTH * youth;
    float normalPower = KALIA_SIGMA_NORMAL / youth;

    vec4 sum = centre * KERNEL[0] * KERNEL[0];
    float weightSum = KERNEL[0] * KERNEL[0];
    float varianceSum = variance * (KERNEL[0] * KERNEL[0]) * (KERNEL[0] * KERNEL[0]);

    for (int y = -2; y <= 2; ++y) {
        for (int x = -2; x <= 2; ++x) {
            if (x == 0 && y == 0) {
                continue;
            }

            vec2 offset = vec2(x, y) * KALIA_STEP * KALIA_TEXEL;
            vec2 tapUv = uv + offset;
            if (!kaliaRtOnScreen(tapUv)) {
                continue;
            }

            vec4 tapSurface = texture(kaliaSurface, tapUv);
            if (tapSurface.w < 0.0) {
                continue;
            }

            vec4 tap = texture(kaliaColour, tapUv);
            float tapVariance = texture(kaliaVariance, tapUv).a;

            float kernel = KERNEL[abs(x)] * KERNEL[abs(y)];
            float weight = kernel *
                kaliaRtDepthWeight(depth, tapSurface.w, depthSigma) *
                kaliaRtNormalWeight(normal, normalize(tapSurface.xyz), normalPower) *
                exp(-abs(centreLuma - kaliaRtLuminance(tap.rgb)) / lumaScale);

            sum += tap * weight;
            weightSum += weight;
            // Variance is the second moment of a weighted mean, so it falls off
            // with the square of the weights rather than linearly.
            varianceSum += tapVariance * weight * weight;
        }
    }

    float inverse = 1.0 / max(weightSum, 1e-6);
    outColour = sum * inverse;
    // History length rides along so the next iteration keeps widening over the
    // same young pixels.
    outVariance = vec4(0.0, 0.0, statistics.b, varianceSum * inverse * inverse);
}
