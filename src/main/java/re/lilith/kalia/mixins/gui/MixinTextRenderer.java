package re.lilith.kalia.mixins.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import re.lilith.kalia.rendering.ui.UI;
import re.lilith.kalia.rendering.ui.text.Font;
import re.lilith.kalia.rendering.ui.text.Glyphs;

import java.util.Arrays;
import java.util.Random;

@Mixin(TextRenderer.class)
public abstract class MixinTextRenderer implements Font {
    @Shadow
    private int[] characterWidths;
    @Shadow
    private byte[] glyphWidths;
    @Shadow
    private int[] colorCodes;
    @Shadow
    private boolean unicode;
    @Shadow
    public int fontHeight;
    @Shadow
    public Random random;
    @Shadow
    private boolean rightToLeft;
    @Shadow
    @Final
    private TextureManager textureManager;
    @Shadow
    @Final
    private Identifier fontTexture;
    @Shadow
    @Final
    private static Identifier[] PAGES;

    @Shadow
    public abstract int getCharWidth(char character);

    @Shadow
    protected abstract String mirror(String text);

    @Unique
    private static final String kalia$SHEET_HEX =
            "00c000c100c200c800ca00cb00cd00d300d400d500da00df00e300f5011f0130013101520153015e"
                    + "015f01740175017e0207000000000000000000000000000000200021002200230024002500260027"
                    + "00280029002a002b002c002d002e002f0030003100320033003400350036003700380039003a003b"
                    + "003c003d003e003f0040004100420043004400450046004700480049004a004b004c004d004e004f"
                    + "0050005100520053005400550056005700580059005a005b005c005d005e005f0060006100620063"
                    + "006400650066006700680069006a006b006c006d006e006f00700071007200730074007500760077"
                    + "00780079007a007b007c007d007e000000c700fc00e900e200e400e000e500e700ea00eb00e800ef"
                    + "00ee00ec00c400c500c900e600c600f400f600f200fb00f900ff00d600dc00f800a300d800d70192"
                    + "00e100ed00f300fa00f100d100aa00ba00bf00ae00ac00bd00bc00a100ab00bb2591259225932502"
                    + "25242561256225562555256325512557255d255c255b251025142534252c251c2500253c255e255f"
                    + "255a25542569256625602550256c25672568256425652559255825522553256b256a2518250c2588"
                    + "2584258c2590258003b103b2039303c003a303c303bc03c403a6039803a903b4221e220522082229"
                    + "226100b1226522642320232100f7224800b0221900b7221a207f00b225a00000";

    @Unique
    private static final String kalia$SHEET = kalia$decodeSheet();

    @Unique
    private static String kalia$decodeSheet() {
        char[] out = new char[kalia$SHEET_HEX.length() / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = (char) Integer.parseInt(kalia$SHEET_HEX.substring(i * 4, i * 4 + 4), 16);
        }
        return new String(out);
    }

    @Unique
    private static final int[] kalia$SHEET_INDEX = new int[Character.MAX_VALUE + 1];

    static {
        Arrays.fill(kalia$SHEET_INDEX, -1);
        for (int i = kalia$SHEET.length() - 1; i >= 0; i--) {
            kalia$SHEET_INDEX[kalia$SHEET.charAt(i)] = i;
        }
    }

    /**
     * @reason Text is deferred
     * @author Lunasa
     */
    @Overwrite
    private int drawLayer(String text, float x, float y, int color, boolean shadow) {
        if (text == null) {
            return 0;
        }
        if (this.rightToLeft) {
            text = this.mirror(text);
        }
        if ((color & 0xFC000000) == 0) {
            color |= 0xFF000000;
        }

        float advance = Glyphs.INSTANCE.draw(this, text, x, y, color, shadow);
        return (int) (x + advance);
    }

    @Override
    @Unique
    public int[] getAsciiWidths() {
        return this.characterWidths;
    }

    @Override
    @Unique
    public byte[] getUnicodeWidths() {
        return this.glyphWidths;
    }

    @Override
    @Unique
    public int[] getFormattingColors() {
        return this.colorCodes;
    }

    @Override
    @Unique
    public boolean isUnicode() {
        return this.unicode;
    }

    @Override
    @Unique
    public int getLineHeight() {
        return this.fontHeight;
    }

    @Override
    @Unique
    public int asciiIndex(char character) {
        return kalia$SHEET_INDEX[character];
    }

    @Override
    @Unique
    public int asciiTextureId() {
        this.textureManager.bindTexture(this.fontTexture);
        return UI.INSTANCE.boundTextureId();
    }

    @Override
    @Unique
    public int unicodeTextureId(int page) {
        if (PAGES[page] == null) {
            PAGES[page] = new Identifier(String.format("textures/font/unicode_page_%02x.png", page));
        }
        this.textureManager.bindTexture(PAGES[page]);
        return UI.INSTANCE.boundTextureId();
    }

    @Override
    @Unique
    public char obfuscate(char character) {
        int width = this.getCharWidth(character);
        char replacement;
        do {
            replacement = kalia$SHEET.charAt(this.random.nextInt(kalia$SHEET.length()));
        } while (width != this.getCharWidth(replacement));
        return replacement;
    }
}
