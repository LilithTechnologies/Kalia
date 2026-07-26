package net.caffeinemc.mods.sodium.mixin.config;

public interface CaffeineConfigPlatform {
    void applyModOverrides(CaffeineConfig config, String jsonKey);
}
