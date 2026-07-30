layout(location = 0) in vec4 vColor;
layout(location = 1) in vec2 vUv;
layout(location = 2) in float vViewDistance;
layout(location = 3) in float vAlphaCutout;
layout(location = 4) in vec2 vLightUv;
layout(location = 0) out vec4 fragColor;

layout(binding = 0) uniform sampler2D kaliaBaseTexture;
layout(binding = 2) uniform sampler2D kaliaLightmapTexture;

#include "kalia:prelude.glsl"

void main() {
    vec4 color = vColor * texture(kaliaBaseTexture, vUv);
    color.rgb *= texture(kaliaLightmapTexture, vLightUv).rgb;
    if (color.a <= vAlphaCutout) {
        discard;
    }
    color.rgb = kaliaApplyFog(color.rgb, vViewDistance);
    fragColor = color;
}
