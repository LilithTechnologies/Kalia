package re.lilith.kalia.rendering.ui.hud

import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.FrameAllocations
import re.lilith.kalia.frame.HostTimings
import dev.rdh.argentum.impl.debug.RenderMetrics
import re.lilith.kalia.frame.graph.BatchStats
import re.lilith.kalia.gl.FfpStats
import re.lilith.kalia.frame.graph.occlusion.EntityOcclusion
import re.lilith.kalia.frame.graph.EntityPoseStats
import re.lilith.kalia.shader.PipelineCache
import re.lilith.kalia.renderer.device.RenderStats
import re.lilith.kalia.rendering.ui.GuiLayer
import re.lilith.kalia.rendering.ui.GuiMaterial
import re.lilith.kalia.rendering.ui.UI
import re.lilith.kalia.rendering.ui.text.Font
import re.lilith.kalia.rendering.ui.text.Glyphs
import re.lilith.kalia.rendering.world.WorldFrameTimings
import re.lilith.kalia.voxel.VoxelDiagnostics
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

        val capabilities = KaliaEngine.device?.capabilities
        lines += "Kalia ${capabilities?.backend?.displayName ?: "inactive"}" +
            (if (capabilities?.supportsBindlessTextures == true) "  bindless" else "  bindless:no") +
            (if (capabilities?.validation == true) "  VALIDATION ON (slow)" else "")
        val wall = WorldFrameTimings.wallMillis
        lines += "wall ${millis(wall)}ms (${if (wall > 0.0) (1000.0 / wall).toInt() else 0} fps)" +
            "  inside ${millis(WorldFrameTimings.insideMillis)}" +
            "  outside ${millis(WorldFrameTimings.outsideMillis)}" +
            "  tick ${millis(HostTimings.tickMillis)}" +
            "  display ${millis(HostTimings.displayMillis)}"
        lines += "kalia ${millis(WorldFrameTimings.frameMillis)}ms" +
            "  collect ${millis(WorldFrameTimings.collectMillis)} (${percent(WorldFrameTimings.collectShare)})" +
            "  encode ${millis(WorldFrameTimings.encodeMillis)}"
        lines += "gpu wait ${millis(WorldFrameTimings.gpuWaitMillis)}ms" +
            if (FrameAllocations.isSupported) "  alloc ${bytes(FrameAllocations.bytesPerFrame)}/frame" else ""

        appendMetrics(WorldFrameTimings.stageCount, WorldFrameTimings::stageName, WorldFrameTimings::stageMillis)
        appendMetrics(WorldFrameTimings.partCount, WorldFrameTimings::partName, WorldFrameTimings::partMillis)

        lines += "draws ${RenderStats.draws}" +
            "  pipelines ${RenderStats.pipelineBinds}/${PipelineCache.distinctPipelines}" +
            "  descriptors ${RenderStats.descriptorBinds} (+${RenderStats.descriptorAllocations})"
        lines += "batches ${RenderStats.batches} absorbing ${RenderStats.batchedDraws} draws"
        // Yeah im not sure how else we can easily profile this
        // TODO: write this to a CSV so we get graphs
        lines += "parts ${BatchStats.parts}/${BatchStats.partFlushes}f" +
            "  labels ${BatchStats.labels} segs ${BatchStats.labelSegments}/${BatchStats.labelFlushes}f" +
            "  glyphs ${BatchStats.glyphs}" +
            "  groupmiss ${BatchStats.groupMisses}"
        lines += "entities ${EntityPoseStats.entitiesPerFrame}" +
            "  pose-stable ${EntityPoseStats.stablePercent}%" +
            "  staged ${BatchStats.stagedEntities}/${BatchStats.stagedParts}p"
        val sampled = RenderMetrics.getSampledFrames().coerceAtLeast(1)
        lines += "ents drawn ${RenderMetrics.getRenderedEntities() / sampled}" +
            "  culled ${RenderMetrics.getCulledEntities() / sampled}" +
            "  queries ${EntityOcclusion.queued}/${EntityOcclusion.capacity}"
        lines += "submit ${millis(RenderStats.submitNanos / NANOS_PER_MILLI)}" +
            "  upload ${millis(RenderStats.uploadNanos / NANOS_PER_MILLI)}" +
            "  graph exec ${millis(RenderStats.graphNanos / NANOS_PER_MILLI)}"
        lines += "passes ${RenderStats.passes}" +
            "  pass setup ${millis(RenderStats.passSetupNanos / NANOS_PER_MILLI)}"
        VoxelDiagnostics.report(lines)
        lines += "ffp matrix ${FfpStats.matrixPerFrame}" +
            "  state ${FfpStats.statePerFrame}" +
            "  uniform ${FfpStats.uniformPerFrame}"
        val overlapped = KaliaEngine.overlappedFrames
        val exclusive = KaliaEngine.exclusiveFrames
        val total = overlapped + exclusive
        lines += "overlap ${if (total == 0) 0 else overlapped * 100 / total}% of $total frames" +
            "  serialised $exclusive" +
            "  skipped ${KaliaEngine.skippedFrames}"

        return lines
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

    private const val NANOS_PER_MILLI = 1_000_000.0
}
