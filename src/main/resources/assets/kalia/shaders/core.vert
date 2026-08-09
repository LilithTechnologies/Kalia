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

#ifdef TEXTURE_SLOTS
layout(location = 11) in uint inTexSlot;
layout(location = 5) flat out uint vTexSlot;
#endif

layout(location = 0) out vec4 vColor;
#ifdef TEXGEN
layout(location = 1) out vec4 vUv0;
#else
layout(location = 1) out vec2 vUv0;
#endif
layout(location = 2) out vec2 vUv1;
layout(location = 3) out vec3 vNormal;
layout(location = 4) out float vViewDistance;

#include "kalia:prelude.glsl"

void main() {
#ifdef TEXTURE_SLOTS
    vTexSlot = inTexSlot;
#endif
    vec3 position = inPosition + kaliaModelOffset.xyz;
    vec4 eye = kaliaModelView * vec4(position, 1.0);
    vViewDistance = abs(eye.z);
    gl_Position = kaliaProjection * eye;

#ifdef HAS_COLOR
    vColor = inColor * kaliaShaderColor;
#else
    vColor = kaliaShaderColor;
#endif

#ifdef TEXGEN
    vec4 genObject = vec4(position, 1.0);
    vec4 gen = vec4(
        dot(kaliaTexGenPlane[0], mix(genObject, eye, kaliaTexGenSource.x)),
        dot(kaliaTexGenPlane[1], mix(genObject, eye, kaliaTexGenSource.y)),
        dot(kaliaTexGenPlane[2], mix(genObject, eye, kaliaTexGenSource.z)),
        dot(kaliaTexGenPlane[3], mix(genObject, eye, kaliaTexGenSource.w)));
    vUv0 = kaliaTextureMatrix * gen;
#else
#ifdef HAS_TEXTURE
    vUv0 = (kaliaTextureMatrix * vec4(inUv0, 0.0, 1.0)).xy;
#else
    vUv0 = vec2(0.0);
#endif
#endif

#ifdef HAS_LIGHTMAP
#ifdef LIGHTMAP_SIGNED_SHORT
    vUv1 = vec2(inUv1) / 256.0;
#else
    vUv1 = inUv1;
#endif
#else
    vUv1 = KALIA_LIGHTMAP_COORDS;
#endif

#ifdef HAS_NORMAL
    vNormal = mat3(kaliaModelView) * inNormal.xyz;
#else
    vNormal = vec3(0.0, 1.0, 0.0);
#endif
}
