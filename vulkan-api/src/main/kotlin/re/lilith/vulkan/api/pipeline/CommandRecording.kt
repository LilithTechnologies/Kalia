package re.lilith.vulkan.api.pipeline

import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10
import re.lilith.vulkan.api.command.CommandRecorder
import java.nio.ByteBuffer

fun CommandRecorder.bindGraphicsPipeline(pipeline: GraphicsPipeline): CommandRecorder = apply {
    require(pipeline.device === commandBuffer.device) { "Graphics pipeline must belong to the same logical device as the command buffer." }
    VK10.vkCmdBindPipeline(commandBuffer.handle, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle)
}

fun CommandRecorder.bindComputePipeline(pipeline: ComputePipeline): CommandRecorder = apply {
    require(pipeline.device === commandBuffer.device) { "Compute pipeline must belong to the same logical device as the command buffer." }
    VK10.vkCmdBindPipeline(commandBuffer.handle, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.handle)
}

fun CommandRecorder.pushConstants(
    layout: PipelineLayout,
    stageFlags: ShaderStageFlags,
    offset: Int = 0,
    data: ByteBuffer,
): CommandRecorder = apply {
    require(layout.device === commandBuffer.device) { "Pipeline layout must belong to the same logical device as the command buffer." }
    require(data.isDirect) { "Push-constant data must be a direct ByteBuffer." }
    val size = data.remaining()
    if (size == 0) {
        return@apply
    }
    VK10.nvkCmdPushConstants(
        commandBuffer.handle,
        layout.handle,
        stageFlags.vkBits,
        offset,
        size,
        // memAddress already includes the buffer's position; adding it again would
        // read past the intended range for any non-zero-position buffer.
        MemoryUtil.memAddress(data),
    )
}
