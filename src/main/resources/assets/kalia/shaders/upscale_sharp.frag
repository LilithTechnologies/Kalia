layout(binding = 0) uniform sampler2D kaliaInput;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform KaliaPost {
    vec4 kaliaParams[6];
    vec2 kaliaInputTexel;
    vec2 kaliaOutputSize;
};

#define SHARPNESS kaliaParams[0].x

void main() {
    vec2 texel = kaliaInputTexel;

    vec3 center = texture(kaliaInput, uv).rgb;
    vec3 n = texture(kaliaInput, uv + vec2(0.0, -texel.y)).rgb;
    vec3 s = texture(kaliaInput, uv + vec2(0.0, texel.y)).rgb;
    vec3 e = texture(kaliaInput, uv + vec2(texel.x, 0.0)).rgb;
    vec3 w = texture(kaliaInput, uv + vec2(-texel.x, 0.0)).rgb;

    vec3 mn = min(center, min(min(n, s), min(e, w)));
    vec3 mx = max(center, max(max(n, s), max(e, w)));

    vec3 blur = (n + s + e + w) * 0.25;
    vec3 sharpened = center + (center - blur) * SHARPNESS;

    fragColor = vec4(clamp(sharpened, mn, mx), 1.0);
}
