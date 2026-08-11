package re.lilith.kalia.mixins.entity;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.frame.draw.EntityBatchers;

@Mixin(SignBlockEntityRenderer.class)
public class MixinSignBlockEntityRenderer {
    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At("HEAD"))
    private void kalia$beforeSign(
        SignBlockEntity sign,
        double x,
        double y,
        double z,
        float tickDelta,
        int destroyStage,
        CallbackInfo ci
    ) {
        EntityBatchers.pushSuppression();
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFI)V", at = @At("RETURN"))
    private void kalia$afterSign(
        SignBlockEntity sign,
        double x,
        double y,
        double z,
        float tickDelta,
        int destroyStage,
        CallbackInfo ci
    ) {
        EntityBatchers.popSuppression();
    }
}