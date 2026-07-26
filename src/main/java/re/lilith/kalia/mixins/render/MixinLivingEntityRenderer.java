package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

import re.lilith.kalia.gl.GlBridge;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {

    @Inject(method = "method_10260", at = @At("HEAD"))
    private void kalia$clearOverlay(CallbackInfo callback) {
        GlBridge.clearOverlay();
    }

    @Inject(method = "method_10259", at = @At("HEAD"))
    private void kalia$clearOutlineOverlay(CallbackInfo callback) {
        GlBridge.clearOverlay();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Redirect(
            method = "method_10252",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glTexEnv(IILjava/nio/FloatBuffer;)V",
                    remap = false
            )
    )
    private void kalia$redirectTexEnv(int target, int name, FloatBuffer values) {
        GlBridge.texEnvColor(name, values);
    }
}
