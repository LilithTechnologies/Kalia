package re.lilith.kalia.mixins.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import re.lilith.kalia.rendering.ui.GuiBackgroundBlur;

@Mixin(Screen.class)
public class MixinScreen {
    @WrapOperation(method = "renderBackground(I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;fillGradient(IIIIII)V"))
    void impl$renderBackground(Screen instance, int i, int e, int g, int h, int j, int k, Operation<Void> original) {
        GuiBackgroundBlur.INSTANCE.setEnabled(true);
        GuiBackgroundBlur.INSTANCE.setRadius(12f);
        original.call(instance, i, e, g, h, j, k);
    }
}
