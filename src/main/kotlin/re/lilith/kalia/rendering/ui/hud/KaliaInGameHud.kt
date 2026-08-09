package re.lilith.kalia.rendering.ui.hud

import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.entity.player.ClientPlayerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import re.lilith.kalia.frame.FrameCounter
import re.lilith.kalia.mixins.access.ChatHudAccess
import re.lilith.kalia.mixins.access.PlayerListHudAccess
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.rendering.ui.GuiLayer
import re.lilith.kalia.rendering.ui.GuiMaterial
import re.lilith.kalia.rendering.ui.UI
import re.lilith.kalia.rendering.ui.text.Font
import re.lilith.kalia.rendering.ui.text.Glyphs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object KaliaInGameHud {
    private val WIDGETS = Identifier("textures/gui/widgets.png")
    private val ICONS = Identifier("textures/gui/icons.png")
    private val VIGNETTE = Identifier("textures/misc/vignette.png")
    private val PUMPKIN_BLUR = Identifier("textures/misc/pumpkinblur.png")

    private val random = Random(0)

    fun render(client: MinecraftClient, font: Font, ticks: Int, state: HudState) {
        val width = UI.width.toInt()
        val height = UI.height.toInt()
        val player = client.player ?: return

        if (client.options.hudHidden) {
            renderChat(client, font, ticks, height)
            return
        }

        val spectator = client.interactionManager?.isSpectator ?: false

        renderPumpkinOverlay(client, width, height)
        renderVignette(client, width, height, state.vignetteDarkness)

        if (showCrosshair(client)) {
            renderCrosshair(width, height)
        }

        renderHotbarBar(player, width, height, spectator)

        val interaction = client.interactionManager
        if (interaction == null || interaction.hasStatusBars()) {
            renderStatusBars(player, width, height, ticks, state)
        }
        if (interaction == null || interaction.hasExperienceBar()) {
            renderExperience(client, player, width, height)
        }

        renderHeldItemName(font, width, height, state)
        if (client.options.debugEnabled) {
            state.debugHud?.run()
        }
        renderOverlayMessage(font, width, height, state)
        renderTitle(font, width, height, state)

        renderSidebar(client, font, player, width, height)
        renderChat(client, font, ticks, height)
        renderPlayerList(client, font, width)

        FrameCounter.render()
    }

    private fun renderSidebar(
        client: MinecraftClient,
        font: Font,
        player: PlayerEntity,
        width: Int,
        height: Int,
    ) {
        val scoreboard = client.world?.scoreboard ?: return

        var objective = scoreboard.getObjectiveForSlot(SIDEBAR_SLOT)
        val playerName = runCatching { player.gameProfile?.name }.getOrNull()
        val team = playerName?.let { runCatching { scoreboard.getPlayerTeam(it) }.getOrNull() }
        if (team != null) {
            val colourIndex = runCatching { team.formatting.colorIndex }.getOrDefault(-1)
            if (colourIndex >= 0) {
                objective = scoreboard.getObjectiveForSlot(TEAM_SIDEBAR_BASE + colourIndex) ?: objective
            }
        }

        if (objective != null) {
            KaliaScoreboardHud.render(font, scoreboard, objective, width, height)
        }
    }

    private fun renderChat(client: MinecraftClient, font: Font, ticks: Int, height: Int) {
        val chat = runCatching { client.inGameHud.chatHud }.getOrNull() ?: return
        val access = chat as? ChatHudAccess ?: return

        KaliaChatHud.render(
            font = font,
            visible = access.getVisibleMessages() ?: return,
            ticks = ticks,
            scrolled = access.getScrolledLines(),
            focused = chat.isChatFocused,
            scale = chat.chatScale,
            widthUnits = chat.width,
            lineCount = chat.visibleLineCount,
            bottom = height - CHAT_BOTTOM_OFFSET,
        )
    }

    private fun renderPlayerList(client: MinecraftClient, font: Font, width: Int) {
        if (!client.options.playerListKey.isPressed) {
            return
        }
        val handler = client.player?.networkHandler ?: return
        val entries = runCatching { handler.playerList }.getOrNull() ?: return
        if (entries.isEmpty()) {
            return
        }

        val hud = client.inGameHud.playerListWidget as? PlayerListHudAccess
        val header = runCatching { hud?.getHeader()?.asFormattedString() }.getOrNull()
        val footer = runCatching { hud?.getFooter()?.asFormattedString() }.getOrNull()

        val scoreboard = client.world?.scoreboard
        val objective = scoreboard?.let { runCatching { it.getObjectiveForSlot(LIST_SLOT) }.getOrNull() }

        val limit = (width - 50).coerceAtLeast(1)
        val headerLines = header?.let { wrap(client, it, limit) } ?: emptyList()
        val footerLines = footer?.let { wrap(client, it, limit) } ?: emptyList()

        val showHeads = runCatching {
            client.isIntegratedServerRunning || client.networkHandler.clientConnection.isEncrypted
        }.getOrDefault(true)

        KaliaPlayerListHud.render(
            font = font,
            entries = entries.sortedWith(PLAYER_ORDER),
            scoreboard = scoreboard,
            objective = objective,
            headerLines = headerLines,
            footerLines = footerLines,
            showHeads = showHeads,
            width = width,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun wrap(client: MinecraftClient, text: String, limit: Int) = runCatching { client.textRenderer.wrapLines(text, limit) as List<String> }.getOrDefault(listOf(text))

    private val PLAYER_ORDER = Comparator<PlayerListEntry> { a, b ->
        val playingA = !isSpectatorEntry(a)
        val playingB = !isSpectatorEntry(b)
        if (playingA != playingB) {
            return@Comparator if (playingA) -1 else 1
        }
        val teamA = runCatching { a.scoreboardTeam?.name }.getOrNull() ?: ""
        val teamB = runCatching { b.scoreboardTeam?.name }.getOrNull() ?: ""
        val byTeam = teamA.compareTo(teamB)
        if (byTeam != 0) {
            return@Comparator byTeam
        }
        val nameA = runCatching { a.profile?.name }.getOrNull() ?: ""
        val nameB = runCatching { b.profile?.name }.getOrNull() ?: ""
        nameA.compareTo(nameB)
    }

    private fun isSpectatorEntry(entry: PlayerListEntry): Boolean =
        runCatching { entry.gameMode?.toString()?.contains("SPECTATOR", ignoreCase = true) }.getOrNull() ?: false

    private fun renderCrosshair(width: Int, height: Int) {
        UI.withMaterial(GuiMaterial.INVERT) {
            blit(
                ICONS,
                x = (width / 2 - 7).toFloat(),
                y = (height / 2 - 7).toFloat(),
                u = 0f,
                v = 0f,
                width = 16f,
                height = 16f,
            )
        }
    }

    private fun showCrosshair(client: MinecraftClient): Boolean {
        if (client.currentScreen != null) {
            return false
        }
        if (client.options.debugEnabled && !client.options.debugFpsEnabled) {
            return false
        }
        if (client.options.perspective != 0) {
            return false
        }
        return !(client.interactionManager?.isSpectator ?: false)
    }

    private fun renderHotbarBar(player: PlayerEntity, width: Int, height: Int, spectator: Boolean) {
        if (spectator) {
            return
        }

        val left = width / 2 - 91
        val top = (height - 22).toFloat()

        UI.withMaterial(GuiMaterial.TRANSLUCENT) {
            blit(WIDGETS, left.toFloat(), top, 0f, 0f, 182f, 22f)

            val selected = player.inventory.selectedSlot
            blit(
                WIDGETS,
                x = (left - 1 + selected * 20).toFloat(),
                y = top - 1f,
                u = 0f,
                v = 22f,
                width = 24f,
                height = 22f,
            )
        }

        for (slot in 0 until HOTBAR_SLOTS) {
            val stack = player.inventory.main.getOrNull(slot) ?: continue
            renderHotbarItem(stack, left + 3 + slot * 20, height - 19)
        }
    }

    private fun renderHotbarItem(stack: ItemStack?, x: Int, y: Int) {
        if (stack == null) {
            return
        }
        val client = MinecraftClient.getInstance()
        client.itemRenderer.renderInGuiWithOverrides(stack, x, y)
        client.itemRenderer.renderGuiItemOverlay(client.textRenderer, stack, x, y)
    }

    fun renderStatusBars(
        player: ClientPlayerEntity,
        width: Int,
        height: Int,
        ticks: Int,
        state: HudState,
    ) {
        val health = ceil(player.health.toDouble()).toInt()
        val maxHealth = max(player.maxHealth, state.lastHealthValue.toFloat())
        val absorption = ceil(player.absorption.toDouble()).toInt()

        val wobbling =
            state.heartJumpEndTick > ticks.toLong() && (state.heartJumpEndTick - ticks.toLong()) / 3L % 2L == 1L
        random.nextInt(2)

        val left = width / 2 - 91
        val right = width / 2 + 91
        val top = height - 39

        renderHealthRow(player, health, maxHealth, absorption, left, top, wobbling, ticks)

        val armour = player.inventory.armorProtectionValue
        if (armour > 0) {
            renderArmourRow(armour, left, top - 10)
        }

        val food = player.hungerManager.foodLevel
        renderFoodRow(player, food, right, top)

        val air = player.air
        if (player.isTouchingWater || air < MAX_AIR) {
            renderAirRow(air, right, top - 10)
        }
    }

    private fun renderHealthRow(
        player: PlayerEntity,
        health: Int,
        maxHealth: Float,
        absorption: Int,
        left: Int,
        top: Int,
        wobbling: Boolean,
        ticks: Int,
    ) {
        val hearts = ceil(maxHealth / 2.0).toInt()
        val regenerating = player.hasStatusEffect(REGENERATION)

        var absorptionLeft = absorption

        for (heart in hearts - 1 downTo 0) {
            var y = top
            if (regenerating && (heart + ticks) % 25 == 0) {
                y -= 1
            }
            if (health <= 4) {
                y += (heart * 3 + ticks) % 2
            }
            if (wobbling) {
                y += 1
            }

            val x = left + heart % 10 * 8
            val backgroundV = if (wobbling) HEART_V_BLINK else HEART_V_BACKGROUND
            blit(ICONS, x.toFloat(), y.toFloat(), backgroundV, 0f, 9f, 9f)

            val value = heart * 2
            if (absorptionLeft > 0) {
                if (absorptionLeft == 1 && value + 1 == health + absorption) {
                    blit(ICONS, x.toFloat(), y.toFloat(), ABSORB_HALF, 0f, 9f, 9f)
                } else {
                    blit(ICONS, x.toFloat(), y.toFloat(), ABSORB_FULL, 0f, 9f, 9f)
                }
                absorptionLeft -= 2
                continue
            }

            if (value + 1 < health) {
                blit(ICONS, x.toFloat(), y.toFloat(), HEART_FULL, 0f, 9f, 9f)
            } else if (value + 1 == health) {
                blit(ICONS, x.toFloat(), y.toFloat(), HEART_HALF, 0f, 9f, 9f)
            }
        }
    }

    private fun renderArmourRow(armour: Int, left: Int, top: Int) {
        for (icon in 0 until 10) {
            val x = (left + icon * 8).toFloat()
            val filled = icon * 2 + 1
            blit(ICONS, x, top.toFloat(), ARMOUR_EMPTY, 9f, 9f, 9f)
            when {
                filled < armour -> blit(ICONS, x, top.toFloat(), ARMOUR_FULL, 9f, 9f, 9f)
                filled == armour -> blit(ICONS, x, top.toFloat(), ARMOUR_HALF, 9f, 9f, 9f)
            }
        }
    }

    private fun renderFoodRow(player: PlayerEntity, food: Int, right: Int, top: Int) {
        val starving = player.hasStatusEffect(HUNGER)
        val backgroundU = if (starving) FOOD_BACKGROUND_HUNGER else FOOD_BACKGROUND
        val fullU = if (starving) FOOD_FULL_HUNGER else FOOD_FULL
        val halfU = if (starving) FOOD_HALF_HUNGER else FOOD_HALF

        for (icon in 0 until 10) {
            val x = (right - icon * 8 - 9).toFloat()
            blit(ICONS, x, top.toFloat(), backgroundU, 27f, 9f, 9f)

            val value = icon * 2
            when {
                value + 1 < food -> blit(ICONS, x, top.toFloat(), fullU, 27f, 9f, 9f)
                value + 1 == food -> blit(ICONS, x, top.toFloat(), halfU, 27f, 9f, 9f)
            }
        }
    }

    private fun renderAirRow(air: Int, right: Int, top: Int) {
        val full = ceil((air - 2).toDouble() * 10.0 / MAX_AIR).toInt()
        val popping = ceil(air.toDouble() * 10.0 / MAX_AIR).toInt() - full

        for (bubble in 0 until full + popping) {
            val u = if (bubble < full) BUBBLE_FULL else BUBBLE_POP
            blit(ICONS, (right - bubble * 8 - 9).toFloat(), top.toFloat(), u, 18f, 9f, 9f)
        }
    }

    fun renderExperience(client: MinecraftClient, player: ClientPlayerEntity, width: Int, height: Int) {
        if (runCatching { player.nextLevelExperience }.getOrDefault(0) <= 0) {
            return
        }

        val left = width / 2 - 91
        val top = height - 32 + 3

        blit(ICONS, left.toFloat(), top.toFloat(), 0f, 64f, 182f, 5f)

        val filled = (player.experienceProgress * 183f).toInt()
        if (filled > 0) {
            blit(ICONS, left.toFloat(), top.toFloat(), 0f, 69f, filled.toFloat(), 5f)
        }

        val level = player.experienceLevel
        if (level > 0) {
            renderExperienceLevel(client, level, width, height - 31 - 4)
        }
    }

    private fun renderExperienceLevel(client: MinecraftClient, level: Int, width: Int, top: Int) {
        val text = level.toString()
        val font = client.textRenderer as Font
        val x = (width - Glyphs.widthOf(font, text)) / 2f

        Glyphs.draw(font, text, x + 1f, top.toFloat(), OUTLINE, shadow = false)
        Glyphs.draw(font, text, x - 1f, top.toFloat(), OUTLINE, shadow = false)
        Glyphs.draw(font, text, x, top + 1f, OUTLINE, shadow = false)
        Glyphs.draw(font, text, x, top - 1f, OUTLINE, shadow = false)
        Glyphs.draw(font, text, x, top.toFloat(), EXPERIENCE_GREEN, shadow = false)
    }

    fun renderHeldItemName(font: Font, width: Int, height: Int, state: HudState) {
        val name = state.heldItemName ?: return
        if (state.heldItemFade <= 0) {
            return
        }

        val alpha = min((state.heldItemFade * 256 / 10), 255)
        if (alpha <= 0) {
            return
        }

        val x = (width - Glyphs.widthOf(font, name)) / 2f
        val colour = (alpha shl 24) or 0x00FFFFFF
        Glyphs.drawWithShadow(font, name, x, (height - 59).toFloat(), colour)
    }

    private fun renderOverlayMessage(font: Font, width: Int, height: Int, state: HudState) {
        val message = state.overlayMessage ?: return
        if (state.overlayRemaining <= 0) {
            return
        }

        val alpha = min(state.overlayRemaining * 255 / 20, 255)
        if (alpha <= 0) {
            return
        }

        val x = (width - Glyphs.widthOf(font, message)) / 2f
        val colour = if (state.overlayTinted) {
            (alpha shl 24) or NOTE_TINT
        } else {
            (alpha shl 24) or 0x00FFFFFF
        }
        Glyphs.drawWithShadow(font, message, x, (height - 68).toFloat(), colour)
    }

    private fun renderTitle(font: Font, width: Int, height: Int, state: HudState) {
        if (state.titleTotalTicks <= 0) {
            return
        }

        val remaining = state.titleTotalTicks - state.tickDelta
        var opacity = 255
        if (state.titleTotalTicks > state.titleFadeOutTicks + state.titleRemainTicks) {
            val elapsed = state.titleFadeInTicks + state.titleRemainTicks + state.titleFadeOutTicks - remaining
            opacity = (elapsed * 255f / state.titleFadeInTicks).toInt()
        }
        if (state.titleTotalTicks <= state.titleFadeOutTicks) {
            opacity = (remaining * 255f / state.titleFadeOutTicks).toInt()
        }
        opacity = opacity.coerceIn(0, 255)
        if (opacity <= TITLE_MIN_OPACITY) {
            return
        }

        val colour = (opacity shl 24) and 0xFF000000.toInt() or 0x00FFFFFF

        UI.inLayer(GuiLayer.OVERLAY) {
            UI.withMaterial(GuiMaterial.TRANSLUCENT) {
                MatrixState.pushMatrix()
                MatrixState.translate(width / 2f, height / 2f, 0f)

                state.titleLarge?.takeIf { it.isNotEmpty() }?.let { text ->
                    MatrixState.pushMatrix()
                    MatrixState.scale(TITLE_SCALE, TITLE_SCALE, TITLE_SCALE)
                    Glyphs.drawWithShadow(font, text, -Glyphs.widthOf(font, text) / 2f, TITLE_Y, colour)
                    MatrixState.popMatrix()
                }

                state.titleSmall?.takeIf { it.isNotEmpty() }?.let { text ->
                    MatrixState.pushMatrix()
                    MatrixState.scale(SUBTITLE_SCALE, SUBTITLE_SCALE, SUBTITLE_SCALE)
                    Glyphs.drawWithShadow(font, text, -Glyphs.widthOf(font, text) / 2f, SUBTITLE_Y, colour)
                    MatrixState.popMatrix()
                }

                MatrixState.popMatrix()
            }
        }
    }

    fun renderVignette(client: MinecraftClient, width: Int, height: Int, darkness: Float) {
        if (client.world == null) {
            return
        }
        val brightness = (1f - darkness).coerceIn(0f, 1f)
        val level = (brightness * 255f).toInt().coerceIn(0, 255)
        val tint = 0xFF000000.toInt() or (level shl 16) or (level shl 8) or level

        UI.inLayer(GuiLayer.BACKGROUND) {
            UI.withMaterial(GuiMaterial.MULTIPLY_INVERSE) {
                stretch(VIGNETTE, width.toFloat(), height.toFloat(), tint)
            }
        }
    }

    fun renderPumpkinOverlay(client: MinecraftClient, width: Int, height: Int) {
        val helmet = client.player?.inventory?.armor?.getOrNull(3) ?: return
        if (helmet.item !== PUMPKIN_ITEM) {
            return
        }
        UI.inLayer(GuiLayer.BACKGROUND) {
            stretch(PUMPKIN_BLUR, width.toFloat(), height.toFloat(), UI.OPAQUE_WHITE)
        }
    }

    private fun blit(sheet: Identifier, x: Float, y: Float, u: Float, v: Float, width: Float, height: Float, argb: Int = -1) {
        MinecraftClient.getInstance().textureManager.bindTexture(sheet)
        UI.blit(
            textureId = UI.boundTextureId(),
            x = x,
            y = y,
            u = u,
            v = v,
            width = width,
            height = height,
            argb = argb
        )
    }

    private fun stretch(sheet: Identifier, width: Float, height: Float, argb: Int) {
        MinecraftClient.getInstance().textureManager.bindTexture(sheet)
        UI.texturedQuad(
            textureId = UI.boundTextureId(),
            x0 = 0f,
            y0 = 0f,
            x1 = width,
            y1 = height,
            u0 = 0f,
            v0 = 0f,
            u1 = 1f,
            v1 = 1f,
            argb = argb,
        )
    }

    class HudState {
        var vignetteDarkness: Float = 0f
        var lastHealthValue: Int = 0
        var heartJumpEndTick: Long = 0L
        var heldItemName: String? = null
        var heldItemFade: Int = 0
        var overlayMessage: String? = null
        var overlayRemaining: Int = 0
        var overlayTinted: Boolean = false
        var debugHud: Runnable? = null
        var tickDelta: Float = 0f
        var titleTotalTicks: Int = 0
        var titleFadeInTicks: Int = 0
        var titleRemainTicks: Int = 0
        var titleFadeOutTicks: Int = 0
        var titleLarge: String? = null
        var titleSmall: String? = null
    }

    private const val HOTBAR_SLOTS = 9
    private const val MAX_AIR = 300

    private const val SIDEBAR_SLOT = 1

    private const val LIST_SLOT = 0

    private const val TEAM_SIDEBAR_BASE = 3

    private const val TITLE_MIN_OPACITY = 8
    private const val TITLE_SCALE = 4f
    private const val TITLE_Y = -10f
    private const val SUBTITLE_SCALE = 2f
    private const val SUBTITLE_Y = 5f

    private const val CHAT_BOTTOM_OFFSET = 40f

    private const val HEART_V_BACKGROUND = 16f
    private const val HEART_V_BLINK = 25f
    private const val HEART_FULL = 52f
    private const val HEART_HALF = 61f
    private const val ABSORB_FULL = 160f
    private const val ABSORB_HALF = 169f
    private const val ARMOUR_EMPTY = 16f
    private const val ARMOUR_HALF = 25f
    private const val ARMOUR_FULL = 34f
    private const val FOOD_BACKGROUND = 16f
    private const val FOOD_BACKGROUND_HUNGER = 133f
    private const val FOOD_FULL = 52f
    private const val FOOD_FULL_HUNGER = 88f
    private const val FOOD_HALF = 61f
    private const val FOOD_HALF_HUNGER = 97f
    private const val BUBBLE_FULL = 16f
    private const val BUBBLE_POP = 25f

    private const val OUTLINE = 0xFF000000.toInt()
    private const val EXPERIENCE_GREEN = 0xFF80FF20.toInt()
    private const val NOTE_TINT = 0x7FFF33

    private const val REGENERATION = 10
    private const val HUNGER = 17

    private val PUMPKIN_ITEM by lazy {
        runCatching {
            Item.fromBlock(Blocks.PUMPKIN)
        }.getOrNull()
    }
}
