layout(location = 0) in vec4 vColor;
layout(location = 1) in vec2 vUv;
layout(location = 2) in vec3 vNormal;
layout(location = 3) in float vViewDistance;
layout(location = 4) in vec4 vMisc;
layout(location = 5) in vec2 vLightUv;
layout(location = 0) out vec4 fragColor;

layout(binding = 0) uniform sampler2D kaliaBaseTexture;
layout(binding = 2) uniform sampler2D kaliaLightmapTexture;

#include "kalia:prelude.glsl"

void main() {
    vec4 color = vColor * texture(kaliaBaseTexture, vUv);
    if (color.a <= vMisc.z) {
        discard;
    }
    if (vMisc.y > 0.5) {
        color.rgb = kaliaDiffuse(color.rgb, vNormal);
    }
    if (vMisc.x > 0.5) {
        color.rgb *= texture(kaliaLightmapTexture, vLightUv).rgb;
    }
    color.rgb = kaliaApplyFog(color.rgb, vViewDistance);
    fragColor = color;
}
