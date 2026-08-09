package re.lilith.kalia.mixins.lwjgl;

import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import re.lilith.kalia.KaliaHooks;

@Mixin(Display.class)
public class MixinDisplay {
    /**
     * @reason VSync is not managed by the windowing system
     * @author Lunasa
     */
    @Overwrite
    public static void setVSyncEnabled(boolean enabled) {
        KaliaHooks.setVsync(enabled);
    }
}
