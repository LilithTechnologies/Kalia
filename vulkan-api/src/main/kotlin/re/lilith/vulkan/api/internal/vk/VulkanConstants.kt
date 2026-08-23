package re.lilith.vulkan.api.internal.vk

import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT

internal object VulkanConstants {
    object QueueCapabilities {
        const val none: Int = 0
        const val graphics: Int = VK10.VK_QUEUE_GRAPHICS_BIT
        const val compute: Int = VK10.VK_QUEUE_COMPUTE_BIT
        const val transfer: Int = VK10.VK_QUEUE_TRANSFER_BIT
        const val sparseBinding: Int = VK10.VK_QUEUE_SPARSE_BINDING_BIT
        const val protectedQueue: Int = VK11.VK_QUEUE_PROTECTED_BIT
    }

    object CommandPoolFlags {
        const val none: Int = 0
        const val transient: Int = VK10.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT
        const val resetCommandBuffer: Int = VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT
        const val protectedPool: Int = VK11.VK_COMMAND_POOL_CREATE_PROTECTED_BIT
    }

    object CommandBufferUsages {
        const val none: Int = 0
        const val oneTimeSubmit: Int = VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT
        const val renderPassContinue: Int = VK10.VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT
        const val simultaneousUse: Int = VK10.VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT
    }

    object PipelineStages {
        const val none: Int = 0
        const val topOfPipe: Int = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
        const val drawIndirect: Int = VK10.VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT
        const val vertexInput: Int = VK10.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT
        const val vertexShader: Int = VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT
        const val tessellationControlShader: Int = VK10.VK_PIPELINE_STAGE_TESSELLATION_CONTROL_SHADER_BIT
        const val tessellationEvaluationShader: Int = VK10.VK_PIPELINE_STAGE_TESSELLATION_EVALUATION_SHADER_BIT
        const val geometryShader: Int = VK10.VK_PIPELINE_STAGE_GEOMETRY_SHADER_BIT
        const val fragmentShader: Int = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
        const val earlyFragmentTests: Int = VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
        const val lateFragmentTests: Int = VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT
        const val colorAttachmentOutput: Int = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
        const val computeShader: Int = VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT
        const val transfer: Int = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT
        const val bottomOfPipe: Int = VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
        const val host: Int = VK10.VK_PIPELINE_STAGE_HOST_BIT
        const val allGraphics: Int = VK10.VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT
        const val allCommands: Int = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT
    }

    object AccessMasks {
        const val none: Int = 0
        const val indirectCommandRead: Int = VK10.VK_ACCESS_INDIRECT_COMMAND_READ_BIT
        const val indexRead: Int = VK10.VK_ACCESS_INDEX_READ_BIT
        const val vertexAttributeRead: Int = VK10.VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT
        const val uniformRead: Int = VK10.VK_ACCESS_UNIFORM_READ_BIT
        const val inputAttachmentRead: Int = VK10.VK_ACCESS_INPUT_ATTACHMENT_READ_BIT
        const val shaderRead: Int = VK10.VK_ACCESS_SHADER_READ_BIT
        const val shaderWrite: Int = VK10.VK_ACCESS_SHADER_WRITE_BIT
        const val colorAttachmentRead: Int = VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
        const val colorAttachmentWrite: Int = VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
        const val depthStencilAttachmentRead: Int = VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
        const val depthStencilAttachmentWrite: Int = VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
        const val transferRead: Int = VK10.VK_ACCESS_TRANSFER_READ_BIT
        const val transferWrite: Int = VK10.VK_ACCESS_TRANSFER_WRITE_BIT
        const val hostRead: Int = VK10.VK_ACCESS_HOST_READ_BIT
        const val hostWrite: Int = VK10.VK_ACCESS_HOST_WRITE_BIT
        const val memoryRead: Int = VK10.VK_ACCESS_MEMORY_READ_BIT
        const val memoryWrite: Int = VK10.VK_ACCESS_MEMORY_WRITE_BIT
    }

    object DependencyFlags {
        const val none: Int = 0
        const val byRegion: Int = VK10.VK_DEPENDENCY_BY_REGION_BIT
        const val viewLocal: Int = VK11.VK_DEPENDENCY_VIEW_LOCAL_BIT
        const val deviceGroup: Int = VK11.VK_DEPENDENCY_DEVICE_GROUP_BIT
    }

    object BufferUsages {
        const val none: Int = 0
        const val transferSource: Int = VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT
        const val transferDestination: Int = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT
        const val uniformTexelBuffer: Int = VK10.VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT
        const val storageTexelBuffer: Int = VK10.VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT
        const val uniformBuffer: Int = VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
        const val storageBuffer: Int = VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
        const val indexBuffer: Int = VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT
        const val vertexBuffer: Int = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
        const val indirectBuffer: Int = VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT
    }

    object ImageUsages {
        const val none: Int = 0
        const val transferSource: Int = VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
        const val transferDestination: Int = VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
        const val sampled: Int = VK10.VK_IMAGE_USAGE_SAMPLED_BIT
        const val storage: Int = VK10.VK_IMAGE_USAGE_STORAGE_BIT
        const val colorAttachment: Int = VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
        const val depthStencilAttachment: Int = VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
        const val transientAttachment: Int = VK10.VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT
        const val inputAttachment: Int = VK10.VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT
    }

    object MemoryPropertyFlags {
        const val none: Int = 0
        const val deviceLocal: Int = VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        const val hostVisible: Int = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
        const val hostCoherent: Int = VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        const val hostCached: Int = VK10.VK_MEMORY_PROPERTY_HOST_CACHED_BIT
        const val lazilyAllocated: Int = VK10.VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT
    }

    object MemoryHeapFlags {
        const val none: Int = 0
        const val deviceLocal: Int = VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT
        const val multiInstance: Int = VK11.VK_MEMORY_HEAP_MULTI_INSTANCE_BIT
    }

    object ImageAspects {
        const val none: Int = 0
        const val color: Int = VK10.VK_IMAGE_ASPECT_COLOR_BIT
        const val depth: Int = VK10.VK_IMAGE_ASPECT_DEPTH_BIT
        const val stencil: Int = VK10.VK_IMAGE_ASPECT_STENCIL_BIT
        const val metadata: Int = VK10.VK_IMAGE_ASPECT_METADATA_BIT
    }

    object SampleCounts {
        const val x1: Int = VK10.VK_SAMPLE_COUNT_1_BIT
        const val x2: Int = VK10.VK_SAMPLE_COUNT_2_BIT
        const val x4: Int = VK10.VK_SAMPLE_COUNT_4_BIT
        const val x8: Int = VK10.VK_SAMPLE_COUNT_8_BIT
        const val x16: Int = VK10.VK_SAMPLE_COUNT_16_BIT
        const val x32: Int = VK10.VK_SAMPLE_COUNT_32_BIT
        const val x64: Int = VK10.VK_SAMPLE_COUNT_64_BIT
    }

    object ImageFlags {
        const val cubemapCompatible: Int = VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT
    }

    object Formats {
        const val undefined: Int = VK10.VK_FORMAT_UNDEFINED
        const val r8Unorm: Int = VK10.VK_FORMAT_R8_UNORM
        const val r8g8Unorm: Int = VK10.VK_FORMAT_R8G8_UNORM
        const val r8g8b8a8Snorm: Int = VK10.VK_FORMAT_R8G8B8A8_SNORM
        const val r8g8b8a8Sint: Int = VK10.VK_FORMAT_R8G8B8A8_SINT
        const val r8g8b8a8Uint: Int = VK10.VK_FORMAT_R8G8B8A8_UINT
        const val r8g8b8a8Unorm: Int = VK10.VK_FORMAT_R8G8B8A8_UNORM
        const val b8g8r8a8Unorm: Int = VK10.VK_FORMAT_B8G8R8A8_UNORM
        const val r16g16Sint: Int = VK10.VK_FORMAT_R16G16_SINT
        const val r16g16Uint: Int = VK10.VK_FORMAT_R16G16_UINT
        const val r16g16Unorm: Int = VK10.VK_FORMAT_R16G16_UNORM
        const val r16g16Uscaled: Int = VK10.VK_FORMAT_R16G16_USCALED
        const val r16g16b16a16Sint: Int = VK10.VK_FORMAT_R16G16B16A16_SINT
        const val r16g16b16a16Sfloat: Int = VK10.VK_FORMAT_R16G16B16A16_SFLOAT
        const val r32Uint: Int = VK10.VK_FORMAT_R32_UINT
        const val r32Sfloat: Int = VK10.VK_FORMAT_R32_SFLOAT
        const val r32g32Uint: Int = VK10.VK_FORMAT_R32G32_UINT
        const val r32g32Sfloat: Int = VK10.VK_FORMAT_R32G32_SFLOAT
        const val r32g32b32Sfloat: Int = VK10.VK_FORMAT_R32G32B32_SFLOAT
        const val r32g32b32a32Sfloat: Int = VK10.VK_FORMAT_R32G32B32A32_SFLOAT
        const val d32Sfloat: Int = VK10.VK_FORMAT_D32_SFLOAT
        const val d24UnormS8Uint: Int = VK10.VK_FORMAT_D24_UNORM_S8_UINT
        const val d32SfloatS8Uint: Int = VK10.VK_FORMAT_D32_SFLOAT_S8_UINT
    }

    object ImageTypes {
        const val image1D: Int = VK10.VK_IMAGE_TYPE_1D
        const val image2D: Int = VK10.VK_IMAGE_TYPE_2D
        const val image3D: Int = VK10.VK_IMAGE_TYPE_3D
    }

    object ImageViewTypes {
        const val image1D: Int = VK10.VK_IMAGE_VIEW_TYPE_1D
        const val image2D: Int = VK10.VK_IMAGE_VIEW_TYPE_2D
        const val image3D: Int = VK10.VK_IMAGE_VIEW_TYPE_3D
        const val cube: Int = VK10.VK_IMAGE_VIEW_TYPE_CUBE
        const val image1DArray: Int = VK10.VK_IMAGE_VIEW_TYPE_1D_ARRAY
        const val image2DArray: Int = VK10.VK_IMAGE_VIEW_TYPE_2D_ARRAY
        const val cubeArray: Int = VK10.VK_IMAGE_VIEW_TYPE_CUBE_ARRAY
    }

    object ImageTilings {
        const val optimal: Int = VK10.VK_IMAGE_TILING_OPTIMAL
        const val linear: Int = VK10.VK_IMAGE_TILING_LINEAR
    }

    object ImageLayouts {
        const val undefined: Int = VK10.VK_IMAGE_LAYOUT_UNDEFINED
        const val general: Int = VK10.VK_IMAGE_LAYOUT_GENERAL
        const val colorAttachmentOptimal: Int = VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        const val depthStencilAttachmentOptimal: Int = VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        const val depthStencilReadOnlyOptimal: Int = VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
        const val shaderReadOnlyOptimal: Int = VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
        const val transferSourceOptimal: Int = VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
        const val transferDestinationOptimal: Int = VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
        const val preinitialized: Int = VK10.VK_IMAGE_LAYOUT_PREINITIALIZED
        const val presentSource: Int = KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
    }

    object AttachmentLoadOps {
        const val load: Int = VK10.VK_ATTACHMENT_LOAD_OP_LOAD
        const val clear: Int = VK10.VK_ATTACHMENT_LOAD_OP_CLEAR
        const val dontCare: Int = VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE
    }

    object AttachmentStoreOps {
        const val store: Int = VK10.VK_ATTACHMENT_STORE_OP_STORE
        const val dontCare: Int = VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE
    }

    object ComponentSwizzles {
        const val identity: Int = VK10.VK_COMPONENT_SWIZZLE_IDENTITY
        const val zero: Int = VK10.VK_COMPONENT_SWIZZLE_ZERO
        const val one: Int = VK10.VK_COMPONENT_SWIZZLE_ONE
        const val r: Int = VK10.VK_COMPONENT_SWIZZLE_R
        const val g: Int = VK10.VK_COMPONENT_SWIZZLE_G
        const val b: Int = VK10.VK_COMPONENT_SWIZZLE_B
        const val a: Int = VK10.VK_COMPONENT_SWIZZLE_A
    }

    object IndexTypes {
        const val unsignedShort: Int = VK10.VK_INDEX_TYPE_UINT16
        const val unsignedInt: Int = VK10.VK_INDEX_TYPE_UINT32
        const val unsignedByte: Int = EXTIndexTypeUint8.VK_INDEX_TYPE_UINT8_EXT
    }

    object SubpassContents {
        const val inline: Int = VK10.VK_SUBPASS_CONTENTS_INLINE
        const val secondaryCommandBuffers: Int = VK10.VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS
    }

    object ResolveModes {
        const val none: Int = VK12.VK_RESOLVE_MODE_NONE
    }

    object PhysicalDeviceTypes {
        const val other: Int = VK10.VK_PHYSICAL_DEVICE_TYPE_OTHER
        const val integratedGpu: Int = VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU
        const val discreteGpu: Int = VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU
        const val virtualGpu: Int = VK10.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU
        const val cpu: Int = VK10.VK_PHYSICAL_DEVICE_TYPE_CPU
    }

    object Subpasses {
        const val external: Int = VK10.VK_SUBPASS_EXTERNAL
    }
}
