package re.lilith.kalia.frame.graph.aa

import re.lilith.kalia.renderer.post.PostEffects
import re.lilith.kalia.shader.ShaderAssets

object FxaaShaders {
    val FAST_PROGRAM by lazy {
        PostEffects.program(
            label = "kalia/aa/fxaa-fast",
            fragment = ShaderAssets.assemble("kalia:fxaa_fast.frag"),
        )
    }

    val QUALITY_PROGRAM by lazy {
        PostEffects.program(
            label = "kalia/aa/fxaa-quality",
            fragment = ShaderAssets.assemble("kalia:fxaa_quality.frag"),
        )
    }
}
