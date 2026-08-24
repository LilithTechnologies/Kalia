package re.lilith.kalia.mixins.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow
    public int ticksAlive;

    @Unique
    private int kalia$lightmapTick = Integer.MIN_VALUE;

    @Unique
    private int kalia$lightmapValue;

    @Inject(method = "getLightmapCoordinates", at = @At("HEAD"), cancellable = true)
    private void kalia$lightmapCacheHit(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        if (kalia$lightmapTick == this.ticksAlive) {
            cir.setReturnValue(kalia$lightmapValue);
        }
    }

    @ModifyReturnValue(method = "getLightmapCoordinates", at = @At("RETURN"))
    private int kalia$lightmapCacheStore(int original) {
        kalia$lightmapTick = this.ticksAlive;
        kalia$lightmapValue = original;
        return original;
    }
}
