package re.lilith.kalia.rendering.ui.hud

import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap
import net.minecraft.client.gui.hud.ChatHudLine
import net.minecraft.text.Text
import re.lilith.kalia.rendering.ui.GuiLayer
import re.lilith.kalia.rendering.ui.GuiMaterial
import re.lilith.kalia.rendering.ui.UI
import re.lilith.kalia.rendering.ui.text.Font
import re.lilith.kalia.rendering.ui.text.Glyphs
import kotlin.math.min

object KaliaChatHud {
    fun render(
        font: Font,
        visible: List<ChatHudLine>,
        ticks: Int,
        scrolled: Int,
        focused: Boolean,
        scale: Float,
        widthUnits: Int,
        lineCount: Int,
        bottom: Float,
    ) {
        if (visible.isEmpty() || scale <= 0f) {
            return
        }

        val shown = min(lineCount, visible.size - scrolled)
        if (shown <= 0) {
            return
        }

        var drawn = 0

        UI.inLayer(GuiLayer.OVERLAY) {
            for (index in 0 until shown) {
                val line = visible.getOrNull(index + scrolled) ?: continue

                val age = ticks - line.creationTick
                if (age >= FADE_OUT_TICKS && !focused) {
                    continue
                }

                val opacity = if (focused) 1.0 else fadeOf(age)
                val textAlpha = (255.0 * opacity).toInt().coerceIn(0, 255)
                if (textAlpha <= 3) {
                    continue
                }
                val backdropAlpha = (255.0 * opacity * BACKDROP_OPACITY).toInt().coerceIn(0, 255)

                val y = bottom - (index + 1) * LINE_HEIGHT * scale
                val height = LINE_HEIGHT * scale
                val width = widthUnits * scale

                if (backdropAlpha > 0) {
                    UI.withMaterial(GuiMaterial.TRANSLUCENT) {
                        UI.fill(
                            x0 = 0f,
                            y0 = y,
                            x1 = width + 4f * scale,
                            y1 = y + height,
                            argb = backdropAlpha shl 24,
                        )
                    }
                }

                val text = line.text?.let(::formattedOf) ?: continue
                drawScaled(
                    font = font,
                    text = text,
                    x = SIDE_PADDING * scale,
                    y = y + (LINE_OFFSET * scale),
                    argb = (textAlpha shl 24) or 0x00FFFFFF,
                    scale = scale,
                )
                drawn++
            }

            if (focused && scrolled > 0) {
                renderScrollBar(visible.size, lineCount, scrolled, scale, bottom)
            }
        }

        lastLinesDrawn = drawn
    }

    var lastLinesDrawn = 0
        private set

    private val formatted = Reference2ObjectLinkedOpenHashMap<Text, String>()

    private fun formattedOf(text: Text): String {
        formatted.getAndMoveToFirst(text)?.let { return it }
        val built = text.asFormattedString()
        formatted.putAndMoveToFirst(text, built)
        if (formatted.size > MAX_CACHED_LINES) {
            formatted.removeLast()
        }
        return built
    }

    fun invalidate() {
        formatted.clear()
    }

    private fun drawScaled(font: Font, text: String, x: Float, y: Float, argb: Int, scale: Float) {
        if (scale == 1f) {
            Glyphs.drawWithShadow(font, text, x, y, argb)
            return
        }
        Glyphs.drawScaledWithShadow(font, text, x, y, argb, scale)
    }

    private fun renderScrollBar(total: Int, lineCount: Int, scrolled: Int, scale: Float, bottom: Float) {
        val trackHeight = lineCount * LINE_HEIGHT * scale
        val top = bottom - trackHeight

        val barHeight = (trackHeight * lineCount / total).coerceAtLeast(MIN_SCROLLBAR)
        val travel = trackHeight - barHeight
        val progress = scrolled.toFloat() / (total - lineCount).coerceAtLeast(1)
        val barTop = top + travel * (1f - progress)

        UI.withMaterial(GuiMaterial.TRANSLUCENT) {
            UI.fill(0f, barTop, SCROLLBAR_WIDTH * scale, barTop + barHeight, SCROLLBAR_COLOUR)
        }
    }

    private fun fadeOf(age: Int): Double {
        if (age >= FADE_OUT_TICKS) {
            return 0.0
        }
        val remaining = 1.0 - age.toDouble() / FADE_OUT_TICKS
        val eased = remaining * 10.0
        val clamped = eased.coerceIn(0.0, 1.0)
        return clamped * clamped
    }

    private const val LINE_HEIGHT = 9f
    private const val LINE_OFFSET = 1f
    private const val SIDE_PADDING = 2f
    private const val FADE_OUT_TICKS = 200
    private const val BACKDROP_OPACITY = 0.5
    private const val SCROLLBAR_WIDTH = 2f
    private const val MIN_SCROLLBAR = 4f
    private const val SCROLLBAR_COLOUR = 0xCC3F3F3F.toInt()
    private const val MAX_CACHED_LINES = 256
}
