package re.lilith.kalia.frame

import re.lilith.kalia.buffer.SharedIndexBuffer
import re.lilith.kalia.buffer.StreamArena
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.device.RenderStats
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.resource.TextureDescription
import re.lilith.kalia.shader.SceneUniformRing
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FrameResources private constructor(val device: RenderDevice) : AutoCloseable {
    private val streams = List(device.capabilities.framesInFlight.coerceAtLeast(1)) { index ->
        Streams(
            vertexArena = StreamArena(device, "kalia/vertices$index", vertex = true),
            sceneUniforms = SceneUniformRing(device),
        )
    }

    private var streamIndex = 0

    val vertexArena: StreamArena get() = streams[streamIndex].vertexArena

    val sceneUniforms: SceneUniformRing get() = streams[streamIndex].sceneUniforms

    val indices = SharedIndexBuffer(device)

    val whiteTexture: GpuTexture = device.createTexture(
        TextureDescription(
            label = "kalia/white",
            extent = Extent(1, 1),
            format = device.surfaceFormat,
            sampled = true,
            renderTarget = false,
            transferable = true,
        ),
    ).apply {
        upload(
            ByteBuffer.allocateDirect(device.surfaceFormat.bytesPerPixel)
                .order(ByteOrder.nativeOrder())
                .apply {
                    repeat(device.surfaceFormat.bytesPerPixel) { put(0xFF.toByte()) }
                    flip()
                },
        )
    }

    val defaultSampler: GpuSampler = device.createSampler(SamplerDescription.NEAREST_CLAMP)

    private val samplerCache = HashMap<SamplerDescription, GpuSampler>()

    private var memoDescription: SamplerDescription? = null
    private var memoSampler: GpuSampler? = null

    fun sampler(description: SamplerDescription): GpuSampler {
        val cached = memoSampler
        if (cached != null && memoDescription === description) {
            return cached
        }
        val resolved = samplerCache.getOrPut(description) { device.createSampler(description) }
        memoDescription = description
        memoSampler = resolved
        return resolved
    }

    fun beginFrame() {
        RenderStats.beginFrame()
        streamIndex = (streamIndex + 1) % streams.size
        vertexArena.reset()
        sceneUniforms.beginFrame()
    }

    override fun close() {
        streams.forEach { stream ->
            stream.vertexArena.close()
            stream.sceneUniforms.close()
        }
        indices.close()
        whiteTexture.close()
        samplerCache.values.forEach { it.close() }
    }

    private class Streams(val vertexArena: StreamArena, val sceneUniforms: SceneUniformRing)

    companion object {
        private var current: FrameResources? = null

        fun of(device: RenderDevice): FrameResources {
            val existing = current
            if (existing != null && existing.device === device) {
                return existing
            }
            existing?.close()
            return FrameResources(device).also { current = it }
        }

        fun release() {
            current?.close()
            current = null
        }
    }
}
