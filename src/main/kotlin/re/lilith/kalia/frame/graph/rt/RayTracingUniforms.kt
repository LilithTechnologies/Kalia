package re.lilith.kalia.frame.graph.rt

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The scene description every ray tracing stage reads.
 *
 * This started life as push constants, which Vulkan only guarantees 128 bytes of
 * and which the lighting model outgrew as soon as it stopped being a single
 * bounce. A uniform buffer has no such ceiling, and letting the trace and the
 * lighting pass read the same one keeps them from disagreeing about where the sun
 * is.
 *
 * One buffer per frame slot, written while the GPU is known to be finished with
 * that slot.
 */
internal class RayTracingUniforms(private val device: RenderDevice) : AutoCloseable {

    private var buffers: Array<GpuBuffer> = emptyArray()

    fun buffer(slot: Int): GpuBuffer? = buffers.getOrNull(slot)

    /**
     * Writes the current frame's scene into [slot]'s buffer.
     */
    fun update(slot: Int, scene: TraceableScene, traceExtent: TraceExtent) {
        if (buffers.isEmpty()) {
            allocate()
        }
        val target = buffers.getOrNull(slot) ?: return
        val mapped = target.mapped()?.order(ByteOrder.nativeOrder()) ?: return

        val frame = RayTracingFrame
        val quality = frame.quality

        mapped.clear()
        frame.inverseViewProjection.get(matrixScratch)
        matrixScratch.forEach(mapped::putFloat)

        vec4(mapped, scene.offsetX, scene.offsetY, scene.offsetZ, frame.frameIndex.toFloat())
        vec4(mapped, frame.sunX, frame.sunY, frame.sunZ, frame.sunStrength)
        sunColour(mapped, frame)
        skyColour(mapped, frame)
        vec4(
            mapped,
            frame.environment.red,
            frame.environment.green,
            frame.environment.blue,
            frame.skyLight,
        )
        vec4(
            mapped,
            quality.diffuseRays.toFloat(),
            quality.bounces.toFloat(),
            quality.rangeBlocks,
            frame.emissiveIntensity,
        )
        vec4(
            mapped,
            if (frame.reflections) 1f else 0f,
            frame.depthA,
            frame.depthB,
            frame.blockLightIntensity,
        )
        vec4(
            mapped,
            frame.indirectIntensity,
            frame.occlusionIntensity,
            frame.exposure,
            frame.debugView.ordinal.toFloat(),
        )
        vec4(
            mapped,
            traceExtent.texelX,
            traceExtent.texelY,
            if (frame.reflections) frame.reflectionIntensity else 0f,
            0f,
        )
        vec4(
            mapped,
            frame.fogStart,
            frame.fogEnd,
            frame.fogDensity,
            frame.fogMode.toFloat(),
        )

        vec4(mapped, frame.trueSunX, frame.trueSunY, frame.trueSunZ, frame.cameraAltitude)

        // Writing fewer vectors than the block declares would leave the shader
        // reading whatever was in the tail, which is far harder to notice than
        // writing too many. Both are caught here.
        check(mapped.position() == BYTES) {
            "The ray tracing scene block is $BYTES bytes but ${mapped.position()} were written."
        }
    }

    /**
     * Sunlight is warm near the horizon and neutral overhead; moonlight is cool
     * and much dimmer. Both are folded into one colour so the shaders never have
     * to know which one is up.
     */
    private fun sunColour(target: ByteBuffer, frame: RayTracingFrame) {
        val horizon = 1f - (frame.sunY.coerceIn(0f, 1f))
        val warmth = horizon * horizon
        if (frame.night) {
            vec4(target, 0.55f, 0.68f, 1f, frame.sunIntensity)
        } else {
            vec4(
                target,
                1f,
                1f - warmth * 0.35f,
                1f - warmth * 0.65f,
                frame.sunIntensity,
            )
        }
    }

    /**
     * The sky's own colour drives ambient light, so an overcast or a sunset sky
     * tints everything under it without any of it being authored twice.
     */
    private fun skyColour(target: ByteBuffer, frame: RayTracingFrame) {
        vec4(
            target,
            frame.environment.red,
            frame.environment.green,
            frame.environment.blue,
            frame.skyAmbient,
        )
    }

    private fun vec4(target: ByteBuffer, x: Float, y: Float, z: Float, w: Float) {
        target.putFloat(x)
        target.putFloat(y)
        target.putFloat(z)
        target.putFloat(w)
    }

    private fun allocate() {
        val slots = device.capabilities.framesInFlight.coerceAtLeast(1)
        buffers = Array(slots) { slot ->
            device.createBuffer(
                BufferDescription(
                    label = "kalia-rt-scene-$slot",
                    sizeBytes = BYTES.toLong(),
                    usage = BufferUsage.STREAM,
                    uniform = true,
                ),
            )
        }
    }

    override fun close() {
        buffers.forEach(GpuBuffer::close)
        buffers = emptyArray()
    }

    private val matrixScratch = FloatArray(16)

    private companion object {
        /**
         * Vectors following the matrix, in the order [update] writes them: scene
         * offset, sun, sun colour, sky colour, environment, trace params, surface
         * params, output params, resolution, fog, celestial. Must match the block declared in
         * rt_scene.glsl exactly.
         */
        const val VECTORS = 11

        const val BYTES = 64 + VECTORS * 16
    }
}
