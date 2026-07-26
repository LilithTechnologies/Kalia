package net.caffeinemc.mods.sodium.mixin.core;

import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Framebuffer.class)
public class MixinFramebuffer {
//    @ModifyConstant(method = "attachTexture", constant = @Constant(intValue = 32856))
//    public int kalia$attachTexture(int constant) {
//        return GL11.GL_RGBA16;
//    }
}