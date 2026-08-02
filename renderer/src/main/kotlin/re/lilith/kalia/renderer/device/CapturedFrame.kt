package re.lilith.kalia.renderer.device

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import java.nio.ByteBuffer

class CapturedFrame(
    val extent: Extent,
    val format: TextureFormat,
    val pixels: ByteBuffer,
)
