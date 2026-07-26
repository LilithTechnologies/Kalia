package net.caffeinemc.mods.sodium.mixin.sodium.core.render;

import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatExtensions;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexFormat.class)
public class VertexFormatMixin implements VertexFormatExtensions {
    @Unique
    private int sodium$globalId;

    @Inject(method = "<init>(Lnet/minecraft/client/render/VertexFormat;)V", at = @At("RETURN"))
    private void afterInit(VertexFormat vertexFormat, CallbackInfo ci) {
        this.sodium$globalId = VertexFormatRegistry.instance()
                .allocateGlobalId((VertexFormat) (Object) this);
    }

    @Unique
    public int sodium$getGlobalId() {
        return this.sodium$globalId;
    }
}
