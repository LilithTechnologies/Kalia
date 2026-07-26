layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUvSelect;

layout(location = 2) in vec3 instOrigin;
layout(location = 3) in vec2 instSize;
layout(location = 4) in vec4 instRow0;
layout(location = 5) in vec4 instRow1;
layout(location = 6) in vec4 instRow2;
layout(location = 7) in vec4 instUvRect;
layout(location = 8) in float instAlpha;

layout(location = 0) out vec2 vUv;
layout(location = 1) out float vAlpha;

#include "kalia:prelude.glsl"

void main() {
    vec3 worldPosition = instOrigin + vec3(aPosition.x * instSize.x, 0.0, aPosition.z * instSize.y);
    vec4 model = vec4(worldPosition, 1.0);
    vec3 eye = vec3(dot(instRow0, model), dot(instRow1, model), dot(instRow2, model));
    gl_Position = kaliaProjection * vec4(eye, 1.0);

    vUv = vec2(mix(instUvRect.x, instUvRect.y, aUvSelect.x), mix(instUvRect.z, instUvRect.w, aUvSelect.y));
    vAlpha = instAlpha;
}
