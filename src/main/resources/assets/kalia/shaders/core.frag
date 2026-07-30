layout(location = 0) in vec4 vColor;
#ifdef TEXGEN
layout(location = 1) in vec4 vUv0;
#else
layout(location = 1) in vec2 vUv0;
#endif
layout(location = 2) in vec2 vUv1;
layout(location = 3) in vec3 vNormal;
layout(location = 4) in float vViewDistance;
layout(location = 0) out vec4 fragColor;

#if defined(HAS_TEXTURE) || defined(TEXGEN)
layout(binding = 0) uniform sampler2D kaliaBaseTexture;
#endif
layout(binding = 2) uniform sampler2D kaliaLightmapTexture;

#ifdef TEXTURE_SLOTS
layout(location = 5) flat in uint vTexSlot;
layout(binding = 4) uniform sampler2D kaliaSlot1;
layout(binding = 5) uniform sampler2D kaliaSlot2;
layout(binding = 6) uniform sampler2D kaliaSlot3;
layout(binding = 7) uniform sampler2D kaliaSlot4;
layout(binding = 8) uniform sampler2D kaliaSlot5;
layout(binding = 9) uniform sampler2D kaliaSlot6;
layout(binding = 10) uniform sampler2D kaliaSlot7;

vec4 kaliaSampleSlot(uint slot, vec2 uv) {
    if (slot == 1u) return texture(kaliaSlot1, uv);
    if (slot == 2u) return texture(kaliaSlot2, uv);
    if (slot == 3u) return texture(kaliaSlot3, uv);
    if (slot == 4u) return texture(kaliaSlot4, uv);
    if (slot == 5u) return texture(kaliaSlot5, uv);
    if (slot == 6u) return texture(kaliaSlot6, uv);
    if (slot == 7u) return texture(kaliaSlot7, uv);
    return texture(kaliaBaseTexture, uv);
}
#endif

#include "kalia:prelude.glsl"

void main() {
    vec4 color = vColor;
#ifdef TEXGEN
    color *= textureProj(kaliaBaseTexture, vUv0);
#elif defined(TEXTURE_SLOTS)
    color *= kaliaSampleSlot(vTexSlot, vUv0);
#elif defined(HAS_TEXTURE)
    color *= texture(kaliaBaseTexture, vUv0);
#endif
    color.rgb = mix(color.rgb, kaliaOverlayColor.rgb, kaliaOverlayColor.a);
#ifdef HAS_NORMAL
    if (KALIA_LIGHTING_ENABLED) {
        color.rgb = kaliaDiffuse(color.rgb, vNormal);
    }
#endif
#ifdef HAS_LIGHTMAP
    color.rgb *= texture(kaliaLightmapTexture, vUv1).rgb;
#elif defined(HAS_TEXTURE)
    if (KALIA_LIGHTMAP_ENABLED) {
        color.rgb *= texture(kaliaLightmapTexture, KALIA_LIGHTMAP_COORDS).rgb;
    }
#endif
    if (color.a <= KALIA_ALPHA_CUTOUT) {
        discard;
    }
    color.rgb = kaliaApplyFog(color.rgb, vViewDistance);
    fragColor = color;
}
