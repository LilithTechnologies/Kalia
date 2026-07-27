layout(location = 0) in vec2 aPosition;

layout(location = 1) in vec3 instCenter;
layout(location = 2) in float instHalf;
layout(location = 3) in vec4 instUv;
layout(location = 4) in vec4 instColor;
layout(location = 5) in vec2 instLightUv;
layout(location = 6) in float instAlphaCutout;

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec2 vUv;
layout(location = 2) out float vViewDistance;
layout(location = 3) out float vAlphaCutout;
layout(location = 4) out vec2 vLightUv;

#include "kalia:prelude.glsl"

void main() {
    vec3 eye = instCenter + vec3(aPosition * instHalf, 0.0);
    vViewDistance = abs(eye.z);
    gl_Position = kaliaProjection * vec4(eye, 1.0);

    vec2 t = aPosition + 0.5;
    vUv = vec2(mix(instUv.z, instUv.x, t.x), mix(instUv.w, instUv.y, t.y));
    vColor = instColor;
    vAlphaCutout = instAlphaCutout;
    vLightUv = instLightUv;
}
