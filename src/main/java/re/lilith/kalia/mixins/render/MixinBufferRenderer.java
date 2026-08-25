package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import dev.rdh.argentum.impl.debug.RenderMetrics;
import re.lilith.kalia.KaliaHooks;
import re.lilith.kalia.frame.draw.TessellatorBridge;

@Mixin(BufferRenderer.class)
public class MixinBufferRenderer {
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

        RenderMetrics.recordDraw();
    }
}
