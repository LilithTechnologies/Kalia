package re.lilith.kalia.mixins.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import re.lilith.kalia.draw.EntityBatchers;
import re.lilith.kalia.gl.GlBridge;

import java.nio.FloatBuffer;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Redirect(method = "renderFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFog(ILjava/nio/FloatBuffer;)V"))
    void impl$renderFog(int i, FloatBuffer floatBuffer) {
        GlBridge.fogColor(floatBuffer.get(0), floatBuffer.get(1), floatBuffer.get(2), floatBuffer.get(3));
    }
}
