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

#include "kalia:prelude.glsl"

void main() {
    vec4 color = vColor;
#ifdef TEXGEN
    color *= textureProj(kaliaBaseTexture, vUv0);
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
