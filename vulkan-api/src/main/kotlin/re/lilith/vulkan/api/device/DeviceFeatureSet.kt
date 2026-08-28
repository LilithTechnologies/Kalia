package re.lilith.vulkan.api.device

data class DeviceFeatureSet(
    val robustBufferAccess: Boolean = false,
    val samplerAnisotropy: Boolean = false,
    val sampleRateShading: Boolean = false,
    val geometryShader: Boolean = false,
    val tessellationShader: Boolean = false,
    val fillModeNonSolid: Boolean = false,
    val wideLines: Boolean = false,
    val logicOp: Boolean = false,
    val multiDrawIndirect: Boolean = false,
    val drawIndirectFirstInstance: Boolean = false,
    val shaderDrawParameters: Boolean = false,
    val shaderFloat64: Boolean = false,
    val shaderInt64: Boolean = false,
    val shaderInt16: Boolean = false,
    val descriptorIndexing: Boolean = false,
    val descriptorBindingPartiallyBound: Boolean = false,
    val descriptorBindingVariableDescriptorCount: Boolean = false,
    val descriptorBindingUpdateUnusedWhilePending: Boolean = false,
    val shaderSampledImageArrayNonUniformIndexing: Boolean = false,
    val runtimeDescriptorArray: Boolean = false,
    val bufferDeviceAddress: Boolean = false,
    val timelineSemaphore: Boolean = false,
    val synchronization2: Boolean = false,
    val dynamicRendering: Boolean = false,
    val imagelessFramebuffer: Boolean = false,
    val pushDescriptors: Boolean = false,
    val multiDraw: Boolean = false,
    /**
     * Whether acceleration structures can be built and traced. Implies
     * [bufferDeviceAddress] and `VK_KHR_deferred_host_operations`.
     */
    val accelerationStructure: Boolean = false,
    /**
     * Whether `rayQueryEXT` is usable from ordinary graphics and compute stages.
     * Requires [accelerationStructure].
     */
    val rayQuery: Boolean = false,
)
