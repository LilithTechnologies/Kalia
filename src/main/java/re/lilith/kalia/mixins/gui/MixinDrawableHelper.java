package re.lilith.kalia.mixins.gui;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.texture.Sprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import re.lilith.kalia.rendering.ui.GuiCompat;

@Mixin(DrawableHelper.class)
public abstract class MixinDrawableHelper {
    /**
     * @reason Deferred into GUI render state.
     * @author Lunasa
     */
    @Overwrite
    public static void fill(int x1, int y1, int x2, int y2, int color) {
        GuiCompat.INSTANCE.fill(x1, y1, x2, y2, color);
    }

    /**
     * @reason Deferred into GUI render state.
     * @author Lunasa
     */
    @Overwrite
    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2) {
        GuiCompat.INSTANCE.fillGradient(x1, y1, x2, y2, color1, color2);
    }

    /**
     * @reason Deferred into GUI render state.
     * @author Lunasa
     */
    @Overwrite
    public void drawHorizontalLine(int x1, int x2, int y, int color) {
        GuiCompat.INSTANCE.horizontalLine(x1, x2, y, color);
    }

    /**
     * @reason Deferred into GUI render state.
     * @author Lunasa
     */
    @Overwrite
    public void drawVerticalLine(int x, int y1, int y2, int color) {
        GuiCompat.INSTANCE.verticalLine(x, y1, y2, color);
    }

    /**
     * @reason Deferred into GUI render state.
     * @author Lunasa
     */
    @Overwrite
    public void drawTexture(int x, int y, int u, int v, int width, int height) {
        GuiCompat.INSTANCE.blit(x, y, u, v, width, height);
    }

    /**
     * @reason Deferred into GUI render state.
     * @author Lunasa
     */
    @Overwrite
    public static void drawTexture(int x, int y, float u, float v, int width, int height, float textureWidth, float textureHeight) {
        GuiCompat.INSTANCE.blitScaled(x, y, u, v, width, height, width, height, textureWidth, textureHeight);
    }

    /**
     * @reason Deferred into GUI render state.
     * @author Lunasa
     */
    @Overwrite
    public static void drawTexture(
            int x,
            int y,
            float u,
            float v,
            int regionWidth,
            int regionHeight,
            int width,
            int height,
            float textureWidth,
            float textureHeight
    ) {
        GuiCompat.INSTANCE.blitScaled(x, y, u, v, regionWidth, regionHeight, width, height, textureWidth, textureHeight);
    }

    /**
     * @reason Deferred into GUI render state.
     * @author Lunasa
     */
    @Overwrite
    public void drawTexture(float x, float y, int u, int v, int width, int height) {
        GuiCompat.INSTANCE.blit(x, y, u, v, width, height);
    }

    /**
     * @reason Deferred into GUI render state.
     * @author Lunasa
     */
    @Overwrite
    public void drawSprite(int x, int y, Sprite sprite, int width, int height) {
        GuiCompat.INSTANCE.sprite(
                x,
                y,
                width,
                height,
                sprite.getMinU(),
                sprite.getMinV(),
                sprite.getMaxU(),
                sprite.getMaxV()
        );
    }
}