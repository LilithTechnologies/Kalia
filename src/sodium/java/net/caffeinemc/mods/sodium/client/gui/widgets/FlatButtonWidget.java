package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.legacy.compat.mojang.minecraft.gui.Renderable;
import net.minecraft.text.Text;
import net.caffeinemc.mods.sodium.client.gui.ButtonTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import java.awt.event.KeyEvent;

public class FlatButtonWidget extends AbstractWidget implements Renderable {
    public static final ButtonTheme DEFAULT_THEME = new ButtonTheme(
            Colors.FOREGROUND, Colors.FOREGROUND, Colors.FOREGROUND_DISABLED,
            Colors.BACKGROUND_HOVER, Colors.BACKGROUND_DEFAULT, Colors.BACKGROUND_LIGHT);

    private final Runnable action;
    private final boolean drawBackground;
    private final boolean drawFrame;
    private final boolean leftAlign;
    private final ButtonTheme theme;
    private final Text label;

    private boolean selected;
    private boolean enabled = true;
    private boolean visible = true;

    public FlatButtonWidget(Dim2i dim, Text label, Runnable action, boolean drawBackground, boolean drawFrame, boolean leftAlign, ButtonTheme theme) {
        super(dim);
        this.label = label;
        this.action = action;
        this.drawBackground = drawBackground;
        this.drawFrame = drawFrame;
        this.leftAlign = leftAlign;
        this.theme = theme;
    }

    public FlatButtonWidget(Dim2i dim, Text label, Runnable action, boolean drawBackground, boolean leftAlign, ButtonTheme theme) {
        this(dim, label, action, drawBackground, !drawBackground, leftAlign, theme);
    }

    public FlatButtonWidget(Dim2i dim, Text label, Runnable action, boolean drawBackground, boolean leftAlign) {
        this(dim, label, action, drawBackground, leftAlign, DEFAULT_THEME);
    }

    public FlatButtonWidget(Dim2i dim, Text label, Runnable action, boolean drawBackground, boolean drawFrame, boolean leftAlign) {
        this(dim, label, action, drawBackground, drawFrame, leftAlign, DEFAULT_THEME);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        this.hovered = this.isMouseOver(mouseX, mouseY);

        if (this.drawBackground) {
            if (this.selected) {
                this.drawVanillaButtonState(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0);
            } else {
                this.drawVanillaButton(this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.enabled, this.hovered);
            }
        }

        int textColor = this.getTextColor();

        if (this.label != null) {
            // Truncate to the button's width so long labels cannot overflow onto neighbors.
            String text = this.truncateTextToFit(this.label.asFormattedString(), this.getWidth() - Layout.TEXT_LEFT_PADDING);
            int strWidth = this.font.getStringWidth(text);
            this.drawStringWithShadow(text, this.leftAlign ? this.getX() + Layout.TEXT_LEFT_PADDING : (this.getCenterX() - (strWidth / 2)), this.getCenterY() - this.font.fontHeight / 2, textColor);
        }

        if (this.drawFrame) {
            this.drawBorder(this.getX(), this.getY(), this.getLimitX(), this.getLimitY(), Colors.FOREGROUND);
        }
    }

    protected int getTextColor() {
        if (!this.enabled) {
            return VANILLA_TEXT_DISABLED;
        }
        return this.hovered || this.selected ? VANILLA_TEXT_HOVER : VANILLA_TEXT;
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

    private void doAction() {
        this.action.run();
        this.playClickSound();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return this.visible;
    }
}
