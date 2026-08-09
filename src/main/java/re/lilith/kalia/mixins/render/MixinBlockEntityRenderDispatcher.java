package re.lilith.kalia.mixins.render;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.frame.draw.EntityBatchers;

@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {
    @Inject(method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V", at = @At("HEAD"))
    private void kalia$enterBlockEntity(BlockEntity blockEntity, double x, double y, double z, float tickDelta, int destroyStage, CallbackInfo ci) {
        EntityBatchers.INSTANCE.enterEntity();
    }

    @Inject(method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V", at = @At("RETURN"))
    private void kalia$exitBlockEntity(BlockEntity blockEntity, double x, double y, double z, float tickDelta, int destroyStage, CallbackInfo ci) {
        EntityBatchers.INSTANCE.exitEntity();
    }
}
