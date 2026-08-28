// Shading shared by the lighting pass.

#ifndef KALIA_SVO_SHADE
#define KALIA_SVO_SHADE

#include "kalia:svo_common.glsl"
#include "kalia:svo_sampling.glsl"

/** Footprint to trace secondary rays at: wide, so they sample cheap coarse mips. */
#define SVO_SECONDARY_FOOTPRINT 0.05

/** Sky radiance for a ray that left the world, with a soft sun disc in it. */
vec3 svoSkyRadiance(vec3 direction) {
    float horizon = clamp(direction.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 sky = mix(svoSkyColor * 0.55, svoSkyColor, horizon);
    float disc = pow(max(dot(direction, svoSunDirection), 0.0), 512.0);
    return sky + svoSunColor * disc * svoSunIntensity * 3.0;
}

/** Radiance leaving a traced hit, using one sun ray and a flat sky term for everything else. */
vec3 svoHitRadiance(vec3 point, SvoHit hit, float shadowRange) {
    vec3 radiance = hit.albedo * hit.emission * 6.0;
    radiance += hit.albedo * svoSkyColor * svoSkyAmbient * 0.5;

    float ndl = max(dot(hit.normal, svoSunDirection), 0.0);
    if (ndl > 0.0 && svoSunIntensity > 0.0) {
        vec3 reached = svoVisibility(point + hit.normal * 0.03, svoSunDirection, shadowRange);
        radiance += hit.albedo * svoSunColor * reached * ndl * svoSunIntensity;
    }
    return radiance;
}

/**
 * Specular contribution for a surface the octree says is reflective.
 *
 * @return luminance of the reflection; the composite tints it with the sky, which is what almost
 *         every reflection in a Minecraft world is made of anyway.
 */
float svoReflection(vec3 surface, vec3 normal, vec3 view, uint flags) {
    if (!svoFeature(SVO_FEATURE_REFLECTIONS) || (flags & SVO_FLAG_REFLECTIVE) == 0u) {
        return 0.0;
    }

    vec3 direction = reflect(view, normal);
    SvoHit hit;
    vec3 radiance;
    if (svoTrace(surface, direction, 0.0, svoReflectRange, SVO_SECONDARY_FOOTPRINT, hit)) {
        radiance = svoHitRadiance(surface + direction * hit.t, hit, svoShadowRange * 0.5);
    } else {
        radiance = svoSkyRadiance(direction);
    }

    // Schlick, with the low reflectance of water and ice as the base.
    float fresnel = 0.04 + 0.96 * pow(1.0 - max(dot(normal, -view), 0.0), 5.0);
    return dot(radiance, vec3(0.2126, 0.7152, 0.0722)) * fresnel;
}

#endif
