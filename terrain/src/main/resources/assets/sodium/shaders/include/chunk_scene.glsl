layout(std140, set = 0, binding = 2) uniform ChunkSceneUniforms {
    mat4 u_ProjectionMatrix;
    mat4 u_ModelViewMatrix;
    vec4 u_FogColor;
    vec4 u_FogParams;
    vec4 u_FogParams2;
};

#define u_FogStart u_FogParams.x
#define u_FogEnd u_FogParams.y
#define u_FogDensity u_FogParams.z
#define u_RenderDistFogStart u_FogParams.w
#define u_RenderDistFogEnd u_FogParams2.x
#define u_EnvFogStart u_FogParams2.y
#define u_EnvFogEnd u_FogParams2.z
