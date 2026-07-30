package re.lilith.kalia.mixins.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import re.lilith.kalia.frame.graph.sky.CloudRenderer;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    @Shadow
    private int ticks;

    @Shadow
    private MinecraftClient client;

    /**
     * @reason Faster cloud rendering
     * @author Lunasa
     */
    @Overwrite
    public void renderClouds(float tickDelta, int anaglyphFilter) {
        if (!this.client.world.dimension.canPlayersSleep()) {
            return;
        }
        if (this.client.options.getCloudMode() == 2) {
            CloudRenderer.INSTANCE.render(tickDelta, anaglyphFilter, this.ticks);
        } else {
            CloudRenderer.INSTANCE.renderFast(tickDelta, anaglyphFilter, this.ticks);
        }
    }

    /**
     * @reason Faster cloud rendering
     * @author Lunasa
     */
    @Overwrite
    private void renderFancyClouds(float tickDelta, int anaglyphFilter) {
        CloudRenderer.INSTANCE.render(tickDelta, anaglyphFilter, this.ticks);
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
