package re.lilith.kalia.mixins.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import re.lilith.kalia.frame.draw.EntityBatchers;

@Mixin(SignEditScreen.class)
public abstract class MixinSignEditScreen extends Screen {

    @Unique
    private static final Identifier SIGN_TEXTURE =
            new Identifier("textures/entity/sign.png");

    @Shadow
    private int currentRow;

    @Shadow
    private int ticksSinceOpened;

    @Shadow
    private SignBlockEntity sign;

    /**
     * @reason Don't render the block entity
     * @author Lunasa
     */
    @Overwrite
    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();

        this.drawCenteredString(
                this.textRenderer,
                I18n.translate("sign.edit"),
                this.width / 2,
                40,
                0xFFFFFF
        );

        GlStateManager.color(1F, 1F, 1F, 1F);

        int centerX = this.width / 2;

        int boardW = 112;
        int boardH = 56;

        int boardX = this.width / 2 - boardW / 2;
        int boardY = 58;

        int textTop = boardY + 8;

        this.client.getTextureManager().bindTexture(SIGN_TEXTURE);

        // stem
        DrawableHelper.drawTexture(
                this.width / 2 - 5,
                boardY + boardH - 2,
                2, 16,
                2, 14,
                10, 65,
                64, 32
        );

        // board
        DrawableHelper.drawTexture(
                boardX,
                boardY,
                4, 2,
                24, 12,
                boardW,
                boardH,
                64,
                32
        );

        for (int i = 0; i < 4; i++) {
            String s = this.sign.text[i].asUnformattedString();

            if (i == this.currentRow &&
                    this.ticksSinceOpened / 6 % 2 == 0) {
                s = "> " + s + " <";
            }

            this.drawCenteredStringWithoutShadow(
                    this.textRenderer,
                    s,
                    centerX,
                    textTop + i * 10,
                    0
            );
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    @Unique
    public void drawCenteredStringWithoutShadow(TextRenderer textRenderer, String text, int centerX, int y, int color) {
        textRenderer.draw(text, (float)(centerX - textRenderer.getStringWidth(text) / 2), (float)y, color, false);
    }

}