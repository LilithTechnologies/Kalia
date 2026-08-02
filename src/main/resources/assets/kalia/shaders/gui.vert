layout(location = 0) in vec4 inCornerA;
layout(location = 1) in vec4 inCornerB;
layout(location = 2) in vec4 inUvRect;
layout(location = 3) in vec4 inTintTop;
layout(location = 4) in vec4 inTintBottom;
layout(location = 5) in uint inFlags;

layout(location = 6) in vec2 inWeight;

layout(push_constant) uniform KaliaGuiPush {
    mat4 guiProjection;
};

layout(location = 0) out vec2 vUv;
layout(location = 1) out vec4 vColor;
layout(location = 2) flat out uint vFlags;

void main() {
    vec2 c0 = inCornerA.xy;
    vec2 c1 = inCornerA.zw;
    vec2 c2 = inCornerB.xy;
    vec2 c3 = inCornerB.zw;

    float wx = inWeight.x;
    float wy = inWeight.y;

    vec2 position =
        c0 * ((1.0 - wx) * (1.0 - wy)) +
        c1 * ((1.0 - wx) * wy) +
        c2 * (wx * wy) +
        c3 * (wx * (1.0 - wy));

    gl_Position = guiProjection * vec4(position, 0.0, 1.0);

    vUv = vec2(mix(inUvRect.x, inUvRect.z, wx), mix(inUvRect.y, inUvRect.w, wy));
    vColor = mix(inTintTop, inTintBottom, wy);
    vFlags = inFlags;
}
