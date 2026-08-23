package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Unique;
import re.lilith.kalia.frame.draw.EntityBatchers;
import re.lilith.kalia.frame.graph.EntityPoseStats;
import re.lilith.kalia.frame.graph.entity.EntityStage;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @Inject(method = "method_6915(Lnet/minecraft/entity/Entity;FZ)Z", at = @At("HEAD"))
    private void kalia$enterEntity(Entity entity, float tickDelta, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        EntityBatchers.INSTANCE.enterEntity();
        long signature = kalia$poseSignature(entity, tickDelta);
        EntityPoseStats.observe(entity.getEntityId(), signature);
        EntityStage.INSTANCE.begin(entity.getEntityId(), signature);
    }

    @Unique
    private static float kalia$lerp(float from, float to, float tickDelta) {
        return from + (to - from) * tickDelta;
    }

    @Unique
    private static long kalia$poseSignature(Entity entity, float tickDelta) {
        long signature = entity.getClass().hashCode();
        signature = signature * 31L + Float.floatToRawIntBits(kalia$lerp(entity.prevYaw, entity.yaw, tickDelta));
        signature = signature * 31L + Float.floatToRawIntBits(kalia$lerp(entity.prevPitch, entity.pitch, tickDelta));
        signature = signature * 31L + (entity.isSneaking() ? 1L : 0L);
        signature = signature * 31L + (entity.isSprinting() ? 2L : 0L);
        signature = signature * 31L + Double.doubleToRawLongBits(entity.x - entity.prevX);
        signature = signature * 31L + Double.doubleToRawLongBits(entity.y - entity.prevY);
        signature = signature * 31L + Double.doubleToRawLongBits(entity.z - entity.prevZ);
        if (entity instanceof LivingEntity living) {
            signature = signature * 31L +
                    Float.floatToRawIntBits(kalia$lerp(living.prevHeadYaw, living.headYaw, tickDelta));
            signature = signature * 31L +
                    Float.floatToRawIntBits(kalia$lerp(living.prevBodyYaw, living.bodyYaw, tickDelta));
            signature = signature * 31L +
                    Float.floatToRawIntBits(kalia$lerp(living.lastHandSwingProgress, living.handSwingProgress, tickDelta));
        }
        return signature;
    }

    @Inject(method = "method_6915(Lnet/minecraft/entity/Entity;FZ)Z", at = @At("RETURN"))
    private void kalia$exitEntity(Entity entity, float tickDelta, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        EntityStage.INSTANCE.end();
        EntityBatchers.INSTANCE.exitEntity();
    }
}
