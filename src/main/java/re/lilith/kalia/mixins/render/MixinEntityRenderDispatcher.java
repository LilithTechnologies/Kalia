package re.lilith.kalia.mixins.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Unique;
import re.lilith.kalia.frame.draw.EntityBatchers;
import re.lilith.kalia.frame.graph.EntityPoseStats;
import re.lilith.kalia.frame.graph.entity.EntityStage;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @WrapMethod(method = "method_6915(Lnet/minecraft/entity/Entity;FZ)Z")
    private boolean kalia$wrapEntity(Entity entity, float tickDelta, boolean bl, Operation<Boolean> original) {
        EntityBatchers.INSTANCE.enterEntity();
        long signature = kalia$poseSignature(entity, tickDelta);
        EntityPoseStats.observe(entity.getEntityId(), signature);
        EntityStage.INSTANCE.begin(entity.getEntityId(), signature);
        try {
            return original.call(entity, tickDelta, bl);
        } finally {
            EntityStage.INSTANCE.end();
            EntityBatchers.INSTANCE.exitEntity();
        }
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
}
