// Traced lighting.
//
// The visible surface comes from the geometry buffer the primary pass wrote, and
// everything past it is traced: a shadow ray decides whether the sun reaches the
// surface, and cosine-weighted rays gather the light bouncing off everything
// else. A hit is shaded by reading the same vertex data the raster path draws, so
// indirect light is correct for geometry that is off screen, behind the camera,
// or occluded.
//
// Target 0: rgb = incoming irradiance, a = sunlight visibility
// Target 1: rgb = specular reflection, a = Fresnel weight
// Target 2: xyz = surface normal, w = linear view depth (negative where there is
//           no traceable surface), which is what the denoiser stops its filters on

#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference2 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

layout(binding = 0) uniform sampler2D kaliaDepth;
layout(binding = 1) uniform sampler2D kaliaAtlas;
layout(binding = 2) uniform sampler2D kaliaGbufferSurface;
layout(binding = 7) uniform sampler2D kaliaSkyLut;

#define KALIA_ATLAS_BINDING    1
#define KALIA_TLAS_BINDING     3
#define KALIA_INSTANCE_BINDING 4
#define KALIA_SCENE_BINDING    6

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 outIndirect;
layout(location = 1) out vec4 outReflection;
layout(location = 2) out vec4 outSurface;

#include "kalia:rt/rt_common.glsl"
#include "kalia:rt/rt_scene.glsl"
#include "kalia:rt/rt_atmosphere.glsl"
#include "kalia:rt/rt_geometry.glsl"

/** Fresnel reflectance at normal incidence for a plain dielectric. */
const float KALIA_FRESNEL_F0 = 0.04;

/** How far a shadow ray travels before the sun is assumed to be reachable. */
const float KALIA_SHADOW_RANGE = 128.0;

/**
 * Angular radius of the sun. Softening the shadow by the real size of the disc is
 * the difference between a hard stencil edge and a shadow that reads as sunlight.
 */
const float KALIA_SUN_RADIUS = 0.03;

/**
 * Ceiling on what one sample may contribute. With only a handful of rays per
 * pixel, a single unusually bright hit dominates the average and survives the
 * denoiser as a lone bright blob, so its energy is capped rather than allowed to
 * speckle the image. The limit sits well above sunlit albedo so it only catches
 * genuine outliers.
 */
const float KALIA_FIREFLY_CLAMP = 24.0;

/** Warm cast Minecraft's light map has, kept so torch-lit rooms stay warm. */
const vec3 KALIA_TORCH_TINT = vec3(1.0, 0.86, 0.66);

/** Lifts the normalised block light into the same range as the sun. */
const float KALIA_TORCH_RADIANCE = 6.0;

vec3 clampFirefly(vec3 radiance) {
    float luma = kaliaRtLuminance(radiance);
    return luma > KALIA_FIREFLY_CLAMP ? radiance * (KALIA_FIREFLY_CLAMP / luma) : radiance;
}

/**
 * Whether the sun reaches a point, sampled somewhere across its disc so the
 * shadow edge softens with distance the way a real one does.
 */
float sunVisibility(vec3 origin, vec3 normal, inout uint seed) {
    if (KALIA_SUN_STRENGTH <= 0.0) {
        return 0.0;
    }

    vec3 direction = normalize(KALIA_SUN_DIRECTION);
    if (dot(direction, normal) <= 0.0) {
        // Facing away from the sun, so it is shadowed by its own surface and no
        // ray needs to be spent finding that out.
        return 0.0;
    }

    // Jitter within the sun's angular radius. Averaged over frames by the
    // denoiser, this is what turns a hard edge into a penumbra.
    vec3 tangent;
    vec3 bitangent;
    kaliaRtBasis(direction, tangent, bitangent);
    float angle = 2.0 * KALIA_RT_PI * kaliaRtRandom(seed);
    float radius = KALIA_SUN_RADIUS * sqrt(kaliaRtRandom(seed));
    vec3 sampled = normalize(direction + (tangent * cos(angle) + bitangent * sin(angle)) * radius);

    return kaliaTrace(origin, sampled, KALIA_SHADOW_RANGE, true).hit ? 0.0 : 1.0;
}

/**
 * Radiance arriving from the sky in a given direction, read from the table the
 * atmosphere pass built. A ray that escapes the world sees the real sky rather
 * than a flat fog colour, which is most of why bounced light stops looking grey.
 */
vec3 skyRadiance(vec3 direction) {
    return texture(kaliaSkyLut, kaliaSkyUv(direction)).rgb * KALIA_SKY_LIGHT;
}

/**
 * Outgoing radiance of a hit, lit the same way the visible surface is, including
 * its own shadow ray. Reading Minecraft's light map here instead would mean every
 * bounce quietly reintroduced the lighting this replaces.
 */
vec3 shadeHit(KaliaHit hit, inout uint seed) {
    vec3 sun = KALIA_SUN_RADIANCE_TOP * kaliaSunColour.w * KALIA_SUN_STRENGTH *
        max(dot(hit.normal, normalize(KALIA_SUN_DIRECTION)), 0.0) *
        sunVisibility(hit.position + hit.normal * KALIA_NORMAL_BIAS, hit.normal, seed);

    vec3 sky = texture(kaliaSkyLut, kaliaSkyUv(hit.normal)).rgb *
        kaliaSkyColour.w * hit.skyLight * hit.skyLight;

    vec3 torch = KALIA_TORCH_TINT * hit.blockLight * hit.blockLight *
        KALIA_BLOCK_LIGHT * KALIA_TORCH_RADIANCE;

    // Block light doubles as a stand-in for emission, which Minecraft has no
    // separate channel for. Scaling it lets glowstone and lava throw light.
    return hit.albedo * (sun + sky + torch) * mix(1.0, 1.0 + KALIA_EMISSIVE, hit.blockLight);
}

void main() {
    vec4 gbuffer = texture(kaliaGbufferSurface, uv);
    float deviceDepth = texture(kaliaDepth, uv).r;

    // The geometry buffer is cleared to zero, so a zero-length normal means
    // nothing solid was drawn here.
    if (kaliaRtIsSky(deviceDepth) || dot(gbuffer.xyz, gbuffer.xyz) < 1e-6) {
        outIndirect = vec4(0.0);
        outReflection = vec4(0.0);
        outSurface = vec4(0.0, 0.0, 0.0, -1.0);
        return;
    }

    float linear = kaliaLinearDepth(deviceDepth);
    vec3 normal = normalize(gbuffer.xyz);
    vec3 origin = kaliaScenePosition(uv, deviceDepth) + normal * KALIA_NORMAL_BIAS;

    outSurface = vec4(normal, linear);

    uint seed = kaliaRtSeed(gl_FragCoord.xy, KALIA_FRAME);
    float sunlight = sunVisibility(origin, normal, seed);

    vec3 irradiance = vec3(0.0);
    int rays = KALIA_RAYS;

    for (int index = 0; index < rays; ++index) {
        vec3 direction = kaliaRtCosineHemisphere(normal, kaliaRtRandom(seed), kaliaRtRandom(seed));
        KaliaTrace trace = kaliaTrace(origin, direction, KALIA_RANGE, false);

        if (!trace.hit) {
            // Nothing between here and the sky, so the ray genuinely sees it.
            irradiance += skyRadiance(direction);
            continue;
        }

        // Indirect light is broad and low frequency, so a blurry fetch is both
        // cheaper and much less noisy than a sharp one.
        KaliaHit hit = kaliaResolveHit(trace, origin, direction, kaliaConeLod(trace.distanceAlong * 0.05));
        irradiance += clampFirefly(shadeHit(hit, seed));

        if (KALIA_BOUNCES < 2) {
            continue;
        }

        // A second bounce is what lets light turn a corner: without it, a room
        // lit through a doorway only brightens the surfaces the opening sees.
        vec3 secondary = kaliaRtCosineHemisphere(hit.normal, kaliaRtRandom(seed), kaliaRtRandom(seed));
        vec3 bounceOrigin = hit.position + hit.normal * KALIA_NORMAL_BIAS;
        KaliaTrace bounce = kaliaTrace(bounceOrigin, secondary, KALIA_RANGE * 0.5, false);

        vec3 incoming = bounce.hit
            ? shadeHit(kaliaResolveHit(bounce, bounceOrigin, secondary, 4.0), seed)
            : skyRadiance(secondary);
        irradiance += clampFirefly(hit.albedo * incoming);
    }

    // Cosine-weighted sampling makes the estimator a plain mean of the radiance.
    outIndirect = vec4(irradiance / float(rays), sunlight);

    if (!KALIA_REFLECTIONS) {
        outReflection = vec4(0.0);
        return;
    }

    // Nothing in the pipeline carries material data, so every surface is treated
    // as a plain dielectric and only its grazing-angle response is used.
    vec3 viewDirection = normalize(origin - KALIA_SCENE_OFFSET);
    float fresnel = kaliaRtFresnel(viewDirection, normal, KALIA_FRESNEL_F0);
    vec3 direction = reflect(viewDirection, normal);
    KaliaTrace trace = kaliaTrace(origin, direction, KALIA_RANGE * 2.0, false);

    vec3 reflected = trace.hit
        ? shadeHit(kaliaResolveHit(trace, origin, direction, kaliaConeLod(trace.distanceAlong * 0.02)), seed)
        : skyRadiance(direction);
    outReflection = vec4(reflected, fresnel);
}
