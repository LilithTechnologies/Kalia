package re.lilith.kalia.mixins.gui;

import net.minecraft.client.gui.screen.ingame.SurvivalInventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.rendering.ui.pip.GuiEntityPreview;

@Mixin(SurvivalInventoryScreen.class)
public class MixinSurvivalInventoryScreen {
    @Inject(
            method = "renderEntity(IIIFFLnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void kalia$captureEntity(
            int x,
            int y,
            int size,
            float mouseX,
            float mouseY,
            LivingEntity entity,
            CallbackInfo ci
    ) {
        if (GuiEntityPreview.INSTANCE.capture(x, y, size, mouseX, mouseY, entity)) {
            ci.cancel();
        }
    }
}
