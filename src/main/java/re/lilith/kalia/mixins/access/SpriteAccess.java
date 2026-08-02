package re.lilith.kalia.mixins.access;

import net.minecraft.client.texture.Sprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Sprite.class)
public interface SpriteAccess {
    @Accessor
    List<int[][]> getFrames();

    @Accessor
    int getFrameIndex();
}
