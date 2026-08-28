// Sampling helpers shared by the tracing passes.

#ifndef KALIA_SVO_SAMPLING
#define KALIA_SVO_SAMPLING

#include "kalia:svo_scene.glsl"

// PCG hash. Cheap, and decorrelated enough across neighbouring pixels that the temporal filter has
// something to average rather than a fixed pattern to bake in.
uint svoHash(uint value) {
    uint state = value * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

float svoRandom(inout uint state) {
    state = svoHash(state);
    return float(state) * (1.0 / 4294967296.0);
}

vec2 svoRandom2(inout uint state) {
    return vec2(svoRandom(state), svoRandom(state));
}

/** Builds an orthonormal basis around a unit normal. Duff et al., branchless. */
void svoBasis(vec3 normal, out vec3 tangent, out vec3 bitangent) {
    float s = normal.z >= 0.0 ? 1.0 : -1.0;
    float a = -1.0 / (s + normal.z);
    float b = normal.x * normal.y * a;
    tangent = vec3(1.0 + s * normal.x * normal.x * a, s * b, -s * normal.x);
    bitangent = vec3(b, s + normal.y * normal.y * a, -normal.y);
}

/** Cosine-weighted direction in the hemisphere around `normal`. */
vec3 svoCosineHemisphere(vec3 normal, vec2 uv) {
    float radius = sqrt(uv.x);
    float angle = 6.28318530718 * uv.y;
    vec3 tangent;
    vec3 bitangent;
    svoBasis(normal, tangent, bitangent);
    return normalize(
        tangent * (radius * cos(angle)) +
        bitangent * (radius * sin(angle)) +
        normal * sqrt(max(0.0, 1.0 - uv.x)));
}

/** Perturbs a direction within a small cone, which is what turns a hard shadow into a penumbra. */
vec3 svoConeSample(vec3 direction, float radius, vec2 uv) {
    if (radius <= 0.0) {
        return direction;
    }
    float angle = 6.28318530718 * uv.y;
    float spread = radius * sqrt(uv.x);
    vec3 tangent;
    vec3 bitangent;
    svoBasis(direction, tangent, bitangent);
    return normalize(direction + (tangent * cos(angle) + bitangent * sin(angle)) * spread);
}

/** Rebuilds camera-relative world space from a depth sample. */
vec3 svoWorldFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0, depth, 1.0);
    vec4 world = svoInvViewProjection * clip;
    return world.xyz / world.w;
}

/**
 * Snaps a normal to its dominant axis when it is already nearly axis-aligned. Voxel terrain is all
 * flat faces, so this removes the wobble that derivative-reconstructed normals pick up along
 * silhouettes, while leaving mob and item geometry alone.
 */
vec3 svoSnapNormal(vec3 normal) {
    vec3 magnitude = abs(normal);
    float dominant = max(magnitude.x, max(magnitude.y, magnitude.z));
    if (dominant < 0.94) {
        return normal;
    }
    if (dominant == magnitude.x) {
        return vec3(sign(normal.x), 0.0, 0.0);
    }
    if (dominant == magnitude.y) {
        return vec3(0.0, sign(normal.y), 0.0);
    }
    return vec3(0.0, 0.0, sign(normal.z));
}

#endif
