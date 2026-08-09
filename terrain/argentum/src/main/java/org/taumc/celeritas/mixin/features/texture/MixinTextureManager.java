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
import org.taumc.celeritas.mixin.core.access.GameRendererAccessor;

import java.util.Map;

@Mixin(TextureManager.class)
public abstract class MixinTextureManager {
    @Unique
    private static final String BLOCK_ATLAS_PATH = "textures/atlas/blocks.png";

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

    @Inject(method = "bindTexture", at = @At("HEAD"))
    private void celeritas$captureBoundTexture(Identifier identifier, CallbackInfo ci) {
        var gameRenderer = MinecraftClient.getInstance().gameRenderer;
        if (gameRenderer == null) {
            return;
        }

        Identifier lightmap = ((GameRendererAccessor)gameRenderer).getLightmapTextureId();
        boolean isLightmap = identifier == lightmap;
        boolean isBlockAtlas = false;
        if (!isLightmap) {
            String path = identifier.getPath();
            isBlockAtlas = path.length() == BLOCK_ATLAS_PATH.length() && BLOCK_ATLAS_PATH.equals(path);
            if (!isBlockAtlas) {
                isLightmap = lightmap != null
                        && path.length() == lightmap.getPath().length()
                        && identifier.equals(lightmap);
                if (!isLightmap) {
                    return;
                }
            }
        }

        if (!(this.textures.get(identifier) instanceof AbstractTexture abstractTexture)) {
            return;
        }
        if (isBlockAtlas) {
            GLStateManagerTextureService.blockAtlasGlId = abstractTexture.getGlId();
        } else {
            GLStateManagerTextureService.lightmapGlId = abstractTexture.getGlId();
        }
    }
}
