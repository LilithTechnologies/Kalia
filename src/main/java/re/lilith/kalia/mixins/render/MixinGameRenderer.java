package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import dev.rdh.argentum.impl.Argentum;
import re.lilith.kalia.gl.GlBridge;

import java.nio.FloatBuffer;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Shadow
    private float viewDistance;

    @Redirect(method = "renderFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFog(ILjava/nio/FloatBuffer;)V"))
    void impl$renderFog(int i, FloatBuffer floatBuffer) {
        GlBridge.fogColor(floatBuffer.get(0), floatBuffer.get(1), floatBuffer.get(2), floatBuffer.get(3));
    }

    @ModifyArg(
        method = "renderFog",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/Dimension;isFogThick(II)Z")),
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;fogStart(F)V"),
        index = 0
    )
    private float kalia$thinFogStart(float value) {
        return Argentum.CONFIG.thinFog ? this.viewDistance * 0.05F : value;
    }

    @ModifyArg(
        method = "renderFog",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/Dimension;isFogThick(II)Z")),
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;fogEnd(F)V"),
        index = 0
    )
    private float kalia$thinFogEnd(float value) {
        return Argentum.CONFIG.thinFog ? this.viewDistance : value;
    }
}
