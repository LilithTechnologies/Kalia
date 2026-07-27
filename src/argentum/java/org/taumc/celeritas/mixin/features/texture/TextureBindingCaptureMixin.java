package org.taumc.celeritas.mixin.features.texture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.Texture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.render.terrain.texture.GLStateManagerTextureService;

import java.util.Map;

@Mixin(TextureManager.class)
public abstract class TextureBindingCaptureMixin {
    @Unique
    private static final String BLOCK_ATLAS_PATH = "textures/atlas/blocks.png";

    @Shadow
    @Final
    private Map<Identifier, Texture> textures;

    @Inject(method = "bindTexture", at = @At("HEAD"))
    private void celeritas$captureBoundTexture(Identifier identifier, CallbackInfo ci) {
        Texture texture = this.textures.get(identifier);
        if (!(texture instanceof AbstractTexture abstractTexture) || MinecraftClient.getInstance().gameRenderer == null) {
            return;
        }

        if (BLOCK_ATLAS_PATH.equals(identifier.getPath())) {
            GLStateManagerTextureService.blockAtlasGlId = abstractTexture.getGlId();
        } else if (identifier.equals(MinecraftClient.getInstance().gameRenderer.lightmapTextureId)) {
            GLStateManagerTextureService.lightmapGlId = abstractTexture.getGlId();
        }
    }
}
