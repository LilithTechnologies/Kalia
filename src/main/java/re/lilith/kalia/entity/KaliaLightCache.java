package re.lilith.kalia.entity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface KaliaLightCache {
    int kalia$getCachedLight(World world, BlockPos pos, int minLight);
}
