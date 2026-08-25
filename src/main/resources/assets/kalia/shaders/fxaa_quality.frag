layout(binding = 0) uniform sampler2D kaliaInput;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform KaliaPost {
    vec4 kaliaParams[6];
    vec2 kaliaInputTexel;
    vec2 kaliaOutputSize;
};

#define EDGE_THRESHOLD_MIN 0.0312
#define EDGE_THRESHOLD_MAX 0.125
#define SUBPIXEL_QUALITY 0.75
#define SEARCH_STEPS 6
#define SEARCH_STEP_SIZE 1.5

float luma(vec3 rgb) {
    return dot(rgb, vec3(0.299, 0.587, 0.114));
}

vec3 sampleAt(vec2 offsetTexels) {
    return texture(kaliaInput, uv + offsetTexels * kaliaInputTexel).rgb;
}

void main() {
    vec3 colorCenter = sampleAt(vec2(0.0));
    float lumaCenter = luma(colorCenter);

    float lumaDown = luma(sampleAt(vec2(0.0, 1.0)));
    float lumaUp = luma(sampleAt(vec2(0.0, -1.0)));
    float lumaLeft = luma(sampleAt(vec2(-1.0, 0.0)));
    float lumaRight = luma(sampleAt(vec2(1.0, 0.0)));

    float lumaMin = min(lumaCenter, min(min(lumaDown, lumaUp), min(lumaLeft, lumaRight)));
    float lumaMax = max(lumaCenter, max(max(lumaDown, lumaUp), max(lumaLeft, lumaRight)));
    float lumaRange = lumaMax - lumaMin;

    if (lumaRange < max(EDGE_THRESHOLD_MIN, lumaMax * EDGE_THRESHOLD_MAX)) {
        fragColor = vec4(colorCenter, 1.0);
        return;
    }

    float lumaDownLeft = luma(sampleAt(vec2(-1.0, 1.0)));
    float lumaUpRight = luma(sampleAt(vec2(1.0, -1.0)));
    float lumaUpLeft = luma(sampleAt(vec2(-1.0, -1.0)));
    float lumaDownRight = luma(sampleAt(vec2(1.0, 1.0)));

    float lumaDownUp = lumaDown + lumaUp;
    float lumaLeftRight = lumaLeft + lumaRight;

    float lumaLeftCorners = lumaDownLeft + lumaUpLeft;
    float lumaDownCorners = lumaDownLeft + lumaDownRight;
    float lumaRightCorners = lumaDownRight + lumaUpRight;
    float lumaUpCorners = lumaUpRight + lumaUpLeft;

    float edgeHorizontal =
        abs(-2.0 * lumaLeft + lumaLeftCorners) +
        abs(-2.0 * lumaCenter + lumaDownUp) * 2.0 +
        abs(-2.0 * lumaRight + lumaRightCorners);
    float edgeVertical =
        abs(-2.0 * lumaUp + lumaUpCorners) +
        abs(-2.0 * lumaCenter + lumaLeftRight) * 2.0 +
        abs(-2.0 * lumaDown + lumaDownCorners);

    bool isHorizontal = edgeHorizontal >= edgeVertical;

    float luma1 = isHorizontal ? lumaDown : lumaLeft;
    float luma2 = isHorizontal ? lumaUp : lumaRight;
    float gradient1 = luma1 - lumaCenter;
    float gradient2 = luma2 - lumaCenter;
    bool is1Steepest = abs(gradient1) >= abs(gradient2);
    float gradientScaled = 0.25 * max(abs(gradient1), abs(gradient2));

    float stepLength = isHorizontal ? kaliaInputTexel.y : kaliaInputTexel.x;
    float lumaLocalAverage;
    if (is1Steepest) {
        stepLength = -stepLength;
        lumaLocalAverage = 0.5 * (luma1 + lumaCenter);
    } else {
        lumaLocalAverage = 0.5 * (luma2 + lumaCenter);
    }

    vec2 currentUv = uv;
    if (isHorizontal) {
        currentUv.y += stepLength * 0.5;
    } else {
        currentUv.x += stepLength * 0.5;
    }

    vec2 offset = isHorizontal ? vec2(kaliaInputTexel.x, 0.0) : vec2(0.0, kaliaInputTexel.y);

    vec2 uv1 = currentUv - offset;
    vec2 uv2 = currentUv + offset;

    float lumaEnd1 = luma(texture(kaliaInput, uv1).rgb) - lumaLocalAverage;
    float lumaEnd2 = luma(texture(kaliaInput, uv2).rgb) - lumaLocalAverage;

    bool reached1 = abs(lumaEnd1) >= gradientScaled;
    bool reached2 = abs(lumaEnd2) >= gradientScaled;
    bool reachedBoth = reached1 && reached2;

    if (!reached1) uv1 -= offset;
    if (!reached2) uv2 += offset;

    if (!reachedBoth) {
        for (int i = 0; i < SEARCH_STEPS; i++) {
            if (!reached1) {
                lumaEnd1 = luma(texture(kaliaInput, uv1).rgb) - lumaLocalAverage;
                reached1 = abs(lumaEnd1) >= gradientScaled;
                if (!reached1) uv1 -= offset * SEARCH_STEP_SIZE;
            }
            if (!reached2) {
                lumaEnd2 = luma(texture(kaliaInput, uv2).rgb) - lumaLocalAverage;
                reached2 = abs(lumaEnd2) >= gradientScaled;
                if (!reached2) uv2 += offset * SEARCH_STEP_SIZE;
            }
            if (reached1 && reached2) break;
        }
    }

    float distance1 = isHorizontal ? (uv.x - uv1.x) : (uv.y - uv1.y);
    float distance2 = isHorizontal ? (uv2.x - uv.x) : (uv2.y - uv.y);

    bool isDirection1 = distance1 < distance2;
    float distanceFinal = min(distance1, distance2);
    float edgeThickness = distance1 + distance2;
    float pixelOffset = -distanceFinal / edgeThickness + 0.5;

    bool isLumaCenterSmaller = lumaCenter < lumaLocalAverage;
    bool correctVariation = ((isDirection1 ? lumaEnd1 : lumaEnd2) < 0.0) != isLumaCenterSmaller;

    float finalOffset = correctVariation ? pixelOffset : 0.0;

    float lumaAverage = (1.0 / 12.0) * (2.0 * (lumaDownUp + lumaLeftRight) + lumaLeftCorners + lumaRightCorners);
    float subPixelOffset1 = clamp(abs(lumaAverage - lumaCenter) / max(lumaRange, 1e-5), 0.0, 1.0);
    float subPixelOffset2 = (-2.0 * subPixelOffset1 + 3.0) * subPixelOffset1 * subPixelOffset1;
    float subPixelOffsetFinal = subPixelOffset2 * subPixelOffset2 * SUBPIXEL_QUALITY;

    finalOffset = max(finalOffset, subPixelOffsetFinal);

    vec2 finalUv = uv;
    if (isHorizontal) {
        finalUv.y += finalOffset * stepLength;
    } else {
        finalUv.x += finalOffset * stepLength;
    }

    fragColor = vec4(texture(kaliaInput, finalUv).rgb, 1.0);
}
