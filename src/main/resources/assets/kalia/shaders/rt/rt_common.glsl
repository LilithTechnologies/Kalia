// Shared helpers for Kalia's screen-space ray tracing chain.
// You can use it with `#include "kalia:rt/rt_common.glsl"`

#define KALIA_RT_PI 3.14159265359

// Kalia builds its projections with JOML in GL orientation with a [0, 1] depth
// range, so view space has -Z pointing away from the eye and the depth buffer
// already holds Vulkan-style device depth. Linear depth is therefore -viewZ.
float kaliaRtLinear(vec3 viewPosition) {
    return -viewPosition.z;
}

// The fullscreen pass writes uv with y running down from the top of the target,
// which matches how the colour and depth attachments are laid out in memory.
vec3 kaliaRtViewPosition(mat4 invProjection, vec2 uv, float deviceDepth) {
    vec4 ndc = vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, deviceDepth, 1.0);
    vec4 view = invProjection * ndc;
    return view.xyz / view.w;
}

// Returns the uv in xy and the clip-space w in z. A non-positive w means the
// point sits behind the eye and the uv is meaningless.
vec3 kaliaRtProject(mat4 projection, vec3 viewPosition) {
    vec4 clip = projection * vec4(viewPosition, 1.0);
    if (clip.w <= 1e-5) {
        return vec3(0.0, 0.0, -1.0);
    }
    vec3 ndc = clip.xyz / clip.w;
    return vec3(ndc.x * 0.5 + 0.5, 0.5 - ndc.y * 0.5, clip.w);
}

bool kaliaRtOnScreen(vec2 uv) {
    return all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)));
}

// The world pass clears depth to one, so anything still at one is sky.
bool kaliaRtIsSky(float deviceDepth) {
    return deviceDepth >= 0.999999;
}

float kaliaRtLuminance(vec3 colour) {
    return dot(colour, vec3(0.2126, 0.7152, 0.0722));
}

uint kaliaRtHash(uint value) {
    value ^= value >> 17;
    value *= 0xed5ad4bbu;
    value ^= value >> 11;
    value *= 0xac4c1b51u;
    value ^= value >> 15;
    value *= 0x31848babu;
    value ^= value >> 14;
    return value;
}

float kaliaRtRandom(inout uint state) {
    state = state * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28) + 4u)) ^ state) * 277803737u;
    word = (word >> 22) ^ word;
    return float(word) * (1.0 / 4294967296.0);
}

uint kaliaRtSeed(vec2 pixel, uint frame) {
    return kaliaRtHash(uint(pixel.x) + 1973u * uint(pixel.y) + 9277u * frame);
}

// Builds an orthonormal basis around a unit normal without a branch on the
// degenerate axis. Duff et al, "Building an Orthonormal Basis, Revisited".
void kaliaRtBasis(vec3 normal, out vec3 tangent, out vec3 bitangent) {
    float sign = normal.z >= 0.0 ? 1.0 : -1.0;
    float a = -1.0 / (sign + normal.z);
    float b = normal.x * normal.y * a;
    tangent = vec3(1.0 + sign * normal.x * normal.x * a, sign * b, -sign * normal.x);
    bitangent = vec3(b, sign + normal.y * normal.y * a, -normal.y);
}

// Cosine-weighted hemisphere sample. Because the pdf matches the cosine term,
// the Monte Carlo estimator collapses to a plain average of the radiance.
vec3 kaliaRtCosineHemisphere(vec3 normal, float u1, float u2) {
    vec3 tangent;
    vec3 bitangent;
    kaliaRtBasis(normal, tangent, bitangent);

    float radius = sqrt(u1);
    float phi = 2.0 * KALIA_RT_PI * u2;
    return normalize(
        tangent * (radius * cos(phi)) +
        bitangent * (radius * sin(phi)) +
        normal * sqrt(max(0.0, 1.0 - u1)));
}

// Schlick's approximation with a dielectric F0. Nothing in the pipeline carries
// material data, so every surface is treated as a plain dielectric and only the
// grazing-angle response is used.
float kaliaRtFresnel(vec3 view, vec3 normal, float f0) {
    float cosTheta = clamp(dot(-view, normal), 0.0, 1.0);
    float factor = pow(1.0 - cosTheta, 5.0);
    return f0 + (1.0 - f0) * factor;
}

// Relative depth comparison, which is what the half-float G-buffer can express
// uniformly across the whole view distance.
float kaliaRtDepthWeight(float centre, float sample_, float sigma) {
    float difference = abs(centre - sample_);
    return exp(-difference / max(sigma * max(abs(centre), 1.0), 1e-4));
}

float kaliaRtNormalWeight(vec3 centre, vec3 sample_, float power) {
    return pow(max(dot(centre, sample_), 0.0), power);
}
