package re.lilith.kalia.rendering.ui.item

import org.joml.Matrix4f
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.resource.TextureDescription
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Rasterises GUI item icons once and reuses them.

 * @author Lunasa
 * @since 1.0.0
 */
class GuiItemAtlas(
    private val device: RenderDevice,
    slotSize: Int,
    atlasSize: Int = DEFAULT_ATLAS_SIZE,
) : AutoCloseable {
    val size: Int = atlasSize

    private val slots = GuiItemSlots(slotSize = slotSize, atlasSize = atlasSize)

    val texture = device.createTexture(
        TextureDescription(
            label = "kalia/gui/item-atlas",
            extent = Extent(atlasSize, atlasSize),
            format = TextureFormat.RGBA8,
            sampled = true,
            renderTarget = true,
            transferable = false,
        ),
    )

    val sampler = device.createSampler(SamplerDescription.NEAREST_CLAMP)

    val depth = device.createTexture(
        TextureDescription(
            label = "kalia/gui/item-atlas-depth",
            extent = Extent(atlasSize, atlasSize),
            format = device.capabilities.supportedDepthFormats.first(),
            sampled = false,
            renderTarget = true,
            transferable = false,
        ),
    )

    private val pending = ArrayList<Fill>()
    private val pool = ArrayDeque<Fill>()

    private var frame = 0L

    var lastFills: Int = 0
        private set

    fun beginFrame() {
        frame++
        recycle()
    }

    fun acquire(key: Any, sourceVersion: Long, bake: (Request) -> Unit): Int {
        val packed = slots.acquire(key, frame, sourceVersion)
        val slot = GuiItemSlots.slotOf(packed)
        if (!GuiItemSlots.needsFill(packed)) {
            return slot
        }

        val fill = pool.removeLastOrNull() ?: Fill()
        fill.reset(slot)
        bake(fill)
        if (fill.vertexCount == 0) {
            fill.cleared = true
        }
        slots.setAnimated(slot, fill.animated)
        pending += fill
        return slot
    }

    fun acquireSlot(key: Any, sourceVersion: Long, animated: Boolean): Long =
        slots.acquire(key, frame, sourceVersion).also { slots.setAnimated(GuiItemSlots.slotOf(it), animated) }

    fun retry(slot: Int) = slots.forget(slot)

    fun retryPending() {
        for (fill in pending) {
            slots.forget(fill.slot)
        }
    }

    fun slotRect(slot: Int) = Rect(slots.slotX(slot), slots.slotY(slot), slots.slotSize, slots.slotSize)

    fun u0(slot: Int): Float = slots.slotU0(slot)
    fun v0(slot: Int): Float = slots.slotV0(slot)
    fun u1(slot: Int): Float = slots.slotU1(slot)
    fun v1(slot: Int): Float = slots.slotV1(slot)

    val isIdle: Boolean get() = pending.isEmpty()

    fun render(pass: PassContext) {
        lastFills = 0
        if (pending.isEmpty()) {
            return
        }

        for (fill in pending) {
            val x = slots.slotX(fill.slot)
            val y = slots.slotY(fill.slot)
            val size = slots.slotSize
            val area = Rect(x, y, size, size)

            pass.viewport(Viewport(x, y, size, size))
            pass.scissor(area)
            pass.clearAttachments(color = Color.TRANSPARENT, depth = 1f, area = area)

            if (fill.cleared || fill.vertexCount == 0) {
                continue
            }

            GuiItemPipeline.draw(device, pass, fill)
            lastFills++
        }

        pass.viewport(Viewport.of(pass.extent))
        pass.scissor(null)
    }

    fun invalidate() {
        slots.clear()
        recycle()
    }

    private fun recycle() {
        pool.addAll(pending)
        pending.clear()
    }

    override fun close() {
        texture.close()
        depth.close()
        sampler.close()
    }

    class Fill : Request {
        var slot: Int = 0
            internal set

        var vertexCount: Int = 0
            private set

        override var lit: Boolean = true
        override var animated: Boolean = false
        override var glint: Boolean = false

        var sourceTexture: GpuTexture? = null
            private set

        var sourceSampler: GpuSampler? = null
            private set

        var glintTexture: GpuTexture? = null
            private set

        var glintSampler: GpuSampler? = null
            private set

        internal var cleared = false

        val transform: Matrix4f = Matrix4f()

        override var baseVertexCount: Int = 0

        var vertices: ByteBuffer = ByteBuffer
            .allocateDirect(INITIAL_VERTEX_BYTES)
            .order(ByteOrder.nativeOrder())
            private set

        internal fun reset(slot: Int) {
            this.slot = slot
            vertexCount = 0
            lit = true
            animated = false
            cleared = false
            sourceTexture = null
            sourceSampler = null
            glintTexture = null
            glintSampler = null
            baseVertexCount = 0
            vertices.clear()
        }

        override fun geometry(source: ByteBuffer, vertexCount: Int) {
            val required = vertexCount * GuiItemMeshBuilder.VERTEX_BYTES
            if (vertices.capacity() < required) {
                var capacity = vertices.capacity()
                while (capacity < required) {
                    capacity = capacity shl 1
                }
                vertices = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
            }
            vertices.clear()
            val view = source.slice()
            view.limit(required)
            vertices.put(view)
            vertices.flip()
            this.vertexCount = vertexCount
        }

        override fun sourceTexture(texture: GpuTexture, sampler: GpuSampler) {
            sourceTexture = texture
            sourceSampler = sampler
        }

        override fun glintTexture(texture: GpuTexture, sampler: GpuSampler) {
            glintTexture = texture
            glintSampler = sampler
        }

        override fun transform(modelView: Matrix4f, projection: Matrix4f) {
            transform.set(projection).mul(modelView)
        }

        private companion object {
            const val INITIAL_VERTEX_BYTES = 4096 * GuiItemMeshBuilder.VERTEX_BYTES
        }
    }

    interface Request {
        var lit: Boolean
        var animated: Boolean

        var glint: Boolean

        fun geometry(source: ByteBuffer, vertexCount: Int)

        var baseVertexCount: Int

        fun sourceTexture(texture: GpuTexture, sampler: GpuSampler)

        fun glintTexture(texture: GpuTexture, sampler: GpuSampler)

        fun transform(modelView: Matrix4f, projection: Matrix4f)

    }

    companion object {
        const val DEFAULT_ATLAS_SIZE = 2048
        const val ICON_GUI_SIZE = 16

        fun slotSizeFor(guiScale: Int): Int = (ICON_GUI_SIZE * guiScale.coerceAtLeast(1))
    }
}
