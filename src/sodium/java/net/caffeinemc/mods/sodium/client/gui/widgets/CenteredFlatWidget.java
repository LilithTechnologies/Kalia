package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.minecraft.text.Text;
import net.caffeinemc.mods.sodium.client.gui.ButtonTheme;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.util.Dim2i;

import java.awt.event.KeyEvent;

public abstract class CenteredFlatWidget extends AbstractWidget {
    private final boolean isSelectable;
    private final ButtonTheme theme;

    private boolean selected;
    private boolean enabled = true;
    private boolean visible = true;

    private final Text label;
    private final Text subtitle;

    public CenteredFlatWidget(Dim2i dim, Text label, Text subtitle, boolean isSelectable, ColorTheme theme) {
        super(dim);
        this.label = label;
        this.subtitle = subtitle;
        this.isSelectable = isSelectable;
        this.theme = new ButtonTheme(theme, Colors.BACKGROUND_HIGHLIGHT, Colors.BACKGROUND_DEFAULT, Colors.BACKGROUND_LIGHT);
    }

    public CenteredFlatWidget(Dim2i dim, Text label, boolean isSelectable, ColorTheme theme) {
        this(dim, label, null, isSelectable, theme);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        this.hovered = this.isMouseOver(mouseX, mouseY);

        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = this.getLimitX();
        int y2 = this.getLimitY();

        int textColor;
        if (this.isSelectable) {
            int state = this.selected ? 0 : (this.hovered ? 2 : 1);
            this.drawVanillaButtonState(x1, y1, this.getWidth(), this.getHeight(), state);
            textColor = this.selected || this.hovered ? VANILLA_TEXT_HOVER : VANILLA_TEXT;
        } else {
            textColor = VANILLA_TEXT;
        }

        // render icon and get offset for text
        int textOffset = this.renderIcon(textColor);

        if (this.subtitle == null) {
            this.drawStringWithShadow(this.truncateToFitWidth(this.label, textOffset), x1 + textOffset, (int) Math.ceil(((y1 + (this.getTextBoxHeight() - this.font.fontHeight) * 0.5f))), textColor);
        } else {
            var center = y1 + this.getTextBoxHeight() * 0.5f;
            this.drawStringWithShadow(this.truncateToFitWidth(this.label, textOffset), x1 + textOffset, (int) Math.ceil(center - (this.font.fontHeight + Layout.TEXT_LINE_SPACING * 0.5f)), textColor);
            this.drawStringWithShadow(this.truncateToFitWidth(this.subtitle, textOffset), x1 + textOffset, (int) Math.ceil(center + Layout.TEXT_LINE_SPACING * 0.5f), VANILLA_TEXT_DISABLED);
        }

        if (this.enabled && this.isFocused()) {
            this.drawBorder(x1, y1, x2, y2, Colors.FOREGROUND);
        }
    }

    protected int getTextBoxHeight() {
        return this.getHeight();
    }

    protected int renderIcon(int textColor) {
        return Layout.TEXT_LEFT_PADDING;
    }

    private String truncateToFitWidth(Text text, int iconOffset) {
        return this.truncateTextToFit(text.asFormattedString(), this.getWidth() - 14 - iconOffset);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.enabled || !this.visible) {
            return false;
        }

        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            doAction();

            return true;
        }

        return false;
    }

    abstract void onAction();

    private void doAction() {
        this.onAction();
        this.playClickSound();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
