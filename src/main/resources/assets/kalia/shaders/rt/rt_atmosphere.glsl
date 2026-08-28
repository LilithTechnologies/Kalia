// A physical sky.
//
// Rayleigh and Mie single scattering through an exponential atmosphere, which is
// the standard model (Nishita, Bruneton) and the reason a real sky is blue
// overhead, pale at the horizon and red at sunset. Minecraft's own sky is a flat
// vertical gradient, so using it as a light source makes everything under it look
// equally flat no matter how the light is traced.
//
// Structure and constants follow the implementation in the Radiance renderer
// (github.com/Minecraft-Radiance/MCVR, GPL-3.0), used with permission.
//
// Distances are in metres, matching the scattering coefficients.

#ifndef KALIA_RT_ATMOSPHERE_GLSL
#define KALIA_RT_ATMOSPHERE_GLSL

/** Radius of the planet. */
const float KALIA_ATMOSPHERE_RG = 6360000.0;

/** Radius of the top of the atmosphere. */
const float KALIA_ATMOSPHERE_RT = 6460000.0;

/** Scale heights over which each kind of scattering thins out. */
const float KALIA_ATMOSPHERE_HR = 8000.0;
const float KALIA_ATMOSPHERE_HM = 1200.0;

/**
 * Rayleigh scattering is wavelength dependent, which is the whole reason the sky
 * is blue: short wavelengths scatter several times more than long ones.
 */
const vec3 KALIA_BETA_R = vec3(0.000005802, 0.000013558, 0.0000331);

/** Mie scattering by larger particles, near enough wavelength independent. */
const vec3 KALIA_BETA_M = vec3(0.000021);

/** Forward bias of Mie scattering, which puts the glow around the sun. */
const float KALIA_MIE_G = 0.8;

/** Keeps a view ray from grazing along the ground and integrating forever. */
const float KALIA_MIN_VIEW_COS = 0.02;

/** Radiance arriving at the top of the atmosphere from each body. */
const vec3 KALIA_SUN_RADIANCE_TOP = vec3(8.0);
const vec3 KALIA_MOON_RADIANCE_TOP = vec3(0.64, 0.8, 1.6);

#ifndef KALIA_PI
#define KALIA_PI 3.14159265359
#endif

/**
 * Where a ray leaves a sphere centred on the origin, or false if it misses.
 */
bool kaliaAtmosphereIntersect(vec3 origin, vec3 direction, float radius, out float near, out float far) {
    float b = dot(origin, direction);
    float c = dot(origin, origin) - radius * radius;
    float height = b * b - c;
    if (height < 0.0) {
        return false;
    }
    height = sqrt(height);
    near = -b - height;
    far = -b + height;
    return true;
}

float kaliaAtmosphereDensity(float height, float scaleHeight) {
    return exp(-max(height, 0.0) / scaleHeight);
}

/**
 * Where a point at radius [r] looking along cosine [mu] lands in the
 * transmittance table.
 */
vec2 kaliaTransmittanceUv(float r, float mu) {
    return vec2(
        clamp(mu * 0.5 + 0.5, 0.0, 1.0),
        clamp((r - KALIA_ATMOSPHERE_RG) / (KALIA_ATMOSPHERE_RT - KALIA_ATMOSPHERE_RG), 0.0, 1.0));
}

/**
 * The fraction of light surviving a trip from radius [r] along cosine [mu] out
 * through the top of the atmosphere.
 */
vec3 kaliaTransmittance(sampler2D table, float r, float mu) {
    vec2 uv = kaliaTransmittanceUv(r, mu);
    vec2 inverseSize = 1.0 / vec2(textureSize(table, 0));
    // Half a texel in, so the bilinear filter never reaches past the edge of a
    // table whose ends mean something specific.
    return texture(table, clamp(uv, 0.5 * inverseSize, 1.0 - 0.5 * inverseSize)).rgb;
}

/**
 * Maps a direction onto the sky table.
 *
 * Elevation is stored against its square root so that most of the resolution
 * lands near the horizon, which is where the sky actually changes quickly. Below
 * the horizon everything collapses to the horizon colour, which is all Minecraft
 * ever needs: the ground is in the way.
 */
vec2 kaliaSkyUv(vec3 direction) {
    float azimuth = atan(direction.z, direction.x) / (2.0 * KALIA_PI) + 0.5;
    float elevation = sqrt(clamp(direction.y, 0.0, 1.0));
    return vec2(azimuth, elevation);
}

vec3 kaliaSkyDirection(vec2 uv) {
    float azimuth = (uv.x - 0.5) * 2.0 * KALIA_PI;
    float elevation = uv.y * uv.y;
    float horizontal = sqrt(max(1.0 - elevation * elevation, 0.0));
    return normalize(vec3(cos(azimuth) * horizontal, elevation, sin(azimuth) * horizontal));
}

#endif
