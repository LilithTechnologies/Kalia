package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.DiffuseLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.gl.GlBridge;

import java.nio.FloatBuffer;

@Mixin(DiffuseLighting.class)
public class MixinDiffuseLighting {
    @Redirect(method = "enableNormally", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLight(IILjava/nio/FloatBuffer;)V"))
    private static void impl$glLight(int light, int name, FloatBuffer values) {
        GlBridge.light(light, name, values);
    }
    @Redirect(method = "enableNormally", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLightModel(ILjava/nio/FloatBuffer;)V"))
    private static void impl$glLightModel(int i, FloatBuffer floatBuffer) {
    }
}
