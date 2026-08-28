// Turns the geometry buffer into a lit image.
//
// This is where Minecraft's own lighting stops being used. The terrain pass hands
// over albedo and a normal; everything that makes the surface bright is computed
// here from real sources: a shadowed sun, the sky dome, light bouncing off the
// rest of the world, and for now Minecraft's block light standing in for torches
// until they become real emitters.
//
// The traced terms arrive at a fraction of the resolution, so they are brought
// back up with a depth-guided joint bilateral upsample. A plain bilinear stretch
// would bleed light across silhouettes, which reads as a halo on every block edge.

layout(binding = 0) uniform sampler2D kaliaAlbedo;
layout(binding = 1) uniform sampler2D kaliaSurface;
layout(binding = 2) uniform sampler2D kaliaDepth;
layout(binding = 3) uniform sampler2D kaliaIndirect;
layout(binding = 4) uniform sampler2D kaliaReflection;
layout(binding = 5) uniform sampler2D kaliaMoments;
layout(binding = 7) uniform sampler2D kaliaTraceSurface;
layout(binding = 8) uniform sampler2D kaliaSkyLut;
layout(binding = 9) uniform sampler2D kaliaTransmittanceLut;

#define KALIA_SCENE_BINDING 6

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColour;

#include "kalia:rt/rt_common.glsl"
#include "kalia:rt/rt_scene.glsl"
#include "kalia:rt/rt_atmosphere.glsl"

#define KALIA_DEBUG_OFF         0
#define KALIA_DEBUG_INDIRECT    1
#define KALIA_DEBUG_OCCLUSION   2
#define KALIA_DEBUG_REFLECTIONS 3
#define KALIA_DEBUG_NORMALS     4
#define KALIA_DEBUG_VARIANCE    5
#define KALIA_DEBUG_HISTORY     6
#define KALIA_DEBUG_INSTANCES   7

/** How far a trace-resolution tap's depth may disagree before it is discarded. */
const float KALIA_DEPTH_TOLERANCE = 0.05;

/**
 * Block light falls off steeply in Minecraft, and reading it back linearly makes
 * every torch-lit room look flat. Squaring it restores something closer to the
 * inverse-square falloff the baked values were sampled from.
 */
float blockLightCurve(float light) {
    return light * light;
}

/**
 * Sky light says how much of the sky dome a surface could see when the world was
 * lit. It is a free, noise-free ambient occlusion term for skylight specifically,
 * which is exactly what it was baked to be.
 */
float skyLightCurve(float light) {
    return light * light;
}

struct Upsampled {
    vec4 indirect;
    vec4 reflection;
    vec4 moments;
};

Upsampled upsample(float linearDepth) {
    vec2 texel = KALIA_TEXEL;
    vec2 pixel = uv / texel - 0.5;
    vec2 base = floor(pixel);
    vec2 fraction = pixel - base;

    float weights[4];
    weights[0] = (1.0 - fraction.x) * (1.0 - fraction.y);
    weights[1] = fraction.x * (1.0 - fraction.y);
    weights[2] = (1.0 - fraction.x) * fraction.y;
    weights[3] = fraction.x * fraction.y;

    vec2 offsets[4];
    offsets[0] = vec2(0.0, 0.0);
    offsets[1] = vec2(1.0, 0.0);
    offsets[2] = vec2(0.0, 1.0);
    offsets[3] = vec2(1.0, 1.0);

    Upsampled result;
    result.indirect = vec4(0.0);
    result.reflection = vec4(0.0);
    result.moments = vec4(0.0);
    float total = 0.0;

    for (int index = 0; index < 4; ++index) {
        vec2 tapUv = (base + offsets[index] + 0.5) * texel;
        if (!kaliaRtOnScreen(tapUv)) {
            continue;
        }
        vec4 tapSurface = texture(kaliaTraceSurface, tapUv);
        if (tapSurface.w < 0.0) {
            continue;
        }
        float weight = weights[index] * kaliaRtDepthWeight(linearDepth, tapSurface.w, KALIA_DEPTH_TOLERANCE);
        if (weight <= 1e-5) {
            continue;
        }
        result.indirect += texture(kaliaIndirect, tapUv) * weight;
        result.reflection += texture(kaliaReflection, tapUv) * weight;
        result.moments += texture(kaliaMoments, tapUv) * weight;
        total += weight;
    }

    if (total <= 1e-5) {
        // Every tap disagreed, which happens on a one-pixel silhouette. Falling
        // back to the nearest tap beats leaving the pixel unlit.
        result.indirect = texture(kaliaIndirect, uv);
        result.reflection = texture(kaliaReflection, uv);
        result.moments = texture(kaliaMoments, uv);
        return result;
    }

    float inverse = 1.0 / total;
    result.indirect *= inverse;
    result.reflection *= inverse;
    result.moments *= inverse;
    return result;
}

/**
 * ACES filmic tone mapping, in the widely used fitted form. Lighting is no longer
 * bounded by Minecraft's zero-to-one light map, so something has to bring the open
 * range back onto a display without simply clipping the highlights.
 */
vec3 toneMap(vec3 colour) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((colour * (a * colour + b)) / (colour * (c * colour + d) + e), 0.0, 1.0);
}

void main() {
    float deviceDepth = texture(kaliaDepth, uv).r;
    vec4 surface = texture(kaliaSurface, uv);

    // Nothing solid here, so the sky the forward pass already drew stays.
    if (kaliaRtIsSky(deviceDepth) || dot(surface.xyz, surface.xyz) < 1e-6) {
        discard;
    }

    vec4 albedoTexel = texture(kaliaAlbedo, uv);
    vec3 albedo = albedoTexel.rgb;
    float blockLight = albedoTexel.a;
    float skyLight = surface.w;
    vec3 normal = normalize(surface.xyz);

    float linearDepth = kaliaLinearDepth(deviceDepth);
    Upsampled resolved = upsample(linearDepth);

    if (KALIA_DEBUG != KALIA_DEBUG_OFF) {
        if (KALIA_DEBUG == KALIA_DEBUG_INDIRECT) {
            fragColour = vec4(resolved.indirect.rgb, 1.0);
        } else if (KALIA_DEBUG == KALIA_DEBUG_OCCLUSION) {
            fragColour = vec4(vec3(resolved.indirect.a), 1.0);
        } else if (KALIA_DEBUG == KALIA_DEBUG_REFLECTIONS) {
            fragColour = vec4(resolved.reflection.rgb * resolved.reflection.a, 1.0);
        } else if (KALIA_DEBUG == KALIA_DEBUG_NORMALS) {
            fragColour = vec4(normal * 0.5 + 0.5, 1.0);
        } else if (KALIA_DEBUG == KALIA_DEBUG_VARIANCE) {
            fragColour = vec4(vec3(sqrt(max(resolved.moments.a, 0.0))), 1.0);
        } else if (KALIA_DEBUG == KALIA_DEBUG_HISTORY) {
            fragColour = vec4(vec3(resolved.moments.b / 48.0), 1.0);
        } else {
            fragColour = vec4(albedo, 1.0);
        }
        return;
    }

    // Direct sunlight, shadowed for real by the traced visibility term.
    //
    // Its radiance is the physical value at the top of the atmosphere attenuated
    // by however much air the light had to cross, which is what turns it orange at
    // sunset without any of that being hand-authored. Everything below is in those
    // same units, so exposure is the single control that maps the result onto a
    // display rather than each term being tuned against the others.
    float altitude = max(KALIA_CAMERA_ALTITUDE, 0.0) + 70.0;
    vec3 sunDirection = normalize(KALIA_SUN_DIRECTION);
    vec3 sunTint = kaliaTransmittance(
        kaliaTransmittanceLut,
        KALIA_ATMOSPHERE_RG + altitude,
        sunDirection.y);
    float sunFacing = max(dot(normal, sunDirection), 0.0);
    vec3 direct = KALIA_SUN_RADIANCE_TOP * sunTint * kaliaSunColour.w *
        KALIA_SUN_STRENGTH * sunFacing * resolved.indirect.a;

    // The sky as an ambient dome, sampled in the direction the surface faces so a
    // wall picks up the horizon and a floor picks up what is overhead. How much of
    // the dome the surface can see at all is what the baked sky light records.
    vec3 skyAbove = texture(kaliaSkyLut, kaliaSkyUv(normalize(normal + vec3(0.0, 1.0, 0.0)))).rgb;
    vec3 ambient = skyAbove * kaliaSkyColour.w * skyLightCurve(skyLight) *
        mix(1.0, 0.5 + 0.5 * normal.y, KALIA_OCCLUSION);

    // Block light still stands in for torches and lava until they are emitters.
    // The tint is the warm cast Minecraft's own light map has, kept so a torch-lit
    // room does not turn grey the moment the vanilla lighting stops being used.
    //
    // Minecraft's light map is normalised to one, so it has to be lifted into the
    // same physical range as the sun or a torch would be invisible beside it.
    const vec3 torchTint = vec3(1.0, 0.86, 0.66);
    const float torchRadiance = 6.0;
    vec3 blockLighting = torchTint * blockLightCurve(blockLight) * KALIA_BLOCK_LIGHT * torchRadiance;

    vec3 indirect = resolved.indirect.rgb * KALIA_INDIRECT;

    vec3 lit = albedo * (direct + ambient + blockLighting + indirect);
    lit += resolved.reflection.rgb * resolved.reflection.a * KALIA_REFLECT_STRENGTH;

    vec3 mapped = toneMap(lit * KALIA_EXPOSURE);

    // Fog is mixed in after tone mapping, in the same display-referred space the
    // sky and everything the forward pass draws already live in. Blending toward
    // it beforehand would leave distant terrain a different colour from the sky it
    // is supposed to disappear into.
    fragColour = vec4(mix(mapped, KALIA_ENVIRONMENT, kaliaFogFactor(linearDepth)), 1.0);
}
