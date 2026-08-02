package re.lilith.kalia.rendering.ui.hud

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.scoreboard.Scoreboard
import net.minecraft.scoreboard.ScoreboardObjective
import net.minecraft.util.Identifier
import re.lilith.kalia.rendering.ui.GuiLayer
import re.lilith.kalia.rendering.ui.GuiMaterial
import re.lilith.kalia.rendering.ui.UI
import re.lilith.kalia.rendering.ui.text.Font
import re.lilith.kalia.rendering.ui.text.Glyphs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object KaliaPlayerListHud {
    private val ICONS = Identifier("textures/gui/icons.png")

    fun render(
        font: Font,
        entries: List<PlayerListEntry>,
        scoreboard: Scoreboard?,
        objective: ScoreboardObjective?,
        headerLines: List<String>,
        footerLines: List<String>,
        showHeads: Boolean,
        width: Int,
    ) {
        val hearts = objective != null && isHearts(objective)

        var nameWidth = 0
        var scoreWidth = 0
        for (entry in entries) {
            nameWidth = max(nameWidth, Glyphs.widthOf(font, nameOf(entry)).toInt())
            if (objective != null && !hearts && scoreboard != null) {
                val score = scoreOf(scoreboard, objective, entry)
                scoreWidth = max(scoreWidth, Glyphs.widthOf(font, " $score").toInt())
            }
        }

        val shown = if (entries.size > MAX_ENTRIES) entries.subList(0, MAX_ENTRIES) else entries
        val total = shown.size

        var rows = total
        var columns = 1
        while (rows > ROWS_PER_COLUMN) {
            columns++
            rows = (total + columns - 1) / columns
        }

        val objectiveWidth = when {
            objective == null -> 0
            hearts -> HEARTS_WIDTH
            else -> scoreWidth
        }
        val headWidth = if (showHeads) HEAD_WIDTH else 0

        val entryWidth =
            min(columns * (headWidth + nameWidth + objectiveWidth + ENTRY_PADDING), width - SCREEN_INSET) / columns
        val left = width / 2 - (entryWidth * columns + (columns - 1) * COLUMN_GAP) / 2
        var y = TOP_MARGIN

        var blockWidth = entryWidth * columns + (columns - 1) * COLUMN_GAP
        for (line in headerLines) {
            blockWidth = max(blockWidth, Glyphs.widthOf(font, line).toInt())
        }
        for (line in footerLines) {
            blockWidth = max(blockWidth, Glyphs.widthOf(font, line).toInt())
        }

        val blockLeft = (width / 2 - blockWidth / 2 - 1).toFloat()
        val blockRight = (width / 2 + blockWidth / 2 + 1).toFloat()

        UI.inLayer(GuiLayer.OVERLAY) {
            UI.withMaterial(GuiMaterial.TRANSLUCENT) {
                if (headerLines.isNotEmpty()) {
                    UI.fill(
                        blockLeft,
                        (y - 1).toFloat(),
                        blockRight,
                        (y + headerLines.size * font.lineHeight).toFloat(),
                        BACKDROP,
                    )
                    for (line in headerLines) {
                        val lineWidth = Glyphs.widthOf(font, line)
                        Glyphs.drawWithShadow(font, line, width / 2f - lineWidth / 2f, y.toFloat(), TEXT)
                        y += font.lineHeight
                    }
                    y++
                }

                UI.fill(blockLeft, (y - 1).toFloat(), blockRight, (y + rows * ROW_HEIGHT).toFloat(), BACKDROP)

                for (index in 0 until total) {
                    val column = index / rows
                    val row = index % rows

                    var x = left + column * entryWidth + column * COLUMN_GAP
                    val entryY = y + row * ROW_HEIGHT

                    UI.fill(
                        x.toFloat(),
                        entryY.toFloat(),
                        (x + entryWidth).toFloat(),
                        (entryY + ENTRY_HEIGHT).toFloat(),
                        ENTRY_OVERLAY,
                    )

                    val entry = shown[index]
                    val spectator = isSpectator(entry)

                    if (showHeads) {
                        renderHead(entry, x.toFloat(), entryY.toFloat())
                        x += HEAD_WIDTH
                    }

                    val name = nameOf(entry)
                    if (spectator) {
                        Glyphs.drawWithShadow(font, ITALIC + name, x.toFloat(), entryY.toFloat(), SPECTATOR_TEXT)
                    } else {
                        Glyphs.drawWithShadow(font, name, x.toFloat(), entryY.toFloat(), TEXT)
                    }

                    if (objective != null && scoreboard != null && !spectator) {
                        val start = x + nameWidth + 1
                        val end = start + objectiveWidth
                        if (end - start > MIN_OBJECTIVE_SPAN) {
                            renderObjective(font, scoreboard, objective, entry, entryY, start, end, hearts)
                        }
                    }

                    renderLatency(entry, entryWidth, x - headWidth, entryY)
                }

                if (footerLines.isNotEmpty()) {
                    y += rows * ROW_HEIGHT + 1
                    UI.fill(
                        blockLeft,
                        (y - 1).toFloat(),
                        blockRight,
                        (y + footerLines.size * font.lineHeight).toFloat(),
                        BACKDROP,
                    )
                    for (line in footerLines) {
                        val lineWidth = Glyphs.widthOf(font, line)
                        Glyphs.drawWithShadow(font, line, width / 2f - lineWidth / 2f, y.toFloat(), TEXT)
                        y += font.lineHeight
                    }
                }
            }
        }
    }

    private fun renderObjective(
        font: Font,
        scoreboard: Scoreboard,
        objective: ScoreboardObjective,
        entry: PlayerListEntry,
        y: Int,
        start: Int,
        end: Int,
        hearts: Boolean,
    ) {
        val score = scoreOf(scoreboard, objective, entry)

        if (!hearts) {
            val text = YELLOW + score
            val textWidth = Glyphs.widthOf(font, text)
            Glyphs.drawWithShadow(font, text, end - textWidth, y.toFloat(), TEXT)
            return
        }

        val full = ceil(score / 2.0).toInt()
        val slots = max(ceil(score / 2.0).toInt(), MIN_HEART_SLOTS)
        if (full <= 0) {
            return
        }

        val spacing = min((end - start - 4).toFloat() / slots, HEART_SPACING)
        if (spacing <= MIN_HEART_SPACING) {
            val text = "${score / 2.0f}"
            Glyphs.drawWithShadow(
                font = font,
                text = text,
                x = (end + start) / 2f - Glyphs.widthOf(font, text) / 2f,
                y = y.toFloat(),
                argb = TEXT,
            )
            return
        }

        MinecraftClient.getInstance().textureManager.bindTexture(ICONS)
        val textureId = UI.boundTextureId()

        for (slot in full until slots) {
            UI.blit(textureId, start + slot * spacing, y.toFloat(), HEART_BACKGROUND, 0f, 9f, 9f)
        }
        for (slot in 0 until full) {
            UI.blit(textureId, start + slot * spacing, y.toFloat(), HEART_BACKGROUND, 0f, 9f, 9f)
            if (slot * 2 + 1 < score) {
                UI.blit(
                    textureId,
                    start + slot * spacing,
                    y.toFloat(),
                    if (slot >= 10) ABSORB_FULL else HEART_FULL,
                    0f,
                    9f,
                    9f,
                )
            }
            if (slot * 2 + 1 == score) {
                UI.blit(
                    textureId,
                    start + slot * spacing,
                    y.toFloat(),
                    if (slot >= 10) ABSORB_HALF else HEART_HALF,
                    0f,
                    9f,
                    9f,
                )
            }
        }
    }

    private fun renderHead(entry: PlayerListEntry, x: Float, y: Float) {
        val skin = runCatching { entry.skinTexture }.getOrNull() ?: return
        MinecraftClient.getInstance().textureManager.bindTexture(skin)
        val textureId = UI.boundTextureId()

        UI.texturedQuad(
            textureId = textureId,
            x0 = x, y0 = y, x1 = x + HEAD_SIZE, y1 = y + HEAD_SIZE,
            u0 = 8f / SKIN_SHEET, v0 = 8f / SKIN_SHEET,
            u1 = 16f / SKIN_SHEET, v1 = 16f / SKIN_SHEET,
        )
        UI.texturedQuad(
            textureId = textureId,
            x0 = x, y0 = y, x1 = x + HEAD_SIZE, y1 = y + HEAD_SIZE,
            u0 = 40f / SKIN_SHEET, v0 = 8f / SKIN_SHEET,
            u1 = 48f / SKIN_SHEET, v1 = 16f / SKIN_SHEET,
        )
    }

    private fun renderLatency(entry: PlayerListEntry, entryWidth: Int, x: Int, y: Int) {
        val latency = runCatching { entry.latency }.getOrDefault(-1)
        val band = when {
            latency < 0 -> 5
            latency < 150 -> 0
            latency < 300 -> 1
            latency < 600 -> 2
            latency < 1000 -> 3
            else -> 4
        }

        MinecraftClient.getInstance().textureManager.bindTexture(ICONS)
        UI.blit(
            textureId = UI.boundTextureId(),
            x = (x + entryWidth - 11).toFloat(),
            y = y.toFloat(),
            u = 0f,
            v = (176 + band * 8).toFloat(),
            width = 10f,
            height = 8f,
        )
    }

    private fun scoreOf(scoreboard: Scoreboard, objective: ScoreboardObjective, entry: PlayerListEntry): Int {
        val name = runCatching { entry.profile?.name }.getOrNull() ?: return 0
        return runCatching { scoreboard.getPlayerScore(name, objective).score }.getOrDefault(0)
    }

    private fun isHearts(objective: ScoreboardObjective): Boolean =
        runCatching { objective.renderType.toString().contains("HEART", ignoreCase = true) }.getOrDefault(false)

    private fun isSpectator(entry: PlayerListEntry): Boolean =
        runCatching { entry.gameMode?.toString()?.contains("SPECTATOR", ignoreCase = true) }.getOrNull() ?: false

    fun nameOf(entry: PlayerListEntry): String {
        runCatching { entry.displayName?.asFormattedString() }.getOrNull()?.let { return it }

        val profileName = runCatching { entry.profile?.name }.getOrNull() ?: return ""
        val team = runCatching { entry.scoreboardTeam }.getOrNull()
        return runCatching { net.minecraft.scoreboard.Team.decorateName(team, profileName) }
            .getOrDefault(profileName)
    }

    private const val MAX_ENTRIES = 80
    private const val ROWS_PER_COLUMN = 20
    private const val ROW_HEIGHT = 9
    private const val ENTRY_HEIGHT = 8
    private const val HEAD_SIZE = 8f
    private const val SKIN_SHEET = 64f

    private const val HEAD_WIDTH = 9

    private const val ENTRY_PADDING = 13

    private const val HEARTS_WIDTH = 90

    private const val SCREEN_INSET = 50

    private const val COLUMN_GAP = 5
    private const val TOP_MARGIN = 10

    private const val MIN_OBJECTIVE_SPAN = 5

    private const val MIN_HEART_SLOTS = 10
    private const val HEART_SPACING = 9f
    private const val MIN_HEART_SPACING = 3f

    private const val HEART_BACKGROUND = 16f
    private const val HEART_FULL = 52f
    private const val HEART_HALF = 61f
    private const val ABSORB_FULL = 160f
    private const val ABSORB_HALF = 169f

    private const val ITALIC = "§o"
    private const val YELLOW = "§e"

    private const val BACKDROP = 0x80000000.toInt()

    private const val ENTRY_OVERLAY = 0x20FFFFFF

    private const val TEXT = 0xFFFFFFFF.toInt()
    private const val SPECTATOR_TEXT = 0x90FFFFFF.toInt()
}
