package re.lilith.kalia.renderer.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkMemoryBarrier
import re.lilith.kalia.renderer.command.ComputeEncoder
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuComputePipeline
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.vulkan.api.command.CommandRecorder
import re.lilith.vulkan.api.command.bindDescriptorSets
import re.lilith.vulkan.api.descriptor.BufferDescriptorInfo
import re.lilith.vulkan.api.descriptor.DescriptorSet
import re.lilith.vulkan.api.descriptor.DescriptorSetWrite
import re.lilith.vulkan.api.pipeline.PipelineBindPoint
import re.lilith.vulkan.api.pipeline.ShaderStageFlags
import re.lilith.vulkan.api.pipeline.bindComputePipeline
import re.lilith.vulkan.api.pipeline.pushConstants
import java.nio.ByteBuffer

/**
 * Records compute dispatches into one command buffer.
 */
internal class VulkanComputeEncoder(
    private val backend: VulkanRenderDevice,
    private val recorder: CommandRecorder,
    private val frame: VulkanFrameSlot,
) : ComputeEncoder {
    private var pipeline: VulkanComputePipeline? = null
    private val boundBuffers = Array(MAX_BINDINGS) { BufferBinding() }
    private var bindingsDirty = false
    private var boundDescriptorSet: DescriptorSet? = null
    private val bindingProbe = BindingKey()
    private var dispatched = false

    override fun bindPipeline(pipeline: GpuComputePipeline) {
        val target = pipeline as VulkanComputePipeline
        if (this.pipeline === target) {
            return
        }
        this.pipeline = target
        recorder.bindComputePipeline(target.pipeline)
        bindingsDirty = true
        boundDescriptorSet = null
    }

    override fun bindStorageBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        bindBuffer(binding, buffer, offsetBytes, sizeBytes, BindingKind.STORAGE_BUFFER)

    override fun bindUniformBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        bindBuffer(binding, buffer, offsetBytes, sizeBytes, BindingKind.UNIFORM_BUFFER)

    private fun bindBuffer(binding: Int, buffer: GpuBuffer, offset: Long, size: Long, kind: BindingKind) {
        require(binding in 0 until MAX_BINDINGS) { "Compute binding $binding is out of range." }
        val slot = boundBuffers[binding]
        val vulkanBuffer = buffer as VulkanBuffer
        if (slot.buffer !== vulkanBuffer || slot.offset != offset || slot.size != size || slot.kind != kind) {
            slot.buffer = vulkanBuffer
            slot.offset = offset
            slot.size = size
            slot.kind = kind
            bindingsDirty = true
        }
    }

    override fun pushConstants(data: ByteBuffer) {
        val active = requirePipeline()
        require(data.remaining() <= active.pushConstantBytes) {
            "Compute program '${active.label}' declares ${active.pushConstantBytes} push-constant bytes, " +
                    "but ${data.remaining()} were supplied."
        }
        recorder.pushConstants(active.layout, ShaderStageFlags.Compute, 0, data)
    }

    override fun dispatch(groupsX: Int, groupsY: Int, groupsZ: Int) {
        if (groupsX <= 0 || groupsY <= 0 || groupsZ <= 0) {
            return
        }
        flushBindings()
        recorder.dispatch(groupsX, groupsY, groupsZ)
        dispatched = true
    }

    override fun barrier() {
        insertBarrier(
            VK10.VK_ACCESS_SHADER_WRITE_BIT,
            VK10.VK_ACCESS_SHADER_READ_BIT or VK10.VK_ACCESS_SHADER_WRITE_BIT,
            VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        )
    }

    fun finish(): Boolean {
        if (!dispatched) {
            return false
        }
        insertBarrier(
            VK10.VK_ACCESS_SHADER_WRITE_BIT,
            VK10.VK_ACCESS_INDIRECT_COMMAND_READ_BIT or
                    VK10.VK_ACCESS_INDEX_READ_BIT or
                    VK10.VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT or
                    VK10.VK_ACCESS_SHADER_READ_BIT or
                    VK10.VK_ACCESS_UNIFORM_READ_BIT,
            VK10.VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT or
                    VK10.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT or
                    VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT or
                    VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT or
                    VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        )
        return true
    }

    private fun insertBarrier(sourceAccess: Int, destinationAccess: Int, destinationStage: Int) {
        MemoryStack.stackPush().use { stack ->
            val barrier = VkMemoryBarrier.calloc(1, stack)
            barrier[0]
                .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                .srcAccessMask(sourceAccess)
                .dstAccessMask(destinationAccess)
            VK10.vkCmdPipelineBarrier(
                recorder.commandBuffer.handle,
                VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                destinationStage,
                0,
                barrier,
                null,
                null,
            )
        }
    }

    private fun flushBindings() {
        val active = requirePipeline()
        val layout = active.descriptorSetLayout ?: return
        if (!bindingsDirty) {
            return
        }

        val bindings = active.bindings
        bindingProbe.begin(layout, bindings.size)
        for (index in bindings.indices) {
            val slot = bindings[index].binding
            val bound = boundBuffers[slot]
            bindingProbe.put(index, bound.buffer, null, bound.offset, bound.size)
        }
        bindingProbe.seal()

        val set = frame.descriptorSet(bindingProbe, layout) { target -> writeDescriptors(active, target) }
        if (set !== boundDescriptorSet) {
            recorder.bindDescriptorSets(active.layout, listOf(set), bindPoint = PipelineBindPoint.Compute)
            boundDescriptorSet = set
        }
        bindingsDirty = false
    }

    private fun writeDescriptors(active: VulkanComputePipeline, set: DescriptorSet) {
        val writes = active.bindings.map { binding ->
            val bound = boundBuffers[binding.binding]
            val buffer = bound.buffer
                ?: error("Compute program '${active.label}' expects buffer '${binding.name}' at binding ${binding.binding}.")
            DescriptorSetWrite.BufferWrite(
                targetSet = set,
                binding = binding.binding,
                descriptorType = Convert.descriptorType(binding.kind),
                descriptors = listOf(BufferDescriptorInfo(buffer.buffer, bound.offset, bound.size)),
            )
        }
        if (writes.isNotEmpty()) {
            backend.context.device.updateDescriptorSets(writes)
        }
    }

    private fun requirePipeline(): VulkanComputePipeline =
        pipeline ?: error("No compute pipeline is bound! Call bindPipeline before dispatching.")

    private class BufferBinding {
        var buffer: VulkanBuffer? = null
        var offset: Long = 0L
        var size: Long = 0L
        var kind: BindingKind? = null
    }

    private companion object {
        const val MAX_BINDINGS = 8
    }
}
