package re.lilith.vulkan.api.accel

import org.lwjgl.vulkan.KHRAccelerationStructure
import org.lwjgl.vulkan.VkAccelerationStructureDeviceAddressInfoKHR
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.memory.Buffer
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.resource.VulkanResource

/**
 * A built or buildable acceleration structure together with the buffer it lives in.
 *
 * Closing the structure also closes its backing [storage], so callers only have
 * one lifetime to track.
 */
class AccelerationStructure internal constructor(
    internal val device: LogicalDevice,
    internal val handle: Long,
    val type: AccelerationStructureType,
    val storage: Buffer,
    val sizeBytes: Long,
) : VulkanResource() {

    /**
     * The address an instance record or a shader points at to reach this structure.
     */
    val deviceAddress: Long by lazy {
        pushStack { stack ->
            val info = VkAccelerationStructureDeviceAddressInfoKHR.calloc(stack)
                .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR)
                .accelerationStructure(handle)
            KHRAccelerationStructure.vkGetAccelerationStructureDeviceAddressKHR(device.handle, info)
        }
    }

    override fun closeResource() {
        KHRAccelerationStructure.vkDestroyAccelerationStructureKHR(device.handle, handle, null)
        storage.close()
    }
}
