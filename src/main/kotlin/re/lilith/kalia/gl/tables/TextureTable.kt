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
    private val textures = Int2ObjectOpenHashMap<GlTexture>()
    private var nextId = 1

    private var lastId = 0
    private var lastTexture: GlTexture? = null

    fun generate(): Int {
        val id = nextId++
        textures[id] = GlTexture(id)
        return id
    }

    fun delete(id: Int) {
        if (id == lastId) {
            lastId = 0
            lastTexture = null
        }
        textures.remove(id)?.close()
    }

    fun get(id: Int): GlTexture? {
        if (id <= 0) {
            return null
        }
        if (id == lastId) {
            return lastTexture
        }
        val texture = textures.getOrPut(id) {
            nextId = maxOf(nextId, id + 1)
            GlTexture(id)
        }
        lastId = id
        lastTexture = texture
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
        proxyWidth = if (fits) width else 0
        proxyHeight = if (fits) height else 0
    }

    private var proxyWidth = 0
    private var proxyHeight = 0

    fun proxyParameter(name: Int): Int = when (name) {
        GL_TEXTURE_WIDTH -> proxyWidth
        GL_TEXTURE_HEIGHT -> proxyHeight
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
                textures.values.forEach { it.dumpStaging(directory) }
            })
        }
    }

    fun clear() {
        textures.values.forEach(GlTexture::close)
        textures.clear()
    }

    private fun device(): RenderDevice? {
        KaliaEngine.ensureStarted()
        return KaliaEngine.device
    }
}
