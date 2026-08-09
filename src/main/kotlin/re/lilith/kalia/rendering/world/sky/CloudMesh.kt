package re.lilith.kalia.rendering.world.sky

import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import org.lwjgl.opengl.GL11.GL_QUADS
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.vertex.VertexFormatBridge

/**
 * The fancy & flat cloud layers
 */
object CloudMesh {
    private val FANCY_FORMAT = VertexFormats.POSITION_TEXTURE_COLOR_NORMAL

    private const val CELL = 8
    private const val THICKNESS = 4.0f
    private const val UV_SCALE = 0.00390625f
    private const val INSET = 9.765625E-4f
    private const val ALPHA = 0.8f

    private const val FIRST_CELL = -3
    private const val LAST_CELL = 4

    private const val TOP_SHADE = 1.0f
    private const val BOTTOM_SHADE = 0.7f
    private const val SIDE_X_SHADE = 0.9f
    private const val SIDE_Z_SHADE = 0.8f

    const val FLAT_UV_SCALE = 4.8828125E-4f
    private const val FLAT_CELL = 32
    private const val FLAT_EXTENT = 256

    private var bottom: PersistentMesh? = null
    private var top: PersistentMesh? = null
    private var sides: PersistentMesh? = null
    private var flat: PersistentMesh? = null

    val bottomMesh: PersistentMesh? get() = bottom
    val topMesh: PersistentMesh? get() = top
    val sideMesh: PersistentMesh? get() = sides
    val flatMesh: PersistentMesh? get() = flat

    /**
     * Builds the flat cloud layer used when clouds are not set to fancy
     */
    fun ensureFlatBuilt(): Boolean {
        if (flat != null) {
            return true
        }
        val device = KaliaEngine.device ?: return false
        flat = build(device, VertexFormats.POSITION_TEXTURE_COLOR, ::emitFlat)
        return true
    }

    private fun emitFlat(builder: BufferBuilder) {
        var originX = -FLAT_EXTENT
        while (originX < FLAT_EXTENT) {
            var originZ = -FLAT_EXTENT
            while (originZ < FLAT_EXTENT) {
                for (corner in 0 until 4) {
                    val dx = if (corner == 1 || corner == 2) FLAT_CELL else 0
                    val dz = if (corner == 0 || corner == 1) FLAT_CELL else 0
                    builder.vertex((originX + dx).toDouble(), 0.0, (originZ + dz).toDouble())
                    builder.texture(
                        ((originX + dx) * FLAT_UV_SCALE).toDouble(),
                        ((originZ + dz) * FLAT_UV_SCALE).toDouble(),
                    )
                    builder.color(1.0f, 1.0f, 1.0f, ALPHA)
                    builder.next()
                }
                originZ += FLAT_CELL
            }
            originX += FLAT_CELL
        }
    }

    fun ensureBuilt(): Boolean {
        if (bottom != null) {
            return true
        }
        val device = KaliaEngine.device ?: return false
        bottom = build(device, FANCY_FORMAT) { emitHorizontal(it, y = 0f, shade = BOTTOM_SHADE, normalY = -1f) }
        top = build(device, FANCY_FORMAT) { emitHorizontal(it, y = THICKNESS - INSET, shade = TOP_SHADE, normalY = 1f) }
        sides = build(device, FANCY_FORMAT, ::emitSides)
        return true
    }

    fun clear() {
        bottom?.close()
        top?.close()
        sides?.close()
        flat?.close()
        bottom = null
        top = null
        sides = null
        flat = null
    }

    private fun build(
        device: RenderDevice,
        format: VertexFormat,
        emit: (BufferBuilder) -> Unit,
    ): PersistentMesh {
        val builder = Tessellator.getInstance().buffer
        builder.begin(GL_QUADS, format)
        emit(builder)
        builder.end()
        val mesh = PersistentMesh(device, "kalia/cloud-mesh")
        mesh.upload(builder.buffer, VertexFormatBridge.translate(builder.format), builder.vertexCount)
        builder.reset()
        return mesh
    }

    private fun emitHorizontal(builder: BufferBuilder, y: Float, shade: Float, normalY: Float) {
        forEachCell { _, _, originX, originZ ->
            quad(builder, shade, 0f, normalY, 0f) { corner ->
                val dx = if (corner == 1 || corner == 2) CELL else 0
                val dz = if (corner == 0 || corner == 1) CELL else 0
                vertex(builder, originX + dx, y, originZ + dz, (originX + dx).toFloat(), (originZ + dz).toFloat())
            }
        }
    }

    private fun emitSides(builder: BufferBuilder) {
        forEachCell { cellX, cellZ, originX, originZ ->
            if (cellX > -1) {
                emitXSide(builder, originX, originZ, offset = 0f, normalX = -1f)
            }
            if (cellX <= 1) {
                emitXSide(builder, originX, originZ, offset = 1f - INSET, normalX = 1f)
            }
            if (cellZ > -1) {
                emitZSide(builder, originX, originZ, offset = 0f, normalZ = -1f)
            }
            if (cellZ <= 1) {
                emitZSide(builder, originX, originZ, offset = 1f - INSET, normalZ = 1f)
            }
        }
    }

    private fun emitXSide(builder: BufferBuilder, originX: Int, originZ: Int, offset: Float, normalX: Float) {
        for (column in 0 until CELL) {
            val x = originX + column + offset
            val u = originX + column + 0.5f
            quad(builder, SIDE_X_SHADE, normalX, 0f, 0f) { corner ->
                val y = if (corner == 1 || corner == 2) THICKNESS else 0f
                val dz = if (corner == 0 || corner == 1) CELL else 0
                vertex(builder, x, y, (originZ + dz).toFloat(), u, (originZ + dz).toFloat())
            }
        }
    }

    private fun emitZSide(builder: BufferBuilder, originX: Int, originZ: Int, offset: Float, normalZ: Float) {
        for (column in 0 until CELL) {
            val z = originZ + column + offset
            val v = originZ + column + 0.5f
            quad(builder, SIDE_Z_SHADE, 0f, 0f, normalZ) { corner ->
                val dx = if (corner == 1 || corner == 2) CELL else 0
                val y = if (corner == 0 || corner == 1) THICKNESS else 0f
                vertex(builder, (originX + dx).toFloat(), y, z, (originX + dx).toFloat(), v)
            }
        }
    }

    private inline fun forEachCell(body: (Int, Int, Int, Int) -> Unit) {
        for (cellX in FIRST_CELL..LAST_CELL) {
            for (cellZ in FIRST_CELL..LAST_CELL) {
                body(cellX, cellZ, cellX * CELL, cellZ * CELL)
            }
        }
    }

    private inline fun quad(
        builder: BufferBuilder,
        shade: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        corner: (Int) -> Unit,
    ) {
        for (index in 0 until 4) {
            corner(index)
            builder.color(shade, shade, shade, ALPHA)
            builder.normal(normalX, normalY, normalZ)
            builder.next()
        }
    }

    private fun vertex(builder: BufferBuilder, x: Number, y: Float, z: Number, u: Float, v: Float) {
        builder.vertex(x.toDouble(), y.toDouble(), z.toDouble())
        builder.texture((u * UV_SCALE).toDouble(), (v * UV_SCALE).toDouble())
    }
}
