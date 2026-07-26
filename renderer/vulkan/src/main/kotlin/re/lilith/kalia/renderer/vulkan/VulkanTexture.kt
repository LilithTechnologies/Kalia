package re.lilith.kalia.renderer.vulkan

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.vulkan.api.command.ImageBarrier
import re.lilith.vulkan.api.memory.Image
import re.lilith.vulkan.api.memory.ImageView
import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.flags.AccessMask
import re.lilith.vulkan.api.types.flags.PipelineStageMask
import re.lilith.vulkan.api.types.image.ImageSubresourceRange
import java.nio.ByteBuffer

internal class VulkanTexture(
    private val owner: VulkanRenderDevice,
    override val label: String,
    override val extent: Extent,
    override val format: TextureFormat,
    override val mipLevels: Int,
    val image: Image,
    val view: ImageView,
) : GpuTexture {
    var layout = ImageLayout.Undefined

    private var closed = false

    override val isClosed: Boolean get() = closed

    val subresourceRange = ImageSubresourceRange(
        aspectMask = Convert.aspect(format),
        levelCount = mipLevels,
        layerCount = 1,
    )

    override fun upload(source: ByteBuffer, mipLevel: Int) {
        check(!closed) { "Texture '$label' is closed." }
        require(mipLevel in 0 until mipLevels) { "Texture '$label' has no mip level $mipLevel." }
        val levelExtent = mipExtent(mipLevel)
        val expected = levelExtent.width.toLong() * levelExtent.height * format.bytesPerPixel
        require(source.remaining().toLong() == expected) {
            "Texture '$label' mip $mipLevel expects $expected bytes, got ${source.remaining()}."
        }
        owner.uploads.stageTextureUpload(this, mipLevel, levelExtent, source)
    }

    override fun generateMipmaps() {
        if (mipLevels <= 1) return
        owner.uploads.stageMipmapGeneration(this)
    }

    fun mipExtent(level: Int): Extent = Extent(
        width = (extent.width shr level).coerceAtLeast(1),
        height = (extent.height shr level).coerceAtLeast(1),
    )

    fun barrierTo(target: ImageLayout): ImageBarrier? {
        if (layout == target) {
            return null
        }
        val barrier = ImageBarrier(
            image = image,
            oldLayout = layout,
            newLayout = target,
            sourceStageMask = stageFor(layout),
            destinationStageMask = stageFor(target),
            sourceAccessMask = accessFor(layout),
            destinationAccessMask = accessFor(target),
            subresourceRange = subresourceRange,
        )
        layout = target
        return barrier
    }

    override fun close() {
        if (closed) return
        closed = true
        // Drop any queued work first
        owner.uploads.forget(this)
        owner.scheduleRelease(view)
        owner.scheduleRelease(image)
    }

    companion object {
        fun stageFor(layout: ImageLayout) = when (layout) {
            ImageLayout.Undefined -> PipelineStageMask.TopOfPipe
            ImageLayout.ColorAttachmentOptimal -> PipelineStageMask.ColorAttachmentOutput
            ImageLayout.DepthStencilAttachmentOptimal,
            ImageLayout.DepthStencilReadOnlyOptimal,
                -> PipelineStageMask.EarlyFragmentTests + PipelineStageMask.LateFragmentTests

            ImageLayout.ShaderReadOnlyOptimal -> PipelineStageMask.FragmentShader
            ImageLayout.TransferSourceOptimal, ImageLayout.TransferDestinationOptimal -> PipelineStageMask.Transfer
            ImageLayout.PresentSource -> PipelineStageMask.BottomOfPipe
            else -> PipelineStageMask.AllCommands
        }

        fun accessFor(layout: ImageLayout) = when (layout) {
            ImageLayout.Undefined, ImageLayout.PresentSource -> AccessMask.None
            ImageLayout.ColorAttachmentOptimal -> AccessMask.ColorAttachmentRead + AccessMask.ColorAttachmentWrite
            ImageLayout.DepthStencilAttachmentOptimal ->
                AccessMask.DepthStencilAttachmentRead + AccessMask.DepthStencilAttachmentWrite

            ImageLayout.DepthStencilReadOnlyOptimal -> AccessMask.DepthStencilAttachmentRead
            ImageLayout.ShaderReadOnlyOptimal -> AccessMask.ShaderRead
            ImageLayout.TransferSourceOptimal -> AccessMask.TransferRead
            ImageLayout.TransferDestinationOptimal -> AccessMask.TransferWrite
            else -> AccessMask.MemoryRead + AccessMask.MemoryWrite
        }
    }
}
