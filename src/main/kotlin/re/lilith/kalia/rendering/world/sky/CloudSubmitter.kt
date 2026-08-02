package re.lilith.kalia.rendering.world.sky

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_QUADS
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.rendering.world.*
import kotlin.math.floor

object CloudSubmitter {
    private val CLOUDS = Identifier("textures/environment/clouds.png")

    private const val FANCY_SCALE = 12.0f
    private const val FANCY_UV_SCALE = 0.00390625f

    private const val FANCY_Z_BIAS = 0.33

    private const val BOTTOM_VISIBLE_ABOVE = -5.0f
    private const val TOP_VISIBLE_BELOW = 5.0f

    fun submit(state: WorldFrameState, submissions: WorldSubmissions) {
        if (!state.active || state.cloudMode == 0) {
            return
        }
        val (texture, sampler) = resolve() ?: return
        val phase = if (state.cloudsAboveTranslucent) WorldPhase.CLOUDS_ABOVE else WorldPhase.CLOUDS_BELOW

        if (state.cloudMode == 2) {
            submitFancy(state, submissions, phase, texture, sampler)
        } else {
            submitFast(state, submissions, phase, texture, sampler)
        }
    }

    private fun submitFancy(
        state: WorldFrameState,
        submissions: WorldSubmissions,
        phase: WorldPhase,
        texture: GpuTexture,
        sampler: GpuSampler,
    ) {
        if (!CloudMesh.ensureBuilt()) {
            return
        }

        val scrollX = state.cloudScrollX / FANCY_SCALE
        val scrollZ = state.cloudScrollZ / FANCY_SCALE + FANCY_Z_BIAS

        val textureTransform = Matrix4f().translation(
            floor(scrollX).toFloat() * FANCY_UV_SCALE,
            floor(scrollZ).toFloat() * FANCY_UV_SCALE,
            0f,
        )

        val model = Matrix4f()
            .scale(FANCY_SCALE, 1f, FANCY_SCALE)
            .translate(
                -(scrollX - floor(scrollX)).toFloat(),
                state.cloudHeight,
                -(scrollZ - floor(scrollZ)).toFloat(),
            )

        for (pass in 0 until 2) {
            val material = WorldMaterial.CLOUDS.copy(
                colorMask = if (pass == 0) ColorMask.NONE else ColorMask.ALL,
            )

            if (state.cloudHeight > BOTTOM_VISIBLE_ABOVE) {
                submitMesh(
                    submissions,
                    CloudMesh.bottomMesh,
                    phase,
                    material,
                    model,
                    textureTransform,
                    state,
                    texture,
                    sampler
                )
            }
            if (state.cloudHeight <= TOP_VISIBLE_BELOW) {
                submitMesh(
                    submissions,
                    CloudMesh.topMesh,
                    phase,
                    material,
                    model,
                    textureTransform,
                    state,
                    texture,
                    sampler
                )
            }
            submitMesh(
                submissions,
                CloudMesh.sideMesh,
                phase,
                material,
                model,
                textureTransform,
                state,
                texture,
                sampler
            )
        }
    }

    private fun submitFast(
        state: WorldFrameState,
        submissions: WorldSubmissions,
        phase: WorldPhase,
        texture: GpuTexture,
        sampler: GpuSampler,
    ) {
        if (!CloudMesh.ensureFlatBuilt()) {
            return
        }

        val textureTransform = Matrix4f().translation(
            (state.cloudScrollX * CloudMesh.FLAT_UV_SCALE).toFloat(),
            (state.cloudScrollZ * CloudMesh.FLAT_UV_SCALE).toFloat(),
            0f,
        )
        val model = Matrix4f().translation(0f, state.cloudHeight, 0f)

        submitMesh(
            submissions,
            CloudMesh.flatMesh,
            phase,
            WorldMaterial.CLOUDS,
            model,
            textureTransform,
            state,
            texture,
            sampler,
        )
    }

    private fun submitMesh(
        submissions: WorldSubmissions,
        mesh: PersistentMesh?,
        phase: WorldPhase,
        material: WorldMaterial,
        model: Matrix4f,
        textureTransform: Matrix4f,
        state: WorldFrameState,
        texture: GpuTexture,
        sampler: GpuSampler,
    ) {
        val buffer = mesh?.vertexBuffer ?: return
        val format = mesh.format ?: return

        submissions.submit(
            WorldSubmission.Resident(
                phase = phase,
                material = material,
                buffer = buffer,
                format = format,
                glMode = GL_QUADS,
                vertexCount = mesh.vertexCount,
                texture = texture,
                sampler = sampler,
                transform = model,
                textureTransform = textureTransform,
                red = state.cloudRed,
                green = state.cloudGreen,
                blue = state.cloudBlue,
            ),
        )
    }

    private fun resolve(): Pair<GpuTexture, GpuSampler>? {
        val device = KaliaEngine.device ?: return null
        val manager = MinecraftClient.getInstance()?.textureManager ?: return null
        manager.bindTexture(CLOUDS)
        val gl = TextureTable.get(manager.getTexture(CLOUDS)?.glId ?: return null) ?: return null
        val texture = gl.texture ?: return null
        return texture to FrameResources.of(device).sampler(gl.pooledSampler)
    }
}
