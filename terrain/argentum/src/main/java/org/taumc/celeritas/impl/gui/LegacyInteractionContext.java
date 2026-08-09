package org.taumc.celeritas.impl.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.Identifier;
import org.embeddedt.embeddium.impl.gui.framework.InteractionContext;

enum LegacyInteractionContext implements InteractionContext {
    INSTANCE;

    private static final Identifier BUTTON_SOUND = new Identifier("gui.button.press");

    @Override
    public boolean isSpecialKeyDown(SpecialKey key) {
        return switch (key) {
            case SHIFT -> Screen.hasShiftDown();
            case CTRL -> Screen.hasControlDown();
            case ALT -> Screen.hasAltDown();
        };
    }

    @Override
    public void playClickSound() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(BUTTON_SOUND, 1.0F));
    }
}
