package org.taumc.celeritas.mixin.core.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.debug.RenderMetrics;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import re.lilith.kalia.frame.FPSCounter;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    private float fogRed;

    @Shadow
    private float fogGreen;

    @Shadow
    private float fogBlue;

    @Shadow
    private MinecraftClient client;

    @Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void celeritas$beginMetricsFrame(float tickDelta, long startTime, CallbackInfo ci) {
        RenderMetrics.beginFrame();
    }

    @WrapOperation(
            method = "render(FJ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(F)V")
    )
    private void celeritas$profileHud(InGameHud gui, float tickDelta, Operation<Void> original) {
        RenderMetrics.Category previous = RenderMetrics.setCategory(RenderMetrics.Category.HUD);
        try {
            original.call(gui, tickDelta);
        } finally {
            RenderMetrics.setCategory(previous);
        }
    }

    @WrapOperation(
            method = "render(FJ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V")
    )
    private void celeritas$profileScreen(Screen screen, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
        RenderMetrics.Category previous = RenderMetrics.setCategory(RenderMetrics.Category.HUD);
        try {
            original.call(screen, mouseX, mouseY, tickDelta);
        } finally {
            RenderMetrics.setCategory(previous);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(F)V", shift = At.Shift.AFTER))
    private void onRenderTwo(float tickDelta, long nanoTime, CallbackInfo ci) {
        if (!this.client.options.debugEnabled) {
            client.profiler.push("radium_fps_overlay");

            FPSCounter.INSTANCE.render();

            client.profiler.pop();
        }
    }

    // kalia already captures this
//    @Inject(method = "setupClearColor", at = @At("RETURN"))
//    private void captureFogColor(float par1, CallbackInfo ci) {
//        GLStateManagerFogService.fogColorRed = this.fogRed;
//        GLStateManagerFogService.fogColorGreen = this.fogGreen;
//        GLStateManagerFogService.fogColorBlue = this.fogBlue;
//    }
}
