#ifdef BINDLESS
#extension GL_EXT_nonuniform_qualifier : require
#endif

layout(location = 0) in vec4 vColor;
layout(location = 1) in vec2 vUv;
layout(location = 2) in float vViewDistance;
layout(location = 3) in float vAlphaCutout;
#ifdef BINDLESS
layout(location = 4) flat in uint vTexture;
#endif
layout(location = 0) out vec4 fragColor;

#ifdef BINDLESS
layout(set = 1, binding = 0) uniform sampler2D kaliaTextures[];
#else
layout(binding = 0) uniform sampler2D kaliaBaseTexture;
#endif

#include "kalia:prelude.glsl"

void main() {
#ifdef BINDLESS
    vec4 color = vColor * texture(kaliaTextures[nonuniformEXT(vTexture)], vUv);
#else
    vec4 color = vColor * texture(kaliaBaseTexture, vUv);
#endif
    if (color.a <= vAlphaCutout) {
        discard;
    }
    color.rgb = kaliaApplyFog(color.rgb, vViewDistance);
    fragColor = color;
}
