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

    private var gameSlot = 0
    private var renderSlot = 0

    private val onRenderThread: Boolean get() = Thread.currentThread() === RenderThreadRef.thread

    fun bindSlot(slot: Int) {
        val bounded = Math.floorMod(slot, streams.size)
        if (onRenderThread) renderSlot = bounded else gameSlot = bounded
    }

    private val streamIndex: Int get() = if (onRenderThread) renderSlot else gameSlot

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

    private val samplerLock = Any()
    private val samplerCache = HashMap<SamplerDescription, GpuSampler>()
    private val gameSamplerMemo = SamplerMemo()
    private val renderSamplerMemo = SamplerMemo()

    fun sampler(description: SamplerDescription): GpuSampler {
        val memo = if (onRenderThread) renderSamplerMemo else gameSamplerMemo
        val cached = memo.sampler
        if (cached != null && memo.description === description) {
            return cached
        }
        val resolved = synchronized(samplerLock) {
            samplerCache.getOrPut(description) { device.createSampler(description) }
        }
        memo.description = description
        memo.sampler = resolved
        return resolved
    }

    fun beginFrame(slot: Int) {
        bindSlot(slot)
        RenderStats.beginFrame()
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
        synchronized(samplerLock) {
            samplerCache.values.forEach { it.close() }
            samplerCache.clear()
        }
    }

    private class Streams(val vertexArena: StreamArena, val sceneUniforms: SceneUniformRing)

    companion object {
        @Volatile
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
