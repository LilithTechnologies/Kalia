package re.lilith.kalia.frame.graph.rt

import org.joml.Matrix4f
import re.lilith.kalia.renderer.accel.GpuAccelerationStructure
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.post.drawFullscreen
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.shader.ShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Texel size of the trace targets, which every stage needs in order to step by
 * exactly one of its own pixels.
 */
internal class TraceExtent(width: Int, height: Int) {
    val texelX = 1f / width
    val texelY = 1f / height
}

/**
 * The traceable scene as the recording pass sees it.
 */
internal class TraceableScene(
    val structure: GpuAccelerationStructure?,
    val instances: GpuBuffer?,
    val offsetX: Float,
    val offsetY: Float,
    val offsetZ: Float,
)

/**
 * Records the fullscreen passes that make up the ray tracing chain.
 *
 * Every texture arrives as a graph handle rather than a raw resource, so the
 * graph inserts the layout transitions and barriers each stage needs.
 */
internal object RayTracingPasses {
    private const val MAX_PUSH_CONSTANT_BYTES = 256

    private val scratch: ByteBuffer =
        ByteBuffer.allocateDirect(MAX_PUSH_CONSTANT_BYTES).order(ByteOrder.nativeOrder())

    private val matrixScratch = FloatArray(16)

    /**
     * Builds the transmittance table. Depends only on the atmosphere's constants,
     * so it runs once rather than every frame.
     */
    fun transmittance(context: PassContext) = with(context) {
        bindPipeline(pipeline(context, RayTracingShaders.TRANSMITTANCE))
        drawFullscreen()
    }

    /**
     * Builds the sky table for this frame's sun position.
     */
    fun sky(context: PassContext, transmittance: TextureHandle, uniforms: GpuBuffer) = with(context) {
        bindPipeline(pipeline(context, RayTracingShaders.SKY))
        bindTexture(0, resolve(transmittance), device.createSampler(SamplerDescription.LINEAR_CLAMP))
        bindUniformBuffer(RayTracingShaders.SCENE_UNIFORM_BINDING, uniforms)
        drawFullscreen()
    }

    /**
     * Replaces the rasterised geometry buffer with what a camera ray actually
     * finds. Where a ray misses the shader discards, so terrain beyond the traced
     * radius keeps whatever the rasteriser wrote.
     */
    fun primary(context: PassContext, scene: TraceableScene, uniforms: GpuBuffer) = with(context) {
        val atlas = RayTracingTextures.blockAtlas(device) ?: return@with
        val structure = scene.structure ?: return@with
        val instances = scene.instances ?: return@with

        bindPipeline(pipeline(context, RayTracingShaders.PRIMARY, depthWrite = true))
        bindTexture(0, atlas, device.createSampler(SamplerDescription.LINEAR_CLAMP))
        bindAccelerationStructure(1, structure)
        bindStorageBuffer(2, instances)
        bindUniformBuffer(RayTracingShaders.SCENE_UNIFORM_BINDING, uniforms)

        drawFullscreen()
    }

    fun trace(
        context: PassContext,
        depth: TextureHandle,
        gbufferSurface: TextureHandle,
        skyLut: TextureHandle,
        scene: TraceableScene,
        uniforms: GpuBuffer,
    ) = with(context) {
        val atlas = RayTracingTextures.blockAtlas(device) ?: return@with
        val structure = scene.structure ?: return@with
        val instances = scene.instances ?: return@with

        val nearest = device.createSampler(SamplerDescription.NEAREST_CLAMP)

        bindPipeline(pipeline(context, RayTracingShaders.TRACE))
        bindTexture(0, resolve(depth), nearest)
        // The atlas is filtered so a distant hit picks up an averaged colour
        // rather than one texel of whatever it happened to land on.
        bindTexture(1, atlas, device.createSampler(SamplerDescription.LINEAR_CLAMP))
        bindTexture(2, resolve(gbufferSurface), nearest)
        bindAccelerationStructure(RayTracingShaders.TRACE_SCENE_BINDING, structure)
        bindStorageBuffer(RayTracingShaders.TRACE_INSTANCE_BINDING, instances)
        bindTexture(7, resolve(skyLut), device.createSampler(SamplerDescription.LINEAR_CLAMP))
        bindUniformBuffer(RayTracingShaders.SCENE_UNIFORM_BINDING, uniforms)

        drawFullscreen()
    }

    fun temporal(
        context: PassContext,
        rawIndirect: TextureHandle,
        rawReflection: TextureHandle,
        surface: TextureHandle,
        historyIndirect: TextureHandle,
        historyMoments: TextureHandle,
        historySurface: TextureHandle,
        historyReflection: TextureHandle,
        depth: TextureHandle,
        hasHistory: Boolean,
        traceExtent: TraceExtent,
    ) = with(context) {
        val linear = device.createSampler(SamplerDescription.LINEAR_CLAMP)
        val nearest = device.createSampler(SamplerDescription.NEAREST_CLAMP)

        bindPipeline(pipeline(context, RayTracingShaders.TEMPORAL))
        bindTexture(0, resolve(rawIndirect), nearest)
        bindTexture(1, resolve(rawReflection), nearest)
        bindTexture(2, resolve(surface), nearest)
        bindTexture(3, resolve(historyIndirect), linear)
        bindTexture(4, resolve(historyMoments), linear)
        bindTexture(5, resolve(historySurface), linear)
        bindTexture(6, resolve(historyReflection), linear)
        bindTexture(7, resolve(depth), nearest)

        val frame = RayTracingFrame
        val accumulation = frame.accumulationFrames.toFloat()
        pushConstants(
            encode {
                matrix(frame.reprojection)
                vec4(traceExtent.texelX, traceExtent.texelY, if (hasHistory) 1f else 0f, 0f)
                vec4(accumulation, 1f / accumulation, CLAMP_GAMMA, 0f)
                vec4(DEPTH_TOLERANCE, NORMAL_TOLERANCE, REFLECTION_FRAMES, 0f)
            },
        )
        drawFullscreen()
    }

    fun atrous(
        context: PassContext,
        colour: TextureHandle,
        variance: TextureHandle,
        surface: TextureHandle,
        step: Float,
        traceExtent: TraceExtent,
    ) = with(context) {
        val nearest = device.createSampler(SamplerDescription.NEAREST_CLAMP)

        bindPipeline(pipeline(context, RayTracingShaders.ATROUS))
        bindTexture(0, resolve(colour), nearest)
        bindTexture(1, resolve(variance), nearest)
        bindTexture(2, resolve(surface), nearest)

        val strength = RayTracingFrame.denoiserStrength
        pushConstants(
            encode {
                vec4(traceExtent.texelX, traceExtent.texelY, step, SIGMA_DEPTH * strength)
                vec4(SIGMA_NORMAL, SIGMA_LUMA * strength, 0f, 0f)
            },
        )
        drawFullscreen()
    }

    /**
     * Lights the geometry buffer with Minecraft's own light map, for when the
     * traced scene is not available. Without it the terrain would not be drawn at
     * all while a world is still streaming in.
     */
    fun fallback(
        context: PassContext,
        albedo: TextureHandle,
        gbufferSurface: TextureHandle,
        depth: TextureHandle,
    ) = with(context) {
        val lightmap = RayTracingTextures.lightmapOrShared(device) ?: return@with
        val nearest = device.createSampler(SamplerDescription.NEAREST_CLAMP)

        bindPipeline(pipeline(context, RayTracingShaders.FALLBACK))
        bindTexture(0, resolve(albedo), nearest)
        bindTexture(1, resolve(gbufferSurface), nearest)
        bindTexture(2, resolve(depth), nearest)
        bindTexture(3, lightmap, device.createSampler(SamplerDescription.LINEAR_CLAMP))

        drawFullscreen()
    }

    fun lighting(
        context: PassContext,
        albedo: TextureHandle,
        gbufferSurface: TextureHandle,
        depth: TextureHandle,
        indirect: TextureHandle,
        reflection: TextureHandle,
        moments: TextureHandle,
        traceSurface: TextureHandle,
        skyLut: TextureHandle,
        transmittance: TextureHandle,
        uniforms: GpuBuffer,
    ) = with(context) {
        val linear = device.createSampler(SamplerDescription.LINEAR_CLAMP)
        val nearest = device.createSampler(SamplerDescription.NEAREST_CLAMP)

        bindPipeline(pipeline(context, RayTracingShaders.LIGHTING))
        bindTexture(0, resolve(albedo), nearest)
        bindTexture(1, resolve(gbufferSurface), nearest)
        bindTexture(2, resolve(depth), nearest)
        bindTexture(3, resolve(indirect), linear)
        bindTexture(4, resolve(reflection), linear)
        bindTexture(5, resolve(moments), nearest)
        bindTexture(7, resolve(traceSurface), nearest)
        bindTexture(8, resolve(skyLut), linear)
        bindTexture(9, resolve(transmittance), linear)
        bindUniformBuffer(RayTracingShaders.SCENE_UNIFORM_BINDING, uniforms)

        drawFullscreen()
    }

    private fun pipeline(
        context: PassContext,
        program: ShaderProgram,
        depthWrite: Boolean = false,
    ) = context.device.createPipeline(
        GraphicsPipelineDescription(
            program = program,
            vertexFormat = null,
            attachments = context.attachments,
            raster = RasterState.TWO_SIDED,
            // A fullscreen pass covers everything, so there is nothing to test
            // against; the primary pass still writes depth, because what it found
            // is what everything drawn afterwards has to sort against.
            depth = if (depthWrite) DepthState(test = false, write = true) else DepthState.DISABLED,
        ),
    )

    private fun encode(build: Encoder.() -> Unit): ByteBuffer {
        scratch.clear()
        Encoder(scratch).build()
        scratch.flip()
        return scratch
    }

    private class Encoder(private val target: ByteBuffer) {
        fun vec4(x: Float, y: Float, z: Float, w: Float) {
            target.putFloat(x)
            target.putFloat(y)
            target.putFloat(z)
            target.putFloat(w)
        }

        fun matrix(value: Matrix4f) {
            value.get(matrixScratch)
            matrixScratch.forEach(target::putFloat)
        }
    }

    // The tracer's own constants live in rt_trace.frag, where the push-constant
    // budget has no room to pass them.

    /** How far history may stray from the neighbourhood before it is clamped back. */
    private const val CLAMP_GAMMA = 2.5f

    /** Relative depth disagreement that rejects a reprojected sample. */
    private const val DEPTH_TOLERANCE = 0.05f

    /** Cosine below which two normals are considered different surfaces. */
    private const val NORMAL_TOLERANCE = 0.9f

    /** Reflections ghost far more visibly than diffuse, so they accumulate over fewer frames. */
    private const val REFLECTION_FRAMES = 12f

    private const val SIGMA_DEPTH = 0.05f
    private const val SIGMA_NORMAL = 64f
    private const val SIGMA_LUMA = 4f
}
