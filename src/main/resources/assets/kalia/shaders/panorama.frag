layout(binding = 0) uniform samplerCube kaliaBaseTexture;

layout(location = 0) in vec3 vUv;
layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = texture(kaliaBaseTexture, vUv);
}