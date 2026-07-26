layout(location = 0) in vec3 inPosition;
#ifdef HAS_COLOR
layout(location = 1) in vec4 inColor;
#endif
#ifdef HAS_TEXTURE
layout(location = 2) in vec2 inUv0;
#endif
#ifdef HAS_LIGHTMAP
#ifdef LIGHTMAP_SIGNED_SHORT
layout(location = 3) in ivec2 inUv1;
#else
layout(location = 3) in vec2 inUv1;
#endif
#endif
#ifdef HAS_NORMAL
layout(location = 4) in vec4 inNormal;
#endif

layout(location = 5) in vec4 instRow0;
layout(location = 6) in vec4 instRow1;
layout(location = 7) in vec4 instRow2;
layout(location = 8) in vec4 instTint;
layout(location = 9) in vec4 instOverlay;

layout(location = 10) in vec4 instLight;

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec2 vUv0;
layout(location = 2) out vec2 vUv1;
layout(location = 3) out vec3 vNormal;
layout(location = 4) out float vViewDistance;
layout(location = 5) out vec4 vOverlay;
layout(location = 6) out vec4 vMisc;

#include "kalia:prelude.glsl"

void main() {
    vec4 model = vec4(inPosition, 1.0);
    vec3 eye = vec3(dot(instRow0, model), dot(instRow1, model), dot(instRow2, model));
    vViewDistance = abs(eye.z);
    gl_Position = kaliaProjection * vec4(eye, 1.0);

#ifdef HAS_COLOR
    vColor = inColor * instTint;
#else
    vColor = instTint;
#endif

#ifdef HAS_TEXTURE
    vUv0 = (kaliaTextureMatrix * vec4(inUv0, 0.0, 1.0)).xy;
#else
    vUv0 = vec2(0.0);
#endif

#ifdef HAS_LIGHTMAP
#ifdef LIGHTMAP_SIGNED_SHORT
    vUv1 = vec2(inUv1) / 256.0;
#else
    vUv1 = inUv1;
#endif
#else
    vUv1 = instLight.xy;
#endif

#ifdef HAS_NORMAL
    vNormal = vec3(dot(instRow0.xyz, inNormal.xyz), dot(instRow1.xyz, inNormal.xyz), dot(instRow2.xyz, inNormal.xyz));
#else
    vNormal = vec3(0.0, 1.0, 0.0);
#endif

    vOverlay = instOverlay;
    int packed = int(instLight.z + 0.5);
    vMisc = vec4(
        (packed & 1) != 0 ? 1.0 : 0.0,
        (packed & 2) != 0 ? 1.0 : 0.0,
        instLight.w,
        float(packed >> 2));
}
