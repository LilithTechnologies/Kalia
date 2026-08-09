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
 * Collects transfers and replays them into whichever queue can run them.
 */
internal class VulkanUploadQueue(private val context: VulkanContext) : AutoCloseable {
    private val pendingBuffers = ArrayDeque<BufferWork>()
    private val pendingImages = ArrayDeque<ImageWork>()
    private val staging = VulkanStagingPool(context)
    private val hazards = VulkanTransferHazards()

    private var batchRecorded = false

    private fun claimSampleable(texture: VulkanTexture): ImageLayout {
        val previous = texture.layout
        texture.layout = ImageLayout.ShaderReadOnlyOptimal
        return previous
    }

    val hasWork: Boolean get() = hasBufferWork || hasImageWork

    @get:Synchronized
    val hasBufferWork: Boolean get() = pendingBuffers.isNotEmpty()

    @get:Synchronized
    val hasImageWork: Boolean get() = pendingImages.isNotEmpty()

    @Synchronized
    fun stageBufferWrite(target: VkBuffer, offsetBytes: Long, source: ByteBuffer) {
        val length = source.remaining().toLong()
        val page = staging.write(source, length)
        pendingBuffers += BufferWork.StagedWrite(page, staging.reservedOffset, target, offsetBytes, length)
    }

    @Synchronized
    fun stageBufferCopy(source: VkBuffer, destination: VkBuffer, readOffset: Long, writeOffset: Long, sizeBytes: Long) {
        pendingBuffers += BufferWork.DeviceCopy(source, destination, readOffset, writeOffset, sizeBytes)
    }

    @Synchronized
    fun stageTextureUpload(target: VulkanTexture, mipLevel: Int, levelExtent: Extent, source: ByteBuffer, layer: Int = 0) {
        val length = source.remaining().toLong()
        val page = staging.write(source, length)
        pendingImages += ImageWork.Upload(
            page,
            staging.reservedOffset,
            target,
            mipLevel,
            levelExtent,
            claimSampleable(target),
            layer,
        )
    }

    @Synchronized
    fun stageMipmapGeneration(target: VulkanTexture) {
        pendingImages += ImageWork.Mipmaps(target, claimSampleable(target))
    }

    @Synchronized
    fun stageMakeSampleable(target: VulkanTexture) {
        if (target.layout != ImageLayout.ShaderReadOnlyOptimal) {
            pendingImages += ImageWork.Transition(target, claimSampleable(target))
        }
    }

    /**
     * Records every queued buffer copy. Safe to call repeatedly within a frame.
     */
    @Synchronized
    fun flushBuffers(recorder: CommandRecorder): Boolean {
        if (pendingBuffers.isEmpty()) {
            return false
        }
        hazards.clear()
        if (batchRecorded) {
            insertTransferBarrier(recorder)
        }

        while (pendingBuffers.isNotEmpty()) {
            when (val work = pendingBuffers.poll()) {
                is BufferWork.StagedWrite -> {
                    if (hazards.writeConflicts(work.destination, work.offsetBytes, work.sizeBytes)) {
                        insertTransferBarrier(recorder)
                    }
                    recorder.copyBuffer(
                        source = work.staging,
                        destination = work.destination,
                        regions = listOf(BufferCopy(work.stagingOffset, work.offsetBytes, work.sizeBytes)),
                    )
                    hazards.recordWrite(work.destination, work.offsetBytes, work.sizeBytes)
                }

                is BufferWork.DeviceCopy -> {
                    if (hazards.copyConflicts(work.source, work.destination, work.writeOffset, work.sizeBytes)) {
                        insertTransferBarrier(recorder)
                    }
                    recorder.copyBuffer(
                        source = work.source,
                        destination = work.destination,
                        regions = listOf(BufferCopy(work.readOffset, work.writeOffset, work.sizeBytes)),
                    )
                    hazards.recordRead(work.source)
                    hazards.recordWrite(work.destination, work.writeOffset, work.sizeBytes)
                }
            }
        }

        hazards.clear()
        batchRecorded = true
        return true
    }

    @Synchronized
    fun flushImages(recorder: CommandRecorder): Boolean {
        if (pendingImages.isEmpty()) {
            return false
        }

        while (pendingImages.isNotEmpty()) {
            val work = pendingImages.poll()
            if (work.target.isClosed) {
                continue
            }
            when (work) {
                is ImageWork.Upload -> recorder.recordTextureUpload(
                    texture = work.target,
                    staging = work.staging,
                    stagingOffset = work.stagingOffset,
                    mipLevel = work.mipLevel,
                    levelExtent = work.levelExtent,
                    sourceLayout = work.sourceLayout,
                    layer = work.layer,
                )

                is ImageWork.Mipmaps -> recorder.recordMipmapGeneration(work.target, work.sourceLayout)

                is ImageWork.Transition -> recorder.recordLayoutTransition(
                    texture = work.target,
                    from = work.sourceLayout,
                    to = ImageLayout.ShaderReadOnlyOptimal,
                )
            }
        }
        return true
    }

    @Synchronized
    fun endFrame(retire: (AutoCloseable) -> Unit) {
        batchRecorded = false
        if (pendingBuffers.isNotEmpty() || pendingImages.isNotEmpty()) {
            return
        }
        retire(staging.endBatch())
    }

    @Synchronized
    fun forget(texture: VulkanTexture) {
        pendingImages.removeAll { it.target === texture }
    }

    override fun close() {
        pendingBuffers.clear()
        pendingImages.clear()
        staging.close()
    }

    private fun insertTransferBarrier(recorder: CommandRecorder) {
        hazards.clear()
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

    private sealed interface BufferWork {
        class DeviceCopy(
            val source: VkBuffer,
            val destination: VkBuffer,
            val readOffset: Long,
            val writeOffset: Long,
            val sizeBytes: Long,
        ) : BufferWork

        class StagedWrite(
            val staging: VkBuffer,
            val stagingOffset: Long,
            val destination: VkBuffer,
            val offsetBytes: Long,
            val sizeBytes: Long,
        ) : BufferWork
    }

    private sealed interface ImageWork {
        val target: VulkanTexture

        class Upload(
            val staging: VkBuffer,
            val stagingOffset: Long,
            override val target: VulkanTexture,
            val mipLevel: Int,
            val levelExtent: Extent,
            val sourceLayout: ImageLayout,
            val layer: Int,
        ) : ImageWork

        class Mipmaps(
            override val target: VulkanTexture,
            val sourceLayout: ImageLayout,
        ) : ImageWork

        class Transition(
            override val target: VulkanTexture,
            val sourceLayout: ImageLayout,
        ) : ImageWork
    }
}
