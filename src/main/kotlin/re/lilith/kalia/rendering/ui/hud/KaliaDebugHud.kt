package re.lilith.kalia.rendering.ui.hud

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.boss.BossBar
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LightType
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.FrameAllocations
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.mixins.access.GameRendererAccess
import re.lilith.kalia.renderer.device.RenderStats
import re.lilith.kalia.rendering.ui.GuiLayer
import re.lilith.kalia.rendering.ui.GuiMaterial
import re.lilith.kalia.rendering.ui.UI
import re.lilith.kalia.rendering.ui.text.Font
import re.lilith.kalia.rendering.ui.text.Glyphs
import re.lilith.kalia.rendering.world.WorldFrameTimings
import java.util.Locale
import kotlin.math.roundToInt

object KaliaDebugHud {
    fun render(font: Font, left: List<String?>, right: List<String?>, width: Int) {
        val diagnostics = buildDiagnostics()

        UI.inLayer(GuiLayer.OVERLAY) {
            UI.withMaterial(GuiMaterial.TRANSLUCENT) {
                renderColumn(font, left, width, rightAligned = false)
                renderColumn(font, right, width, rightAligned = true)
                renderColumn(font, diagnostics, width, rightAligned = true, firstLine = right.size + 1)
            }
        }
    }

    private val lines = ArrayList<String>()
    private val builder = StringBuilder()

    private fun buildDiagnostics(): List<String> {
        lines.clear()

        lines += "Kalia ${KaliaEngine.device?.capabilities?.backend?.displayName ?: "inactive"}"
        lines += "frame ${millis(WorldFrameTimings.frameMillis)}ms" +
            "  collect ${millis(WorldFrameTimings.collectMillis)} (${percent(WorldFrameTimings.collectShare)})" +
            "  encode ${millis(WorldFrameTimings.encodeMillis)}"
        lines += "gpu wait ${millis(WorldFrameTimings.gpuWaitMillis)}ms" +
            if (FrameAllocations.isSupported) "  alloc ${bytes(FrameAllocations.bytesPerFrame)}/frame" else ""

        appendMetrics(WorldFrameTimings.stageCount, WorldFrameTimings::stageName, WorldFrameTimings::stageMillis)
        appendMetrics(WorldFrameTimings.partCount, WorldFrameTimings::partName, WorldFrameTimings::partMillis)

        appendWorldLight()

        lines += "draws ${RenderStats.draws}" +
            "  pipelines ${RenderStats.pipelineBinds}" +
            "  descriptors ${RenderStats.descriptorBinds} (+${RenderStats.descriptorAllocations})"
        lines += "batches ${RenderStats.batches} absorbing ${RenderStats.batchedDraws} draws"

        return lines
    }

    private fun appendWorldLight() {
        val client = MinecraftClient.getInstance() ?: return
        val world = client.world ?: return
        val player = client.player ?: return

        val pos = BlockPos(player.x, player.y + 1.0, player.z)
        val sky = world.getLightAtPos(LightType.SKY, pos)
        val block = world.getLightAtPos(LightType.BLOCK, pos)
        val packed = world.getLight(pos, 0)

        lines += "world light sky=$sky block=$block" +
            "  packed=${packed.toString(16)}" +
            "  sunlit=${world.receivesSunlight(pos)}"
        lines += "entity lightmap s=${ShaderUniforms.lightmapS()} t=${ShaderUniforms.lightmapT()}" +
            "  enabled=${ShaderUniforms.isLightmapEnabled()}"

        val brightness = world.dimension.lightLevelToBrightness
        lines += "sky bright=${millis(world.method_3649(1f).toDouble())}" +
            "  table[0]=${millis(brightness[0].toDouble())}" +
            "  table[15]=${millis(brightness[15].toDouble())}" +
            "  angle=${millis(world.getSkyAngle(1f).toDouble())}"

        val renderer = client.gameRenderer as? GameRendererAccess ?: return
        lines += "gamma=${millis(client.options.gamma.toDouble())}" +
            "  skyDark=${millis(renderer.skyDarkness.toDouble())}" +
            "  flicker=${millis(renderer.lightmapFlicker.toDouble())}" +
            "  time=${world.timeOfDay % 24000L}"
        lines += "boss darken=${BossBar.darkenSky}" +
            "  live=${BossBar.framesToLive}" +
            "  name=${BossBar.name ?: "none"}"
    }

    private fun appendMetrics(count: Int, name: (Int) -> String, value: (Int) -> Double) {
        builder.setLength(0)
        for (index in 0 until count) {
            if (builder.isNotEmpty()) {
                builder.append("  ")
            }
            builder.append(name(index)).append(' ').append(millis(value(index)))
            if ((index + 1) % METRICS_PER_LINE == 0) {
                lines += builder.toString()
                builder.setLength(0)
            }
        }
        if (builder.isNotEmpty()) {
            lines += builder.toString()
        }
    }

    private fun millis(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun bytes(value: Double): String = when {
        value >= BYTES_PER_MEBIBYTE -> String.format(Locale.ROOT, "%.2f MiB", value / BYTES_PER_MEBIBYTE)
        value >= BYTES_PER_KIBIBYTE -> String.format(Locale.ROOT, "%.1f KiB", value / BYTES_PER_KIBIBYTE)
        else -> "${value.roundToInt()} B"
    }

    private fun percent(share: Double): String = "${(share * 100.0).roundToInt()}%"

    private fun renderColumn(
        font: Font,
        lines: List<String?>,
        width: Int,
        rightAligned: Boolean,
        firstLine: Int = 0,
    ) {
        for (index in lines.indices) {
            val line = lines[index]
            if (line.isNullOrEmpty()) {
                continue
            }

            val textWidth = Glyphs.widthOf(font, line)
            val top = (LINE_SPACING * (firstLine + index) + MARGIN).toFloat()
            val x = if (rightAligned) width - MARGIN - textWidth else MARGIN.toFloat()

            UI.fill(
                x0 = x - 1f,
                y0 = top - 1f,
                x1 = x + textWidth + 1f,
                y1 = top + (LINE_HEIGHT - 1),
                argb = BACKDROP,
            )

            Glyphs.draw(font, line, x, top, TEXT, shadow = false)
        }
    }

    private const val BYTES_PER_KIBIBYTE = 1024.0
    private const val BYTES_PER_MEBIBYTE = 1024.0 * 1024.0
    private const val METRICS_PER_LINE = 4
    private const val LINE_SPACING = 9
    private const val LINE_HEIGHT = 9f
    private const val MARGIN = 2
    private const val BACKDROP = 0x90505050.toInt()
    private const val TEXT = 0xFFE0E0E0.toInt()
}
