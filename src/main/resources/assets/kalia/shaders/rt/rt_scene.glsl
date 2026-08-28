// The scene description shared by every ray tracing stage.
//
// Both the tracer and the lighting pass read this same block, which is what stops
// them from disagreeing about where the sun is or how bright it should be.
//
// You can use it with `#include "kalia:rt/rt_scene.glsl"` after defining
// KALIA_SCENE_BINDING.

#ifndef KALIA_SCENE_BINDING
#error "Define KALIA_SCENE_BINDING before including rt_scene.glsl"
#endif

layout(binding = KALIA_SCENE_BINDING, std140) uniform KaliaRtScene {
    /** Clip space back to the camera-relative world space the scene is built in. */
    mat4 kaliaInverseViewProjection;

    /** xyz: offset from camera-relative space to the structure's snapped origin. w: frame counter. */
    vec4 kaliaSceneOffset;

    /** xyz: direction towards the sun or moon. w: how much light it is delivering. */
    vec4 kaliaSun;

    /** rgb: colour of that light. w: its brightness multiplier. */
    vec4 kaliaSunColour;

    /** rgb: colour of the sky as an ambient source. w: its brightness multiplier. */
    vec4 kaliaSkyColour;

    /** rgb: sky and fog tint a ray sees when it escapes. w: how much of it comes back. */
    vec4 kaliaEnvironment;

    /** Diffuse rays, bounces, ray range in blocks, emissive strength. */
    vec4 kaliaTraceParams;

    /** Reflections on, the two depth linearisation terms, block light strength. */
    vec4 kaliaSurfaceParams;

    /** Indirect strength, occlusion strength, exposure, debug view. */
    vec4 kaliaOutputParams;

    /** Trace target texel size, reflection strength, spare. */
    vec4 kaliaResolution;

    /** Fog start, end, density, and which mode of the three is in use. */
    vec4 kaliaFog;

    /**
     * xyz: the true direction of the sun, which stays put when it sets rather
     * than flipping to the moon. The atmosphere needs the real one, because a sun
     * below the horizon is exactly what makes a sunset look like one.
     * w: the camera's altitude in blocks.
     */
    vec4 kaliaCelestial;
};

#define KALIA_TRUE_SUN        kaliaCelestial.xyz
#define KALIA_CAMERA_ALTITUDE kaliaCelestial.w

#define KALIA_SCENE_OFFSET   kaliaSceneOffset.xyz
#define KALIA_FRAME          uint(kaliaSceneOffset.w)
#define KALIA_SUN_DIRECTION  kaliaSun.xyz
#define KALIA_SUN_STRENGTH   kaliaSun.w
#define KALIA_SUN_COLOUR     (kaliaSunColour.rgb * kaliaSunColour.w)
#define KALIA_SKY_COLOUR     (kaliaSkyColour.rgb * kaliaSkyColour.w)
#define KALIA_ENVIRONMENT    kaliaEnvironment.rgb
#define KALIA_SKY_LIGHT      kaliaEnvironment.w
#define KALIA_RAYS           int(kaliaTraceParams.x)
#define KALIA_BOUNCES        int(kaliaTraceParams.y)
#define KALIA_RANGE          kaliaTraceParams.z
#define KALIA_EMISSIVE       kaliaTraceParams.w
#define KALIA_REFLECTIONS    (kaliaSurfaceParams.x > 0.5)
#define KALIA_DEPTH_A        kaliaSurfaceParams.y
#define KALIA_DEPTH_B        kaliaSurfaceParams.z
#define KALIA_BLOCK_LIGHT    kaliaSurfaceParams.w
#define KALIA_INDIRECT       kaliaOutputParams.x
#define KALIA_OCCLUSION      kaliaOutputParams.y
#define KALIA_EXPOSURE       kaliaOutputParams.z
#define KALIA_DEBUG          int(kaliaOutputParams.w)
#define KALIA_TEXEL          kaliaResolution.xy
#define KALIA_REFLECT_STRENGTH kaliaResolution.z

// Undoes the projection's depth mapping. Cheaper than a full unprojection and,
// unlike a radial distance, it agrees with the linear depth every other stage
// derives from the same two terms.
float kaliaLinearDepth(float deviceDepth) {
    return KALIA_DEPTH_B / (deviceDepth + KALIA_DEPTH_A);
}

#define KALIA_FOG_OFF    0
#define KALIA_FOG_LINEAR 1
#define KALIA_FOG_EXP    2
#define KALIA_FOG_EXP2   3

/**
 * How much fog stands between the eye and a surface at [viewDistance].
 *
 * The geometry buffer path deliberately never applies fog, because a geometry
 * buffer has no business holding a colour that is not the surface's own. It is
 * applied once, after the surface has been lit, which is also where it lines up
 * with the sky it has to blend into.
 */
float kaliaFogFactor(float viewDistance) {
    int mode = int(kaliaFog.w + 0.5);
    if (mode == KALIA_FOG_OFF) {
        return 0.0;
    }
    if (mode == KALIA_FOG_EXP) {
        return clamp(1.0 - exp(-kaliaFog.z * viewDistance), 0.0, 1.0);
    }
    if (mode == KALIA_FOG_EXP2) {
        float volume = kaliaFog.z * viewDistance;
        return clamp(1.0 - exp(-volume * volume), 0.0, 1.0);
    }
    float span = max(kaliaFog.y - kaliaFog.x, 1e-4);
    return clamp((viewDistance - kaliaFog.x) / span, 0.0, 1.0);
}

// Reconstructs a position in the space the acceleration structure was built in:
// world coordinates relative to the scene's snapped origin.
vec3 kaliaScenePosition(vec2 sampleUv, float deviceDepth) {
    vec4 ndc = vec4(sampleUv.x * 2.0 - 1.0, 1.0 - sampleUv.y * 2.0, deviceDepth, 1.0);
    vec4 position = kaliaInverseViewProjection * ndc;
    return position.xyz / position.w + KALIA_SCENE_OFFSET;
}
