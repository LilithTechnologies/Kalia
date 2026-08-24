package re.lilith.kalia.rendering.world

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.effect.StatusEffect
import org.lwjgl.opengl.GL11.GL_RGBA
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.mixins.access.GameRendererAccess
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LightMap {
    private const val SIZE = 16
    private const val LEVELS = 16

    private const val PUSH_CONSTANT_BYTES = 96

    private val program by lazy {
        ShaderProgram(
            label = "kalia/lightmap",
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("lightmap.vert", ShaderAssets.assemble("kalia:lightmap.vert")),
                ShaderStage.FRAGMENT to ShaderSource.Glsl("lightmap.frag", ShaderAssets.assemble("kalia:lightmap.frag")),
            ),
            bindings = emptyList(),
            pushConstantBytes = PUSH_CONSTANT_BYTES,
        )
    }

    private val pushConstants = ByteBuffer.allocateDirect(PUSH_CONSTANT_BYTES).order(ByteOrder.nativeOrder())

    fun texture(device: RenderDevice): GpuTexture? {
        val client = MinecraftClient.getInstance() ?: return null
        val renderer = client.gameRenderer ?: return null
        val glId = client.textureManager?.getTexture(renderer.lightmapTextureId)?.glId ?: return null
        val gl = TextureTable.get(glId) ?: return null
        // Vanilla only sizes the texture on its first upload, which no longer happens
        gl.defineLevel(0, SIZE, SIZE, GL_RGBA)
        return gl.ensureAllocated(device)
    }

    fun render(pass: PassContext) {
        val client = MinecraftClient.getInstance() ?: return
        val world = client.world ?: return
        val renderer = client.gameRenderer as? GameRendererAccess ?: return

        val tickDelta = WorldFrame.consumedState.tickDelta
        val brightness = world.dimension.lightLevelToBrightness

        pushConstants.clear()
        for (level in 0 until LEVELS) {
            pushConstants.putFloat(brightness[level])
        }

        pushConstants.putFloat(world.method_3649(1f))
        pushConstants.putFloat(renderer.lightmapFlicker)
        pushConstants.putFloat(client.options.gamma)
        pushConstants.putFloat(skyDarkness(renderer, tickDelta))
        pushConstants.putFloat(nightVision(client, renderer, tickDelta))
        pushConstants.putFloat(if (world.lightningTicksLeft > 0) 1f else 0f)
        pushConstants.putInt(world.dimension.type)
        pushConstants.flip()

        pass.bindPipeline(pipeline(pass))
        pass.pushConstants(pushConstants)
        pass.draw(vertexCount = 3)
    }

    private fun skyDarkness(renderer: GameRendererAccess, tickDelta: Float): Float {
        val current = renderer.skyDarkness
        if (current <= 0f) {
            return 0f
        }
        val last = renderer.lastSkyDarkness
        return last + (current - last) * tickDelta
    }

    private fun nightVision(client: MinecraftClient, renderer: GameRendererAccess, tickDelta: Float): Float {
        val player = client.player ?: return 0f
        if (!player.hasStatusEffect(StatusEffect.NIGHTVISION)) {
            return 0f
        }
        return renderer.invokeGetNightVisionStrength(player, tickDelta)
    }

    private fun pipeline(pass: PassContext): GpuPipeline = pass.device.createPipeline(
        GraphicsPipelineDescription(
            program = program,
            vertexFormat = null,
            attachments = pass.attachments,
            raster = RasterState.TWO_SIDED,
            depth = DepthState.DISABLED,
        ),
    )
}
