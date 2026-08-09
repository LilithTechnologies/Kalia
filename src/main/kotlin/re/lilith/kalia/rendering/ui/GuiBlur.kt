package re.lilith.kalia.rendering.ui

import re.lilith.kalia.renderer.post.PostEffects

object GuiBlur {
    var enabled = false
    var radius = 8f

    val PROGRAM by lazy {
        PostEffects.program(
            label = "kalia/gui/blur",
            //language=glsl
            fragment = """
#version 450
layout(binding = 0) uniform sampler2D kaliaInput;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;
${PostEffects.PUSH_CONSTANT_BLOCK}

#define BLUR_DIRECTION kaliaParams[0].xy
#define BLUR_RADIUS    kaliaParams[0].z

void main() {
    float radius = max(BLUR_RADIUS, 1.0);
    int taps = int(radius);

    float sigma = radius * 0.5;
    float twoSigmaSquared = 2.0 * sigma * sigma;

    vec4 total = texture(kaliaInput, uv);
    float weightSum = 1.0;

    for (int i = 1; i <= taps; ++i) {
        float offset = float(i);
        float weight = exp(-(offset * offset) / twoSigmaSquared);
        vec2 step = BLUR_DIRECTION * kaliaInputTexel * offset;

        total += texture(kaliaInput, uv + step) * weight;
        total += texture(kaliaInput, uv - step) * weight;
        weightSum += weight * 2.0;
    }

    fragColor = total / weightSum;
}
""",
        )
    }
}
