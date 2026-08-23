package re.lilith.kalia.frame.graph.instance

import re.lilith.kalia.buffer.InstanceArena
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.draw.BatchEnvironment
import re.lilith.kalia.frame.graph.BatchStats
import re.lilith.kalia.frame.graph.entity.shadow.ShadowBatcher
import re.lilith.kalia.frame.graph.entity.nametag.NametagBatcher
import re.lilith.kalia.frame.graph.entity.cuboid.CuboidBatcher
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.shader.ShaderPrelude

/**
 * Unified path for instanced renderers.
 *
 * @see [CuboidBatcher]
 * @see [NametagBatcher]
 * @see [ShadowBatcher]
 */
internal class InstanceGroups(
    private val bytesPerInstance: Int,
    private val initialInstances: Int,
    private val poolCapacity: Int = DEFAULT_POOL_CAPACITY,
) {
    val environment = BatchEnvironment()

    private val groups = LinkedHashMap<InstanceKey, InstanceArena>()
    private val pool = ArrayDeque<InstanceArena>()
    private val probe = InstanceKey()
    private val pending = InstanceDraw()

    private val pipelines = HashMap<GraphicsPipelineDescription, GpuPipeline>()
    private var pipelineDevice: RenderDevice? = null

    private val descProgram = arrayOfNulls<ShaderProgram>(MEMO_SLOTS)
    private val descVertexFormat = arrayOfNulls<VertexFormat>(MEMO_SLOTS)
    private val descInstanceFormat = arrayOfNulls<VertexFormat>(MEMO_SLOTS)
    private val descAttachments = arrayOfNulls<AttachmentLayout>(MEMO_SLOTS)
    private val descRaster = arrayOfNulls<RasterState>(MEMO_SLOTS)
    private val descDepth = arrayOfNulls<DepthState>(MEMO_SLOTS)
    private val descBlend = arrayOfNulls<BlendState>(MEMO_SLOTS)
    private val descColorMask = arrayOfNulls<ColorMask>(MEMO_SLOTS)
    private val descriptions = arrayOfNulls<GraphicsPipelineDescription>(MEMO_SLOTS)
    private var descCursor = 0

    private val memoDescription = arrayOfNulls<GraphicsPipelineDescription>(MEMO_SLOTS)
    private val memoMesh = arrayOfNulls<Any>(MEMO_SLOTS)
    private val memoTexture = arrayOfNulls<GpuTexture>(MEMO_SLOTS)
    private val memoSampler = arrayOfNulls<GpuSampler>(MEMO_SLOTS)
    private val memoLightmap = arrayOfNulls<GpuTexture>(MEMO_SLOTS)
    private val memoLightmapSampler = arrayOfNulls<GpuSampler>(MEMO_SLOTS)
    private val memoInstances = arrayOfNulls<InstanceArena>(MEMO_SLOTS)
    private var memoCursor = 0

    var activeInstances: InstanceArena? = null

    val isEmpty: Boolean get() = groups.isEmpty()

    fun describe(
        program: ShaderProgram,
        vertexFormat: VertexFormat,
        instanceFormat: VertexFormat,
        attachments: AttachmentLayout,
        raster: RasterState,
        depth: DepthState,
        blend: BlendState,
        colorMask: ColorMask,
    ): GraphicsPipelineDescription {
        for (slot in 0 until MEMO_SLOTS) {
            val cached = descriptions[slot] ?: continue
            if (descProgram[slot] === program &&
                descVertexFormat[slot] === vertexFormat &&
                descInstanceFormat[slot] === instanceFormat &&
                descAttachments[slot] === attachments &&
                descRaster[slot] === raster &&
                descDepth[slot] === depth &&
                descBlend[slot] === blend &&
                descColorMask[slot] === colorMask
            ) {
                return cached
            }
        }
        val created = GraphicsPipelineDescription(
            program = program,
            vertexFormat = vertexFormat,
            attachments = attachments,
            raster = raster,
            depth = depth,
            blend = blend,
            colorMask = colorMask,
            instanceFormat = instanceFormat,
        )
        val slot = descCursor
        descCursor = if (slot + 1 == MEMO_SLOTS) 0 else slot + 1
        descProgram[slot] = program
        descVertexFormat[slot] = vertexFormat
        descInstanceFormat[slot] = instanceFormat
        descAttachments[slot] = attachments
        descRaster[slot] = raster
        descDepth[slot] = depth
        descBlend[slot] = blend
        descColorMask[slot] = colorMask
        descriptions[slot] = created
        return created
    }

    fun resolve(
        description: GraphicsPipelineDescription,
        mesh: Any? = null,
        texture: GpuTexture? = null,
        sampler: GpuSampler? = null,
        lightmap: GpuTexture? = null,
        lightmapSampler: GpuSampler? = null,
    ): InstanceArena {
        for (slot in 0 until MEMO_SLOTS) {
            val memo = memoInstances[slot] ?: continue
            if (memoDescription[slot] === description &&
                memoMesh[slot] === mesh &&
                memoTexture[slot] === texture &&
                memoSampler[slot] === sampler &&
                memoLightmap[slot] === lightmap &&
                memoLightmapSampler[slot] === lightmapSampler
            ) {
                return memo
            }
        }

        BatchStats.groupMisses++
        val key = probe.set(description, mesh, texture, sampler, lightmap, lightmapSampler)
        var instances = groups[key]
        if (instances == null) {
            instances = pool.removeLastOrNull()?.also { it.reset() }
                ?: InstanceArena(bytesPerInstance, initialInstances)
            groups[key.copy()] = instances
        }

        val slot = memoCursor
        memoCursor = if (slot + 1 == MEMO_SLOTS) 0 else slot + 1
        memoDescription[slot] = description
        memoMesh[slot] = mesh
        memoTexture[slot] = texture
        memoSampler[slot] = sampler
        memoLightmap[slot] = lightmap
        memoLightmapSampler[slot] = lightmapSampler
        memoInstances[slot] = instances
        return instances
    }

    fun flush(geometry: InstanceGeometry) {
        if (groups.isEmpty()) {
            return
        }
        val encoder = GameFrame.current
        if (encoder == null) {
            recycle()
            return
        }
        val device = encoder.device
        val resources = FrameResources.of(device)
        if (pipelineDevice !== device) {
            pipelines.clear()
            pipelineDevice = device
        }

        for ((key, instances) in groups) {
            if (instances.count == 0 || !geometry.resolve(key, device, resources, pending)) {
                continue
            }
            val vertexBuffer = pending.vertexBuffer ?: continue
            val indexBuffer = pending.indexBuffer ?: continue

            val pipeline = pipelines.getOrPut(key.description) { device.createPipeline(key.description) }
            encoder.bindPipeline(pipeline)
            GlBridge.applyDepthBias()
            encoder.lineWidth(GlState.lineWidth)

            val texture = key.texture
            val sampler = key.sampler
            if (texture != null && sampler != null) {
                encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, texture, sampler)
            }
            val lightmap = key.lightmap
            val lightmapSampler = key.lightmapSampler
            if (lightmap != null && lightmapSampler != null) {
                encoder.bindTexture(ShaderPrelude.Bindings.LIGHTMAP_TEXTURE, lightmap, lightmapSampler)
            }
            environment.apply(encoder)

            val data = instances.finish()
            val slice = resources.vertexArena.append(data, data.remaining())
            encoder.bindVertexBuffer(0, vertexBuffer)
            encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
            encoder.bindIndexBuffer(indexBuffer, IndexFormat.UINT32)
            encoder.drawIndexed(pending.indexCount, instances.count, 0, 0, 0)
        }
        recycle()
    }

    fun recycle() {
        for (instances in groups.values) {
            if (pool.size < poolCapacity) {
                pool.addLast(instances)
            } else {
                instances.release()
            }
        }
        groups.clear()
        environment.close()
        memoDescription.fill(null)
        memoMesh.fill(null)
        memoTexture.fill(null)
        memoSampler.fill(null)
        memoLightmap.fill(null)
        memoLightmapSampler.fill(null)
        memoInstances.fill(null)
        memoCursor = 0
        activeInstances = null
    }

    private companion object {
        const val DEFAULT_POOL_CAPACITY = 16
        const val MEMO_SLOTS = 8
    }
}
