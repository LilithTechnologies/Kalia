package re.lilith.kalia.mixins.access;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccess {
    @Accessor
    int getTicks();

    @Accessor
    Map<Integer, ?> getBlockBreakingInfos();
}
