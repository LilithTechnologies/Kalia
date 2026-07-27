package re.lilith.kalia.renderer.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkMemoryBarrier
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.vulkan.api.command.CommandRecorder
import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.transfer.BufferCopy
import java.nio.ByteBuffer
import java.util.*
import re.lilith.vulkan.api.memory.Buffer as VkBuffer

/**
 * Collects transfers and replays them into the command buffer that runs ahead of the frame
 */
internal class VulkanUploadQueue(private val context: VulkanContext) {
    private val pending = ArrayDeque<PendingUpload>()

    /**
     * Claims the sampleable layout for [texture] straight away and reports what it was
     */
    private fun claimSampleable(texture: VulkanTexture): ImageLayout {
        val previous = texture.layout
        texture.layout = ImageLayout.ShaderReadOnlyOptimal
        return previous
    }

    val hasWork: Boolean get() = pending.isNotEmpty()

    @Synchronized
    fun stageBufferWrite(target: VkBuffer, offsetBytes: Long, source: ByteBuffer) {
        val length = source.remaining().toLong()
        val staging = context.createStagingBuffer(length)
        staging.mappedByteBuffer(0L, length).put(source.duplicate())
        pending += PendingUpload.BufferCopyUpload(staging, target, offsetBytes, length)
    }

    @Synchronized
    fun stageBufferCopy(source: VkBuffer, destination: VkBuffer, readOffset: Long, writeOffset: Long, sizeBytes: Long) {
        pending += PendingUpload.BufferToBufferCopy(source, destination, readOffset, writeOffset, sizeBytes)
    }

    @Synchronized
    fun stageTextureUpload(target: VulkanTexture, mipLevel: Int, levelExtent: Extent, source: ByteBuffer, layer: Int = 0) {
        val length = source.remaining().toLong()
        val staging = context.createStagingBuffer(length)
        staging.mappedByteBuffer(0L, length).put(source.duplicate())
        pending += PendingUpload.TextureUpload(staging, target, mipLevel, levelExtent, claimSampleable(target), layer)
    }

    @Synchronized
    fun stageMipmapGeneration(target: VulkanTexture) {
        pending += PendingUpload.MipmapGeneration(target, claimSampleable(target))
    }

    /**
     * Registers a texture that must be sampleable even though nothing has been uploaded to it
     */
    @Synchronized
    fun stageMakeSampleable(target: VulkanTexture) {
        if (target.layout != ImageLayout.ShaderReadOnlyOptimal) {
            pending += PendingUpload.LayoutTransition(target, claimSampleable(target))
        }
    }

    /**
     * Records everything queued so far and hands the staging buffers to [retire]
     */
    @Synchronized
    fun flush(recorder: CommandRecorder, retire: (AutoCloseable) -> Unit) {
        while (pending.isNotEmpty()) {
            val upload = pending.poll()
            if (upload.target?.isClosed == true) {
                (upload as? PendingUpload.WithStaging)?.staging?.let(retire)
                continue
            }

            when (upload) {
                is PendingUpload.BufferCopyUpload -> {
                    insertTransferBarrier(recorder)
                    recorder.copyBuffer(
                        source = upload.staging,
                        destination = upload.target2,
                        regions = listOf(BufferCopy(0L, upload.offsetBytes, upload.sizeBytes)),
                    )
                    retire(upload.staging)
                }

                is PendingUpload.BufferToBufferCopy -> {
                    insertTransferBarrier(recorder)
                    recorder.copyBuffer(
                        source = upload.source,
                        destination = upload.destination,
                        regions = listOf(BufferCopy(upload.readOffset, upload.writeOffset, upload.sizeBytes)),
                    )
                }

                is PendingUpload.TextureUpload -> {
                    recorder.recordTextureUpload(
                        texture = requireNotNull(upload.target),
                        staging = upload.staging,
                        mipLevel = upload.mipLevel,
                        levelExtent = upload.levelExtent,
                        sourceLayout = upload.sourceLayout,
                        layer = upload.layer,
                    )
                    retire(upload.staging)
                }

                is PendingUpload.MipmapGeneration ->
                    recorder.recordMipmapGeneration(requireNotNull(upload.target), upload.sourceLayout)

                is PendingUpload.LayoutTransition -> recorder.recordLayoutTransition(
                    texture = requireNotNull(upload.target),
                    from = upload.sourceLayout,
                    to = ImageLayout.ShaderReadOnlyOptimal,
                )
            }
        }
    }

    @Synchronized
    fun forget(texture: VulkanTexture) {
        pending.removeAll { it.target === texture }
    }

    private fun insertTransferBarrier(recorder: CommandRecorder) {
        MemoryStack.stackPush().use { stack ->
            val barrier = VkMemoryBarrier.calloc(1, stack)
            barrier[0]
                .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT or VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
            VK10.vkCmdPipelineBarrier(
                recorder.commandBuffer.handle,
                VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                barrier,
                null,
                null,
            )
        }
    }

    private sealed interface PendingUpload {
        val target: VulkanTexture?

        sealed interface WithStaging : PendingUpload {
            val staging: VkBuffer
        }

        class BufferToBufferCopy(
            val source: VkBuffer,
            val destination: VkBuffer,
            val readOffset: Long,
            val writeOffset: Long,
            val sizeBytes: Long,
        ) : PendingUpload {
            override val target: VulkanTexture? get() = null
        }

        class BufferCopyUpload(
            override val staging: VkBuffer,
            val target2: VkBuffer,
            val offsetBytes: Long,
            val sizeBytes: Long,
        ) : WithStaging {
            override val target: VulkanTexture? get() = null
        }

        class TextureUpload(
            override val staging: VkBuffer,
            override val target: VulkanTexture,
            val mipLevel: Int,
            val levelExtent: Extent,
            val sourceLayout: ImageLayout,
            val layer: Int,
        ) : WithStaging

        class MipmapGeneration(
            override val target: VulkanTexture,
            val sourceLayout: ImageLayout,
        ) : PendingUpload

        class LayoutTransition(
            override val target: VulkanTexture,
            val sourceLayout: ImageLayout,
        ) : PendingUpload
    }
}
