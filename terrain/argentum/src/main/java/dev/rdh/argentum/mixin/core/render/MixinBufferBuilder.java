package dev.rdh.argentum.mixin.core.render;

import java.nio.IntBuffer;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferBuilder.class)
public class MixinBufferBuilder {
    @Shadow
    private IntBuffer intBuffer;

    @Shadow
    private int vertexCount;

    @Shadow
    private VertexFormat format;

    @Inject(method = "next", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BufferBuilder;grow(I)V"))
    private void celeritas$syncBufferPosition(CallbackInfo ci) {
        this.intBuffer.position(this.vertexCount * this.format.getVertexSizeInteger());
    }
}
