// Per-frame constants shared by every voxel pass.
//
// These live in a uniform block rather than push constants: at 256 bytes they would sit exactly on
// the portable push-constant ceiling, and plenty of drivers only guarantee half of that.

#ifndef KALIA_SVO_SCENE
#define KALIA_SVO_SCENE

layout(std140, binding = 6) uniform KaliaSvoScene {
    // Clip space back to camera-relative world space.
    mat4 svoInvViewProjection;
    // Camera-relative world space to clip space, for writing depth from a traced hit.
    mat4 svoViewProjection;
    // This frame's clip space to last frame's, for temporal reprojection.
    mat4 svoReprojection;
    // xyz: the octree's minimum corner, in blocks relative to the camera.
    vec4 svoTreeOrigin;
    // xyz: direction towards the sun. w: how strongly it lights the world, 0 at night.
    vec4 svoSunVector;
    // rgb: sunlight colour. w: how much sky light reaches an unoccluded surface.
    vec4 svoSunTint;
    // rgb: sky colour used for rays that escape. w: bounce strength.
    vec4 svoSkyTint;
    // x: octree depth. y: traversal step ceiling. z: shadow range. w: diffuse range.
    vec4 svoRangeParams;
    // x: diffuse rays per pixel. y: frame index. z: composite intensity. w: sun angular radius.
    vec4 svoSampleParams;
    // x: reflection range. y: feature bits. z: cone footprint per block. w: root node index.
    vec4 svoTraceParams;
    // xy: trace target size in pixels. z: temporal blend. w: a-trous step width.
    vec4 svoScreenParams;
    // rgb: the colour distant geometry fades into. w: how far shadows darken what they fall on.
    vec4 svoFogColor;
    // x: fog start distance. y: fog end distance. z: shadow ray step budget. w: level-of-detail bias.
    vec4 svoFogParams;
    // x: which debug view to draw, zero for none. y: ambient occlusion strength.
    // z: how brightly emissive voxels light their surroundings. w: spare.
    vec4 svoExtraParams;
};

#define svoTreeMin       svoTreeOrigin.xyz
#define svoSunDirection  svoSunVector.xyz
#define svoSunIntensity  svoSunVector.w
#define svoSunColor      svoSunTint.rgb
#define svoSkyAmbient    svoSunTint.w
#define svoSkyColor      svoSkyTint.rgb
#define svoBounceScale   svoSkyTint.w

#define svoLevels        int(svoRangeParams.x)
#define svoMaxSteps      int(svoRangeParams.y)
#define svoShadowRange   svoRangeParams.z
#define svoDiffuseRange  svoRangeParams.w

#define svoRayCount      int(svoSampleParams.x)
#define svoFrameIndex    int(svoSampleParams.y)
#define svoIntensity     svoSampleParams.z
#define svoSunSoftness   svoSampleParams.w

#define svoReflectRange  svoTraceParams.x
#define svoFeatureBits   uint(svoTraceParams.y)
// Blocks of cone width per block of distance, i.e. one pixel's footprint. Drives texture mip
// selection directly, and the octree level-of-detail cutoff once scaled by the bias.
#define svoFootprint     svoTraceParams.z
#define svoRoot          uint(svoTraceParams.w)

#define svoFogStart      svoFogParams.x
#define svoFogEnd        svoFogParams.y
// Secondary rays get a smaller budget than primary ones; they are shorter and far more numerous.
#define svoShadowSteps   int(svoFogParams.z)
#define SVO_LOD_BIAS     svoFogParams.w
#define svoShadowStrength svoFogColor.w

#define svoDebugView          int(svoExtraParams.x)
#define svoOcclusionStrength  svoExtraParams.y
#define svoEmissionStrength   svoExtraParams.z

// Debug views, selected by SvoSettings.debugView. Each one isolates a different stage of the
// chain, so a single screenshot says which of them is misbehaving.
#define SVO_DEBUG_OFF        0
#define SVO_DEBUG_LIGHT      1
#define SVO_DEBUG_NORMAL     2
#define SVO_DEBUG_DISTANCE   3
#define SVO_DEBUG_DEPTH      4
#define SVO_DEBUG_REACHED    5
#define SVO_DEBUG_COVERAGE   6

#define svoTargetSize    svoScreenParams.xy
#define svoTemporalAlpha svoScreenParams.z
#define svoFilterStep    svoScreenParams.w

#define SVO_FEATURE_SHADOWS      1u
#define SVO_FEATURE_OCCLUSION    2u
#define SVO_FEATURE_BOUNCE       4u
#define SVO_FEATURE_REFLECTIONS  8u
#define SVO_FEATURE_HISTORY      16u

bool svoFeature(uint bit) {
    return (svoFeatureBits & bit) != 0u;
}

#endif
