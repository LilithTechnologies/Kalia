// Primary rays.
//
// The surface each pixel is looking at is found by tracing the acceleration
// structure rather than reading what the rasteriser left behind. The point is not
// that it looks different for flat opaque terrain, because it does not: it is
// that the visible surface and the traced scene become the same thing by
// construction. A rasterised surface can disagree with the structure, and then it
// gets lit by a scene it is not part of.
//
// Where a ray finds nothing the rasterised geometry buffer is left as it was, so
// terrain beyond the traced radius still renders normally.
//
// Target 0: rgb = albedo, a = block light
// Target 1: xyz = geometric normal, a = sky light
// Depth is written so everything drawn afterwards still sorts against the world.

#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference2 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

layout(binding = 0) uniform sampler2D kaliaAtlas;

#define KALIA_ATLAS_BINDING    0
#define KALIA_TLAS_BINDING     1
#define KALIA_INSTANCE_BINDING 2
#define KALIA_SCENE_BINDING    6

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outSurface;

#include "kalia:rt/rt_common.glsl"
#include "kalia:rt/rt_scene.glsl"
#include "kalia:rt/rt_geometry.glsl"

/**
 * How far a primary ray travels. Beyond the traced radius there is no structure
 * to hit, so there is nothing to gain by looking further.
 */
const float KALIA_PRIMARY_RANGE = 512.0;

void main() {
    // The camera sits at the scene offset, and the far plane point through this
    // pixel gives the direction to look in.
    vec3 origin = KALIA_SCENE_OFFSET;
    vec3 target = kaliaScenePosition(uv, 1.0);
    vec3 direction = normalize(target - origin);

    KaliaTrace trace = kaliaTrace(origin, direction, KALIA_PRIMARY_RANGE, false);
    if (!trace.hit) {
        // Nothing traceable here. Whatever the rasteriser wrote stands, which is
        // what keeps terrain outside the traced radius on screen.
        discard;
    }

    // The ray's footprint at the hit, from how fast the direction changes between
    // neighbouring pixels. This is exact for a fullscreen pass and costs nothing,
    // where guessing it leaves distant terrain shimmering.
    float spread = length(dFdx(direction)) + length(dFdy(direction));
    float lod = kaliaConeLod(trace.distanceAlong * spread * 0.5);

    KaliaHit hit = kaliaResolveHit(trace, origin, direction, lod);

    // Depth has to agree with the projection the rasteriser used, or everything
    // drawn over the top sorts against the wrong surface. Distance along the view
    // axis is what the depth buffer stores, not distance along the ray.
    vec3 forward = normalize(kaliaScenePosition(vec2(0.5), 1.0) - origin);
    float linear = max(trace.distanceAlong * dot(direction, forward), 1e-4);
    gl_FragDepth = clamp(KALIA_DEPTH_B / linear - KALIA_DEPTH_A, 0.0, 1.0);

    outAlbedo = vec4(hit.albedo, hit.blockLight);
    outSurface = vec4(hit.normal, hit.skyLight);
}
