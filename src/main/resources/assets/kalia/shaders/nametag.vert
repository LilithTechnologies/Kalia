layout(location = 0) in vec2 aPosition;

layout(location = 1) in vec4 instRow0;
layout(location = 2) in vec4 instRow1;
layout(location = 3) in vec4 instRow2;
layout(location = 4) in vec4 instQuad;
layout(location = 5) in vec4 instUv;
layout(location = 6) in vec4 instColor;
layout(location = 7) in float instAlphaCutout;
#ifdef BINDLESS
layout(location = 8) in uint instTexture;
#endif

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec2 vUv;
layout(location = 2) out float vViewDistance;
layout(location = 3) out float vAlphaCutout;
#ifdef BINDLESS
layout(location = 4) flat out uint vTexture;
#endif

#include "kalia:prelude.glsl"

void main() {
    vec2 local = mix(instQuad.xy, instQuad.zw, aPosition);
    vec4 model = vec4(local, 0.0, 1.0);
    vec3 eye = vec3(dot(instRow0, model), dot(instRow1, model), dot(instRow2, model));
    vViewDistance = abs(eye.z);
    gl_Position = kaliaProjection * vec4(eye, 1.0);

    vUv = mix(instUv.xy, instUv.zw, aPosition);
    vColor = instColor;
    vAlphaCutout = instAlphaCutout;
#ifdef BINDLESS
    vTexture = instTexture;
#endif
}
