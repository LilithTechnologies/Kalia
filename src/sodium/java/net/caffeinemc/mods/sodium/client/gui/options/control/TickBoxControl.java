package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.util.Dim2i;


import java.awt.event.KeyEvent;

public class TickBoxControl implements Control {
    private final BooleanOption option;

    public TickBoxControl(BooleanOption option) {
        this.option = option;
    }

    @Override
    public ControlElement createElement(Screen screen, AbstractOptionList list, Dim2i dim, ColorTheme theme) {
        return new TickBoxControlElement(list, this.option, dim, theme);
    }

    @Override
    public int getMaxWidth() {
        return 30;
    }

    @Override
    public StatefulOption<Boolean> getOption() {
        return this.option;
    }

    private static class TickBoxControlElement extends ControlElement {
        private final BooleanOption option;

        public TickBoxControlElement(AbstractOptionList list, BooleanOption option, Dim2i dim, ColorTheme theme) {
            super(list, dim, theme);

            this.option = option;
        }

        @Override
        public Option getOption() {
            return this.option;
        }

        @Override
        protected net.minecraft.text.Text getValueText() {
            return new net.minecraft.text.TranslatableText(this.option.getValidatedValue() ? "options.on" : "options.off");
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            super.render(mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.option.isEnabled() && button == 0 && this.isMouseOver(mouseX, mouseY)) {
                toggleControl();
                return true;
            }

            return false;
        }

        private void toggleControl() {
            this.playClickSound();

            this.option.modifyValue(!this.option.getValidatedValue());
        }
    }
}
