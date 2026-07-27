package org.taumc.celeritas.mixin.features.texture;

import net.minecraft.client.texture.Texture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {
    @Shadow
    @Final
    private Map<Identifier, Texture> textures;

    @Inject(
            method = "close",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/TextureUtil;deleteTexture(I)V")
    )
    private void celeritas$removeClosedTexture(Identifier identifier, CallbackInfo ci) {
        this.textures.remove(identifier);
    }
}
