#version 450

#import <sodium:include/fog.glsl>
#import <sodium:include/chunk_vertex.glsl>
#import <sodium:include/chunk_scene.glsl>
#import <sodium:include/chunk_push_constants.glsl>
#import <sodium:include/chunk_material.glsl>

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec2 v_TexCoord;

#if defined(USE_FOG) && defined(CHUNK_FADE_IN_DURATION_MS) && CHUNK_FADE_IN_DURATION_MS > 0
layout(location = 2) out float v_ChunkAgeMs;
#endif

layout(location = 3) out float v_MaterialMipBias;
#ifdef USE_FRAGMENT_DISCARD
layout(location = 4) out float v_MaterialAlphaCutoff;
#endif

#if defined(USE_FOG_POSTMODERN)
layout(location = 5) out float v_SphericalFragDistance;
layout(location = 6) out float v_CylindricalFragDistance;
#elif defined(USE_FOG)
layout(location = 7) out float v_FragDistance;
#endif

#ifndef CELERITAS_NO_LIGHTMAP
layout(set = 0, binding = 1) uniform sampler2D u_LightTex; // The light map texture sampler

vec4 _sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}
#endif

#if defined(USE_FOG) && defined(CHUNK_FADE_IN_DURATION_MS) && CHUNK_FADE_IN_DURATION_MS > 0
layout(std140, set = 0, binding = 3) uniform ChunkRegionAges {
    vec4 celeritas_ChunkAgesPacked[REGION_SIZE / 4];
};
#define celeritas_ChunkAges(i) celeritas_ChunkAgesPacked[(i) >> 2][int((i) & 3u)]
#endif

void main() {
    _vert_init();

    // Transform the chunk-local vertex position into world model space
    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;

#if defined(USE_FOG_POSTMODERN)
    v_SphericalFragDistance = getFragDistance(FOG_SHAPE_SPHERICAL, position);
    v_CylindricalFragDistance = getFragDistance(FOG_SHAPE_CYLINDRICAL, position);
#elif defined(USE_FOG)
    v_FragDistance = getFragDistance(u_FogShape, position);
#endif

    // Transform the vertex position into model-view-projection space
    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    // Add the light color to the vertex color, and pass the texture coordinates to the fragment shader
#ifdef CELERITAS_NO_LIGHTMAP
    v_Color = _vert_color;
#else
    v_Color = _vert_color * _sample_lightmap(u_LightTex, _vert_tex_light_coord);
#endif
    v_TexCoord = _vert_tex_diffuse_coord;

    v_MaterialMipBias = _material_mip_bias(_material_params);
#ifdef USE_FRAGMENT_DISCARD
    v_MaterialAlphaCutoff = _material_alpha_cutoff(_material_params);
#endif
#if defined(USE_FOG) && defined(CHUNK_FADE_IN_DURATION_MS) && CHUNK_FADE_IN_DURATION_MS > 0
    v_ChunkAgeMs = celeritas_ChunkAges(_draw_id);
#endif
}
