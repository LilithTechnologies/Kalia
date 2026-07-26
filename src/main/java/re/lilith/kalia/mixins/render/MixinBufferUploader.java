package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import re.lilith.kalia.KaliaHooks;
import re.lilith.kalia.draw.TessellatorBridge;

@Mixin(BufferRenderer.class)
public class MixinBufferUploader {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public void draw(BufferBuilder builder) {
        if (KaliaHooks.isActive()) {
            TessellatorBridge.INSTANCE.draw(builder);
        }
        builder.reset();
    }
}
