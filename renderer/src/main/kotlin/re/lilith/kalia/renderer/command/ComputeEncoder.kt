package re.lilith.kalia.renderer.command

import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuComputePipeline
import java.nio.ByteBuffer

/**
 * Records compute work.
 */
interface ComputeEncoder {
    fun bindPipeline(pipeline: GpuComputePipeline)

    fun bindStorageBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long = 0L, sizeBytes: Long = buffer.sizeBytes)

    fun bindUniformBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long = 0L, sizeBytes: Long = buffer.sizeBytes)

    fun pushConstants(data: ByteBuffer)

    fun dispatch(groupsX: Int, groupsY: Int = 1, groupsZ: Int = 1)

    /**
     * Orders a later dispatch in this submission after the writes of an earlier one.
     */
    fun barrier()
}
