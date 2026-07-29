package re.lilith.kalia.frame.graph.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import org.lwjgl.opengl.GL11
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.GameFrameGraph
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.mesh.MeshBuilder
import re.lilith.kalia.renderer.mesh.UploadedMesh
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.vertex.VertexFormats

class CubeMap(
    val texture: Identifier
) {
    private val mesh: UploadedMesh
    private val sampler: GpuSampler
    private val pipeline: GpuPipeline

    init {
        val device = KaliaEngine.device ?: throw Exception("Kalia isn't initialized yet")

        mesh = MeshBuilder(VertexFormats.POSITION) {
            addVertex(-1f, -1f,  1f)
            addVertex(-1f,  1f,  1f)
            addVertex( 1f,  1f,  1f)
            addVertex( 1f, -1f,  1f)
            quad()

            addVertex( 1f, -1f,  1f)
            addVertex( 1f,  1f,  1f)
            addVertex( 1f,  1f, -1f)
            addVertex( 1f, -1f, -1f)
            quad()

            addVertex( 1f, -1f, -1f)
            addVertex( 1f,  1f, -1f)
            addVertex(-1f,  1f, -1f)
            addVertex(-1f, -1f, -1f)
            quad()

            addVertex(-1f, -1f, -1f)
            addVertex(-1f,  1f, -1f)
            addVertex(-1f,  1f,  1f)
            addVertex(-1f, -1f,  1f)
            quad()

            addVertex(-1f, -1f, -1f)
            addVertex(-1f, -1f,  1f)
            addVertex( 1f, -1f,  1f)
            addVertex( 1f, -1f, -1f)
            quad()

            addVertex(-1f,  1f,  1f)
            addVertex(-1f,  1f, -1f)
            addVertex( 1f,  1f, -1f)
            addVertex( 1f,  1f,  1f)
            quad()
        }.upload(device, "cube-map-vertices")

        pipeline = device.createPipeline(
            GraphicsPipelineDescription(
                CubeMapShaders.program(),
                VertexFormats.POSITION,
                AttachmentLayout(colorFormats = listOf(GameFrameGraph.sceneFormat), depthFormat = device.capabilities.supportedDepthFormats.first()),
                RasterState.TWO_SIDED,
                DepthState.READ_WRITE,
            )
        )

        sampler = device.createSampler(SamplerDescription.LINEAR_CLAMP)
    }

    fun render(rotationX: Float, rotationY: Float) {
        val encoder = GameFrame.current ?: throw Exception("Kalia isn't initialized yet")

        val nativeTexture =
            TextureTable.get(MinecraftClient.getInstance().textureManager.getTexture(texture).glId)?.texture
                ?: throw Exception("Texture '$texture' does not exist")
        val resources = FrameResources.of(encoder.device)

        MatrixState.matrixMode(GL11.GL_MODELVIEW)
        MatrixState.pushMatrix()
        MatrixState.loadIdentity()

        MatrixState.matrixMode(GL11.GL_PROJECTION)
        MatrixState.pushMatrix()
        MatrixState.loadIdentity()

        MatrixState.perspective(
            PROJ_FOV,
            encoder.extent.width.toDouble() / encoder.extent.height.toDouble(),
            PROJ_Z_NEAR,
            PROJ_Z_FAR
        )

        MatrixState.matrixMode(GL11.GL_MODELVIEW)
        MatrixState.rotate(180f, 1f, 0f, 0f)
        MatrixState.rotate(rotationX, 1f, 0f, 0f)
        MatrixState.rotate(rotationY, 0f, 1f, 0f)

        MatrixState.flush()

        encoder.bindPipeline(pipeline)
        encoder.lineWidth(GlState.lineWidth)
        encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, nativeTexture, sampler)
        resources.sceneUniforms.sync()
        encoder.bindUniformBuffer(
            binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
            buffer = resources.sceneUniforms.uniformBuffer,
            offsetBytes = resources.sceneUniforms.offsetBytes,
            sizeBytes = resources.sceneUniforms.sizeBytes,
        )
        encoder.pushConstants(ShaderUniforms.pushConstants())

        mesh.draw(encoder)

        MatrixState.popMatrix()
        MatrixState.matrixMode(GL11.GL_PROJECTION)
        MatrixState.popMatrix()
        MatrixState.matrixMode(GL11.GL_MODELVIEW)
    }

    companion object {
        private const val PROJ_Z_NEAR = 0.05
        private const val PROJ_Z_FAR = 10.0
        private const val PROJ_FOV = 85.0
    }
}