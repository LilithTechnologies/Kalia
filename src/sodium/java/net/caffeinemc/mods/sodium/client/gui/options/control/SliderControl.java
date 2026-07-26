package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.caffeinemc.mods.sodium.legacy.compat.mojang.math.Mth;
import net.minecraft.client.gui.screen.Screen;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.util.Dim2i;


import java.awt.event.KeyEvent;

public class SliderControl implements Control {
    private final IntegerOption option;

    private double thumbPosition;
    private boolean sliderHeld;

    public SliderControl(IntegerOption option) {
        this.option = option;
    }

    @Override
    public ControlElement createElement(Screen screen, AbstractOptionList list, Dim2i dim, ColorTheme theme) {
        return new SliderControlElement(list, this, dim, theme);
    }

    @Override
    public StatefulOption<Integer> getOption() {
        return this.option;
    }

    @Override
    public int getMaxWidth() {
        throw new UnsupportedOperationException("Not implemented");
    }

    static class SliderControlElement extends ControlElement {
        private static final int THUMB_WIDTH = 2, TRACK_HEIGHT = 1;

        private final SliderControl control;
        private final IntegerOption option;

        private int contentWidth;

        public SliderControlElement(AbstractOptionList list, SliderControl control, Dim2i dim, ColorTheme theme) {
            super(list, dim, theme);

            this.control = control;
            this.option = control.option;

            if (!control.sliderHeld) {
                control.thumbPosition = this.getThumbPositionForValue(this.option.getValidatedValue());
            }
        }

        @Override
        public Option getOption() {
            return this.option;
        }

        @Override
        protected net.minecraft.text.Text getValueText() {
            return this.option.formatValue(this.option.getValidatedValue());
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            this.hovered = this.isMouseOver(mouseX, mouseY);

            if (!this.option.showControl()) {
                super.render(mouseX, mouseY, delta);
                return;
            }

            if (!this.control.sliderHeld) {
                this.control.thumbPosition = this.getThumbPositionForValue(this.option.getValidatedValue());
            }

            final int knobWidth = 8;
            int thumbX = (int) (this.getX() + this.control.thumbPosition * (this.getWidth() - knobWidth));

            this.drawVanillaButtonState(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0);
            if (this.option.isEnabled()) {
                this.drawVanillaSliderKnob(thumbX, this.getY(), knobWidth, this.getHeight());
            }
            this.drawCenteredLabel();

        }

        public int getSliderX() {
            return this.getX();
        }

        public int getSliderWidth() {
            return this.getWidth();
        }

        public boolean isMouseOverSlider(double mouseX, double mouseY) {
            return this.isMouseOver(mouseX, mouseY);
        }

        @Override
        public int getContentWidth() {
            return this.contentWidth;
        }

        public double getThumbPositionForValue(int value) {
            var range = this.option.getSteppedValidator();
            int min = range.min();
            int max = range.max();
            return Mth.clamp((double) (value - min) / (max - min), 0.0d, 1.0d);
        }

        private int getValueForThumbPosition() {
            var range = this.option.getSteppedValidator();
            int step = range.step();
            int min = range.min();
            int max = range.max();
            return min + (step * (int) Math.round((this.control.thumbPosition * (max - min)) / step));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.control.sliderHeld = false;

            if (this.option.isEnabled() && button == 0 && this.isMouseOver(mouseX, mouseY)) {
                if (this.isMouseOverSlider(mouseX, mouseY)) {
                    this.setValueFromMouse(mouseX);
                    this.control.sliderHeld = true;
                }

                return true;
            }

            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (this.option.isEnabled() && button == 0 && this.control.sliderHeld) {
                this.control.sliderHeld = false;
                playClickSound();
                return true;
            }

            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button) {
            if (this.option.isEnabled() && button == 0 && this.control.sliderHeld) {
                this.setValueFromMouse(mouseX);
                return true;
            }

            return false;
        }

        private void setValueFromMouse(double d) {
            this.setValue(Mth.clamp((d - (this.getSliderX() + 4.0D)) / (this.getSliderWidth() - 8.0D), 0.0D, 1.0D));
        }

        public void setValue(double newThumbPosition) {
            this.control.thumbPosition = newThumbPosition;

            this.option.modifyValue(this.getValueForThumbPosition());
        }
    }
}
