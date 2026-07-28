package re.lilith.kalia.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import org.lwjgl.opengl.GL11
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.Kalia
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.mesh.MeshBuilder
import re.lilith.kalia.renderer.mesh.UploadedMesh
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.PrimitiveTopology
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.texture.TextureTable
import re.lilith.kalia.vertex.VertexFormats
import kotlin.math.exp

class CubeMap(
    val texture: Identifier
) {
    private val mesh: UploadedMesh
    private val sampler: GpuSampler
    private var pipeline: GpuPipeline? = null

    private var lastDescAttachments: AttachmentLayout? = null

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

        sampler = device.createSampler(SamplerDescription.LINEAR_CLAMP)
    }

    fun render(rotationX: Float, rotationY: Float) {
        val encoder = GameFrame.current ?: throw Exception("Kalia isn't initialized yet")
        if (encoder.attachments != lastDescAttachments || pipeline == null) recreatePipeline(encoder.attachments)

        val nativeTexture = TextureTable.get(MinecraftClient.getInstance().textureManager.getTexture(texture).glId)?.texture ?: throw Exception("Texture '$texture' does not exist")
            val resources = FrameResources.of(encoder.device)

        MatrixState.matrixMode(GL11.GL_MODELVIEW)
        MatrixState.pushMatrix()
        MatrixState.loadIdentity()

        MatrixState.matrixMode(GL11.GL_PROJECTION)
        MatrixState.pushMatrix()
        MatrixState.loadIdentity()

        MatrixState.perspective(PROJ_FOV, encoder.extent.width.toDouble() / encoder.extent.height.toDouble(), PROJ_Z_NEAR, PROJ_Z_FAR)

        MatrixState.matrixMode(GL11.GL_MODELVIEW)
        MatrixState.rotate(180f, 1f, 0f, 0f)
        MatrixState.rotate(rotationX, 1f, 0f, 0f)
        MatrixState.rotate(rotationY, 0f, 1f, 0f)

        MatrixState.flush()

        pipeline?.let {
            encoder.bindPipeline(it)
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
        }

        GlBridge.disableDepthTest()

        MatrixState.popMatrix()
        MatrixState.matrixMode(GL11.GL_PROJECTION)
        MatrixState.popMatrix()
        MatrixState.matrixMode(GL11.GL_MODELVIEW)

    }

    private fun recreatePipeline(attachments: AttachmentLayout) {
        pipeline?.close()
        val device = GameFrame.current?.device ?: throw Exception("Kalia isn't initialized yet")
        pipeline = device.createPipeline(
            GraphicsPipelineDescription(
                CubeMapShaders.program(),
                VertexFormats.POSITION,
                attachments,
                depth = DepthState.READ_WRITE,
                raster = RasterState.TWO_SIDED
            )
        )
        this.lastDescAttachments = attachments
    }

    companion object {
        private const val SIDES = 6
        private const val PROJ_Z_NEAR = 0.05
        private const val PROJ_Z_FAR = 10.0
        private const val PROJ_FOV = 85.0
    }
}