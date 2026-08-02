package re.lilith.kalia.mixins.render;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import re.lilith.kalia.rendering.ui.GuiBackgroundBlur;
import re.lilith.kalia.rendering.ui.GuiBlur;
import re.lilith.kalia.rendering.ui.UI;

@Mixin(Screen.class)
public class MixinScreen {
    @Redirect(method = "renderBackground(I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;fillGradient(IIIIII)V"))
    void impl$renderBackground(Screen instance, int a, int b, int c, int d, int e, int f) {
        GuiBackgroundBlur.INSTANCE.setEnabled(true);
        GuiBackgroundBlur.INSTANCE.setRadius(12f);
        UI.INSTANCE.fillGradient(a, b, c, d, e, f);
    }
}
