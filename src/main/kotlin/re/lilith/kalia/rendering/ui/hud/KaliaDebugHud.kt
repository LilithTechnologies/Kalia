package re.lilith.kalia.rendering.ui.hud

import re.lilith.kalia.rendering.ui.GuiLayer
import re.lilith.kalia.rendering.ui.GuiMaterial
import re.lilith.kalia.rendering.ui.UI
import re.lilith.kalia.rendering.ui.text.Font
import re.lilith.kalia.rendering.ui.text.Glyphs

object KaliaDebugHud {
    fun render(font: Font, left: List<String?>, right: List<String?>, width: Int) {
        UI.inLayer(GuiLayer.OVERLAY) {
            UI.withMaterial(GuiMaterial.TRANSLUCENT) {
                renderColumn(font, left, width, rightAligned = false)
                renderColumn(font, right, width, rightAligned = true)
            }
        }
    }

    private fun renderColumn(font: Font, lines: List<String?>, width: Int, rightAligned: Boolean) {
        for (index in lines.indices) {
            val line = lines[index]
            if (line.isNullOrEmpty()) {
                continue
            }

            val textWidth = Glyphs.widthOf(font, line)
            val top = (LINE_SPACING * index + MARGIN).toFloat()
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

    private const val LINE_SPACING = 9
    private const val LINE_HEIGHT = 9f
    private const val MARGIN = 2
    private const val BACKDROP = 0x90505050.toInt()
    private const val TEXT = 0xFFE0E0E0.toInt()
}
