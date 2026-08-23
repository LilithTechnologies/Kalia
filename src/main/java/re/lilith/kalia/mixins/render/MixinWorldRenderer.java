package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.KaliaEngine;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    @Inject(method = "setWorld", at = @At("HEAD"))
    private void kalia$awaitRenderBeforeWorldChange(CallbackInfo ci) {
        KaliaEngine.INSTANCE.awaitRender();
    }

    @Inject(method = "reload()V", at = @At("HEAD"))
    private void kalia$awaitRenderBeforeReload(CallbackInfo ci) {
        KaliaEngine.INSTANCE.awaitRender();
    }

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
