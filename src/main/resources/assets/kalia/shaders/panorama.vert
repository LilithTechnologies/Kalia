layout(location = 0) in vec3 inPosition;
layout(location = 0) out vec3 vUv;

#include "kalia:prelude.glsl"

void main() {
    gl_Position = kaliaProjection * kaliaModelView * vec4(inPosition, 1.0);
    gl_Position.y = -gl_Position.y;
    vUv = inPosition;
}