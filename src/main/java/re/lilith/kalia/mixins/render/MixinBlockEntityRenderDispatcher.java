package re.lilith.kalia.mixins.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import re.lilith.kalia.frame.draw.EntityBatchers;

@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {
    @WrapMethod(method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V")
    private void kalia$wrapBlockEntity(
            BlockEntity blockEntity, double x, double y, double z, float tickDelta, int destroyStage,
            Operation<Void> original
    ) {
        EntityBatchers.INSTANCE.enterEntity();
        try {
            original.call(blockEntity, x, y, z, tickDelta, destroyStage);
        } finally {
            EntityBatchers.INSTANCE.exitEntity();
        }
    }
}
