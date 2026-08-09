layout(location = 0) in vec4 vColor;
layout(location = 1) in vec2 vUv0;
layout(location = 2) in vec2 vUv1;
layout(location = 3) in vec3 vNormal;
layout(location = 4) in float vViewDistance;
layout(location = 5) in vec4 vOverlay;
layout(location = 6) in vec4 vMisc;
layout(location = 0) out vec4 fragColor;

#ifdef HAS_TEXTURE
#ifdef TEXTURE_ARRAY
layout(binding = 0) uniform sampler2DArray kaliaBaseTexture;
#else
layout(binding = 0) uniform sampler2D kaliaBaseTexture;
#endif
#endif
layout(binding = 2) uniform sampler2D kaliaLightmapTexture;

#include "kalia:prelude.glsl"

void main() {
    vec4 color = vColor;
#ifdef HAS_TEXTURE
#ifdef TEXTURE_ARRAY
    color *= texture(kaliaBaseTexture, vec3(vUv0, vMisc.w));
#else
    color *= texture(kaliaBaseTexture, vUv0);
#endif
#endif
    color.rgb = mix(color.rgb, vOverlay.rgb, vOverlay.a);
#ifdef HAS_NORMAL
    if (vMisc.y > 0.5) {
        color.rgb = kaliaDiffuse(color.rgb, vNormal);
    }
#endif
#ifdef HAS_LIGHTMAP
    color.rgb *= texture(kaliaLightmapTexture, vUv1).rgb;
#else
    if (vMisc.x > 0.5) {
        color.rgb *= texture(kaliaLightmapTexture, vUv1).rgb;
    }
#endif
    if (color.a <= vMisc.z) {
        discard;
    }
    color.rgb = kaliaApplyFog(color.rgb, vViewDistance);
    fragColor = color;
}
