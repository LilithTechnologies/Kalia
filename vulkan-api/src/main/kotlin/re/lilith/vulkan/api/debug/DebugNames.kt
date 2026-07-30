package re.lilith.vulkan.api.debug

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.EXTDebugUtils
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkDebugUtilsLabelEXT
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT
import re.lilith.vulkan.api.command.CommandRecorder
import re.lilith.vulkan.api.descriptor.Sampler
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.memory.Buffer
import re.lilith.vulkan.api.memory.Image
import re.lilith.vulkan.api.memory.ImageView
import re.lilith.vulkan.api.pipeline.ComputePipeline
import re.lilith.vulkan.api.pipeline.GraphicsPipeline

object DebugNames {
    private const val DEBUG_UTILS = "VK_EXT_debug_utils"

    fun isSupported(device: LogicalDevice): Boolean =
        DEBUG_UTILS in device.physicalDevice.instance.config.enabledExtensions

    fun set(device: LogicalDevice, buffer: Buffer, name: String) =
        name(device, VK10.VK_OBJECT_TYPE_BUFFER, buffer.handle, name)

    fun set(device: LogicalDevice, image: Image, name: String) =
        name(device, VK10.VK_OBJECT_TYPE_IMAGE, image.handle, name)

    fun set(device: LogicalDevice, view: ImageView, name: String) =
        name(device, VK10.VK_OBJECT_TYPE_IMAGE_VIEW, view.handle, name)

    fun set(device: LogicalDevice, pipeline: GraphicsPipeline, name: String) =
        name(device, VK10.VK_OBJECT_TYPE_PIPELINE, pipeline.handle, name)

    fun set(device: LogicalDevice, pipeline: ComputePipeline, name: String) =
        name(device, VK10.VK_OBJECT_TYPE_PIPELINE, pipeline.handle, name)

    fun set(device: LogicalDevice, sampler: Sampler, name: String) =
        name(device, VK10.VK_OBJECT_TYPE_SAMPLER, sampler.handle, name)

    private fun name(device: LogicalDevice, objectType: Int, handle: Long, label: String) {
        if (handle == VK10.VK_NULL_HANDLE || !isSupported(device)) {
            return
        }
        MemoryStack.stackPush().use { stack ->
            val info = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                .sType(EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_OBJECT_NAME_INFO_EXT)
                .objectType(objectType)
                .objectHandle(handle)
                .pObjectName(stack.UTF8(label))
            EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device.handle, info)
        }
    }
}

fun CommandRecorder.beginDebugLabel(name: String) {
    if (!DebugNames.isSupported(commandBuffer.device)) {
        return
    }
    val commandBuffer = commandBuffer.handle
    MemoryStack.stackPush().use { stack ->
        val label = VkDebugUtilsLabelEXT.calloc(stack)
            .sType(EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_LABEL_EXT)
            .pLabelName(stack.UTF8(name))
        EXTDebugUtils.vkCmdBeginDebugUtilsLabelEXT(commandBuffer, label)
    }
}

fun CommandRecorder.endDebugLabel() {
    if (!DebugNames.isSupported(commandBuffer.device)) {
        return
    }
    EXTDebugUtils.vkCmdEndDebugUtilsLabelEXT(commandBuffer.handle)
}
