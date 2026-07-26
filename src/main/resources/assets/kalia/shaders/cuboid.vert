layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUV;

layout(location = 2) in vec4 instRow0;
layout(location = 3) in vec4 instRow1;
layout(location = 4) in vec4 instRow2;
layout(location = 5) in vec4 instTint;
layout(location = 6) in vec4 instOverlay;
layout(location = 7) in vec4 instLight;
layout(location = 8) in vec4 instBoxA;
layout(location = 9) in vec4 instBoxB;
layout(location = 10) in float instScale;
layout(location = 11) in vec3 instCenter;

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec2 vUv0;
layout(location = 2) out vec3 vNormal;
layout(location = 3) out float vViewDistance;
layout(location = 4) out vec4 vOverlay;
layout(location = 5) out vec4 vMisc;
layout(location = 6) out vec2 vLightUv;

#include "kalia:prelude.glsl"

const vec3 FACE_NORMALS[6] = vec3[6](
    vec3(0.0, 0.0, 1.0),
    vec3(0.0, 0.0, -1.0),
    vec3(-1.0, 0.0, 0.0),
    vec3(1.0, 0.0, 0.0),
    vec3(0.0, 1.0, 0.0),
    vec3(0.0, -1.0, 0.0)
);

void faceRect(int faceIndex, float i, float j, float k, float l, float m, float tw, float th, out vec2 rectMin, out vec2 rectMax) {
    float x0 = i / tw;
    float x1 = (i + m) / tw;
    float x2 = (i + m + k) / tw;
    float x3 = (i + 2.0 * m + k) / tw;
    float x4 = (i + 2.0 * m + 2.0 * k) / tw;
    float x5 = (i + m + 2.0 * k) / tw;
    float y0 = j / th;
    float y1 = (j + m) / th;
    float y2 = (j + m + l) / th;

    if (faceIndex == 0) { rectMin = vec2(x3, y1); rectMax = vec2(x4, y2); }
    else if (faceIndex == 1) { rectMin = vec2(x1, y1); rectMax = vec2(x2, y2); }
    else if (faceIndex == 2) { rectMin = vec2(x0, y1); rectMax = vec2(x1, y2); }
    else if (faceIndex == 3) { rectMin = vec2(x2, y1); rectMax = vec2(x3, y2); }
    else if (faceIndex == 4) { rectMin = vec2(x2, y0); rectMax = vec2(x5, y1); }
    else { rectMin = vec2(x1, y0); rectMax = vec2(x2, y1); }
}

void main() {
    float sizeX = instBoxA.z;
    float sizeY = instBoxA.w;
    float sizeZ = instBoxB.x;
    float textureWidth = instBoxB.y;
    float textureHeight = instBoxB.z;
    float inflate = instBoxB.w;

    vec3 fullSize = (vec3(sizeX, sizeY, sizeZ) + vec3(inflate) * 2.0) * instScale;
    vec3 localPosition = instCenter + aPosition * fullSize;

    vec4 model = vec4(localPosition, 1.0);
    vec3 eye = vec3(dot(instRow0, model), dot(instRow1, model), dot(instRow2, model));
    vViewDistance = abs(eye.z);
    gl_Position = kaliaProjection * vec4(eye, 1.0);

    int faceIndex = gl_VertexIndex / 4;
    vec2 rectMin, rectMax;
    faceRect(
        faceIndex, instBoxA.x, instBoxA.y, sizeX, sizeY, sizeZ,
        textureWidth, textureHeight, rectMin, rectMax
    );
    vUv0 = mix(rectMin, rectMax, aUV);

    vNormal = normalize(vec3(
        dot(instRow0.xyz, FACE_NORMALS[faceIndex]),
        dot(instRow1.xyz, FACE_NORMALS[faceIndex]),
        dot(instRow2.xyz, FACE_NORMALS[faceIndex])
    ));

    vColor = instTint;
    vOverlay = instOverlay;
    int packed = int(instLight.z + 0.5);
    vMisc = vec4(
        (packed & 1) != 0 ? 1.0 : 0.0,
        (packed & 2) != 0 ? 1.0 : 0.0,
        instLight.w,
        float(packed >> 2));
    vLightUv = instLight.xy;
}
