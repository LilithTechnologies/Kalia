package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class MixinArmorFeatureRenderer {
    @Shadow
    public abstract ItemStack getSlot(LivingEntity entity, int slot);

    @Inject(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kalia$skipWhenUnarmoured(
            LivingEntity entity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float age,
            float headYaw,
            float headPitch,
            float scale,
            CallbackInfo callback
    ) {
        for (int slot = 1; slot <= 4; slot++) {
            ItemStack stack = this.getSlot(entity, slot);
            if (stack != null && stack.getItem() instanceof ArmorItem) {
                return;
            }
        }
        callback.cancel();
    }
}
