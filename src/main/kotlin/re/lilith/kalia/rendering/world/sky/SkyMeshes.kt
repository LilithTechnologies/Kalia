package re.lilith.kalia.rendering.world.sky

import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormats
import org.lwjgl.opengl.GL11.GL_QUADS
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.vertex.VertexFormatBridge
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object SkyMeshes {
    private const val CELL = 64
    private const val EXTENT = 384

    private const val LIGHT_SKY_Y = 16.0f
    private const val DARK_SKY_Y = -16.0f

    private const val STAR_SEED = 10842L
    private const val STAR_COUNT = 1500
    private const val STAR_DISTANCE = 100.0

    private var light: PersistentMesh? = null
    private var dark: PersistentMesh? = null
    private var stars: PersistentMesh? = null

    val lightSky get() = light

    val darkSky get() = dark

    val starField get() = stars

    fun ensureBuilt(): Boolean {
        if (light != null) {
            return true
        }
        val device = KaliaEngine.device ?: return false
        light = build(device, "kalia/sky-light") { emitHemisphere(it, LIGHT_SKY_Y, flipped = false) }
        dark = build(device, "kalia/sky-dark") { emitHemisphere(it, DARK_SKY_Y, flipped = true) }
        stars = build(device, "kalia/sky-stars", ::emitStars)
        return true
    }

    fun release() {
        light?.close()
        dark?.close()
        stars?.close()
        light = null
        dark = null
        stars = null
    }

    private fun build(device: RenderDevice, label: String, emit: (BufferBuilder) -> Unit): PersistentMesh {
        val builder = Tessellator.getInstance().buffer
        builder.begin(GL_QUADS, VertexFormats.POSITION)
        emit(builder)
        builder.end()
        val mesh = PersistentMesh(device, label)
        mesh.upload(builder.buffer, VertexFormatBridge.translate(builder.format), builder.vertexCount)
        builder.reset()
        return mesh
    }

    private fun emitHemisphere(buffer: BufferBuilder, y: Float, flipped: Boolean) {
        var x = -EXTENT
        while (x <= EXTENT) {
            var z = -EXTENT
            while (z <= EXTENT) {
                val near: Float
                val far: Float
                if (flipped) {
                    near = (x + CELL).toFloat()
                    far = x.toFloat()
                } else {
                    near = x.toFloat()
                    far = (x + CELL).toFloat()
                }

                buffer.vertex(near.toDouble(), y.toDouble(), z.toDouble()).next()
                buffer.vertex(far.toDouble(), y.toDouble(), z.toDouble()).next()
                buffer.vertex(far.toDouble(), y.toDouble(), (z + CELL).toDouble()).next()
                buffer.vertex(near.toDouble(), y.toDouble(), (z + CELL).toDouble()).next()

                z += CELL
            }
            x += CELL
        }
    }

    private fun emitStars(buffer: BufferBuilder) {
        val random = Random(STAR_SEED)

        repeat(STAR_COUNT) {
            var x = (random.nextFloat() * 2.0f - 1.0f).toDouble()
            var y = (random.nextFloat() * 2.0f - 1.0f).toDouble()
            var z = (random.nextFloat() * 2.0f - 1.0f).toDouble()
            val size = (0.15f + random.nextFloat() * 0.1f).toDouble()

            var lengthSquared = x * x + y * y + z * z
            if (lengthSquared >= 1.0 || lengthSquared <= 0.01) {
                return@repeat
            }

            lengthSquared = 1.0 / sqrt(lengthSquared)
            x *= lengthSquared
            y *= lengthSquared
            z *= lengthSquared

            val centreX = x * STAR_DISTANCE
            val centreY = y * STAR_DISTANCE
            val centreZ = z * STAR_DISTANCE

            val yaw = atan2(x, z)
            val yawSin = sin(yaw)
            val yawCos = cos(yaw)

            val pitch = atan2(sqrt(x * x + z * z), y)
            val pitchSin = sin(pitch)
            val pitchCos = cos(pitch)

            val roll = random.nextDouble() * Math.PI * 2.0
            val rollSin = sin(roll)
            val rollCos = cos(roll)

            for (corner in 0 until 4) {
                val cornerX = ((corner and 2) - 1) * size
                val cornerY = ((corner + 1 and 2) - 1) * size

                val rolledX = cornerX * rollCos - cornerY * rollSin
                val rolledY = cornerY * rollCos + cornerX * rollSin

                val pitchedY = rolledX * pitchSin
                val pitchedZ = -rolledX * pitchCos

                buffer.vertex(
                    centreX + (pitchedZ * yawSin - rolledY * yawCos),
                    centreY + pitchedY,
                    centreZ + (rolledY * yawSin + pitchedZ * yawCos),
                ).next()
            }
        }
    }
}
