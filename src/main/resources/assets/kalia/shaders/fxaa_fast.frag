layout(binding = 0) uniform sampler2D kaliaInput;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform KaliaPost {
    vec4 kaliaParams[6];
    vec2 kaliaInputTexel;
    vec2 kaliaOutputSize;
};

#define FXAA_SPAN_MAX 8.0
#define FXAA_REDUCE_MUL (1.0 / 8.0)
#define FXAA_REDUCE_MIN (1.0 / 128.0)

float luma(vec3 rgb) {
    return dot(rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 texel = kaliaInputTexel;

    vec4 colorM = texture(kaliaInput, uv);
    vec3 rgbNW = texture(kaliaInput, uv + vec2(-1.0, -1.0) * texel).rgb;
    vec3 rgbNE = texture(kaliaInput, uv + vec2(1.0, -1.0) * texel).rgb;
    vec3 rgbSW = texture(kaliaInput, uv + vec2(-1.0, 1.0) * texel).rgb;
    vec3 rgbSE = texture(kaliaInput, uv + vec2(1.0, 1.0) * texel).rgb;

    float lumaNW = luma(rgbNW);
    float lumaNE = luma(rgbNE);
    float lumaSW = luma(rgbSW);
    float lumaSE = luma(rgbSE);
    float lumaM = luma(colorM.rgb);

    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    vec2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y = ((lumaNW + lumaSW) - (lumaNE + lumaSE));

    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * FXAA_REDUCE_MUL), FXAA_REDUCE_MIN);
    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);

    dir = clamp(dir * rcpDirMin, vec2(-FXAA_SPAN_MAX), vec2(FXAA_SPAN_MAX)) * texel;

    vec3 rgbA = 0.5 * (
        texture(kaliaInput, uv + dir * (1.0 / 3.0 - 0.5)).rgb +
        texture(kaliaInput, uv + dir * (2.0 / 3.0 - 0.5)).rgb);

    vec3 rgbB = rgbA * 0.5 + 0.25 * (
        texture(kaliaInput, uv + dir * -0.5).rgb +
        texture(kaliaInput, uv + dir * 0.5).rgb);

    float lumaB = luma(rgbB);

    vec3 rgbResult = (lumaB < lumaMin || lumaB > lumaMax) ? rgbA : rgbB;

    fragColor = vec4(rgbResult, colorM.a);
}
