package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuTexture

@RenderGraphDsl
class RenderGraphBuilder internal constructor(private val graphName: String) {
    private val textures = mutableListOf<GraphTexture>()
    private val passes = mutableListOf<GraphPass>()
    private var nextId = 1

    fun texture(
        name: String,
        format: TextureFormat = TextureFormat.RGBA8,
        sizing: TextureSizing = TextureSizing.RelativeToBackbuffer(1f),
        mipLevels: Int = 1,
    ): TextureHandle {
        require(format.isColor) { "Texture '$name' uses a depth format; use depthTexture instead." }
        return declare(name, format, sizing, mipLevels, imported = null)
    }

    fun depthTexture(
        name: String,
        format: TextureFormat = TextureFormat.DEPTH32F,
        sizing: TextureSizing = TextureSizing.RelativeToBackbuffer(1f),
    ): TextureHandle {
        require(format.isDepth) { "Depth texture '$name' needs a depth format." }
        return declare(name, format, sizing, mipLevels = 1, imported = null)
    }

    /**
     * Brings an externally owned texture into the graph
     */
    fun import(name: String, texture: GpuTexture): TextureHandle =
        declare(name, texture.format, TextureSizing.Fixed(texture.extent), texture.mipLevels, texture)

    fun scaledTexture(name: String, factor: Float, format: TextureFormat = TextureFormat.RGBA8): TextureHandle =
        texture(name, format, TextureSizing.RelativeToBackbuffer(factor))

    fun fixedTexture(name: String, extent: Extent, format: TextureFormat = TextureFormat.RGBA8): TextureHandle =
        texture(name, format, TextureSizing.Fixed(extent))

    fun pass(name: String, build: PassBuilder.() -> Unit) {
        passes += PassBuilder(name).apply(build).build()
    }

    internal fun addPass(pass: GraphPass) {
        passes += pass
    }

    private fun declare(
        name: String,
        format: TextureFormat,
        sizing: TextureSizing,
        mipLevels: Int,
        imported: GpuTexture?,
    ): TextureHandle {
        val handle = TextureHandle(nextId++)
        textures += GraphTexture(handle, name, format, sizing, mipLevels, imported)
        return handle
    }

    internal fun build(): RenderGraph = RenderGraph(graphName, textures.toList(), passes.toList())
}

