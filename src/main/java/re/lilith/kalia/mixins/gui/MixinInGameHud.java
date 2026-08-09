package re.lilith.kalia.mixins.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import re.lilith.kalia.rendering.ui.hud.KaliaInGameHud;
import re.lilith.kalia.rendering.ui.text.Font;

@Mixin(InGameHud.class)
public abstract class MixinInGameHud {
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    private int ticks;
    @Shadow
    private float vignetteDarkness;
    @Shadow
    private int lastHealthValue;
    @Shadow
    private long heartJumpEndTick;
    @Shadow
    private ItemStack heldItem;
    @Shadow
    private int heldItemTooltipFade;
    @Shadow
    private String overlayMessage;
    @Shadow
    private int overlayRemaining;
    @Shadow
    private boolean overlayTinted;
    @Shadow
    @Final
    private DebugHud debugHud;
    @Shadow
    private int titleTotalTicks;
    @Shadow
    private int titleFadeInTicks;
    @Shadow
    private int titleRemainTicks;
    @Shadow
    private int titleFadeOutTicks;
    @Shadow
    private String title;
    @Shadow
    private String subtitle;

    @Unique
    private final KaliaInGameHud.HudState kalia$state = new KaliaInGameHud.HudState();

    /**
     * @reason The HUD is drawn via Kalia
     * @author Lunasa
     */
    @Overwrite
    public void render(float tickDelta) {
        if (this.client.player == null) {
            return;
        }

        kalia$state.setVignetteDarkness(this.vignetteDarkness);
        kalia$state.setLastHealthValue(this.lastHealthValue);
        kalia$state.setHeartJumpEndTick(this.heartJumpEndTick);
        kalia$state.setHeldItemName(this.heldItem == null ? null : this.heldItem.getCustomName());
        kalia$state.setHeldItemFade(this.heldItemTooltipFade);
        kalia$state.setOverlayMessage(this.overlayMessage);
        kalia$state.setOverlayRemaining(this.overlayRemaining);
        kalia$state.setOverlayTinted(this.overlayTinted);
        kalia$state.setDebugHud(() -> this.debugHud.render(new Window(this.client)));
        kalia$state.setTickDelta(tickDelta);
        kalia$state.setTitleTotalTicks(this.titleTotalTicks);
        kalia$state.setTitleFadeInTicks(this.titleFadeInTicks);
        kalia$state.setTitleRemainTicks(this.titleRemainTicks);
        kalia$state.setTitleFadeOutTicks(this.titleFadeOutTicks);
        kalia$state.setTitleLarge(this.subtitle);
        kalia$state.setTitleSmall(this.title);

        KaliaInGameHud.INSTANCE.render(
                this.client,
                (Font) this.client.textRenderer,
                this.ticks,
                kalia$state
        );
    }
}
