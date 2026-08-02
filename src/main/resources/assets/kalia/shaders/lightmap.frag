layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform LightMap {
    vec4 brightnessTable[4];

    float skyBrightness;
    float flicker;
    float gamma;
    float skyDarkness;
    float nightVision;
    float lightning;

    int dimensionType;
};

float brightness(int level) {
    return brightnessTable[level >> 2][level & 3];
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);

    float f = skyBrightness;
    float g = f * 0.95 + 0.05;

    float sky = brightness(texel.y);
    float h = lightning > 0.5 ? sky : sky * g;
    float j = brightness(texel.x) * (flicker * 0.1 + 1.5);

    float k = h * (f * 0.65 + 0.35);
    float o = j * ((j * 0.6 + 0.4) * 0.6 + 0.4);
    float p = j * (j * j * 0.6 + 0.4);

    vec3 colour = vec3(k + j, k + o, h + p) * 0.96 + 0.03;

    if (skyDarkness > 0.0) {
        colour = mix(colour, colour * vec3(0.7, 0.6, 0.6), skyDarkness);
    }

    if (dimensionType == 1) {
        colour = vec3(0.22 + j * 0.75, 0.28 + o * 0.75, 0.25 + p * 0.75);
    }

    if (nightVision > 0.0) {
        float scale = min(min(1.0 / colour.r, 1.0 / colour.g), 1.0 / colour.b);
        colour = mix(colour, colour * scale, nightVision);
    }

    colour = min(colour, vec3(1.0));

    vec3 inverted = 1.0 - colour;
    colour = mix(colour, 1.0 - inverted * inverted * inverted * inverted, gamma);

    fragColor = vec4(clamp(colour * 0.96 + 0.03, 0.0, 1.0), 1.0);
}
