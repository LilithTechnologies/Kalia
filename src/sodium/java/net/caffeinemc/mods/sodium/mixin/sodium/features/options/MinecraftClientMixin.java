package net.caffeinemc.mods.sodium.mixin.sodium.features.options;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "initializeGame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GLX;createContext()V"))
    void impl$init(CallbackInfo ci) {
        ConfigManager.registerConfigsLate();
    }

    /**
     * @author JellySquid
     * @reason Make ambient occlusion user configurable
     */
    @Overwrite
    public static boolean isAmbientOcclusionEnabled() {
        return SodiumClientMod.options().quality.smoothLighting != SodiumOptions.LightingQuality.OFF;
    }
}