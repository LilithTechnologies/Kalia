package re.lilith.kalia.frame.graph.rt

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.LoadOp
import re.lilith.kalia.renderer.graph.RenderGraphBuilder
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.graph.TextureSizing

/**
 * The ray traced lighting chain.
 *
 * Terrain is rasterised into a geometry buffer rather than a finished image, and
 * everything that makes a surface bright is computed here instead: a shadowed sun,
 * the sky dome, and light bouncing off the rest of the world, all traced against
 * the acceleration structure the chunk meshes were built into. Minecraft's own
 * light map stops being the lighting and becomes, at most, a stand-in for torches.
 */
object RayTracingGraph {
    /**
     * Albedo with the block light coordinate in alpha, then the geometric normal
     * with the sky light coordinate in alpha.
     */
    val GBUFFER_FORMATS: List<TextureFormat> = listOf(TextureFormat.RGBA8, TextureFormat.RGBA16F)

    private var resources: RayTracingResources? = null
    private var uniforms: RayTracingUniforms? = null
    private var atmosphere: AtmosphereResources? = null
    private var device: RenderDevice? = null

    /**
     * Whether the adapter can trace at all. Drives what the options screen offers.
     */
    fun isSupported(device: RenderDevice?): Boolean = device?.capabilities?.supportsRayTracing == true

    /**
     * Whether terrain should be rasterised as a geometry buffer this run.
     *
     * Read while chunk pipelines are being compiled, so it must not change without
     * a renderer reload; the option that sets it is flagged accordingly.
     */
    fun isDeferred(device: RenderDevice?): Boolean = RayTracingFrame.enabled && isSupported(device)

    /**
     * Frees everything the chain holds. Called when the device goes away or the
     * player leaves a world.
     */
    fun release() {
        resources?.close()
        resources = null
        uniforms?.close()
        uniforms = null
        atmosphere?.close()
        atmosphere = null
        device = null
        RayTracingScene.release()
    }

    /**
     * Adds the chain to [builder].
     *
     * @param albedo Terrain albedo, with the block light coordinate in alpha.
     * @param gbufferSurface Terrain normal, with the sky light coordinate in alpha.
     * @param depth The terrain depth buffer.
     * @param lit The target the lit image is written over, already holding the sky.
     * @return true when lighting was recorded, so the caller knows the target was
     * written rather than left as it was.
     */
    fun attach(
        builder: RenderGraphBuilder,
        device: RenderDevice,
        albedo: TextureHandle,
        gbufferSurface: TextureHandle,
        depth: TextureHandle,
        lit: TextureHandle,
        worldExtent: Extent,
    ): Boolean = with(builder) {
        val frame = RayTracingFrame
        if (!frame.enabled || !isSupported(device)) {
            release()
            return false
        }

        if (this@RayTracingGraph.device !== device) {
            release()
            this@RayTracingGraph.device = device
            resources = RayTracingResources(device)
            uniforms = RayTracingUniforms(device)
            atmosphere = AtmosphereResources(device)
        }

        if (!RayTracingScene.update(device)) {
            // Nothing is traceable yet, which is the normal state while a world
            // is still streaming in.
            return false
        }

        val history = resources ?: return false
        val scenes = uniforms ?: return false
        val traceExtent = worldExtent.scaled(frame.traceScale.factor)
        if (!history.begin(traceExtent)) {
            return false
        }

        val slot = device.frameSlot
        val scene = TraceableScene(
            structure = RayTracingScene.structure(slot),
            instances = RayTracingScene.instanceBuffer(slot),
            offsetX = RayTracingScene.sceneOffsetX,
            offsetY = RayTracingScene.sceneOffsetY,
            offsetZ = RayTracingScene.sceneOffsetZ,
        )
        if (scene.structure == null || scene.instances == null) {
            return false
        }

        val extent = TraceExtent(traceExtent.width, traceExtent.height)
        scenes.update(slot, scene, extent)
        val sceneBuffer = scenes.buffer(slot) ?: return false

        val air = atmosphere ?: return false
        if (!air.ensure()) {
            return false
        }
        val transmittance = import("rt-transmittance", air.transmittance ?: return false)
        val skyLut = import("rt-sky", air.sky ?: return false)

        if (air.transmittanceStale) {
            // Depends only on the atmosphere's constants, so it is built once and
            // then read for the rest of the session.
            pass("rt/transmittance") {
                color(transmittance)
                draw {
                    RayTracingPasses.transmittance(this)
                    // Marked here rather than while the graph is being built, so
                    // a frame that is discarded before it executes does not leave
                    // the table claimed but never written.
                    air.commitTransmittance()
                }
            }

        }

        pass("rt/sky") {
            color(skyLut)
            reads(transmittance)
            draw { RayTracingPasses.sky(this, transmittance, sceneBuffer) }
        }

        // Primary rays replace the rasterised surface with what the structure
        // actually holds, so the visible surface and the traced scene cannot
        // disagree. Depth is rewritten with it, because everything drawn after
        // this still has to sort against the world.
        pass("rt/primary") {
            color(albedo, load = LoadOp.LOAD)
            color(gbufferSurface, load = LoadOp.LOAD)
            depth(depth, load = LoadOp.LOAD)
            draw { RayTracingPasses.primary(this, scene, sceneBuffer) }
        }

        val sizing = TextureSizing.Fixed(traceExtent)
        val rawIndirect = texture("rt-indirect", TextureFormat.RGBA16F, sizing)
        val rawReflection = texture("rt-reflection", TextureFormat.RGBA16F, sizing)

        val surface = import("rt-surface", history.currentSurface() ?: return false)
        val previousSurface = import("rt-surface-history", history.previousSurface() ?: return false)
        val indirect = import("rt-indirect-history", history.currentIndirect() ?: return false)
        val previousIndirect = import("rt-indirect-previous", history.previousIndirect() ?: return false)
        val moments = import("rt-moments", history.currentMoments() ?: return false)
        val previousMoments = import("rt-moments-previous", history.previousMoments() ?: return false)
        val reflection = import("rt-reflection-history", history.currentReflection() ?: return false)
        val previousReflection = import("rt-reflection-previous", history.previousReflection() ?: return false)

        pass("rt/trace") {
            color(rawIndirect)
            color(rawReflection)
            color(surface)
            reads(setOf(depth, gbufferSurface, skyLut))
            draw { RayTracingPasses.trace(this, depth, gbufferSurface, skyLut, scene, sceneBuffer) }
        }

        pass("rt/temporal") {
            color(indirect)
            color(moments)
            color(reflection)
            reads(
                setOf(
                    rawIndirect,
                    rawReflection,
                    surface,
                    previousIndirect,
                    previousMoments,
                    previousSurface,
                    previousReflection,
                    depth,
                ),
            )
            draw {
                RayTracingPasses.temporal(
                    context = this,
                    rawIndirect = rawIndirect,
                    rawReflection = rawReflection,
                    surface = surface,
                    historyIndirect = previousIndirect,
                    historyMoments = previousMoments,
                    historySurface = previousSurface,
                    historyReflection = previousReflection,
                    depth = depth,
                    hasHistory = frame.denoiser != DenoiserMode.OFF && frame.hasHistory && history.hasHistory,
                    traceExtent = extent,
                )
            }
        }

        var colourSource = indirect
        var varianceSource = moments
        val iterations = if (frame.denoiser == DenoiserMode.FULL) frame.filterIterations else 0

        for (iteration in 0 until iterations) {
            val colourTarget = texture("rt-filtered-$iteration", TextureFormat.RGBA16F, sizing)
            val varianceTarget = texture("rt-variance-$iteration", TextureFormat.RGBA16F, sizing)
            val step = (1 shl iteration).toFloat()
            val readColour = colourSource
            val readVariance = varianceSource

            pass("rt/atrous-$iteration") {
                color(colourTarget)
                color(varianceTarget)
                reads(setOf(readColour, readVariance, surface))
                draw { RayTracingPasses.atrous(this, readColour, readVariance, surface, step, extent) }
            }

            colourSource = colourTarget
            varianceSource = varianceTarget
        }

        val denoised = colourSource

        pass("rt/lighting") {
            // The sky the forward pass drew is already here, and the lighting
            // shader discards where there is no solid surface so it survives.
            color(lit, load = LoadOp.LOAD)
            reads(setOf(albedo, gbufferSurface, depth, denoised, reflection, moments, surface, skyLut, transmittance))
            draw {
                RayTracingPasses.lighting(
                    context = this,
                    albedo = albedo,
                    gbufferSurface = gbufferSurface,
                    depth = depth,
                    indirect = denoised,
                    reflection = reflection,
                    moments = moments,
                    traceSurface = surface,
                    skyLut = skyLut,
                    transmittance = transmittance,
                    uniforms = sceneBuffer,
                )
            }
        }

        history.commit()
        return true
    }
}
