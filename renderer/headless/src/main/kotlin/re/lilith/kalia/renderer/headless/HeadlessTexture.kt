package re.lilith.kalia.renderer.headless

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuTexture
import java.nio.ByteBuffer

internal class HeadlessTexture(
    override val label: String,
    override val extent: Extent,
    override val format: TextureFormat,
    override val mipLevels: Int,
    override val layers: Int,
) : GpuTexture {
    private var closed = false

    override val isClosed: Boolean
        get() = closed

    override fun upload(source: ByteBuffer, mipLevel: Int, layer: Int) {
        check(!closed) { "Texture '$label' is closed." }

        require(mipLevel in 0 until mipLevels) {
            "Texture '$label' has no mip level $mipLevel."
        }

        require(layer in 0 until layers) {
            "Texture '$label' has no layer $layer."
        }

        val levelExtent = mipExtent(mipLevel)

        val expected =
            levelExtent.width.toLong() *
            levelExtent.height.toLong() *
            format.bytesPerPixel

        require(source.remaining().toLong() == expected) {
            "Texture '$label' mip $mipLevel expects $expected bytes, got ${source.remaining()}."
        }
    }

    override fun generateMipmaps() {
        check(!closed) { "Texture '$label' is closed." }
    }

    fun mipExtent(level: Int): Extent = Extent(
        width = (extent.width shr level).coerceAtLeast(1),
        height = (extent.height shr level).coerceAtLeast(1),
    )

    override fun close() {
        closed = true
    }
}