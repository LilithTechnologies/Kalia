// Reading the world back out of the acceleration structure.
//
// A hit gives an instance, a triangle and a pair of barycentrics; turning that
// into a surface means fetching the very same vertices the raster path draws,
// through the address the instance record carries. Both the primary pass and the
// lighting trace need this, so it lives here.
//
// Requires, before including:
//   KALIA_ATLAS_BINDING, KALIA_TLAS_BINDING, KALIA_INSTANCE_BINDING

#ifndef KALIA_RT_GEOMETRY_GLSL
#define KALIA_RT_GEOMETRY_GLSL

layout(binding = KALIA_TLAS_BINDING) uniform accelerationStructureEXT kaliaScene;

layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer ChunkVertices {
    uint words[];
};

struct KaliaInstance {
    uint64_t vertexAddress;
    uint vertexStride;
    uint flags;
};

layout(binding = KALIA_INSTANCE_BINDING, std430) readonly buffer KaliaInstances {
    KaliaInstance kaliaInstances[];
};

/** A ray hit resolves alpha against the atlas to see through cut-out blocks. */
const float KALIA_ALPHA_CUTOFF = 0.1;

/** Offset along a ray before a hit counts, keeping a surface from shadowing itself. */
const float KALIA_NORMAL_BIAS = 0.02;

// Matches VanillaLikeChunkVertex: position, packed colour, texture coordinate
// and packed light, interleaved at a stride the instance record carries.
struct ChunkVertex {
    vec3 position;
    vec4 colour;
    vec2 coord;
    vec2 light;
};

ChunkVertex kaliaReadVertex(uint64_t base, uint stride, uint index) {
    ChunkVertices vertices = ChunkVertices(base);
    uint word = index * (stride >> 2u);

    ChunkVertex result;
    result.position = vec3(
        uintBitsToFloat(vertices.words[word + 0u]),
        uintBitsToFloat(vertices.words[word + 1u]),
        uintBitsToFloat(vertices.words[word + 2u]));

    uint packedColour = vertices.words[word + 3u];
    result.colour = vec4(
        float((packedColour >> 0u) & 0xFFu),
        float((packedColour >> 8u) & 0xFFu),
        float((packedColour >> 16u) & 0xFFu),
        float((packedColour >> 24u) & 0xFFu)) * (1.0 / 255.0);

    result.coord = vec2(
        uintBitsToFloat(vertices.words[word + 4u]),
        uintBitsToFloat(vertices.words[word + 5u]));

    // The light word packs the block coordinate in bits 16..23 and the sky
    // coordinate in bits 24..31, both on the 0..255 scale the light map uses.
    uint packedLight = vertices.words[word + 6u];
    result.light = vec2(
        float((packedLight >> 16u) & 0xFFu),
        float((packedLight >> 24u) & 0xFFu));

    return result;
}

// Chunk meshes are quads expanded to triangles with a fixed winding, so a
// triangle's vertices follow from its index without reading the index buffer.
uvec3 kaliaTriangleVertices(uint primitive) {
    uint quad = (primitive >> 1u) * 4u;
    return (primitive & 1u) == 0u
        ? uvec3(quad + 0u, quad + 1u, quad + 2u)
        : uvec3(quad + 2u, quad + 3u, quad + 0u);
}

/**
 * Alpha of a hit, used to see through the cut-out parts of foliage, glass and
 * grass instead of treating their quads as solid.
 *
 * A rayQueryEXT is an opaque local-only object that cannot cross a function
 * boundary, so the candidate's identity is read out at the call site and passed
 * in as plain values.
 */
float kaliaHitAlpha(uint instanceIndex, uint primitive, vec2 barycentrics) {
    KaliaInstance instance = kaliaInstances[instanceIndex];
    uvec3 corners = kaliaTriangleVertices(primitive);

    vec2 a = kaliaReadVertex(instance.vertexAddress, instance.vertexStride, corners.x).coord;
    vec2 b = kaliaReadVertex(instance.vertexAddress, instance.vertexStride, corners.y).coord;
    vec2 c = kaliaReadVertex(instance.vertexAddress, instance.vertexStride, corners.z).coord;

    vec3 weights = vec3(1.0 - barycentrics.x - barycentrics.y, barycentrics.x, barycentrics.y);
    return textureLod(kaliaAtlas, a * weights.x + b * weights.y + c * weights.z, 0.0).a;
}

struct KaliaTrace {
    bool hit;
    float distanceAlong;
    uint instanceIndex;
    uint primitive;
    vec2 barycentrics;
};

/**
 * Traces a ray against the scene.
 *
 * [anyHit] stops at the first surface rather than searching for the nearest one.
 * A shadow ray only needs to know whether anything is in the way, and letting it
 * stop early is the difference between traversing a handful of nodes and
 * traversing the whole structure for an answer it discards.
 */
KaliaTrace kaliaTrace(vec3 origin, vec3 direction, float range, bool anyHit) {
    KaliaTrace result;
    result.hit = false;
    result.distanceAlong = range;

    rayQueryEXT query;
    rayQueryInitializeEXT(
        query,
        kaliaScene,
        anyHit ? gl_RayFlagsTerminateOnFirstHitEXT : gl_RayFlagsNoneEXT,
        0xFFu,
        origin,
        KALIA_NORMAL_BIAS,
        direction,
        range);

    // Solid sections are marked opaque and never surface as candidates. Cut-out
    // sections are not, so their quads are alpha tested here before the hit is
    // allowed to count.
    while (rayQueryProceedEXT(query)) {
        if (rayQueryGetIntersectionTypeEXT(query, false) != gl_RayQueryCandidateIntersectionTriangleEXT) {
            continue;
        }
        float alpha = kaliaHitAlpha(
            uint(rayQueryGetIntersectionInstanceCustomIndexEXT(query, false)),
            uint(rayQueryGetIntersectionPrimitiveIndexEXT(query, false)),
            rayQueryGetIntersectionBarycentricsEXT(query, false));
        if (alpha >= KALIA_ALPHA_CUTOFF) {
            rayQueryConfirmIntersectionEXT(query);
        }
    }

    if (rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
        return result;
    }

    result.hit = true;
    result.distanceAlong = rayQueryGetIntersectionTEXT(query, true);
    result.instanceIndex = uint(rayQueryGetIntersectionInstanceCustomIndexEXT(query, true));
    result.primitive = uint(rayQueryGetIntersectionPrimitiveIndexEXT(query, true));
    result.barycentrics = rayQueryGetIntersectionBarycentricsEXT(query, true);
    return result;
}

/**
 * The interpolated surface at a hit, in the space the structure was built in.
 */
struct KaliaHit {
    vec3 position;
    vec3 normal;
    vec3 albedo;
    float blockLight;
    float skyLight;
};

/**
 * Resolves a hit into a surface.
 *
 * [lod] selects the mip level. There are no screen-space derivatives along a ray,
 * so the caller has to say how wide the ray's footprint has become; picking that
 * badly is the difference between crisp terrain and a shimmering mess.
 */
KaliaHit kaliaResolveHit(KaliaTrace trace, vec3 origin, vec3 direction, float lod) {
    KaliaInstance instance = kaliaInstances[trace.instanceIndex];
    uvec3 corners = kaliaTriangleVertices(trace.primitive);

    ChunkVertex a = kaliaReadVertex(instance.vertexAddress, instance.vertexStride, corners.x);
    ChunkVertex b = kaliaReadVertex(instance.vertexAddress, instance.vertexStride, corners.y);
    ChunkVertex c = kaliaReadVertex(instance.vertexAddress, instance.vertexStride, corners.z);

    vec3 weights = vec3(
        1.0 - trace.barycentrics.x - trace.barycentrics.y,
        trace.barycentrics.x,
        trace.barycentrics.y);

    vec2 coord = a.coord * weights.x + b.coord * weights.y + c.coord * weights.z;
    vec4 tint = a.colour * weights.x + b.colour * weights.y + c.colour * weights.z;
    vec2 light = a.light * weights.x + b.light * weights.y + c.light * weights.z;

    KaliaHit result;
    result.position = origin + direction * trace.distanceAlong;
    result.albedo = textureLod(kaliaAtlas, coord, lod).rgb * tint.rgb;
    result.blockLight = light.x * (1.0 / 255.0);
    result.skyLight = light.y * (1.0 / 255.0);

    result.normal = normalize(cross(b.position - a.position, c.position - a.position));
    if (dot(result.normal, direction) > 0.0) {
        result.normal = -result.normal;
    }

    return result;
}

/**
 * Mip level for a ray whose footprint has spread to [coneRadius] world units.
 *
 * Block textures are sixteen texels across a block, so a footprint of one
 * sixteenth of a block is exactly one texel and wants the base level.
 */
float kaliaConeLod(float coneRadius) {
    const float texelsPerBlock = 16.0;
    return clamp(log2(max(coneRadius * texelsPerBlock, 1e-4)), 0.0, 5.0);
}

#endif
