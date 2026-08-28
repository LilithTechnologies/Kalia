// Lighting pass. Runs at a fraction of the world resolution and feeds the denoiser.
//
// It finds its own surfaces rather than reading them back out of the depth buffer. Minecraft binds
// its own framebuffer for world rendering, so Kalia retargets the world pass and the depth ends up
// in an emulated renderbuffer that is not even created as sampleable; the graph's own depth
// attachment never receives anything. Tracing a primary ray here sidesteps that entirely, and is
// more accurate besides: the octree hands back an exact face normal and the real material, where
// reconstructing from depth gives a derivative-estimated normal and nothing else.
//
// The output is a multiplicative light factor plus a specular term. Keeping it multiplicative is
// what lets it composite over both the traced albedo and Minecraft's own entity shading without
// the two having to agree about anything.

layout(location = 0) in vec2 uv;

layout(location = 0) out vec4 outLight;
layout(location = 1) out vec4 outGeometry;

#include "kalia:svo_shade.glsl"

void main() {
    vec3 near = svoWorldFromDepth(uv, 0.0);
    vec3 far = svoWorldFromDepth(uv, 1.0);
    vec3 view = normalize(far - near);

    SvoHit primary;
    if (!svoTrace(near, view, 0.0, svoFogEnd, svoFootprint, primary) || primary.coarse) {
        // Sky, or too distant to shade meaningfully. A negative distance marks the pixel for the
        // filters and the compositor to leave alone.
        outLight = vec4(1.0, 1.0, 1.0, 0.0);
        outGeometry = vec4(0.0, 1.0, 0.0, -1.0);
        return;
    }

    vec3 position = near + view * primary.t;
    vec3 normal = primary.normal;
    float distance = length(position);
    vec3 surface = position + normal * (0.02 + distance * 2.0e-4);

    uint seed = svoHash(
        uint(gl_FragCoord.x) * 1973u +
        uint(gl_FragCoord.y) * 9277u +
        uint(svoFrameIndex) * 26699u);

    // Vanilla's own sky light level, which is exact and free of the noise a traced estimate has.
    // Using it to gate the sun is what keeps caves dark without also suppressing the shadow of a
    // tree standing in full daylight.
    float skyExposure = float((primary.light >> 4u) & 0xFu) * (1.0 / 15.0);

    // -- direct sun -----------------------------------------------------------------------------

    float ndl = max(dot(normal, svoSunDirection), 0.0);
    vec3 sunVisibility = vec3(1.0);
    if (svoFeature(SVO_FEATURE_SHADOWS) && ndl > 0.0 && svoSunIntensity > 0.0 && skyExposure > 0.0) {
        vec3 direction = svoConeSample(svoSunDirection, svoSunSoftness, svoRandom2(seed));
        sunVisibility = svoVisibility(surface, direction, svoShadowRange);
    }

    // -- sky visibility and one bounce ----------------------------------------------------------

    float skyVisibility = 1.0;
    vec3 bounce = vec3(0.0);
    int rays = svoFeature(SVO_FEATURE_OCCLUSION) ? svoRayCount : 0;
    if (rays > 0) {
        float open = 0.0;
        for (int index = 0; index < rays; ++index) {
            vec3 direction = svoCosineHemisphere(normal, svoRandom2(seed));
            SvoHit hit;
            if (!svoTrace(surface, direction, 0.0, svoDiffuseRange, SVO_SECONDARY_FOOTPRINT, hit)) {
                // Open sky contributes nothing here on purpose. Vanilla's lightmap already pays
                // for ambient, and adding it again lifted the whole frame uniformly, which is
                // both wrong and exactly what drowned the occlusion term out.
                open += 1.0;
                continue;
            }

            // Emissive surfaces throw light of their own, with real occlusion behind it. Vanilla's
            // block light already floods the area around a torch, so this is scaled to read as the
            // directional part on top rather than a second full-strength source.
            bounce += hit.albedo * hit.emission * svoEmissionStrength;

            if (svoFeature(SVO_FEATURE_BOUNCE)) {
                vec3 point = surface + direction * hit.t;
                float bounceNdl = max(dot(hit.normal, svoSunDirection), 0.0);
                if (bounceNdl > 0.0 && svoSunIntensity > 0.0) {
                    vec3 reached = svoVisibility(point + hit.normal * 0.03, svoSunDirection, svoShadowRange * 0.5);
                    bounce += hit.albedo * svoSunColor * reached * bounceNdl * svoSunIntensity;
                }
            }
        }
        float inverse = 1.0 / float(rays);
        skyVisibility = open * inverse;
        bounce *= inverse * svoBounceScale;
    }

    float specular = svoReflection(surface, normal, view, primary.flags);

    // Everything here is a multiplier over shading that already happened, so it darkens rather
    // than lights, and sits at one wherever the tracer found nothing to say.
    float sunWeight = clamp(ndl * skyExposure * svoSunIntensity, 0.0, 1.0);
    vec3 shadowed = mix(vec3(1.0 - svoShadowStrength), vec3(1.0), sunVisibility);
    vec3 sunTerm = mix(vec3(1.0), shadowed, sunWeight);
    vec3 occlusionTerm = mix(vec3(1.0), vec3(skyVisibility), svoOcclusionStrength);

    outLight = vec4(sunTerm * occlusionTerm + bounce, specular);
    outGeometry = vec4(normal, distance);
}
