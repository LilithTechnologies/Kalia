package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.geometry.Color

class ColorAttachment internal constructor(
    val target: TextureHandle,
    val loadOp: LoadOp,
    val clearColor: Color,
)
