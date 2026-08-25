package dev.rdh.argentum.api;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;

import java.util.ServiceLoader;

public interface IHooks {
    void setVsyncEnabled(boolean enabled);
    TextComponent getFriendlyModName(String id);

    IHooks INSTANCE = ServiceLoader.load(IHooks.class)
            .findFirst()
            .orElseThrow(() -> new NullPointerException("Failed to load hook service"));
}
