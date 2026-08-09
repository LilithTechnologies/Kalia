package re.lilith.kalia.mixins.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import re.lilith.kalia.rendering.ui.UI;
import re.lilith.kalia.rendering.ui.hud.KaliaDebugHud;
import re.lilith.kalia.rendering.ui.text.Font;

import java.util.List;

@Mixin(DebugHud.class)
public abstract class MixinDebugHud {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    protected abstract List<String> getLeftText();

    @Shadow
    protected abstract List<String> getRightText();

    /**
     * @reason The overlay is submitted as render state rather than drawn immediately
     * @author Lunasa
     */
    @Overwrite
    public void render(Window window) {
        KaliaDebugHud.INSTANCE.render(
                (Font) this.client.textRenderer,
                this.getLeftText(),
                this.getRightText(),
                (int) UI.INSTANCE.getWidth()
        );
    }
}
