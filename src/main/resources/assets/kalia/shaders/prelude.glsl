// These are common declarations that the base for everything else (hence the name)
// You can use it with `#include "kalia:prelude.glsl"`

layout(push_constant) uniform KaliaPush {
    mat4 kaliaModelView;
    vec4 kaliaShaderColor;
    vec4 kaliaModelOffset;
    vec4 kaliaFogColor;
    vec4 kaliaFogParams;
};
#define KALIA_ALPHA_CUTOUT kaliaModelOffset.w
#define KALIA_FOG_START    kaliaFogParams.x
#define KALIA_FOG_END      kaliaFogParams.y
#define KALIA_FOG_DENSITY  kaliaFogParams.z
#define KALIA_FOG_MODE     (int(kaliaFogParams.w + 0.5) - 1)
#define KALIA_FOG_EXP      0
#define KALIA_FOG_EXP2     1
#define KALIA_FOG_LINEAR   2

layout(binding = 3, std140) uniform KaliaScene {
    mat4 kaliaProjection;
    mat4 kaliaTextureMatrix;
    vec4 kaliaLight0;
    vec4 kaliaLight1;
    vec4 kaliaOverlayColor;
    vec4 kaliaLightmap;
    vec4 kaliaScreenSize;
    vec4 kaliaTexGenPlane[4];
    vec4 kaliaTexGenSource;
};
#define KALIA_LIGHTMAP_COORDS  kaliaLightmap.xy
#define KALIA_LIGHTMAP_ENABLED (kaliaLightmap.z > 0.5)
#define KALIA_LIGHTING_ENABLED (kaliaLightmap.w > 0.5)

float kaliaFogFactor(float viewDistance) {
    int mode = KALIA_FOG_MODE;
    if (mode == KALIA_FOG_EXP) {
        return exp(-KALIA_FOG_DENSITY * viewDistance);
    }
    if (mode == KALIA_FOG_EXP2) {
        float volume = KALIA_FOG_DENSITY * viewDistance;
        return exp(-(volume * volume));
    }
    float span = max(KALIA_FOG_END - KALIA_FOG_START, 1e-4);
    return (KALIA_FOG_END - viewDistance) / span;
}

vec3 kaliaApplyFog(vec3 color, float viewDistance) {
    if (KALIA_FOG_MODE < 0) {
        return color;
    }
    float factor = clamp(kaliaFogFactor(viewDistance), 0.0, 1.0);
    return mix(clamp(kaliaFogColor.rgb, 0.0, 1.0), color, factor);
}

vec3 kaliaDiffuse(vec3 color, vec3 normal) {
    vec3 unit = normalize(normal);
    float first = max(dot(unit, normalize(kaliaLight0.xyz)), 0.0);
    float second = max(dot(unit, normalize(kaliaLight1.xyz)), 0.0);
    return color * min(1.0, 0.4 + 0.6 * (first + second));
}
