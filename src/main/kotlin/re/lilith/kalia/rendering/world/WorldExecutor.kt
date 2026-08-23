package re.lilith.kalia.rendering.world

import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_MODELVIEW
import org.lwjgl.opengl.GL11.GL_PROJECTION
import org.lwjgl.opengl.GL11.GL_TEXTURE
import re.lilith.kalia.frame.draw.KaliaDraw
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.pipeline.CullMode

object WorldExecutor {
    private const val GUI_ALPHA_CUTOUT = 0.1f

    private val modelView = Matrix4f()
    private val identity = Matrix4f()

    private var lastMaterial: WorldMaterial? = null

    fun draw(pass: PassContext, submissions: WorldSubmissions, phase: WorldPhase): Int {
        val state = WorldFrameState
        val work = submissions[phase]
        if (!state.active || work.isEmpty()) {
            return 0
        }

        lastMaterial = null
        applyProjection(projectionFor(state, phase))
        val fog = fogFor(state, phase)

        var recorded = 0
        for (submission in work) {
            applyMaterial(submission.material, fog)
            when (submission) {
                is WorldSubmission.Resident -> {
                    applyTransforms(state, submission.transform, submission.textureTransform)
                    ShaderUniforms.setShaderColor(
                        submission.red,
                        submission.green,
                        submission.blue,
                        submission.alpha,
                    )
                    KaliaDraw.drawResident(
                        buffer = submission.buffer,
                        format = submission.format,
                        glMode = submission.glMode,
                        vertexCount = submission.vertexCount,
                        offsetBytes = submission.offsetBytes,
                        texture = submission.texture,
                        sampler = submission.sampler,
                    )
                }

                is WorldSubmission.Transient -> {
                    applyTransforms(state, submission.transform, null)
                    ShaderUniforms.setShaderColor(
                        submission.red,
                        submission.green,
                        submission.blue,
                        submission.alpha,
                    )
                    KaliaDraw.drawStaged(
                        source = submissions.stagedAt(submission.stagingOffset, submission.byteCount),
                        format = submission.format,
                        glMode = submission.glMode,
                        vertexCount = submission.vertexCount,
                        texture = submission.texture,
                        sampler = submission.sampler,
                    )
                }

                is WorldSubmission.Custom -> {
                    applyTransforms(state, submission.transform, null)
                    ShaderUniforms.setShaderColor(1f, 1f, 1f, 1f)
                    submission.body(pass)
                }
            }
            recorded++
        }

        resetTextureMatrix()
        restoreDefaults()
        return recorded
    }

    fun restoreDefaults() {
        ShaderUniforms.setShaderColor(1f, 1f, 1f, 1f)
        ShaderUniforms.setAlphaCutout(GUI_ALPHA_CUTOUT)
        ShaderUniforms.setFogEnabled(false)
        ShaderUniforms.setLightmapEnabled(false)
        ShaderUniforms.setLightingEnabled(false)
        GlState.colorMask(red = true, green = true, blue = true, alpha = true)
    }

    private fun projectionFor(state: WorldFrameState, phase: WorldPhase): Matrix4f = when (phase) {
        WorldPhase.SKY -> state.skyProjection
        WorldPhase.CLOUDS_BELOW, WorldPhase.CLOUDS_ABOVE -> state.cloudProjection
        else -> state.terrainProjection
    }

    private fun fogFor(state: WorldFrameState, phase: WorldPhase): FogSnapshot =
        if (phase == WorldPhase.SKY) state.skyFog else state.worldFog

    private fun applyProjection(projection: Matrix4f) {
        MatrixState.matrixMode(GL_PROJECTION)
        MatrixState.loadIdentity()
        MatrixState.multiply(projection)
        MatrixState.matrixMode(GL_MODELVIEW)
    }

    private fun applyTransforms(state: WorldFrameState, model: Matrix4f?, texture: Matrix4f?) {
        modelView.set(state.view)
        if (model != null) {
            modelView.mul(model)
        }

        MatrixState.matrixMode(GL_MODELVIEW)
        MatrixState.loadIdentity()
        MatrixState.multiply(modelView)

        applyTextureMatrix(texture ?: identity)
    }

    private fun applyTextureMatrix(matrix: Matrix4f) {
        val unit = MatrixState.activeTextureUnit
        MatrixState.activeTextureUnit = 0
        MatrixState.matrixMode(GL_TEXTURE)
        MatrixState.loadIdentity()
        MatrixState.multiply(matrix)
        MatrixState.matrixMode(GL_MODELVIEW)
        MatrixState.activeTextureUnit = unit
    }

    private fun resetTextureMatrix() {
        applyTextureMatrix(identity)
    }

    private fun applyMaterial(material: WorldMaterial, fog: FogSnapshot) {
        if (lastMaterial === material) {
            return
        }
        lastMaterial = material

        GlState.depthTest = material.depthTest
        GlState.depthWrite = material.depthWrite
        GlState.cullEnabled = material.cull
        GlState.cullFace = CullMode.BACK
        GlState.blendEnabled = material.blend
        GlState.blendFuncSeparate(material.srcRgb, material.dstRgb, material.srcAlpha, material.dstAlpha)
        GlState.colorMask(
            material.colorMask.red,
            material.colorMask.green,
            material.colorMask.blue,
            material.colorMask.alpha,
        )

        ShaderUniforms.setAlphaCutout(material.alphaCutout)
        ShaderUniforms.setLightmapEnabled(material.lightmap)
        ShaderUniforms.setLightingEnabled(material.diffuseLighting)
        fog.apply(enable = material.fog)
    }
}
