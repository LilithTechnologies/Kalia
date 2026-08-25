package dev.rdh.argentum.api;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;

import java.util.ServiceLoader;

public interface IHooks {
    void setVsyncEnabled(boolean enabled);
    TextComponent getFriendlyModName(String id);

    int getFxaaMode();
    void setFxaaMode(int ordinal);

    int getUpscaleMode();
    void setUpscaleMode(int ordinal);

    float getWorldDownscale();
    void setWorldDownscale(float value);

    IHooks INSTANCE = ServiceLoader.load(IHooks.class)
            .findFirst()
            .orElseThrow(() -> new NullPointerException("Failed to load hook service"));
}
