package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    /**
     * @reason Unsupported by Kalia at the moment
     * @author Lunasa
     */
    @Overwrite
    public void setupEntityOutlineShader() {
    }

    /**
     * @reason Unsupported by Kalia at the moment
     * @author Lunasa
     */
    @Overwrite
    public void drawEntityOutlineFramebuffer() {
    }
}
