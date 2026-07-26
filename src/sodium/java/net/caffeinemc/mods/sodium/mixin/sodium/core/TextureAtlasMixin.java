package net.caffeinemc.mods.sodium.mixin.sodium.core;

import net.caffeinemc.mods.sodium.legacy.util.IAtlas;
import net.minecraft.client.render.TextureStitcher;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(SpriteAtlasTexture.class)
public class TextureAtlasMixin implements IAtlas {
    @Unique
    private int w = 0, h = 0;

    @Redirect(method = "m_46857130", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextureStitcher;getStitchedSprites()Ljava/util/List;"))
    List<Sprite> impl$m_46857130(TextureStitcher instance) {
        w = instance.getWidth();
        h = instance.getHeight();

        return instance.getStitchedSprites();
    }

    @Override
    public int getWidth() {
        return w;
    }

    @Override
    public int getHeight() {
        return h;
    }
}
