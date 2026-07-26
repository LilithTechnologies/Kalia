package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.lilith.kalia.draw.EntityBatchers;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @Inject(method = "method_6915(Lnet/minecraft/entity/Entity;FZ)Z", at = @At("HEAD"))
    private void kalia$enterEntity(Entity entity, float tickDelta, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        EntityBatchers.INSTANCE.enterEntity();
    }

    @Inject(method = "method_6915(Lnet/minecraft/entity/Entity;FZ)Z", at = @At("RETURN"))
    private void kalia$exitEntity(Entity entity, float tickDelta, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        EntityBatchers.INSTANCE.exitEntity();
    }
}
