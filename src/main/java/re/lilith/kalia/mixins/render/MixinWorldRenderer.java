package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    /**
     * @reason Clouds are submitted by Kalia rather than drawn from here
     * @author Lunasa
     */
    @Overwrite
    public void renderClouds(float tickDelta, int anaglyphFilter) {
    }

    /**
     * @reason Clouds are submitted by Kalia rather than drawn from here
     * @author Lunasa
     */
    @Overwrite
    private void renderFancyClouds(float tickDelta, int anaglyphFilter) {
    }

    /**
     * @reason The sky is submitted by Kalia rather than drawn from here
     * @author Lunasa
     */
    @Overwrite
    public void renderSky(float tickDelta, int anaglyphFilter) {
    }

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
