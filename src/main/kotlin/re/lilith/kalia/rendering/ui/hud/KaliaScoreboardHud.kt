package re.lilith.kalia.rendering.ui.hud

import net.minecraft.scoreboard.Scoreboard
import net.minecraft.scoreboard.ScoreboardObjective
import net.minecraft.scoreboard.ScoreboardPlayerScore
import net.minecraft.scoreboard.Team
import re.lilith.kalia.rendering.ui.GuiLayer
import re.lilith.kalia.rendering.ui.GuiMaterial
import re.lilith.kalia.rendering.ui.UI
import re.lilith.kalia.rendering.ui.text.Font
import re.lilith.kalia.rendering.ui.text.Glyphs
import kotlin.math.max

object KaliaScoreboardHud {
    private val rows = ArrayList<ScoreboardPlayerScore>(MAX_ROWS)

    fun render(
        font: Font,
        scoreboard: Scoreboard,
        objective: ScoreboardObjective,
        width: Int,
        height: Int,
    ) {
        val all = runCatching { scoreboard.getAllPlayerScores(objective) }.getOrNull() ?: return

        rows.clear()
        for (score in all) {
            if (score == null) {
                continue
            }
            val name = score.playerName
            if (name != null && !name.startsWith("#")) {
                rows += score
            }
        }
        if (rows.isEmpty()) {
            return
        }
        while (rows.size > MAX_ROWS) {
            rows.removeAt(0)
        }

        val title = objective.displayName ?: ""
        var widest = Glyphs.widthOf(font, title)

        for (score in rows) {
            val joined = decorate(scoreboard, score) + ": " + RED + score.score
            widest = max(widest, Glyphs.widthOf(font, joined))
        }

        val totalHeight = rows.size * LINE_HEIGHT
        val bottom = height / 2f + totalHeight / 3f
        val left = width - widest - MARGIN
        val right = (width - MARGIN + 2).toFloat()

        UI.inLayer(GuiLayer.OVERLAY) {
            UI.withMaterial(GuiMaterial.TRANSLUCENT) {
                var index = 0
                for (score in rows) {
                    index++

                    val name = decorate(scoreboard, score)
                    val value = RED + score.score
                    val y = bottom - index * LINE_HEIGHT

                    UI.fill(left - 2f, y, right, y + LINE_HEIGHT, ROW_BACKDROP)

                    Glyphs.draw(font, name, left, y, TEXT, shadow = false)
                    Glyphs.draw(
                        font = font,
                        text = value,
                        x = right - Glyphs.widthOf(font, value),
                        y = y,
                        argb = TEXT,
                        shadow = false,
                    )

                    if (index == rows.size) {
                        UI.fill(left - 2f, y - LINE_HEIGHT - 1f, right, y - 1f, TITLE_BACKDROP)
                        UI.fill(left - 2f, y - 1f, right, y, ROW_BACKDROP)
                        Glyphs.draw(
                            font = font,
                            text = title,
                            x = left + widest / 2f - Glyphs.widthOf(font, title) / 2f,
                            y = y - LINE_HEIGHT,
                            argb = TEXT,
                            shadow = false,
                        )
                    }
                }
            }
        }
    }

    private fun decorate(scoreboard: Scoreboard, score: ScoreboardPlayerScore): String {
        val name = score.playerName ?: return ""
        val team = runCatching { scoreboard.getPlayerTeam(name) }.getOrNull()
        return runCatching { Team.decorateName(team, name) }.getOrDefault(name)
    }

    private const val MAX_ROWS = 15
    private const val LINE_HEIGHT = 9f
    private const val MARGIN = 3

    private const val RED = "§c"

    private const val ROW_BACKDROP = 0x50000000
    private const val TITLE_BACKDROP = 0x60000000

    private const val TEXT = 0xFFFFFFFF.toInt()
}
