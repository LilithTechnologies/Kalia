// Transmittance through the atmosphere, tabulated once.
//
// For every starting altitude and view angle, how much light survives the trip
// out through the top of the atmosphere. The sky integration needs this at every
// step and the sun needs it to work out its own colour, so computing it once into
// a small table is what makes both affordable.
//
// The table depends only on the atmosphere's constants, so it never has to be
// rebuilt once it exists.

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 outTransmittance;

#include "kalia:rt/rt_atmosphere.glsl"

/** Steps along one ray. The function is smooth, so this converges quickly. */
const int KALIA_TRANSMITTANCE_STEPS = 64;

void main() {
    // Undo the table's parameterisation to recover the altitude and view angle
    // this texel stands for.
    float mu = uv.x * 2.0 - 1.0;
    float r = mix(KALIA_ATMOSPHERE_RG, KALIA_ATMOSPHERE_RT, uv.y);

    vec3 origin = vec3(0.0, r, 0.0);
    float horizontal = sqrt(max(1.0 - mu * mu, 0.0));
    vec3 direction = vec3(horizontal, mu, 0.0);

    float near;
    float far;
    if (!kaliaAtmosphereIntersect(origin, direction, KALIA_ATMOSPHERE_RT, near, far)) {
        outTransmittance = vec4(1.0);
        return;
    }

    // A ray that meets the planet is fully blocked, which is what puts the
    // terminator in the right place at sunrise and sunset.
    float groundNear;
    float groundFar;
    if (kaliaAtmosphereIntersect(origin, direction, KALIA_ATMOSPHERE_RG, groundNear, groundFar) && groundFar > 0.0) {
        if (groundNear > 0.0) {
            outTransmittance = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
    }

    float distance = max(far, 0.0);
    float step = distance / float(KALIA_TRANSMITTANCE_STEPS);

    vec3 opticalDepth = vec3(0.0);
    for (int index = 0; index < KALIA_TRANSMITTANCE_STEPS; ++index) {
        vec3 position = origin + direction * (float(index) + 0.5) * step;
        float height = length(position) - KALIA_ATMOSPHERE_RG;
        opticalDepth +=
            (KALIA_BETA_R * kaliaAtmosphereDensity(height, KALIA_ATMOSPHERE_HR) +
             KALIA_BETA_M * kaliaAtmosphereDensity(height, KALIA_ATMOSPHERE_HM)) * step;
    }

    outTransmittance = vec4(exp(-opticalDepth), 1.0);
}
