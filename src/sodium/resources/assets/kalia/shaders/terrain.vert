#version 450

layout(location = 0) in uvec2 in_position;
layout(location = 1) in vec4 in_color;
layout(location = 2) in uvec2 in_texCoord;
layout(location = 3) in uvec4 in_lightAndData;

layout(location = 0) out vec4 v_color;
layout(location = 1) out vec2 v_uv;
layout(location = 2) out vec2 v_fragDistance;
layout(location = 3) flat out uint v_material;

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

layout(set = 0, binding = 1) uniform sampler2D lightTex;

const uint POSITION_BITS = 20u;
const uint TEXTURE_BITS = 15u;
const uint TEXTURE_MAX_VALUE = (1u << TEXTURE_BITS) - 1u;

const float VERTEX_SCALE = 32.0 / float(1u << POSITION_BITS);
const float VERTEX_OFFSET = -8.0;

uvec3 deinterleavePosition(uvec2 data) {
    uvec3 hi = (uvec3(data.x) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    uvec3 lo = (uvec3(data.y) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    return (hi << 10u) | lo;
}

// Packing scheme is defined by LocalSectionIndex.
vec3 drawTranslation(uint packedSection) {
    return vec3((uvec3(packedSection) >> uvec3(5u, 0u, 2u)) & uvec3(7u, 3u, 7u)) * 16.0;
}

void main() {
    vec3 local = vec3(deinterleavePosition(in_position)) * VERTEX_SCALE + VERTEX_OFFSET;
    vec3 position = local + pc.regionOffset + drawTranslation(in_lightAndData.w);

    // x is the cylindrical distance, y the spherical one.
    v_fragDistance = vec2(max(length(position.xz), abs(position.y)), length(position));

    gl_Position = pc.projectionMatrix * (pc.modelViewMatrix * vec4(position, 1.0));

    v_color = in_color * textureLod(lightTex, vec2(in_lightAndData.xy) / 256.0, 0.0);

    // The vertex format stores only the sign of the atlas-bleed bias; the magnitude comes from
    // the push constant, applied as an FMA for precision.
    vec2 texCoord = vec2(in_texCoord & uvec2(TEXTURE_MAX_VALUE)) / float(1u << TEXTURE_BITS);
    vec2 bias = mix(vec2(-1.0), vec2(1.0), notEqual(in_texCoord >> TEXTURE_BITS, uvec2(0u)));
    v_uv = bias * pc.texCoordShrink + texCoord;

    v_material = in_lightAndData.z;
}
