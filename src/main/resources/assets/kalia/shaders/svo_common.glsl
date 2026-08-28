// Sparse voxel octree traversal.
//
// The octree covers a cube of bricks anchored near the camera, each brick holding a 16^3 block of
// voxels. Internal nodes store their present children as one contiguous run, so a child address is
// the run pointer plus a population count, and empty regions cost nothing at all.
//
// Each brick carries a small palette of the surfaces it contains and one narrow index per solid
// voxel, so a hit resolves to three atlas sprites (top, side, bottom) and a tint. The texture is
// then sampled exactly where the ray crossed the face.
//
// Rays are given in camera-relative world space with a unit direction, so every `t` in here is a
// distance in blocks. Internally the descent works in brick units, which keeps the numbers small
// enough that single precision stays comfortable out to the edge of a 2048 block root.

#ifndef KALIA_SVO_COMMON
#define KALIA_SVO_COMMON

#include "kalia:svo_scene.glsl"

layout(binding = 2) uniform sampler2D svoLightmap;
layout(binding = 3) uniform sampler2D svoAtlas;

layout(std430, binding = 4) readonly buffer KaliaSvoNodes {
    uvec2 svoNodes[];
};

layout(std430, binding = 5) readonly buffer KaliaSvoBricks {
    uint svoBricks[];
};

layout(std430, binding = 7) readonly buffer KaliaSvoSprites {
    vec4 svoSprites[];
};

// Brick layout, in 32-bit words. Mirrors VoxelFormat on the CPU side.
#define SVO_COARSE_OFFSET          0u
#define SVO_OCCUPANCY_OFFSET       2u
#define SVO_PREFIX_OFFSET          130u
#define SVO_PALETTE_HEADER_OFFSET  146u
#define SVO_LIGHT_INFO_OFFSET      147u
#define SVO_PALETTE_OFFSET         148u

#define SVO_TINT_FACE_TOP      1u
#define SVO_TINT_FACE_SIDE     2u
#define SVO_TINT_FACE_BOTTOM   4u

#define SVO_FLAG_TRANSLUCENT   1u
#define SVO_FLAG_REFLECTIVE    2u
#define SVO_FLAG_FOLIAGE       4u
#define SVO_FLAG_FLUID         8u

#define SVO_MODE_CLOSEST       0
#define SVO_MODE_SHADOW        1

#define SVO_MAX_LEVELS         7

// Texels a block face spans on the atlas. Used to turn a ray footprint into a mip level.
#define SVO_FACE_TEXELS        16.0

struct SvoHit {
    float t;
    vec3 normal;
    vec3 albedo;
    float emission;
    uint flags;
    // Vanilla block and sky levels packed as sky<<4|block, for the lightmap lookup.
    uint light;
    // Vanilla per-face dimming.
    float shade;
    // Light that survived the trip for shadow rays; white when nothing was in the way.
    vec3 transmittance;
    // True when the hit came from a node average rather than an individual voxel.
    bool coarse;
};

SvoHit svoMiss() {
    SvoHit hit;
    hit.t = 0.0;
    hit.normal = vec3(0.0, 1.0, 0.0);
    hit.albedo = vec3(0.0);
    hit.emission = 0.0;
    hit.light = 0u;
    hit.shade = 1.0;
    hit.flags = 0u;
    hit.transmittance = vec3(1.0);
    hit.coarse = false;
    return hit;
}

vec3 svoUnpack565(uint packed) {
    return vec3(
        float((packed >> 11) & 0x1Fu) * (1.0 / 31.0),
        float((packed >> 5) & 0x3Fu) * (1.0 / 63.0),
        float(packed & 0x1Fu) * (1.0 / 31.0));
}

/** The 4-bit-per-channel tint a palette entry multiplies over its texture. */
vec3 svoTint(uvec2 entry) {
    return vec3(
        float((entry.y >> 20) & 0xFu),
        float((entry.y >> 16) & 0xFu),
        float((entry.y >> 12) & 0xFu)) * (1.0 / 15.0);
}

// -- brick sampling ------------------------------------------------------------------------------

bool svoVoxelSolid(uint base, ivec3 voxel) {
    uint bit = (uint(voxel.y) << 8) | (uint(voxel.z) << 4) | uint(voxel.x);
    uint word = svoBricks[base + SVO_OCCUPANCY_OFFSET + (bit >> 5u)];
    return (word & (1u << (bit & 31u))) != 0u;
}

bool svoCoarseSolid(uint base, ivec3 cell) {
    uint bit = (uint(cell.y) << 4) | (uint(cell.z) << 2) | uint(cell.x);
    uint word = svoBricks[base + SVO_COARSE_OFFSET + (bit >> 5u)];
    return (word & (1u << (bit & 31u))) != 0u;
}

/**
 * Position of a voxel among the brick's solid voxels, found by counting the ones that precede it.
 * The running counts stored every four occupancy words cap the walk at three extra bit counts.
 */
uint svoSolidOrdinal(uint base, ivec3 voxel) {
    uint bit = (uint(voxel.y) << 8) | (uint(voxel.z) << 4) | uint(voxel.x);
    uint word = bit >> 5u;
    uint group = word >> 2u;
    uint packed = svoBricks[base + SVO_PREFIX_OFFSET + (group >> 1u)];
    uint index = ((group & 1u) != 0u) ? (packed >> 16u) : (packed & 0xFFFFu);

    for (uint w = group << 2u; w < word; ++w) {
        index += uint(bitCount(svoBricks[base + SVO_OCCUPANCY_OFFSET + w]));
    }
    uint occupancy = svoBricks[base + SVO_OCCUPANCY_OFFSET + word];
    index += uint(bitCount(occupancy & ((1u << (bit & 31u)) - 1u)));
    return index;
}

/** A solid voxel's surface description and the vanilla light reaching it. */
struct SvoSurface {
    uvec2 entry;
    uint light;
};

SvoSurface svoReadSurface(uint base, ivec3 voxel) {
    uint header = svoBricks[base + SVO_PALETTE_HEADER_OFFSET];
    uint info = svoBricks[base + SVO_LIGHT_INFO_OFFSET];
    uint count = header & 0xFFFFu;
    uint bits = header >> 16u;
    uint lightBits = (info >> 16u) & 0xFFu;

    // The ordinal is what both the palette index and the light byte are addressed by, so it is
    // worth computing once even though either array may be absent.
    uint ordinal = (bits > 0u || lightBits > 0u) ? svoSolidOrdinal(base, voxel) : 0u;

    uint indexBase = base + SVO_PALETTE_OFFSET + count * 2u;
    uint slot = 0u;
    uint indexWords = 0u;
    if (bits > 0u) {
        uint perWord = 32u / bits;
        slot = (svoBricks[indexBase + ordinal / perWord] >> ((ordinal % perWord) * bits))
            & ((1u << bits) - 1u);
        indexWords = ((info & 0xFFFFu) * bits + 31u) / 32u;
    }

    SvoSurface surface;
    uint entry = base + SVO_PALETTE_OFFSET + slot * 2u;
    surface.entry = uvec2(svoBricks[entry], svoBricks[entry + 1u]);

    if (lightBits == 0u) {
        surface.light = (info >> 24u) & 0xFFu;
    } else {
        uint lightBase = indexBase + indexWords;
        surface.light = (svoBricks[lightBase + (ordinal >> 2u)] >> ((ordinal & 3u) * 8u)) & 0xFFu;
    }
    return surface;
}

/** Where on a block face the ray crossed, with v running downwards to match the atlas. */
vec2 svoFaceUv(vec3 local, vec3 normal) {
    if (abs(normal.x) > 0.5) {
        return vec2(normal.x > 0.0 ? 1.0 - local.z : local.z, 1.0 - local.y);
    }
    if (abs(normal.y) > 0.5) {
        return vec2(local.x, normal.y > 0.0 ? 1.0 - local.z : local.z);
    }
    return vec2(normal.z > 0.0 ? local.x : 1.0 - local.x, 1.0 - local.y);
}

/**
 * Samples the atlas for a face, at whatever mip the ray's footprint calls for.
 *
 * The tint only lands on the faces whose quad actually carried one, which is what keeps a grass
 * block's sides looking like dirt with a grassy fringe rather than a solid green cube.
 */
vec3 svoSurfaceAlbedo(uvec2 entry, vec3 normal, vec2 faceUv, float lod) {
    uint sprite;
    uint faceBit;
    if (normal.y > 0.5) {
        sprite = entry.x & 0xFFFu;
        faceBit = SVO_TINT_FACE_TOP;
    } else if (normal.y < -0.5) {
        sprite = entry.y & 0xFFFu;
        faceBit = SVO_TINT_FACE_BOTTOM;
    } else {
        sprite = (entry.x >> 12u) & 0xFFFu;
        faceBit = SVO_TINT_FACE_SIDE;
    }
    vec4 rect = svoSprites[sprite];
    vec2 uv = mix(rect.xy, rect.zw, clamp(faceUv, vec2(0.0), vec2(1.0)));
    vec3 albedo = textureLod(svoAtlas, uv, lod).rgb;

    uint tintedFaces = (entry.y >> 24u) & 0x7u;
    return (tintedFaces & faceBit) != 0u ? albedo * svoTint(entry) : albedo;
}

/** Vanilla's per-face dimming, which is most of what makes untextured voxels read as Minecraft. */
float svoFaceShade(vec3 normal) {
    if (normal.y > 0.5) {
        return 1.0;
    }
    if (normal.y < -0.5) {
        return 0.5;
    }
    return abs(normal.z) > 0.5 ? 0.8 : 0.6;
}

/** Minecraft's block-and-sky light table, sampled exactly the way the rasteriser would. */
vec3 svoLightmapColor(uint light) {
    vec2 uv = (vec2(float(light & 0xFu), float((light >> 4u) & 0xFu)) + 0.5) * (1.0 / 16.0);
    return texture(svoLightmap, uv).rgb;
}

// -- traversal -----------------------------------------------------------------------------------

struct SvoRay {
    vec3 origin;        // brick units, relative to the tree corner
    vec3 direction;     // brick units per block travelled
    vec3 inverse;       // reciprocal of direction, with zeroes nudged off the axis
    vec3 voxelDir;      // blocks per block, i.e. the original unit direction
    vec3 voxelInverse;  // reciprocal of voxelDir, guarded the same way
};

// An axis-aligned ray would divide by zero on the axes it never crosses. Nudging the component to
// a tiny value of the same sign pushes those slab crossings out to a huge finite t instead, which
// the min/max chains then simply ignore.
vec3 svoGuard(vec3 direction) {
    vec3 signs = vec3(
        direction.x >= 0.0 ? 1.0 : -1.0,
        direction.y >= 0.0 ? 1.0 : -1.0,
        direction.z >= 0.0 ? 1.0 : -1.0);
    return signs * max(abs(direction), vec3(1.0e-9));
}

float svoCubeExit(SvoRay ray, ivec3 cell, int level) {
    float size = float(1 << level);
    vec3 low = vec3(cell) * size;
    vec3 slabA = (low - ray.origin) * ray.inverse;
    vec3 slabB = (low + size - ray.origin) * ray.inverse;
    vec3 far = max(slabA, slabB);
    return min(min(far.x, far.y), far.z);
}

// Advancing by a hair before sampling keeps a ray that lands exactly on a face from picking the
// cell it just left. Scaled with t so it stays meaningful at the far edge of the root.
float svoEpsilon(float t) {
    return 4.0e-3 + abs(t) * 1.0e-6;
}

/** Which face of a unit voxel the ray entered through. */
vec3 svoFaceNormal(SvoRay ray, vec3 originVoxel, ivec3 voxel) {
    vec3 slabA = (vec3(voxel) - originVoxel) * ray.voxelInverse;
    vec3 slabB = (vec3(voxel) + 1.0 - originVoxel) * ray.voxelInverse;
    vec3 near = min(slabA, slabB);
    if (near.x >= near.y && near.x >= near.z) {
        return vec3(-sign(ray.voxelDir.x), 0.0, 0.0);
    }
    if (near.y >= near.z) {
        return vec3(0.0, -sign(ray.voxelDir.y), 0.0);
    }
    return vec3(0.0, 0.0, -sign(ray.voxelDir.z));
}

/**
 * Walks one brick. Returns true on an opaque hit; translucent voxels tint `hit.transmittance` and
 * the walk carries on, which is what gives stained glass and water coloured shadows.
 */
bool svoTraceBrick(
    SvoRay ray,
    uint base,
    ivec3 brickCell,
    float tEnter,
    float tExit,
    int mode,
    float lod,
    inout SvoHit hit
) {
    vec3 originVoxel = (ray.origin - vec3(brickCell)) * 16.0;
    float t = tEnter;

    for (int coarseStep = 0; coarseStep < 24; ++coarseStep) {
        vec3 point = originVoxel + (t + svoEpsilon(t)) * ray.voxelDir;
        ivec3 cell = ivec3(floor(point * 0.25));
        if (any(lessThan(cell, ivec3(0))) || any(greaterThan(cell, ivec3(3)))) {
            return false;
        }

        vec3 low = vec3(cell) * 4.0;
        vec3 slabA = (low - originVoxel) * ray.voxelInverse;
        vec3 slabB = (low + 4.0 - originVoxel) * ray.voxelInverse;
        vec3 far = max(slabA, slabB);
        float cellExit = min(min(far.x, far.y), far.z);

        if (svoCoarseSolid(base, cell)) {
            float fine = t;
            float fineLimit = min(cellExit, tExit);
            for (int voxelStep = 0; voxelStep < 16; ++voxelStep) {
                vec3 inner = originVoxel + (fine + svoEpsilon(fine)) * ray.voxelDir;
                ivec3 voxel = clamp(ivec3(floor(inner)), cell * 4, cell * 4 + 3);

                if (svoVoxelSolid(base, voxel)) {
                    SvoSurface surface = svoReadSurface(base, voxel);
                    uvec2 entry = surface.entry;
                    uint flags = (entry.x >> 28u) & 0xFu;

                    if (mode == SVO_MODE_SHADOW) {
                        // Shadow rays never sample the atlas: a tint is enough to colour what gets
                        // through, and skipping the fetch is most of why they are cheap.
                        if ((flags & SVO_FLAG_TRANSLUCENT) != 0u) {
                            hit.transmittance *= mix(vec3(1.0), svoTint(entry), 0.85) * 0.6;
                        } else if ((flags & SVO_FLAG_FOLIAGE) != 0u) {
                            // Leaves cover part of their cell, so they dim rather than block.
                            // Without this every tree casts a solid black cube.
                            hit.transmittance *= 0.45;
                        } else {
                            hit.t = fine;
                            return true;
                        }
                        if (dot(hit.transmittance, vec3(1.0)) < 0.02) {
                            hit.t = fine;
                            return true;
                        }
                    } else {
                        vec3 normal = svoFaceNormal(ray, originVoxel, voxel);
                        vec3 local = clamp(inner - vec3(voxel), vec3(0.0), vec3(1.0));
                        hit.t = fine;
                        hit.normal = normal;
                        hit.albedo = svoSurfaceAlbedo(entry, normal, svoFaceUv(local, normal), lod);
                        hit.emission = float((entry.x >> 24u) & 0xFu) * (1.0 / 15.0);
                        hit.flags = flags;
                        hit.light = surface.light;
                        hit.shade = svoFaceShade(normal);
                        hit.coarse = false;
                        return true;
                    }
                }

                vec3 voxelLow = vec3(voxel);
                vec3 voxelA = (voxelLow - originVoxel) * ray.voxelInverse;
                vec3 voxelB = (voxelLow + 1.0 - originVoxel) * ray.voxelInverse;
                vec3 voxelFar = max(voxelA, voxelB);
                float next = min(min(voxelFar.x, voxelFar.y), voxelFar.z);
                fine = max(next, fine + svoEpsilon(fine));
                if (fine >= fineLimit) {
                    break;
                }
            }
        }

        t = max(cellExit, t + svoEpsilon(t));
        if (t >= tExit) {
            return false;
        }
    }
    return false;
}

/**
 * Traces the octree.
 *
 * @param footprint how many blocks across the ray's cone is per block of distance. It drives both
 *                  the mip level the atlas is sampled at and the point past which the descent
 *                  settles for a node's average colour.
 * @return true when something was hit. In shadow mode a false result still leaves the accumulated
 *         transmittance in `hit`.
 */
bool svoTraceEx(
    vec3 origin,
    vec3 direction,
    float tMin,
    float tMax,
    float footprint,
    int levels,
    int maxSteps,
    int mode,
    out SvoHit hit
) {
    hit = svoMiss();

    SvoRay ray;
    ray.origin = (origin - svoTreeMin) * (1.0 / 16.0);
    ray.voxelDir = direction;
    ray.direction = direction * (1.0 / 16.0);
    ray.inverse = 1.0 / svoGuard(ray.direction);
    ray.voxelInverse = 1.0 / svoGuard(direction);

    float span = float(1 << levels);
    vec3 slabA = (vec3(0.0) - ray.origin) * ray.inverse;
    vec3 slabB = (vec3(span) - ray.origin) * ray.inverse;
    vec3 near = min(slabA, slabB);
    vec3 far = max(slabA, slabB);
    float tEnter = max(max(max(near.x, near.y), near.z), tMin);
    float tLeave = min(min(min(far.x, far.y), far.z), tMax);
    if (tEnter > tLeave) {
        return false;
    }

    uint stack[SVO_MAX_LEVELS + 1];
    int level = levels;
    ivec3 cell = ivec3(0);
    uint node = svoRoot;
    stack[level] = node;

    float t = tEnter;
    float nodeExit = tLeave;

    for (int step = 0; step < maxSteps; ++step) {
        int childLevel = level - 1;
        vec3 point = ray.origin + (t + svoEpsilon(t)) * ray.direction;
        ivec3 childCell = clamp(
            ivec3(floor(point / float(1 << childLevel))),
            cell * 2,
            cell * 2 + 1);
        int slot = (childCell.x & 1) | ((childCell.y & 1) << 1) | ((childCell.z & 1) << 2);

        uvec2 packed = svoNodes[node];
        uint childMask = (packed.x >> 8) & 0xFFu;
        float childExit = min(svoCubeExit(ray, childCell, childLevel), tLeave);

        if ((childMask & (1u << uint(slot))) != 0u) {
            uint child = packed.y + uint(bitCount(childMask & ((1u << uint(slot)) - 1u)));
            bool internal = ((packed.x >> uint(slot)) & 1u) != 0u;

            // Stop descending once the node is finer than the ray's footprint.
            float nodeSize = float(16 << childLevel);
            if (mode == SVO_MODE_CLOSEST && nodeSize < t * footprint * SVO_LOD_BIAS) {
                uint color = internal ? (svoNodes[child].x >> 16) : (svoNodes[child].y >> 16);
                hit.t = t;
                hit.normal = -direction;
                hit.albedo = svoUnpack565(color);
                hit.emission = 0.0;
                hit.flags = 0u;
                hit.coarse = true;
                return true;
            }

            if (!internal) {
                uint base = svoNodes[child].x;
                // One texel per footprint: log2 of how many atlas texels the cone covers.
                float lod = log2(max(1.0, t * footprint * SVO_FACE_TEXELS));
                if (svoTraceBrick(ray, base, childCell, t, childExit, mode, lod, hit)) {
                    return true;
                }
            } else {
                stack[childLevel] = child;
                level = childLevel;
                cell = childCell;
                node = child;
                nodeExit = childExit;
                continue;
            }
        }

        t = max(childExit, t + svoEpsilon(t));
        if (t >= tLeave) {
            return false;
        }
        while (level < levels && t >= nodeExit) {
            cell >>= 1;
            level += 1;
            node = stack[level];
            nodeExit = min(svoCubeExit(ray, cell, level), tLeave);
        }
        if (t >= nodeExit) {
            return false;
        }
    }
    return false;
}

/**
 * Reads the voxel containing a point, without tracing.
 *
 * The rasteriser has no idea what material it drew, but the octree does, so a plain point lookup
 * recovers the surface flags and emission for whatever is under a pixel.
 *
 * @return false when the point falls in air or outside the tree.
 */
bool svoSample(vec3 position, out SvoSurface found) {
    found.entry = uvec2(0u);
    found.light = 0u;
    vec3 local = (position - svoTreeMin) * (1.0 / 16.0);
    int levels = svoLevels;
    float span = float(1 << levels);
    if (any(lessThan(local, vec3(0.0))) || any(greaterThanEqual(local, vec3(span)))) {
        return false;
    }

    ivec3 brick = ivec3(floor(local));
    uint node = svoRoot;
    for (int level = levels; level >= 1; --level) {
        int shift = level - 1;
        int slot = ((brick.x >> shift) & 1) | (((brick.y >> shift) & 1) << 1) | (((brick.z >> shift) & 1) << 2);
        uvec2 packed = svoNodes[node];
        uint childMask = (packed.x >> 8) & 0xFFu;
        if ((childMask & (1u << uint(slot))) == 0u) {
            return false;
        }
        node = packed.y + uint(bitCount(childMask & ((1u << uint(slot)) - 1u)));
    }

    uint base = svoNodes[node].x;
    ivec3 voxel = clamp(ivec3(floor((local - vec3(brick)) * 16.0)), ivec3(0), ivec3(15));
    if (!svoVoxelSolid(base, voxel)) {
        return false;
    }
    found = svoReadSurface(base, voxel);
    return true;
}

bool svoTrace(vec3 origin, vec3 direction, float tMin, float tMax, float footprint, out SvoHit hit) {
    return svoTraceEx(origin, direction, tMin, tMax, footprint, svoLevels, svoMaxSteps, SVO_MODE_CLOSEST, hit);
}

/** Fraction of light that reaches `origin` from `direction`, one for a clear line of sight. */
vec3 svoVisibility(vec3 origin, vec3 direction, float tMax) {
    SvoHit hit;
    if (svoTraceEx(origin, direction, 0.0, tMax, 0.0, svoLevels, svoShadowSteps, SVO_MODE_SHADOW, hit)) {
        return vec3(0.0);
    }
    return hit.transmittance;
}

#endif
