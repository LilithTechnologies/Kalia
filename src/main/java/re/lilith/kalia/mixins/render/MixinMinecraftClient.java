package re.lilith.kalia.mixins.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.lilith.kalia.KaliaHooks;
import re.lilith.kalia.frame.HostTimings;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient {
    @Shadow
    public net.minecraft.client.option.GameOptions options;

    @Shadow
    protected abstract void loadLogo(TextureManager textureManager);

    @Unique
    private boolean kalia$insideLoadingScreen;

    @ModifyConstant(method = "getMaxFramerate", constant = @Constant(intValue = 30))
    int impl$getMaxFramerate(int constant) {
        return 120;
    }

    @Inject(method = "isFramerateValid", at = @At("HEAD"), cancellable = true)
    private void kalia$extendFramerateLimit(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(this.options.maxFramerate < (int) GameOptions.Option.MAX_FPS.getMaxValue());
    }

    @Inject(method = "loadLogo", at = @At("HEAD"), cancellable = true)
    private void kalia$frameLoadingScreen(TextureManager textureManager, CallbackInfo callback) {
        if (kalia$insideLoadingScreen) {
            return;
        }

        callback.cancel();
        kalia$insideLoadingScreen = true;
        try {
            // TODO: Loading screen
        } finally {
            kalia$insideLoadingScreen = false;
        }
    }

    @Redirect(
            method = "runGameLoop",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V")
    )
    private void kalia$renderFrame(GameRenderer renderer, float tickDelta, long limitTime) {
        KaliaHooks.setFrameState(
                tickDelta, limitTime
        );
        KaliaHooks.renderFrame();
    }

    @Unique
    private long kalia$tickMark;

    @Unique
    private long kalia$displayMark;

    @Inject(method = "tick", at = @At("HEAD"))
    private void kalia$tickStart(CallbackInfo callback) {
        kalia$tickMark = System.nanoTime();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void kalia$tickEnd(CallbackInfo callback) {
        HostTimings.addTick(System.nanoTime() - kalia$tickMark);
    }

    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void kalia$displayStart(CallbackInfo callback) {
        kalia$displayMark = System.nanoTime();
    }

    @Inject(method = "updateDisplay", at = @At("RETURN"))
    private void kalia$displayEnd(CallbackInfo callback) {
        HostTimings.addDisplay(System.nanoTime() - kalia$displayMark);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void kalia$shutdown(CallbackInfo callback) {
        KaliaHooks.shutdown();
    }
}
