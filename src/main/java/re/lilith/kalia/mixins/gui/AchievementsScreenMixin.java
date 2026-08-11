package re.lilith.kalia.mixins.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AchievementsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.rendering.ui.UI;

@Mixin(AchievementsScreen.class)
public abstract class AchievementsScreenMixin extends Screen {
    @Shadow
    protected int originX;

    @Shadow
    protected int originY;

    @Inject(
            method = "renderIcons",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;pushMatrix()V"
            )
    )
    private void kalia$enableScissor(
            int mouseX,
            int mouseY,
            float tickDelta,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = new Window(client);

        int scale = window.getScaleFactor();

        int guiLeft = (this.width - this.originX) / 2;
        int guiTop = (this.height - this.originY) / 2;

        int x = guiLeft + 14;
        int y = guiTop + 18;
        int w = 238;
        int h = 180;

        UI.INSTANCE.setRawScissor(
                x * scale,
                (this.height - y - h) * scale,
                w * scale,
                h * scale
        );
    }

    @Inject(
            method = "renderIcons",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;popMatrix()V",
                    shift = At.Shift.AFTER
            )
    )
    private void kalia$disableScissor(
            int mouseX,
            int mouseY,
            float tickDelta,
            CallbackInfo ci
    ) {
        UI.INSTANCE.clearRawScissor();
    }
}