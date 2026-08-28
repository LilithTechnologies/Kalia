package re.lilith.kalia.voxel.render

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.post.drawFullscreen
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.FilterMode
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.rendering.world.LightMap
import re.lilith.kalia.voxel.SvoSettings
import re.lilith.kalia.voxel.VoxelWorld
import re.lilith.kalia.voxel.gpu.VoxelBufferMirror
import re.lilith.kalia.voxel.gpu.VoxelSpriteTable

/**
 * Owns the GPU side of the voxel pipeline and records every voxel pass.
 *
 * Lives on the render thread. The octree mirrors are re-uploaded here rather than at write time,
 * so a frame always sees a self-consistent tree even while chunk builders keep feeding the CPU
 * side new bricks.
 */
object SvoRenderer {
    private var device: RenderDevice? = null
    private var uniforms: SvoUniforms? = null
    private var targets: SvoTargets? = null

    private val nodeMirror = VoxelBufferMirror("kalia/svo-nodes")
    private val brickMirror = VoxelBufferMirror("kalia/svo-bricks")
    private val spriteTable = VoxelSpriteTable()

    private var nodeBuffer: GpuBuffer? = null
    private var brickBuffer: GpuBuffer? = null
    private var spriteBuffer: GpuBuffer? = null
    private var atlas: GpuTexture? = null
    private var lightmap: GpuTexture? = null

    /** Bytes streamed to the GPU during the most recent frame, for the debug overlay. */
    var lastUploadBytes: Long = 0L
        private set

    /** Whether the voxel passes have everything they need this frame. */
    var ready: Boolean = false
        private set

    /**
     * Uploads whatever changed and picks this frame's history slot. Called once while the graph is
     * being built, before any pass body runs.
     *
     * @param traceExtent size of the lighting targets
     */
    fun beginFrame(device: RenderDevice, traceExtent: Extent): Boolean {
        ready = false
        if (this.device !== device) {
            releaseFor(device)
        }

        val state = SvoScene.current
        if (!state.active || !SvoSettings.enabled) {
            return false
        }

        val ring = uniforms ?: SvoUniforms(device).also { uniforms = it }
        ring.beginFrame()

        nodeBuffer = nodeMirror.sync(device, VoxelWorld.nodes.storage, UPLOAD_BUDGET_BYTES)
        brickBuffer = brickMirror.sync(device, VoxelWorld.bricks.storage, UPLOAD_BUDGET_BYTES)
        spriteBuffer = spriteTable.sync(device)
        lastUploadBytes = nodeMirror.lastUploadBytes + brickMirror.lastUploadBytes

        // Without the atlas there is nothing to texture surfaces with, and an unbound sampler is a
        // hard error rather than a blank frame, so the whole chain sits out until it is ready.
        atlas = SvoAtlas.texture()
        lightmap = LightMap.texture(device)
        if (nodeBuffer == null || brickBuffer == null || spriteBuffer == null ||
            atlas == null || lightmap == null
        ) {
            return false
        }

        val history = targets ?: SvoTargets(device).also { targets = it }
        if (!history.beginFrame(traceExtent)) {
            return false
        }

        ready = true
        return true
    }

    /** Persistent history textures for this frame, or null before [beginFrame] succeeds. */
    val history: SvoTargets? get() = targets

    fun invalidateHistory() {
        targets?.invalidate()
    }

    fun release() {
        nodeMirror.close()
        spriteTable.close()
        brickMirror.close()
        uniforms?.close()
        targets?.close()
        uniforms = null
        targets = null
        nodeBuffer = null
        spriteBuffer = null
        atlas = null
        lightmap = null
        brickBuffer = null
        device = null
        ready = false
    }

    private fun releaseFor(next: RenderDevice) {
        release()
        device = next
    }

    // -- pass bodies ---------------------------------------------------------------------------

    /** Traces primary rays and lights what they find, into the light and geometry targets. */
    fun trace(context: PassContext) {
        val state = SvoScene.current
        val ring = uniforms ?: return
        with(context) {
            bindPipeline(pipelineFor(this, SvoShaders.TRACE))
            bindOctree(this)
            bindScene(
                this,
                ring.push(
                    state = state,
                    targetWidth = extent.width.toFloat(),
                    targetHeight = extent.height.toFloat(),
                    filterStep = 1f,
                    footprint = state.footprint(extent.height),
                    features = traceFeatures(),
                ),
            )
            drawFullscreen()
        }
    }

    /** Reprojects last frame's lighting into this one and writes the result back to history. */
    fun temporal(
        context: PassContext,
        light: TextureHandle,
        geometry: TextureHandle,
        historyLight: TextureHandle,
        historyGeometry: TextureHandle,
    ) {
        val state = SvoScene.current
        val ring = uniforms ?: return
        val history = targets ?: return

        val usable = SvoSettings.denoiseEnabled && history.primed && state.hasHistory

        with(context) {
            val linear = device.createSampler(SamplerDescription.LINEAR_CLAMP)
            val point = device.createSampler(SamplerDescription.NEAREST_CLAMP)
            bindPipeline(pipelineFor(this, SvoShaders.TEMPORAL))
            bindTexture(0, resolve(light), point)
            bindTexture(1, resolve(geometry), point)
            // Bound even on the first frame, when their contents are undefined; the feature bit is
            // what tells the shader whether to believe them. The geometry is point sampled because
            // interpolating normals across an edge invents a direction that matches nothing and
            // makes the history look rejectable everywhere.
            bindTexture(2, resolve(historyLight), linear)
            bindTexture(3, resolve(historyGeometry), point)

            var features = traceFeatures()
            if (usable) {
                features = features or FEATURE_HISTORY
            }
            bindScene(
                this,
                ring.push(
                    state = state,
                    targetWidth = extent.width.toFloat(),
                    targetHeight = extent.height.toFloat(),
                    filterStep = 1f,
                    footprint = state.footprint(extent.height),
                    features = features,
                ),
            )
            drawFullscreen()
        }
        history.markWritten()
    }

    /** One a-trous iteration at the given step width. */
    fun denoise(context: PassContext, light: TextureHandle, geometry: TextureHandle, step: Float) {
        val state = SvoScene.current
        val ring = uniforms ?: return
        with(context) {
            val point = device.createSampler(SamplerDescription.NEAREST_CLAMP)
            bindPipeline(pipelineFor(this, SvoShaders.DENOISE))
            bindTexture(0, resolve(light), point)
            bindTexture(1, resolve(geometry), point)
            bindScene(
                this,
                ring.push(
                    state = state,
                    targetWidth = extent.width.toFloat(),
                    targetHeight = extent.height.toFloat(),
                    filterStep = step,
                    footprint = state.footprint(extent.height),
                    features = traceFeatures(),
                ),
            )
            drawFullscreen()
        }
    }

    /**
     * The lighting this frame's terrain should read, published by the graph builder because the
     * primary pass runs inside the world pass and has no handles of its own.
     */
    var lighting: SvoPasses.Lighting? = null

    /**
     * Traces primary visibility straight into the world pass, depth and all.
     *
     * Unlike the other bodies this one runs inside somebody else's pass, so it takes the
     * attachment layout it finds and writes depth so that entities still sort correctly against it.
     */
    fun primary(context: PassContext) {
        val state = SvoScene.current
        if (!ready || !state.active) {
            return
        }
        val ring = uniforms ?: return
        val prepared = lighting ?: return
        with(context) {
            bindPipeline(
                device.createPipeline(
                    GraphicsPipelineDescription(
                        program = SvoShaders.PRIMARY,
                        vertexFormat = null,
                        attachments = attachments,
                        raster = RasterState.TWO_SIDED,
                        depth = DepthState(test = true, write = true),
                    ),
                ),
            )
            val point = device.createSampler(SamplerDescription.NEAREST_CLAMP)
            bindTexture(0, resolve(prepared.light), point)
            bindTexture(1, resolve(prepared.geometry), point)
            bindOctree(this)
            bindScene(
                this,
                ring.push(
                    state = state,
                    targetWidth = extent.width.toFloat(),
                    targetHeight = extent.height.toFloat(),
                    filterStep = 1f,
                    footprint = state.footprint(extent.height),
                    features = traceFeatures(),
                ),
            )
            drawFullscreen()
        }
    }

    // -- helpers -------------------------------------------------------------------------------

    private fun pipelineFor(context: PassContext, program: ShaderProgram) =
        context.device.createPipeline(
            GraphicsPipelineDescription(
                program = program,
                vertexFormat = null,
                attachments = context.attachments,
                raster = RasterState.TWO_SIDED,
                depth = DepthState.DISABLED,
            ),
        )

    private fun bindOctree(context: PassContext) {
        val nodes = nodeBuffer ?: return
        val bricks = brickBuffer ?: return
        val sprites = spriteBuffer ?: return
        val texture = atlas ?: return
        val light = lightmap ?: return
        context.bindStorageBuffer(SvoShaders.Bindings.NODES, nodes)
        context.bindStorageBuffer(SvoShaders.Bindings.BRICKS, bricks)
        context.bindStorageBuffer(SvoShaders.Bindings.SPRITES, sprites)
        // Trilinear so the cone-driven mip selection actually has mips to blend between; without
        // it distant terrain shimmers badly.
        context.bindTexture(
            SvoShaders.Bindings.LIGHTMAP,
            light,
            context.device.createSampler(SamplerDescription.LINEAR_CLAMP),
        )
        context.bindTexture(
            SvoShaders.Bindings.ATLAS,
            texture,
            context.device.createSampler(ATLAS_SAMPLER),
        )
    }

    private fun bindScene(context: PassContext, offset: Long) {
        val ring = uniforms ?: return
        context.bindUniformBuffer(SvoShaders.Bindings.SCENE, ring.uniformBuffer, offset, ring.sizeBytes)
    }

    private fun traceFeatures(): Int {
        var bits = 0
        if (SvoSettings.shadowsEnabled) {
            bits = bits or FEATURE_SHADOWS
        }
        if (SvoSettings.ambientOcclusionEnabled) {
            bits = bits or FEATURE_OCCLUSION
        }
        if (SvoSettings.bounceLightEnabled) {
            bits = bits or FEATURE_BOUNCE
        }
        if (SvoSettings.reflectionsEnabled) {
            bits = bits or FEATURE_REFLECTIONS
        }
        return bits
    }

    const val FEATURE_SHADOWS = 1
    const val FEATURE_OCCLUSION = 2
    const val FEATURE_BOUNCE = 4
    const val FEATURE_REFLECTIONS = 8
    const val FEATURE_HISTORY = 16

    /** Ceiling on octree streaming per frame, so a world join spreads out instead of stalling. */
    private const val UPLOAD_BUDGET_BYTES = 12L * 1024 * 1024

    /**
     * Point-sampled within a mip so blocks stay crisp, linear between mips so the far field does
     * not crawl as the cone widens.
     */
    private val ATLAS_SAMPLER = SamplerDescription(
        label = "kalia/svo-atlas",
        minFilter = FilterMode.NEAREST,
        magFilter = FilterMode.NEAREST,
        mipFilter = FilterMode.LINEAR,
        maxLod = 4f,
    )
}
