package re.lilith.kalia.renderer.headless

import re.lilith.kalia.renderer.device.BackendId
import re.lilith.kalia.renderer.device.DeviceCapabilities
import re.lilith.kalia.renderer.device.DeviceSettings
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.resource.TextureDescription
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderStage

class HeadlessRenderDevice : RenderDevice {
    override val capabilities = DeviceCapabilities(
        backend = BackendId.Headless,
        adapterName = "Headless",
        driverVersion = "1.0.0",
        apiVersion = "1.0.0",
        vendorName = "Lilith Technologies",
        2048,
        2048,
        true,
        16f,
        listOf(TextureFormat.DEPTH24_STENCIL8, TextureFormat.DEPTH32F_STENCIL8, TextureFormat.DEPTH32F),
        3
    )
    override var surfaceExtent = Extent(1920, 1080)
    override val surfaceFormat = TextureFormat.RGBA8
    override var settings = DeviceSettings()
    private var closed = false
    private val executor = HeadlessGraphExecutor(this)

    override fun createBuffer(description: BufferDescription): GpuBuffer {
        require(description.sizeBytes > 0) {
            "Buffer '${description.label}' must have a positive size."
        }

        return HeadlessBuffer(
            label = description.label,
            sizeBytes = description.sizeBytes,
            usage = description.usage,
        )
    }

    override fun createTexture(description: TextureDescription): GpuTexture {
        require(description.extent.width > 0) {
            "Texture '${description.label}' width must be greater than zero."
        }

        require(description.extent.height > 0) {
            "Texture '${description.label}' height must be greater than zero."
        }

        require(description.mipLevels > 0) {
            "Texture '${description.label}' must have at least one mip level."
        }

        require(description.layers > 0) {
            "Texture '${description.label}' must have at least one layer."
        }

        require(description.extent.width <= capabilities.maxTextureSize) {
            "Texture '${description.label}' width exceeds device limit."
        }

        require(description.extent.height <= capabilities.maxTextureSize) {
            "Texture '${description.label}' height exceeds device limit."
        }

        val maxMipLevels = Integer.SIZE - Integer.numberOfLeadingZeros(
            maxOf(description.extent.width, description.extent.height)
        )

        require(description.mipLevels <= maxMipLevels) {
            "Texture '${description.label}' requests ${description.mipLevels} mip levels, but only $maxMipLevels are possible for ${description.extent.width}x${description.extent.height}."
        }

        return HeadlessTexture(
            label = description.label,
            extent = description.extent,
            format = description.format,
            mipLevels = description.mipLevels,
            layers = description.layers,
        )
    }

    override fun createSampler(description: SamplerDescription): GpuSampler {
        require(description.maxAnisotropy >= 1f) {
            "Sampler '${description.label}' maxAnisotropy must be at least 1."
        }

        require(description.maxLod >= 0f) {
            "Sampler '${description.label}' maxLod cannot be negative."
        }

        if (!capabilities.supportsAnisotropy) {
            require(description.maxAnisotropy <= 1f) {
                "Sampler '${description.label}' requests anisotropic filtering but the device does not support it."
            }
        } else {
            require(description.maxAnisotropy <= capabilities.maxAnisotropy) {
                "Sampler '${description.label}' requests anisotropy ${description.maxAnisotropy}, " +
                        "but the device limit is ${capabilities.maxAnisotropy}."
            }
        }

        return HeadlessSampler(description.label)
    }

    override fun createPipeline(
        description: GraphicsPipelineDescription
    ): GpuPipeline {

        val program = description.program

        require(program.stages.isNotEmpty()) {
            "Program '${program.label}' contains no shader stages."
        }

        require(program.stages.containsKey(ShaderStage.VERTEX)) {
            "Program '${program.label}' is missing a vertex shader."
        }

        require(program.stages.containsKey(ShaderStage.FRAGMENT)) {
            "Program '${program.label}' is missing a fragment shader."
        }

        require(program.pushConstantBytes >= 0) {
            "Program '${program.label}' has a negative push constant size."
        }

        require(program.pushConstantBytes <= ShaderProgram.MAX_PUSH_CONSTANT_BYTES) {
            "Program '${program.label}' requests ${program.pushConstantBytes} bytes of push constants, but the limit is ${ShaderProgram.MAX_PUSH_CONSTANT_BYTES}."
        }

        val seenBindings = HashSet<Pair<Int, BindingKind>>()

        for (binding in program.bindings) {
            require(binding.binding >= 0) {
                "Binding '${binding.name}' in '${program.label}' uses a negative binding index."
            }

            require(seenBindings.add(binding.binding to binding.kind)) {
                "Program '${program.label}' contains duplicate ${binding.kind.name.lowercase()} binding ${binding.binding}."
            }
        }

        return HeadlessPipeline(
            label = program.label,
            description = description,
        )
    }

    override fun copyBuffer(
        source: GpuBuffer,
        destination: GpuBuffer,
        sourceOffset: Long,
        destinationOffset: Long,
        sizeBytes: Long,
    ) {
        require(source is HeadlessBuffer) {
            "Source buffer must be a HeadlessBuffer."
        }

        require(destination is HeadlessBuffer) {
            "Destination buffer must be a HeadlessBuffer."
        }

        check(!source.isClosed) {
            "Source buffer '${source.label}' is closed."
        }

        check(!destination.isClosed) {
            "Destination buffer '${destination.label}' is closed."
        }

        require(sizeBytes >= 0) {
            "Copy size cannot be negative."
        }

        require(sourceOffset >= 0) {
            "Source offset cannot be negative."
        }

        require(destinationOffset >= 0) {
            "Destination offset cannot be negative."
        }

        require(sourceOffset + sizeBytes <= source.sizeBytes) {
            "Copy reads beyond source buffer '${source.label}'."
        }

        require(destinationOffset + sizeBytes <= destination.sizeBytes) {
            "Copy writes beyond destination buffer '${destination.label}'."
        }
    }

    override fun render(graph: RenderGraph): Boolean {
        executor.execute(graph)

        return true
    }

    override fun resize(extent: Extent) {
        require(extent.width > 0) { "Width must be greater than zero." }
        require(extent.height > 0) { "Height must be greater than zero." }

        surfaceExtent = extent
    }

    override fun waitIdle() = Unit
    override fun close() {
        require(!closed) { "A render device should not be closed twice." }
        closed = true
    }
}