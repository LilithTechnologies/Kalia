layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUv;

layout(location = 5) in ivec4 instCenter;
layout(location = 6) in uvec4 instSize;

#include "kalia:prelude.glsl"

const float KALIA_OCCLUSION_CENTER_SCALE = 1.0 / 16.0;
const float KALIA_OCCLUSION_SIZE_SCALE = 1.0 / 8.0;

void main() {
    vec3 center = vec3(instCenter.xyz) * KALIA_OCCLUSION_CENTER_SCALE;
    vec3 size = vec3(instSize.xyz) * KALIA_OCCLUSION_SIZE_SCALE;
    vec3 local = center + aPosition * size;
    gl_Position = kaliaProjection * (kaliaModelView * vec4(local, 1.0));
}
