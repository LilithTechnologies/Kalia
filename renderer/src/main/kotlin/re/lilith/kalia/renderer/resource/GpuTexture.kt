package re.lilith.kalia.renderer.resource

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import java.nio.ByteBuffer

interface GpuTexture : GpuResource {
    val extent: Extent
    val format: TextureFormat
    val mipLevels: Int

    /**
     * Uploads tightly packed pixels for one mip level.
     */
    fun upload(source: ByteBuffer, mipLevel: Int = 0)

    /** Regenerates mips 1 onward from level 0. No-op when [mipLevels] is 1. */
    fun generateMipmaps()
}
