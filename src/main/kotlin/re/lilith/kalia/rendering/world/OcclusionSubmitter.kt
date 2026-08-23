package re.lilith.kalia.rendering.world

import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.graph.occlusion.EntityOcclusion
import re.lilith.kalia.frame.graph.occlusion.OcclusionBoxes
import re.lilith.kalia.frame.graph.occlusion.OcclusionShaders
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.frame.graph.entity.cuboid.CuboidMesh
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.shader.ShaderPrelude

object OcclusionSubmitter {
    private var pipeline: GpuPipeline? = null
    private var pipelineKey: GraphicsPipelineDescription? = null

    fun submit(state: WorldFrameState, submissions: WorldSubmissions) {
        if (!state.active || !EntityOcclusion.enabled) {
            return
        }
        val device = KaliaEngine.device ?: return
        EntityOcclusion.configure(device.occlusionQueryCapacity)

        OcclusionBoxes.reset()
        EntityOcclusion.forEachQuery { _, entity ->
            val box = entity.boundingBox ?: return@forEachQuery
            val centerX = ((box.minX + box.maxX) * 0.5 - state.cameraX).toFloat()
            val centerY = ((box.minY + box.maxY) * 0.5 - state.cameraY).toFloat()
            val centerZ = ((box.minZ + box.maxZ) * 0.5 - state.cameraZ).toFloat()
            val sizeX = (box.maxX - box.minX).toFloat() + MARGIN
            val sizeY = (box.maxY - box.minY).toFloat() + MARGIN
            val sizeZ = (box.maxZ - box.minZ).toFloat() + MARGIN
            if (OcclusionBoxes.withinRange(centerX, centerY, centerZ, sizeX, sizeY, sizeZ)) {
                OcclusionBoxes.add(centerX, centerY, centerZ, sizeX, sizeY, sizeZ)
            }
        }
        if (OcclusionBoxes.count == 0) {
            return
        }
        device.prepareOcclusionQueries(OcclusionBoxes.count)

        submissions.submit(
            WorldSubmission.Custom(phase = WorldPhase.OCCLUSION, material = WorldMaterial.TERRAIN_OPAQUE) { pass ->
                draw(pass)
            },
        )
    }

    private fun draw(pass: PassContext) {
        val count = OcclusionBoxes.count
        if (count == 0) {
            return
        }
        val device = pass.device
        val resources = FrameResources.of(device)

        val description = GraphicsPipelineDescription(
            program = OcclusionShaders.program(),
            vertexFormat = CuboidMesh.VERTEX_FORMAT,
            attachments = pass.attachments,
            raster = RasterState.TWO_SIDED,
            depth = DepthState.READ_ONLY,
            blend = BlendState.OPAQUE,
            colorMask = ColorMask.NONE,
            instanceFormat = OcclusionBoxes.INSTANCE_FORMAT,
        )
        val cached = pipeline
        val built = if (cached != null && pipelineKey == description) {
            cached
        } else {
            device.createPipeline(description).also {
                pipeline = it
                pipelineKey = description
            }
        }

        pass.bindPipeline(built)
        resources.sceneUniforms.sync()
        pass.bindUniformBuffer(
            binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
            buffer = resources.sceneUniforms.uniformBuffer,
            offsetBytes = resources.sceneUniforms.offsetBytes,
            sizeBytes = resources.sceneUniforms.sizeBytes,
        )
        pass.pushConstants(ShaderUniforms.pushConstants())

        val data = OcclusionBoxes.instances.finish()
        val slice = resources.vertexArena.append(data, data.remaining())
        pass.bindVertexBuffer(0, CuboidMesh.vertices(device))
        pass.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
        pass.bindIndexBuffer(CuboidMesh.indices(device), IndexFormat.UINT32)

        for (index in 0 until count) {
            pass.beginOcclusionQuery(index)
            pass.drawIndexed(CuboidMesh.INDEX_COUNT, 1, 0, 0, index)
            pass.endOcclusionQuery(index)
        }
        EntityOcclusion.publish(device::occlusionResult)
    }

    private const val MARGIN = 0.4f
}
