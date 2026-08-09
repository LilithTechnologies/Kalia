layout(location = 0) in vec2 vUv;
layout(location = 1) in float vAlpha;
layout(location = 0) out vec4 fragColor;

layout(binding = 0) uniform sampler2D kaliaBaseTexture;

void main() {
    vec4 sampled = texture(kaliaBaseTexture, vUv);
    fragColor = vec4(sampled.rgb, sampled.a * vAlpha);
}
