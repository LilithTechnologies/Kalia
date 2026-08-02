layout(location = 0) in vec2 vUv;
layout(location = 1) in vec4 vColor;
layout(location = 2) flat in uint vFlags;

layout(location = 0) out vec4 fragColor;

layout(binding = 0) uniform sampler2D guiTexture0;
layout(binding = 1) uniform sampler2D guiTexture1;
layout(binding = 2) uniform sampler2D guiTexture2;
layout(binding = 3) uniform sampler2D guiTexture3;
layout(binding = 4) uniform sampler2D guiTexture4;
layout(binding = 5) uniform sampler2D guiTexture5;
layout(binding = 6) uniform sampler2D guiTexture6;
layout(binding = 7) uniform sampler2D guiTexture7;

#define GUI_FLAG_SLOT_MASK  0x7u
#define GUI_FLAG_TEXTURED   0x100u

vec4 kaliaSampleSlot(uint slot, vec2 uv) {
    switch (slot) {
        case 0u: return texture(guiTexture0, uv);
        case 1u: return texture(guiTexture1, uv);
        case 2u: return texture(guiTexture2, uv);
        case 3u: return texture(guiTexture3, uv);
        case 4u: return texture(guiTexture4, uv);
        case 5u: return texture(guiTexture5, uv);
        case 6u: return texture(guiTexture6, uv);
        default: return texture(guiTexture7, uv);
    }
}

void main() {
    vec4 color = vColor;

    if ((vFlags & GUI_FLAG_TEXTURED) != 0u) {
        color *= kaliaSampleSlot(vFlags & GUI_FLAG_SLOT_MASK, vUv);
    }

    if (color.a <= 0.0) {
        discard;
    }

    fragColor = color;
}
