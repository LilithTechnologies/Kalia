package re.lilith.kalia.rendering.world.sky

import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_QUADS
import org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.rendering.world.*
import re.lilith.kalia.rendering.world.WorldSubmission.Resident
import re.lilith.kalia.vertex.VertexFormatBridge

object SkySubmitter {
    private val SUN = Identifier("textures/environment/sun.png")
    private val MOON_PHASES = Identifier("textures/environment/moon_phases.png")
    private val END_SKY = Identifier("textures/environment/end_sky.png")

    private const val SUN_RADIUS = 30.0
    private const val MOON_RADIUS = 20.0
    private const val CELESTIAL_DISTANCE = 100.0

    private const val SUNRISE_SEGMENTS = 16
    private const val SUNRISE_RADIUS = 120.0f
    private const val SUNRISE_DEPTH = 40.0f
    private const val SUNRISE_APEX_Y = 100.0

    private const val VOID_BOX_DROP = 65.0

    private const val DARK_SKY_LIFT = 12.0f
    private const val VOID_PLANE_LIFT = 16.0

    private val transform = Matrix4f()

    fun submit(state: WorldFrameState, submissions: WorldSubmissions) {
        if (!state.active || !state.skyEnabled) {
            return
        }
        if (state.endSky) {
            submitEndSky(submissions)
            return
        }
        if (!state.hasSky) {
            return
        }
        if (!SkyMeshes.ensureBuilt()) {
            return
        }

        submitDome(state, submissions)
        submitSunrise(state, submissions)
        submitCelestials(state, submissions)
        submitVoid(state, submissions)
    }

    private fun submitDome(state: WorldFrameState, submissions: WorldSubmissions) {
        val mesh = SkyMeshes.lightSky ?: return
        val buffer = mesh.vertexBuffer ?: return
        val format = mesh.format ?: return

        submissions.submit(
            Resident(
                phase = WorldPhase.SKY,
                material = WorldMaterial.SKY,
                buffer = buffer,
                format = format,
                glMode = GL_QUADS,
                vertexCount = mesh.vertexCount,
                red = state.skyRed,
                green = state.skyGreen,
                blue = state.skyBlue,
            ),
        )
    }

    private fun submitSunrise(state: WorldFrameState, submissions: WorldSubmissions) {
        val colour = state.sunriseColor ?: return

        transform.identity()
            .rotate(Math.toRadians(90.0).toFloat(), 1f, 0f, 0f)
            .rotate(Math.toRadians(if (state.skyAngleSin < 0f) 180.0 else 0.0).toFloat(), 0f, 0f, 1f)
            .rotate(Math.toRadians(90.0).toFloat(), 0f, 0f, 1f)

        val builder = Tessellator.getInstance().buffer
        builder.begin(GL_TRIANGLE_FAN, VertexFormats.POSITION_COLOR)

        builder.vertex(0.0, SUNRISE_APEX_Y, 0.0).color(colour[0], colour[1], colour[2], colour[3]).next()
        for (segment in 0..SUNRISE_SEGMENTS) {
            val angle = segment.toFloat() * Math.PI.toFloat() * 2f / SUNRISE_SEGMENTS
            val sin = MathHelper.sin(angle)
            val cos = MathHelper.cos(angle)
            builder.vertex(
                (sin * SUNRISE_RADIUS).toDouble(),
                (cos * SUNRISE_RADIUS).toDouble(),
                (-cos * SUNRISE_DEPTH * colour[3]).toDouble(),
            ).color(colour[0], colour[1], colour[2], 0.0f).next()
        }
        builder.end()

        stage(
            submissions = submissions,
            builder = builder,
            phase = WorldPhase.SKY,
            material = WorldMaterial.SKY_BLENDED,
            glMode = GL_TRIANGLE_FAN,
            transform = Matrix4f(transform),
        )
    }

    private fun submitCelestials(state: WorldFrameState, submissions: WorldSubmissions) {
        val brightness = 1.0f - state.rainGradient

        transform.identity()
            .rotate(Math.toRadians(-90.0).toFloat(), 0f, 1f, 0f)
            .rotate(Math.toRadians((state.skyAngle * 360.0f).toDouble()).toFloat(), 1f, 0f, 0f)
        val celestial = Matrix4f(transform)

        sunTexture()?.let { (texture, sampler) ->
            val builder = Tessellator.getInstance().buffer
            builder.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE)
            builder.vertex(-SUN_RADIUS, CELESTIAL_DISTANCE, -SUN_RADIUS).texture(0.0, 0.0).next()
            builder.vertex(SUN_RADIUS, CELESTIAL_DISTANCE, -SUN_RADIUS).texture(1.0, 0.0).next()
            builder.vertex(SUN_RADIUS, CELESTIAL_DISTANCE, SUN_RADIUS).texture(1.0, 1.0).next()
            builder.vertex(-SUN_RADIUS, CELESTIAL_DISTANCE, SUN_RADIUS).texture(0.0, 1.0).next()
            builder.end()
            stage(
                submissions = submissions,
                builder = builder,
                phase = WorldPhase.SKY,
                material = WorldMaterial.CELESTIAL,
                glMode = GL_QUADS,
                transform = celestial,
                texture = texture,
                sampler = sampler,
                alpha = brightness,
            )
        }

        moonTexture()?.let { (texture, sampler) ->
            val column = state.moonPhase % 4
            val row = state.moonPhase / 4 % 2
            val u0 = column / 4.0f
            val v0 = row / 2.0f
            val u1 = (column + 1) / 4.0f
            val v1 = (row + 1) / 2.0f

            val builder = Tessellator.getInstance().buffer
            builder.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE)
            builder.vertex(-MOON_RADIUS, -CELESTIAL_DISTANCE, MOON_RADIUS).texture(u1.toDouble(), v1.toDouble()).next()
            builder.vertex(MOON_RADIUS, -CELESTIAL_DISTANCE, MOON_RADIUS).texture(u0.toDouble(), v1.toDouble()).next()
            builder.vertex(MOON_RADIUS, -CELESTIAL_DISTANCE, -MOON_RADIUS).texture(u0.toDouble(), v0.toDouble()).next()
            builder.vertex(-MOON_RADIUS, -CELESTIAL_DISTANCE, -MOON_RADIUS).texture(u1.toDouble(), v0.toDouble()).next()
            builder.end()
            stage(
                submissions = submissions,
                builder = builder,
                phase = WorldPhase.SKY,
                material = WorldMaterial.CELESTIAL,
                glMode = GL_QUADS,
                transform = celestial,
                texture = texture,
                sampler = sampler,
                alpha = brightness,
            )
        }

        val starAlpha = state.starBrightness * brightness
        if (starAlpha > 0f) {
            val mesh = SkyMeshes.starField ?: return
            val buffer = mesh.vertexBuffer ?: return
            val format = mesh.format ?: return
            submissions.submit(
                Resident(
                    phase = WorldPhase.SKY,
                    material = WorldMaterial.CELESTIAL,
                    buffer = buffer,
                    format = format,
                    glMode = GL_QUADS,
                    vertexCount = mesh.vertexCount,
                    transform = celestial,
                    red = starAlpha,
                    green = starAlpha,
                    blue = starAlpha,
                    alpha = starAlpha,
                ),
            )
        }
    }

    private fun submitVoid(state: WorldFrameState, submissions: WorldSubmissions) {
        val mesh = SkyMeshes.darkSky ?: return
        val buffer = mesh.vertexBuffer ?: return
        val format = mesh.format ?: return

        if (state.voidOffset < 0.0) {
            submissions.submit(
                Resident(
                    phase = WorldPhase.SKY,
                    material = WorldMaterial.SKY,
                    buffer = buffer,
                    format = format,
                    glMode = GL_QUADS,
                    vertexCount = mesh.vertexCount,
                    transform = Matrix4f().translate(0f, DARK_SKY_LIFT, 0f),
                    red = 0f,
                    green = 0f,
                    blue = 0f,
                ),
            )
            submitVoidBox(state, submissions)
        }

        val red: Float
        val green: Float
        val blue: Float
        if (state.hasGround) {
            red = state.skyRed * 0.2f + 0.04f
            green = state.skyGreen * 0.2f + 0.04f
            blue = state.skyBlue * 0.6f + 0.1f
        } else {
            red = state.skyRed
            green = state.skyGreen
            blue = state.skyBlue
        }

        submissions.submit(
            Resident(
                phase = WorldPhase.SKY,
                material = WorldMaterial.SKY,
                buffer = buffer,
                format = format,
                glMode = GL_QUADS,
                vertexCount = mesh.vertexCount,
                transform = Matrix4f().translate(0f, -(state.voidOffset - VOID_PLANE_LIFT).toFloat(), 0f),
                red = red,
                green = green,
                blue = blue,
            ),
        )
    }

    private fun submitVoidBox(state: WorldFrameState, submissions: WorldSubmissions) {
        val top = -((state.voidOffset + VOID_BOX_DROP).toFloat()).toDouble()
        val builder = Tessellator.getInstance().buffer
        builder.begin(GL_QUADS, VertexFormats.POSITION_COLOR)

        fun corner(x: Double, y: Double, z: Double) {
            builder.vertex(x, y, z).color(0, 0, 0, 255).next()
        }

        corner(-1.0, top, 1.0)
        corner(1.0, top, 1.0)
        corner(1.0, -1.0, 1.0)
        corner(-1.0, -1.0, 1.0)
        corner(-1.0, -1.0, -1.0)
        corner(1.0, -1.0, -1.0)
        corner(1.0, top, -1.0)
        corner(-1.0, top, -1.0)
        corner(1.0, -1.0, -1.0)
        corner(1.0, -1.0, 1.0)
        corner(1.0, top, 1.0)
        corner(1.0, top, -1.0)
        corner(-1.0, top, -1.0)
        corner(-1.0, top, 1.0)
        corner(-1.0, -1.0, 1.0)
        corner(-1.0, -1.0, -1.0)
        corner(-1.0, -1.0, -1.0)
        corner(-1.0, -1.0, 1.0)
        corner(1.0, -1.0, 1.0)
        corner(1.0, -1.0, -1.0)

        builder.end()
        stage(
            submissions = submissions,
            builder = builder,
            phase = WorldPhase.SKY,
            material = WorldMaterial.SKY_BLENDED,
            glMode = GL_QUADS,
        )
    }

    private fun submitEndSky(submissions: WorldSubmissions) {
        val (texture, sampler) = endSkyTexture() ?: return

        for (face in 0 until 6) {
            val model = Matrix4f()
            when (face) {
                1 -> model.rotate(Math.toRadians(90.0).toFloat(), 1f, 0f, 0f)
                2 -> model.rotate(Math.toRadians(-90.0).toFloat(), 1f, 0f, 0f)
                3 -> model.rotate(Math.toRadians(180.0).toFloat(), 1f, 0f, 0f)
                4 -> model.rotate(Math.toRadians(90.0).toFloat(), 0f, 0f, 1f)
                5 -> model.rotate(Math.toRadians(-90.0).toFloat(), 0f, 0f, 1f)
            }

            val builder = Tessellator.getInstance().buffer
            builder.begin(GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR)
            builder.vertex(-100.0, -100.0, -100.0).texture(0.0, 0.0).color(40, 40, 40, 255).next()
            builder.vertex(-100.0, -100.0, 100.0).texture(0.0, 16.0).color(40, 40, 40, 255).next()
            builder.vertex(100.0, -100.0, 100.0).texture(16.0, 16.0).color(40, 40, 40, 255).next()
            builder.vertex(100.0, -100.0, -100.0).texture(16.0, 0.0).color(40, 40, 40, 255).next()
            builder.end()

            stage(
                submissions = submissions,
                builder = builder,
                phase = WorldPhase.SKY,
                material = WorldMaterial.SKY_BLENDED,
                glMode = GL_QUADS,
                transform = model,
                texture = texture,
                sampler = sampler,
            )
        }
    }

    private fun stage(
        submissions: WorldSubmissions,
        builder: BufferBuilder,
        phase: WorldPhase,
        material: WorldMaterial,
        glMode: Int,
        transform: Matrix4f? = null,
        texture: GpuTexture? = null,
        sampler: GpuSampler? = null,
        red: Float = 1f,
        green: Float = 1f,
        blue: Float = 1f,
        alpha: Float = 1f,
    ) {
        val format = VertexFormatBridge.translate(builder.format)
        val vertexCount = builder.vertexCount
        val byteCount = vertexCount * format.format.stride
        if (vertexCount > 0) {
            val offset = submissions.stage(builder.buffer, byteCount)
            submissions.submit(
                WorldSubmission.Transient(
                    phase = phase,
                    material = material,
                    stagingOffset = offset,
                    byteCount = byteCount,
                    format = format,
                    glMode = glMode,
                    vertexCount = vertexCount,
                    texture = texture,
                    sampler = sampler,
                    transform = transform,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                ),
            )
        }
        builder.reset()
    }

    private fun sunTexture() = resolve(SUN)

    private fun moonTexture() = resolve(MOON_PHASES)

    private fun endSkyTexture() = resolve(END_SKY)

    private fun resolve(identifier: Identifier): Pair<GpuTexture, GpuSampler>? {
        val device = KaliaEngine.device ?: return null
        val manager = MinecraftClient.getInstance()?.textureManager ?: return null
        manager.bindTexture(identifier)
        val gl = TextureTable.get(manager.getTexture(identifier)?.glId ?: return null) ?: return null
        val texture = gl.texture ?: return null
        return texture to FrameResources.of(device).sampler(gl.pooledSampler)
    }
}
