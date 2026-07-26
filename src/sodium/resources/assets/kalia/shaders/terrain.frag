#version 450

// Ported from Graphite's terrain.slang (+ helpers.chunk_material, helpers.fog).
// Push-constant layout must stay byte-identical to terrain.vert and DefaultShaderInterface.

layout(location = 0) in vec4 v_color;
layout(location = 1) in vec2 v_uv;
layout(location = 2) in vec2 v_fragDistance;
layout(location = 3) flat in uint v_material;

layout(location = 0) out vec4 out_color;

layout(push_constant) uniform PushConstants {
    vec3 regionOffset;     //   0
    int padding;           //  12
    mat4 modelViewMatrix;  //  16
    mat4 projectionMatrix; //  80
    vec2 texCoordShrink;   // 144
    vec2 fogColorRG;       // 152
    vec2 fogColorB_Mode;   // 160 (blue, then 0 = off / 1 + FogMode ordinal)
    vec2 fogRange;         // 168 (linear start, end)
    vec2 fogDensity_pad;   // 176
} pc;

layout(set = 0, binding = 0) uniform sampler2D blockAtlas;

#ifndef IS_CUTOUT
#define IS_CUTOUT 0
#endif
const bool isCutout = IS_CUTOUT != 0;

const uint MATERIAL_USE_MIP_OFFSET = 0u;

#define FOG_MODE     (int(pc.fogColorB_Mode.y + 0.5) - 1)
#define FOG_EXP      0
#define FOG_EXP2     1
#define FOG_DENSITY  pc.fogDensity_pad.x

// The OpenGL 2.1 fixed-function fog stage (3.10), same as Kalia's core shaders so terrain fogs
// identically to everything drawn around it. Returns how much of the fragment survives.
float fogFactor(float viewDistance) {
    int mode = FOG_MODE;
    if (mode == FOG_EXP) {
        return exp(-FOG_DENSITY * viewDistance);
    }
    if (mode == FOG_EXP2) {
        float volume = FOG_DENSITY * viewDistance;
        return exp(-(volume * volume));
    }
    float span = max(pc.fogRange.y - pc.fogRange.x, 1e-4);
    return (pc.fogRange.y - viewDistance) / span;
}

void main() {
    bool useMips = ((v_material >> MATERIAL_USE_MIP_OFFSET) & 1u) != 0u;
    vec4 color = (useMips ? texture(blockAtlas, v_uv) : textureLod(blockAtlas, v_uv, 0.0)) * v_color;

    if (isCutout && color.a < 0.1) {
        discard;
    }

    if (FOG_MODE < 0) {
        out_color = color;
        return;
    }

    // y is the spherical distance, which is what the game asks fog to measure.
    float factor = clamp(fogFactor(v_fragDistance.y), 0.0, 1.0);
    vec3 fogColor = clamp(vec3(pc.fogColorRG, pc.fogColorB_Mode.x), 0.0, 1.0);
    out_color = vec4(mix(fogColor, color.rgb, factor), color.a);
}
