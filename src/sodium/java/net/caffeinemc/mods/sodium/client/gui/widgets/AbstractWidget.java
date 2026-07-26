package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.caffeinemc.mods.sodium.legacy.compat.mojang.minecraft.gui.Renderable;
import net.caffeinemc.mods.sodium.legacy.compat.mojang.minecraft.gui.event.GuiEventListener;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.caffeinemc.mods.sodium.client.gui.Dimensioned;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.caffeinemc.mods.sodium.client.util.ScissorUtil;

public abstract class AbstractWidget extends DrawableHelper implements Renderable, GuiEventListener, Dimensioned {
    protected static final Identifier WIDGETS_TEXTURE = new Identifier("textures/gui/widgets.png");

    protected static final int VANILLA_TEXT = 0xFFE0E0E0;
    protected static final int VANILLA_TEXT_HOVER = 0xFFFFFFA0;
    protected static final int VANILLA_TEXT_DISABLED = 0xFFA0A0A0;

    protected final TextRenderer font = MinecraftClient.getInstance().textRenderer;
    private final Dim2i dim;
    protected boolean focused;
    protected boolean hovered;
    private long textScrollHoverStart = -1L;

    protected AbstractWidget(Dim2i dim) {
        this.dim = dim;
    }

    protected void drawVanillaButton(int x, int y, int width, int height, boolean enabled, boolean hovered) {
        int state = !enabled ? 0 : (hovered ? 2 : 1);
        drawVanillaButtonState(x, y, width, height, state);
    }

    protected void drawVanillaButtonState(int x, int y, int width, int height, int state) {
        MinecraftClient.getInstance().getTextureManager().bindTexture(WIDGETS_TEXTURE);
        com.mojang.blaze3d.platform.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int v = 46 + state * 20;
        int topHeight = Math.min((height + 1) / 2, 20);
        int bottomHeight = Math.min(height - topHeight, 20);

        drawButtonBand(x, y, width, topHeight, v);
        drawButtonBand(x, y + height - bottomHeight, width, bottomHeight, v + 20 - bottomHeight);
        // Middle band for heights over 40px: repeat the sprite's center rows
        int filled = topHeight + bottomHeight;
        int fillY = y + topHeight;
        while (filled < height) {
            int band = Math.min(12, height - filled);
            drawButtonBand(x, fillY, width, band, v + 4);
            fillY += band;
            filled += band;
        }
    }

    private static void drawButtonBand(int x, int y, int width, int height, int v) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (width <= 200) {
            int leftWidth = width / 2;
            int rightWidth = width - leftWidth;
            DrawableHelper.drawTexture(x, y, 0, v, leftWidth, height, 256, 256);
            DrawableHelper.drawTexture(x + leftWidth, y, 200 - rightWidth, v, rightWidth, height, 256, 256);
            return;
        }

        final int cap = 4;                 // width of the sprite's beveled side edge
        final int bodyWidth = 200 - cap * 2; // tileable center region (u = 4..196)

        DrawableHelper.drawTexture(x, y, 0, v, cap, height, 256, 256);
        int bodyX = x + cap;
        int bodyEnd = x + width - cap;
        while (bodyX < bodyEnd) {
            int chunk = Math.min(bodyWidth, bodyEnd - bodyX);
            DrawableHelper.drawTexture(bodyX, y, cap, v, chunk, height, 256, 256);
            bodyX += chunk;
        }
        DrawableHelper.drawTexture(x + width - cap, y, 200 - cap, v, cap, height, 256, 256);
    }

    protected void drawVanillaSliderKnob(int x, int y, int width, int height) {
        MinecraftClient.getInstance().getTextureManager().bindTexture(WIDGETS_TEXTURE);
        com.mojang.blaze3d.platform.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int leftWidth = width / 2;
        int rightWidth = width - leftWidth;
        int topHeight = Math.min(height / 2, 20);
        int bottomHeight = Math.min(height - topHeight, 20);
        DrawableHelper.drawTexture(x, y, 0, 66, leftWidth, topHeight, 256, 256);
        DrawableHelper.drawTexture(x + leftWidth, y, 200 - rightWidth, 66, rightWidth, topHeight, 256, 256);
        DrawableHelper.drawTexture(x, y + height - bottomHeight, 0, 66 + 20 - bottomHeight, leftWidth, bottomHeight, 256, 256);
        DrawableHelper.drawTexture(x + leftWidth, y + height - bottomHeight, 200 - rightWidth, 66 + 20 - bottomHeight, rightWidth, bottomHeight, 256, 256);
    }

    protected void drawStringWithShadow(String text, int x, int y, int color) {
        this.font.drawWithShadow(text, x, y, color);
    }

    protected void drawStringWithShadow(Text text, int x, int y, int color) {
        this.font.drawWithShadow(text.asFormattedString(), x, y, color);
    }

    protected void drawCenteredScrollingTextWithShadow(String text, int x, int y, int width, int color) {
        int textWidth = this.font.getStringWidth(text);

        if (!this.hovered) {
            this.textScrollHoverStart = -1L;
        }

        if (textWidth <= width) {
            this.drawStringWithShadow(text, x + (width - textWidth) / 2, y, color);
            return;
        }

        if (!this.hovered) {
            String truncated = this.truncateTextToFit(text, width);
            this.drawStringWithShadow(truncated, x + (width - this.font.getStringWidth(truncated)) / 2, y, color);
            return;
        }

        int overflow = textWidth - width;
        long now = System.nanoTime();
        if (this.textScrollHoverStart < 0L) {
            this.textScrollHoverStart = now;
        }
        double travelTime = overflow / 24.0D;
        double pauseTime = 0.75D;
        double cycleTime = (travelTime * 2.0D) + (pauseTime * 2.0D);
        double phase = ((now - this.textScrollHoverStart) * 1.0E-9D) % cycleTime;
        double offset;

        if (phase < pauseTime) {
            offset = 0.0D;
        } else if (phase < pauseTime + travelTime) {
            offset = (phase - pauseTime) / travelTime * overflow;
        } else if (phase < pauseTime * 2.0D + travelTime) {
            offset = overflow;
        } else {
            offset = (1.0D - ((phase - (pauseTime * 2.0D + travelTime)) / travelTime)) * overflow;
        }

        ScissorUtil.withScissor(
            x, y, width, this.font.fontHeight + 2,
            () -> this.drawStringWithShadow(text, x - (int) Math.round(offset), y, color)
        );
    }

    @Override
    public Dim2i getDimensions() {
        return this.dim;
    }

    protected void drawString(String text, int x, int y, int color) {
        this.font.draw(text, x, y, color);
    }

    protected void drawString(Text text, int x, int y, int color) {
        this.font.draw(text.asFormattedString(), x, y, color);
    }

    protected void drawCenteredString(Text text, int x, int y, int color) {
//        graphics.drawCenteredString(this.font, text, x, y, color);
    }

    public boolean isHovered() {
        return this.hovered;
    }

    protected void drawRect(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y2, color);
    }

    protected void playClickSound() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(new Identifier("gui.button.press"), 1.0F));
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX < this.getLimitX() && mouseY >= this.getY() && mouseY < this.getLimitY();
    }

    protected int getStringWidth(Text text) {
        return this.font.getStringWidth(text.asFormattedString());
    }

    @Override
    public boolean isFocused() {
        return this.focused;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) {
            this.focused = false;
        }
    }

    protected String truncateTextToFit(String name, int targetWidth) {
        var suffix = "...";
        var suffixWidth = this.font.getStringWidth(suffix);
        var nameFontWidth = this.font.getStringWidth(name);
        if (nameFontWidth > targetWidth) {
            targetWidth -= suffixWidth;
            int maxLabelChars = name.length() - 3;
            int minLabelChars = 1;

            // binary search on how many chars fit
            while (maxLabelChars - minLabelChars > 1) {
                var mid = (maxLabelChars + minLabelChars) / 2;
                var midName = name.substring(0, mid);
                var midWidth = this.font.getStringWidth(midName);
                if (midWidth > targetWidth) {
                    maxLabelChars = mid;
                } else {
                    minLabelChars = mid;
                }
            }

            name = name.substring(0, minLabelChars).trim() + suffix;
        }
        return name;
    }

    protected void drawBorder(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y1 + 1, color);
        DrawableHelper.fill(x1, y2 - 1, x2, y2, color);
        DrawableHelper.fill(x1, y1, x1 + 1, y2, color);
        DrawableHelper.fill(x2 - 1, y1, x2, y2, color);
    }
}
