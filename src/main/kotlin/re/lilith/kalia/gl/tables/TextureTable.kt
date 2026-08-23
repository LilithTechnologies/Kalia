package re.lilith.kalia.gl.tables

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT
import org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.gl.TextureUnits
import re.lilith.kalia.gl.emulation.GlTexture
import re.lilith.kalia.renderer.device.RenderDevice
import java.io.File
import java.nio.ByteBuffer

object TextureTable {
    private val lock = Any()
    private val textures = Int2ObjectOpenHashMap<GlTexture>()
    private var nextId = 1

    @Volatile
    private var epoch = 0

    private val threadState = ThreadLocal.withInitial { TextureTableData() }

    private val state: TextureTableData get() = threadState.get()

    internal fun bindContext(data: TextureTableData) {
        threadState.set(data)
    }

    internal fun context(): TextureTableData = state

    private fun current(): TextureTableData {
        val active = state
        val currentEpoch = epoch
        if (active.epoch != currentEpoch) {
            active.epoch = currentEpoch
            active.forget()
        }
        return active
    }

    fun generate(): Int = synchronized(lock) {
        val id = nextId++
        textures[id] = GlTexture(id)
        id
    }

    fun delete(id: Int) {
        val removed = synchronized(lock) {
            epoch++
            textures.remove(id)
        }
        removed?.close()
    }

    fun get(id: Int): GlTexture? {
        if (id <= 0) {
            return null
        }
        val active = current()
        if (id == active.lastId) {
            return active.lastTexture
        }
        val texture = synchronized(lock) {
            textures.get(id) ?: GlTexture(id).also {
                nextId = maxOf(nextId, id + 1)
                textures.put(id, it)
            }
        }
        active.lastId = id
        active.lastTexture = texture
        return texture
    }

    fun boundTexture(unit: Int): GlTexture? = get(TextureUnits.boundTexture(unit))

    fun active(): GlTexture? = boundTexture(TextureUnits.activeUnit)

    fun defineLevel(level: Int, width: Int, height: Int, internalFormat: Int) {
        active()?.defineLevel(level, width, height, internalFormat)
    }

    fun defineProxyLevel(width: Int, height: Int) {
        val limit = KaliaEngine.device?.capabilities?.maxTextureSize ?: run {
            KaliaEngine.ensureStarted()
            KaliaEngine.device?.capabilities?.maxTextureSize
        } ?: 0

        val fits = width in 1..limit && height in 1..limit
        val active = state
        active.proxyWidth = if (fits) width else 0
        active.proxyHeight = if (fits) height else 0
    }

    fun proxyParameter(name: Int): Int = when (name) {
        GL_TEXTURE_WIDTH -> state.proxyWidth
        GL_TEXTURE_HEIGHT -> state.proxyHeight
        else -> 0
    }

    fun upload(
        level: Int,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        pixelFormat: Int,
        pixelType: Int,
        pixels: ByteBuffer?,
    ) {
        val device = device() ?: return
        active()?.upload(device, level, xOffset, yOffset, width, height, pixelFormat, pixelType, pixels)
    }

    fun generateMipmaps() {
        val device = device() ?: return
        active()?.generateMipmaps(device)
    }

    fun setParameter(name: Int, value: Int) {
        active()?.setParameter(name, value)
    }

    fun setParameter(name: Int, value: Float) {
        active()?.setParameter(name, value)
    }

    fun levelParameter(level: Int, name: Int): Int = active()?.levelParameter(level, name) ?: 0

    init {
        if (System.getProperty("kalia.dumpMips") != null) {
            Runtime.getRuntime().addShutdownHook(Thread {
                val directory = File("kalia-mip-dump").apply { mkdirs() }
                synchronized(lock) { textures.values.forEach { it.dumpStaging(directory) } }
            })
        }
    }

    fun clear() {
        synchronized(lock) {
            epoch++
            textures.values.forEach(GlTexture::close)
            textures.clear()
        }
    }

    private fun device(): RenderDevice? {
        KaliaEngine.ensureStarted()
        return KaliaEngine.device
    }
}
