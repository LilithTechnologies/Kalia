layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec4 aColor;
layout(location = 2) in vec2 aUv0;
layout(location = 4) in vec4 aNormal;

layout(location = 5) in vec4 instRow0;
layout(location = 6) in vec4 instRow1;
layout(location = 7) in vec4 instRow2;
layout(location = 10) in vec4 instLight;

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec2 vUv;
layout(location = 2) out vec3 vNormal;
layout(location = 3) out float vViewDistance;
layout(location = 4) out vec4 vMisc;
layout(location = 5) out vec2 vLightUv;

#include "kalia:prelude.glsl"

void main() {
    vec4 model = vec4(aPosition, 1.0);
    vec3 eye = vec3(dot(instRow0, model), dot(instRow1, model), dot(instRow2, model));
    vViewDistance = abs(eye.z);
    gl_Position = kaliaProjection * vec4(eye, 1.0);

    vUv = aUv0;
    vColor = aColor;
    vNormal = normalize(vec3(
        dot(instRow0.xyz, aNormal.xyz),
        dot(instRow1.xyz, aNormal.xyz),
        dot(instRow2.xyz, aNormal.xyz)
    ));

    int flags = int(instLight.z + 0.5);
    vMisc = vec4(
        (flags & 1) != 0 ? 1.0 : 0.0,
        (flags & 2) != 0 ? 1.0 : 0.0,
        instLight.w,
        0.0);
    vLightUv = instLight.xy;
}
