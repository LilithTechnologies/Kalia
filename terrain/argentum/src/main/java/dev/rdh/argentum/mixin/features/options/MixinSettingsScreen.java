package dev.rdh.argentum.mixin.features.options;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SettingsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.gui.CeleritasVideoOptionsScreen;

@Mixin(SettingsScreen.class)
public abstract class MixinSettingsScreen extends Screen {
    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void celeritas$openVideoOptions(ButtonWidget button, CallbackInfo ci) {
        if (button.active && button.id == 101) {
            this.client.options.save();
            this.client.setScreen(new CeleritasVideoOptionsScreen(this));
            ci.cancel();
        }
    }
}
