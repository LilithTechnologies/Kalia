package dev.rdh.argentum.impl.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.taumc.celeritas.api.options.structure.OptionStorage;

final class GameOptionsStorage implements OptionStorage<GameOptions> {
    @Override
    public GameOptions getData() {
        return MinecraftClient.getInstance().options;
    }

    @Override
    public void save() {
        this.getData().save();
    }
}
