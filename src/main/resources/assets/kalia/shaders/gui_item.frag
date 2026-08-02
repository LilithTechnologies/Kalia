layout(location = 0) in vec4 vColor;
layout(location = 1) in vec2 vUv;

layout(location = 0) out vec4 fragColor;

layout(binding = 0) uniform sampler2D guiItemAtlas;

void main() {
    vec4 color = texture(guiItemAtlas, vUv) * vColor;

    if (color.a < 0.1) {
        discard;
    }

    fragColor = color;
}
