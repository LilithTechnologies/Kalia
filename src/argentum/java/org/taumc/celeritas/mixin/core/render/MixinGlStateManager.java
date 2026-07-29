package org.taumc.celeritas.mixin.core.render;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.debug.RenderMetrics;

@Mixin(GlStateManager.class)
public abstract class MixinGlStateManager {
    @Inject(method = "callList", at = @At("HEAD"))
    private static void celeritas$countDisplayListDraw(int list, CallbackInfo ci) {
        RenderMetrics.recordDraw();
    }
}
