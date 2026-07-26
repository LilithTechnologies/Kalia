package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import re.lilith.kalia.frame.GameFrame;
import re.lilith.kalia.gl.GlBridge;
import re.lilith.kalia.gl.GlState;

import java.nio.IntBuffer;

@Mixin(Camera.class)
public class MixinCamera {
    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glGetInteger(ILjava/nio/IntBuffer;)V",
                    remap = false
            )
    )
    private static void impl$setup(int pname, IntBuffer params) {
        var viewport = GameFrame.INSTANCE.getViewport().getArray();

        int pos = params.position();
        for (int i = 0; i < 4; i++) {
            params.put(pos + i, viewport[i]);
        }
    }
}
