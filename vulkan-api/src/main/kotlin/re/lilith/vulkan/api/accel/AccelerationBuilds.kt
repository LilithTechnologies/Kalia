package re.lilith.vulkan.api.accel

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRAccelerationStructure
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkAccelerationStructureBuildGeometryInfoKHR
import org.lwjgl.vulkan.VkAccelerationStructureBuildRangeInfoKHR
import org.lwjgl.vulkan.VkAccelerationStructureBuildSizesInfoKHR
import org.lwjgl.vulkan.VkAccelerationStructureCreateInfoKHR
import org.lwjgl.vulkan.VkAccelerationStructureGeometryKHR
import org.lwjgl.vulkan.VkDeviceOrHostAddressConstKHR
import org.lwjgl.vulkan.VkMemoryBarrier
import re.lilith.vulkan.api.command.CommandRecorder
import re.lilith.vulkan.api.device.LogicalDevice
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.memory.BufferConfig
import re.lilith.vulkan.api.memory.MemoryAllocator
import re.lilith.vulkan.api.memory.MemoryUsage
import re.lilith.vulkan.api.qol.pushStack
import re.lilith.vulkan.api.types.flags.BufferUsage

/**
 * Alignment `VkAccelerationStructureBuildGeometryInfoKHR::scratchData` requires.
 * The real value comes from `minAccelerationStructureScratchOffsetAlignment`; 256
 * satisfies every implementation shipped so far and avoids a properties query on
 * every allocation.
 */
const val ACCELERATION_SCRATCH_ALIGNMENT: Long = 256L

private fun MemoryStack.encodeGeometries(
    info: AccelerationBuildInfo,
): VkAccelerationStructureGeometryKHR.Buffer {
    val geometries = VkAccelerationStructureGeometryKHR.calloc(info.geometries.size, this)
    info.geometries.forEachIndexed { index, geometry ->
        val target = geometries[index]
            .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR)

        when (geometry) {
            is AccelerationGeometry.Triangles -> {
                target.geometryType(KHRAccelerationStructure.VK_GEOMETRY_TYPE_TRIANGLES_KHR)
                target.flags(
                    if (geometry.opaque) KHRAccelerationStructure.VK_GEOMETRY_OPAQUE_BIT_KHR else 0,
                )
                target.geometry().triangles()
                    .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR)
                    .vertexFormat(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                    .vertexData(
                        VkDeviceOrHostAddressConstKHR.calloc(this)
                            .deviceAddress(geometry.vertexBuffer.deviceAddress + geometry.vertexOffset),
                    )
                    .vertexStride(geometry.vertexStride)
                    .maxVertex(geometry.vertexCount - 1)
                    .indexType(VK10.VK_INDEX_TYPE_UINT32)
                    .indexData(
                        VkDeviceOrHostAddressConstKHR.calloc(this)
                            .deviceAddress(geometry.indexBuffer.deviceAddress + geometry.indexOffset),
                    )
                    .transformData(VkDeviceOrHostAddressConstKHR.calloc(this).deviceAddress(0L))
            }

            is AccelerationGeometry.Instances -> {
                target.geometryType(KHRAccelerationStructure.VK_GEOMETRY_TYPE_INSTANCES_KHR)
                target.geometry().instances()
                    .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR)
                    .arrayOfPointers(false)
                    .data(
                        VkDeviceOrHostAddressConstKHR.calloc(this)
                            .deviceAddress(geometry.buffer.deviceAddress + geometry.offset),
                    )
            }
        }
    }
    return geometries
}

private fun MemoryStack.buildGeometryInfo(
    info: AccelerationBuildInfo,
    geometries: VkAccelerationStructureGeometryKHR.Buffer,
): VkAccelerationStructureBuildGeometryInfoKHR {
    var flags = if (info.preferFastTrace) {
        KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR
    } else {
        KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_BUILD_BIT_KHR
    }
    if (info.allowUpdate) {
        flags = flags or KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_UPDATE_BIT_KHR
    }

    return VkAccelerationStructureBuildGeometryInfoKHR.calloc(this)
        .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
        .type(
            when (info.type) {
                AccelerationStructureType.BottomLevel ->
                    KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR

                AccelerationStructureType.TopLevel ->
                    KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR
            },
        )
        .flags(flags)
        .pGeometries(geometries)
        .geometryCount(geometries.remaining())
}

/**
 * Asks the driver how much memory [info] would need, before anything is allocated.
 */
fun LogicalDevice.accelerationBuildSizes(info: AccelerationBuildInfo): AccelerationBuildSizes = pushStack { stack ->
    val geometries = stack.encodeGeometries(info)
    val geometryInfo = stack.buildGeometryInfo(info, geometries)

    val sizes = VkAccelerationStructureBuildSizesInfoKHR.calloc(stack)
        .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR)

    KHRAccelerationStructure.vkGetAccelerationStructureBuildSizesKHR(
        handle,
        KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
        geometryInfo,
        info.primitiveCounts,
        sizes,
    )

    AccelerationBuildSizes(
        structureBytes = sizes.accelerationStructureSize(),
        buildScratchBytes = sizes.buildScratchSize(),
        updateScratchBytes = sizes.updateScratchSize(),
    )
}

/**
 * Allocates the storage buffer for a structure of [sizeBytes] and creates the
 * structure on top of it. The result is empty until a build is recorded into it.
 */
fun MemoryAllocator.createAccelerationStructure(
    device: LogicalDevice,
    type: AccelerationStructureType,
    sizeBytes: Long,
): AccelerationStructure {
    require(sizeBytes > 0L) { "Acceleration structure size must be positive." }

    val storage = createBuffer(
        BufferConfig(
            size = sizeBytes,
            usage = BufferUsage.AccelerationStructureStorage + BufferUsage.ShaderDeviceAddress,
        ),
        MemoryUsage.GpuOnly,
    )

    return pushStack { stack ->
        val createInfo = VkAccelerationStructureCreateInfoKHR.calloc(stack)
            .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
            .buffer(storage.handle)
            .offset(0L)
            .size(sizeBytes)
            .type(
                when (type) {
                    AccelerationStructureType.BottomLevel ->
                        KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR

                    AccelerationStructureType.TopLevel ->
                        KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR
                },
            )

        val pointer = stack.mallocLong(1)
        try {
            checkVulkanResult(
                KHRAccelerationStructure.vkCreateAccelerationStructureKHR(device.handle, createInfo, null, pointer),
                "Creating acceleration structure",
            )
        } catch (failure: Throwable) {
            storage.close()
            throw failure
        }

        AccelerationStructure(device, pointer[0], type, storage, sizeBytes)
    }
}

/**
 * Orders acceleration structure builds against whatever reads them next.
 *
 * With [traceable] set the dependency reaches the shader stages that trace the
 * structure; otherwise it only orders one build against the next, which is what
 * lets a batch of builds share a single scratch allocation.
 */
fun CommandRecorder.accelerationStructureBarrier(traceable: Boolean = false): CommandRecorder = apply {
    pushStack { stack ->
        val barriers = VkMemoryBarrier.calloc(1, stack)
            .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_BARRIER)
            .srcAccessMask(KHRAccelerationStructure.VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
            .dstAccessMask(
                KHRAccelerationStructure.VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR or
                        KHRAccelerationStructure.VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR,
            )

        VK10.vkCmdPipelineBarrier(
            commandBuffer.handle,
            KHRAccelerationStructure.VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
            if (traceable) {
                VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT or VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT
            } else {
                KHRAccelerationStructure.VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR
            },
            0,
            barriers,
            null,
            null,
        )
    }
}

/**
 * Records a build into [target].
 *
 * Passing [source] records an update (refit) instead of a full rebuild, which is
 * only legal when the structure was built with [AccelerationBuildInfo.allowUpdate]
 * and the topology has not changed. [scratchAddress] must be aligned to
 * [ACCELERATION_SCRATCH_ALIGNMENT] and point at a region at least as large as the
 * matching scratch size reported by [accelerationBuildSizes].
 */
fun CommandRecorder.buildAccelerationStructure(
    target: AccelerationStructure,
    info: AccelerationBuildInfo,
    scratchAddress: Long,
    source: AccelerationStructure? = null,
): CommandRecorder = apply {
    require(target.type == info.type) {
        "Build describes a ${info.type} structure but targets a ${target.type} one."
    }
    require(scratchAddress % ACCELERATION_SCRATCH_ALIGNMENT == 0L) {
        "Scratch address must be aligned to $ACCELERATION_SCRATCH_ALIGNMENT bytes."
    }
    require(source == null || info.allowUpdate) {
        "Updating a structure requires it to have been built with allowUpdate."
    }

    pushStack { stack ->
        val geometries = stack.encodeGeometries(info)
        val geometryInfo = stack.buildGeometryInfo(info, geometries)
            .mode(
                if (source == null) {
                    KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR
                } else {
                    KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_MODE_UPDATE_KHR
                },
            )
            .srcAccelerationStructure(source?.handle ?: VK10.VK_NULL_HANDLE)
            .dstAccelerationStructure(target.handle)
        geometryInfo.scratchData().deviceAddress(scratchAddress)

        val ranges = VkAccelerationStructureBuildRangeInfoKHR.calloc(info.geometries.size, stack)
        info.geometries.forEachIndexed { index, geometry ->
            val range = ranges[index].firstVertex(0).primitiveOffset(0).transformOffset(0)
            range.primitiveCount(
                when (geometry) {
                    is AccelerationGeometry.Triangles -> geometry.triangleCount
                    is AccelerationGeometry.Instances -> geometry.count
                },
            )
        }

        val infos = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
        infos.put(0, geometryInfo)

        KHRAccelerationStructure.vkCmdBuildAccelerationStructuresKHR(
            commandBuffer.handle,
            infos,
            stack.pointers(ranges),
        )
    }
}
