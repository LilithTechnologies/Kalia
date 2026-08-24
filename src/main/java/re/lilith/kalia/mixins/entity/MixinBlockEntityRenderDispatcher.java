package re.lilith.kalia.mixins.entity;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import re.lilith.kalia.entity.KaliaLightCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcher {
    @Redirect(
            method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;FI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getLight(Lnet/minecraft/util/math/BlockPos;I)I")
    )
    private int kalia$cachedLight(
            World world, BlockPos pos, int minLight,
            @Local(argsOnly = true) BlockEntity entity
    ) {
        return ((KaliaLightCache) entity).kalia$getCachedLight(world, pos, minLight);
    }
}
