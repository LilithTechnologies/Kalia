package re.lilith.kalia.mixins.entity;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import re.lilith.kalia.entity.KaliaLightCache;
import re.lilith.kalia.entity.KaliaTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntity.class)
public abstract class MixinBlockEntity implements KaliaLightCache {
    @Unique
    private int kalia$lightTick = Integer.MIN_VALUE;

    @Unique
    private int kalia$light;

    @Override
    public int kalia$getCachedLight(World world, BlockPos pos, int minLight) {
        int tick = KaliaTick.current();
        if (kalia$lightTick != tick) {
            kalia$lightTick = tick;
            kalia$light = world.getLight(pos, minLight);
        }
        return kalia$light;
    }
}
