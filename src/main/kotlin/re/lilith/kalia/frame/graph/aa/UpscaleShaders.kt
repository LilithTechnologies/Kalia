package re.lilith.kalia.frame.graph.aa

import re.lilith.kalia.renderer.post.PostEffects
import re.lilith.kalia.shader.ShaderAssets

object UpscaleShaders {
    val SHARP_PROGRAM by lazy {
        PostEffects.program(
            label = "kalia/aa/upscale-sharp",
            fragment = ShaderAssets.assemble("kalia:upscale_sharp.frag"),
        )
    }
}
