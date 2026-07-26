package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;

import java.awt.*;

public abstract class ControlElement extends AbstractWidget {
    protected final AbstractOptionList list;
    protected final ColorTheme theme;

    public ControlElement(AbstractOptionList list, Dim2i dim, ColorTheme theme) {
        super(dim);
        this.list = list;
        this.theme = theme;
    }

    public abstract Option getOption();

    public int getContentWidth() {
        return this.getOption().getControl().getMaxWidth();
    }

    /** The current value shown after the option name; null for controls without one. */
    protected Text getValueText() {
        return null;
    }

    protected String buildLabel() {
        String name = this.getOption().getName().asFormattedString();

//        if (this.getOption().isEnabled() && this.getOption().hasChanged()) {
//            name = name + " *";
//        }

        var value = this.getValueText();
        String label = value != null ? name + ": " + value.asFormattedString() : name;

        if (this.getOption().isEnabled()) {
            if (this.getOption().hasChanged()) {
                label = Formatting.ITALIC + label;
            }
        } else {
            label = Formatting.GRAY + label;
        }

        return label;
    }

    protected void drawCenteredLabel() {
        String label = this.buildLabel();
        int color = this.getOption().isEnabled()
                ? (this.hovered ? VANILLA_TEXT_HOVER : VANILLA_TEXT)
                : VANILLA_TEXT_DISABLED;
        this.drawCenteredScrollingTextWithShadow(
            label,
            this.getX() + Layout.OPTION_TEXT_SIDE_PADDING,
            this.getCenterY() + Layout.REGULAR_TEXT_BASELINE_OFFSET,
            this.getWidth() - Layout.OPTION_TEXT_SIDE_PADDING * 2,
            color
        );
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.hovered = this.isMouseOver(mouseX, mouseY);

        this.drawVanillaButton(
                this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                this.getOption().isEnabled(), this.hovered);
        this.drawCenteredLabel();
    }

    protected Text formatDisabledControlValue(Text value) {
        return value.copy().setStyle(new Style()
                .setFormatting(Formatting.GRAY)
                .setItalic(true)
        );
    }

    @Override
    public int getY() {
        return super.getY() - this.list.getScrollAmount();
    }
}
