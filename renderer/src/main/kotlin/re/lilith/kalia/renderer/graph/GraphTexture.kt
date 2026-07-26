package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.resource.GpuTexture

/**
 * A texture the graph owns
 */
class GraphTexture internal constructor(
    val handle: TextureHandle,
    val name: String,
    val format: TextureFormat,
    val sizing: TextureSizing,
    val mipLevels: Int,
    val imported: GpuTexture?,
)