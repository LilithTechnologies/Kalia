package org.taumc.celeritas.mixin.features.texture;

import com.google.common.collect.Iterators;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.Celeritas;
import org.taumc.celeritas.impl.extensions.SpriteExtension;
import org.taumc.celeritas.impl.extensions.TextureAtlasExtension;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mixin(SpriteAtlasTexture.class)
public class TextureAtlasMixin implements TextureAtlasExtension {
    @Shadow @Final
    private Map<String, Sprite> sprites;

    private QuadTree<Sprite> celeritas$quadTree;
    private int celeritas$width;
    private int celeritas$height;

    @Inject(method = "m_46857130", at = @At("RETURN"))
    private void celeritas$buildLookup(CallbackInfo ci) {
        int width = 0;
        int height = 0;
        int minSize = Integer.MAX_VALUE;
        for (Sprite sprite : this.sprites.values()) {
            width = Math.max(width, sprite.getX() + sprite.getWidth());
            height = Math.max(height, sprite.getY() + sprite.getHeight());
            minSize = Math.min(minSize, Math.max(sprite.getWidth(), sprite.getHeight()));
        }
        this.celeritas$width = nextPowerOfTwo(width);
        this.celeritas$height = nextPowerOfTwo(height);
        Rect2i bounds = new Rect2i(0, 0, this.celeritas$width, this.celeritas$height);
        this.celeritas$quadTree = new QuadTree<>(bounds, minSize, this.sprites.values(),
                sprite -> new Rect2i(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight()));
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<Sprite> celeritas$visibleAnimations(List<Sprite> sprites) {
        Iterator<Sprite> iterator = sprites.iterator();
        return Celeritas.CONFIG.animateOnlyVisibleTextures
                ? Iterators.filter(iterator, sprite -> ((SpriteExtension)sprite).celeritas$shouldUpdate())
                : iterator;
    }

    @Override
    public QuadTree<Sprite> celeritas$getQuadTree() {
        return this.celeritas$quadTree;
    }

    @Override
    public Sprite celeritas$findFromUV(float u, float v) {
        return this.celeritas$quadTree.find(Math.round(u * this.celeritas$width), Math.round(v * this.celeritas$height));
    }

    private static int nextPowerOfTwo(int value) {
        return value <= 1 ? 1 : Integer.highestOneBit(value - 1) << 1;
    }
}
