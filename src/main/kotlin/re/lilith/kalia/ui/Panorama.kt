package re.lilith.kalia.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper

class Panorama(
    baseTextureId: Identifier
) {
    private val cubeMapTexture = CubeMapTexture(baseTextureId)
    private val cubeMap = CubeMap(baseTextureId)

    private var spin = 0f
    private var lastTime = System.nanoTime()

    init {
        MinecraftClient.getInstance().textureManager.loadTexture(baseTextureId, cubeMapTexture)
    }

    fun render(shouldSpin: Boolean) {
        val now = System.nanoTime()
        val deltaSeconds = (now - this.lastTime) / 1000000000.0f
        this.lastTime = now

        if (shouldSpin) {
            val speed = 1.0f

            this.spin = MathHelper.wrapDegrees(this.spin + deltaSeconds * 20.0f * speed * 0.1f)
        }

        cubeMap.render(10.0f, spin)
    }
}