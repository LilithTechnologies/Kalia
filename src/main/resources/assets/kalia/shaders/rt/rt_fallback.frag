// Lights the geometry buffer the way Minecraft would have.
//
// Terrain is rasterised into a geometry buffer whenever ray tracing is on, but
// the traced scene is not always ready to light it: a world still streaming in
// has no acceleration structure yet, and a device that loses one has none either.
// Without this the terrain would simply not appear, which is a far worse failure
// than looking like vanilla for a moment.
//
// Reproducing the vanilla light map exactly is the point. This is what the chunk
// shader used to do before its output was split apart.

layout(binding = 0) uniform sampler2D kaliaAlbedo;
layout(binding = 1) uniform sampler2D kaliaSurface;
layout(binding = 2) uniform sampler2D kaliaDepth;
layout(binding = 3) uniform sampler2D kaliaLightmap;

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColour;

#include "kalia:rt/rt_common.glsl"

void main() {
    vec4 surface = texture(kaliaSurface, uv);
    float deviceDepth = texture(kaliaDepth, uv).r;

    // Nothing solid here, so whatever the sky pass left behind stays.
    if (kaliaRtIsSky(deviceDepth) || dot(surface.xyz, surface.xyz) < 1e-6) {
        discard;
    }

    vec4 albedo = texture(kaliaAlbedo, uv);
    // The geometry buffer stores both light coordinates normalised, so they go
    // back onto the zero-to-255 scale the light map is addressed with.
    vec2 light = vec2(albedo.a, surface.w) * 255.0;
    vec3 lightmap = texture(kaliaLightmap, clamp(light / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0))).rgb;

    fragColour = vec4(albedo.rgb * lightmap, 1.0);
}
