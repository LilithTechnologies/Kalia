#version 450

#import <sodium:include/fog.glsl>
#import <sodium:include/chunk_scene.glsl>

// Locations must match block_layer_opaque.vsh's matching `out` declarations exactly.
layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;

#if defined(USE_FOG) && defined(CHUNK_FADE_IN_DURATION_MS) && CHUNK_FADE_IN_DURATION_MS > 0
layout(location = 2) in float v_ChunkAgeMs;
#endif

layout(location = 3) in float v_MaterialMipBias;
#ifdef USE_FRAGMENT_DISCARD
layout(location = 4) in float v_MaterialAlphaCutoff;
#endif

#if defined(USE_FOG_POSTMODERN)
layout(location = 5) in float v_SphericalFragDistance;
layout(location = 6) in float v_CylindricalFragDistance;
#elif defined(USE_FOG)
layout(location = 7) in float v_FragDistance;
#endif

layout(set = 0, binding = 0) uniform sampler2D u_BlockTex; // The block texture

#ifdef KALIA_GBUFFER
layout(location = 8) in vec2 v_LightCoord;
layout(location = 9) in vec3 v_WorldPos;

// Albedo and the block light coordinate, which stands in for emission.
layout(location = 0) out vec4 fragAlbedo;
// Geometric normal and the sky light coordinate, which says how exposed to the
// sky the surface was baked as.
layout(location = 1) out vec4 fragSurface;
#else
layout(location = 0) out vec4 fragColor; // The output fragment for the color framebuffer
#endif

void main() {
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    vec4 m_color = v_Color;

#ifdef KALIA_GBUFFER
    // Block faces are planar, so the derivatives of the interpolated position give
    // the geometric normal exactly rather than approximately, and cost nothing.
    vec3 normal = normalize(cross(dFdx(v_WorldPos), dFdy(v_WorldPos)));
    // v_WorldPos is relative to the camera, so the eye is at the origin and any
    // visible face has to point back towards it.
    if (dot(normal, v_WorldPos) > 0.0) {
        normal = -normal;
    }

    // Only the biome tint goes into albedo. The alpha of the per-vertex colour is
    // Minecraft's baked smooth-lighting shade, and occlusion is lighting rather
    // than surface colour: folding it in here would darken every block corner a
    // second time on top of the occlusion the tracer works out for real, which
    // reads as an outline around everything.
    vec3 albedo = diffuseColor.rgb * m_color.rgb;

    fragAlbedo = vec4(albedo, clamp(v_LightCoord.x / 255.0, 0.0, 1.0));
    fragSurface = vec4(normal, clamp(v_LightCoord.y / 255.0, 0.0, 1.0));
#else

#ifdef USE_VANILLA_COLOR_FORMAT
    // Apply per-vertex color. AO shade is applied ahead of time on the CPU.
    diffuseColor *= m_color;
#else
    // Apply per-vertex color
    diffuseColor.rgb *= m_color.rgb;

    // Apply ambient occlusion "shade"
    diffuseColor.rgb *= m_color.a;
#endif

#ifdef USE_FOG
#if defined(CHUNK_FADE_IN_DURATION_MS) && CHUNK_FADE_IN_DURATION_MS > 0
    // Make chunk fade in over a short duration
    diffuseColor = vec4(mix(u_FogColor.rgb, diffuseColor.rgb, (clamp(v_ChunkAgeMs, 0, CHUNK_FADE_IN_DURATION_MS) / CHUNK_FADE_IN_DURATION_MS)), diffuseColor.a);
#endif

#ifdef USE_FOG_POSTMODERN
    float fogValue = max(_linearFogValue(v_CylindricalFragDistance, u_RenderDistFogStart, u_RenderDistFogEnd),
                         _linearFogValue(v_SphericalFragDistance, u_EnvFogStart, u_EnvFogEnd));

    fragColor = vec4(mix(diffuseColor.rgb, u_FogColor.rgb, fogValue * u_FogColor.a), diffuseColor.a);
#elif defined(USE_FOG_EXP2)
    fragColor = _exp2Fog(diffuseColor, v_FragDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_SMOOTH)
    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
#endif
#else
    fragColor = diffuseColor;
#endif

#endif // KALIA_GBUFFER
}
