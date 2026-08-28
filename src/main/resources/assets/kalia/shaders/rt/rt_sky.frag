// The sky, tabulated per frame.
//
// One texel per view direction, holding the light scattered towards the eye from
// the sun and the moon. Building it once per frame means the tracer can read the
// sky for the cost of a texture fetch, which matters because every ray that
// escapes the world asks for it.

layout(binding = 0) uniform sampler2D kaliaTransmittanceLut;

#define KALIA_SCENE_BINDING 6

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 outSky;

#include "kalia:rt/rt_common.glsl"
#include "kalia:rt/rt_scene.glsl"
#include "kalia:rt/rt_atmosphere.glsl"

/** Steps along one view ray. */
const int KALIA_SCATTER_STEPS = 32;

/**
 * Light scattered towards the eye along a ray, from a source in [lightDirection]
 * with radiance [lightRadiance].
 */
vec3 scatter(vec3 origin, vec3 direction, vec3 lightDirection, vec3 lightRadiance) {
    float atmosphereNear;
    float atmosphereFar;
    if (!kaliaAtmosphereIntersect(origin, direction, KALIA_ATMOSPHERE_RT, atmosphereNear, atmosphereFar)) {
        return vec3(0.0);
    }
    atmosphereNear = max(atmosphereNear, 0.0);

    // Stop at the ground rather than integrating through the planet.
    float groundNear;
    float groundFar;
    if (kaliaAtmosphereIntersect(origin, direction, KALIA_ATMOSPHERE_RG, groundNear, groundFar)) {
        float hit = groundNear > 0.0 ? groundNear : groundFar;
        if (hit > 0.0) {
            atmosphereFar = min(atmosphereFar, hit);
        }
    }

    float step = (atmosphereFar - atmosphereNear) / float(KALIA_SCATTER_STEPS);
    if (step <= 0.0) {
        return vec3(0.0);
    }

    // Rayleigh scatters nearly evenly forwards and back; Mie throws most of its
    // light forward, which is what makes the halo around the sun.
    float cosTheta = dot(lightDirection, direction);
    float rayleighPhase = 3.0 / (16.0 * KALIA_PI) * (1.0 + cosTheta * cosTheta);
    float g = clamp(KALIA_MIE_G, -0.999, 0.999);
    float g2 = g * g;
    float miePhase = (1.0 - g2) /
        (4.0 * KALIA_PI * pow(1.0 + g2 - 2.0 * g * clamp(cosTheta, -1.0, 1.0) + 1e-6, 1.5));

    vec3 radiance = vec3(0.0);
    vec3 viewTransmittance = vec3(1.0);

    for (int index = 0; index < KALIA_SCATTER_STEPS; ++index) {
        vec3 position = origin + direction * (atmosphereNear + (float(index) + 0.5) * step);
        float r = length(position);
        float height = r - KALIA_ATMOSPHERE_RG;

        vec3 rayleigh = KALIA_BETA_R * kaliaAtmosphereDensity(height, KALIA_ATMOSPHERE_HR);
        vec3 mie = KALIA_BETA_M * kaliaAtmosphereDensity(height, KALIA_ATMOSPHERE_HM);

        // How much of the source's light reaches this point in the first place.
        float mu = dot(position / r, lightDirection);
        vec3 lightTransmittance = kaliaTransmittance(kaliaTransmittanceLut, r, mu);

        vec3 scattering = rayleigh * rayleighPhase + mie * miePhase;
        radiance += viewTransmittance * lightTransmittance * scattering * lightRadiance * step;
        viewTransmittance *= exp(-(rayleigh + mie) * step);
    }

    return radiance;
}

void main() {
    vec3 direction = kaliaSkyDirection(uv);
    // Rays aimed at or below the horizon are lifted just above it. The ground is
    // in the way down there anyway, and grazing rays integrate for a very long
    // distance for a result nobody sees.
    direction.y = max(direction.y, KALIA_MIN_VIEW_COS);
    direction = normalize(direction);

    // Altitude of the observer. Minecraft's sea level is 64, and the extra lift
    // keeps a player standing in a valley from being treated as underground.
    float altitude = max(KALIA_CAMERA_ALTITUDE, 0.0) + 70.0;
    vec3 origin = vec3(0.0, KALIA_ATMOSPHERE_RG + altitude, 0.0);

    vec3 sun = normalize(KALIA_TRUE_SUN);
    vec3 sky = scatter(origin, direction, sun, KALIA_SUN_RADIANCE_TOP) +
               scatter(origin, direction, -sun, KALIA_MOON_RADIANCE_TOP);

    outSky = vec4(sky, 1.0);
}
