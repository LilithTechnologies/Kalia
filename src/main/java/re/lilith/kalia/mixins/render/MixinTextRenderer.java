package re.lilith.kalia.mixins.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@Mixin(TextRenderer.class)
public class MixinTextRenderer {
    @Shadow
    @Final
    private static Identifier[] PAGES;
    @Shadow
    private int[] characterWidths;
    @Shadow
    private byte[] glyphWidths;
    @Shadow
    private boolean unicode;
    @Shadow
    @Final
    private TextureManager textureManager;
    @Shadow
    @Final
    private Identifier fontTexture;
    @Shadow
    private float x;
    @Shadow
    private float y;
    @Shadow
    private boolean obfuscated;
    @Shadow
    private boolean bold;
    @Shadow
    private boolean italic;
    @Shadow
    private boolean underline;
    @Shadow
    private boolean strikethrough;
    @Shadow
    private int[] colorCodes;
    @Shadow
    private float red;
    @Shadow
    private float green;
    @Shadow
    private float blue;
    @Shadow
    private float alpha;
    @Shadow
    private int color;
    @Shadow
    public Random random;
    @Shadow
    public int fontHeight;

    @Unique
    private static final String kalia$TABLE =
            "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5"
                    + "\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e"
                    + "\u015f\u0174\u0175\u017e\u0207"
                    + "\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
                    + " !\"#$%&'()*+,-./0123456789:;<=>?"
                    + "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_"
                    + "`abcdefghijklmnopqrstuvwxyz{|}~\u0000"
                    + "\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb"
                    + "\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4"
                    + "\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8"
                    + "\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba"
                    + "\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb"
                    + "\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563"
                    + "\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c"
                    + "\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550"
                    + "\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b"
                    + "\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580"
                    + "\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398"
                    + "\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264"
                    + "\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2"
                    + "\u25a0\u0000";

    @Unique
    private static final int[] kalia$CHAR_INDEX = new int[65536];

    @Unique
    private static final int kalia$PAGE_NONE = Integer.MIN_VALUE;

    static {
        Arrays.fill(kalia$CHAR_INDEX, -1);
        for (int i = kalia$TABLE.length() - 1; i >= 0; i--) {
            kalia$CHAR_INDEX[kalia$TABLE.charAt(i)] = i;
        }
    }

    @Unique
    private final Map<String, Integer> kalia$widthCache =
            new LinkedHashMap<>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > 256;
                }
            };

    @Inject(method = "reload", at = @At("HEAD"))
    private void kalia$onReload(ResourceManager mgr, CallbackInfo ci) {
        kalia$widthCache.clear();
    }

    @Inject(method = "setUnicode", at = @At("HEAD"))
    private void kalia$onSetUnicode(boolean unicode, CallbackInfo ci) {
        kalia$widthCache.clear();
    }

    /**
     * @reason Cached width computation
     * @author Lunasa
     */
    @Overwrite
    public int getCharWidth(char character) {
        if (character == 167) return -1;
        if (character == ' ') return 4;

        int index = kalia$CHAR_INDEX[character];
        if (character > 0 && index != -1 && !this.unicode) {
            return this.characterWidths[index];
        }
        if (this.glyphWidths[character] != 0) {
            int start = this.glyphWidths[character] >>> 4;
            int end = this.glyphWidths[character] & 15;
            if (end > 7) {
                end = 15;
                start = 0;
            }
            ++end;
            return (end - start) / 2 + 1;
        }
        return 0;
    }

    /**
     * @reason Cached width computation
     * @author Lunasa
     */
    @Overwrite
    public int getStringWidth(String text) {
        if (text == null) return 0;

        Integer cached = kalia$widthCache.get(text);
        if (cached != null) return cached;

        int width = 0;
        boolean boldStyle = false;

        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            int charWidth = this.getCharWidth(c);
            if (charWidth < 0 && i < text.length() - 1) {
                c = text.charAt(++i);
                if (c == 'l' || c == 'L') {
                    boldStyle = true;
                } else if (c == 'r' || c == 'R') {
                    boldStyle = false;
                }
                charWidth = 0;
            }
            width += charWidth;
            if (boldStyle && charWidth > 0) {
                ++width;
            }
        }

        kalia$widthCache.put(text, width);
        return width;
    }

    /**
     * @reason Batched vanilla-compatible text rendering
     * @author Lunasa
     */
    @Overwrite
    private void draw(String text, boolean shadow) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();

        int currentPage = kalia$PAGE_NONE;
        boolean batchActive = false;
        float currentRed = this.red;
        float currentGreen = this.green;
        float currentBlue = this.blue;
        float currentAlpha = this.alpha;

        float[] decorations = new float[256 * 8];
        int decorationCount = 0;

        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);

            if (c == 167 && i + 1 < text.length()) {
                int formatting = kalia$formattingIndex(text.charAt(i + 1));
                if (formatting < 16) {
                    this.obfuscated = false;
                    this.bold = false;
                    this.strikethrough = false;
                    this.underline = false;
                    this.italic = false;
                    if (formatting < 0) formatting = 15;
                    if (shadow) formatting += 16;
                    int rgb = this.colorCodes[formatting];
                    this.color = rgb;

                    if (batchActive) {
                        tessellator.draw();
                        batchActive = false;
                        currentPage = kalia$PAGE_NONE;
                    }

                    currentRed = (float) (rgb >> 16 & 255) / 255.0F;
                    currentGreen = (float) (rgb >> 8 & 255) / 255.0F;
                    currentBlue = (float) (rgb & 255) / 255.0F;
                    currentAlpha = this.alpha;
                    GlStateManager.color(currentRed, currentGreen, currentBlue, currentAlpha);
                } else if (formatting == 16) {
                    this.obfuscated = true;
                } else if (formatting == 17) {
                    this.bold = true;
                } else if (formatting == 18) {
                    this.strikethrough = true;
                } else if (formatting == 19) {
                    this.underline = true;
                } else if (formatting == 20) {
                    this.italic = true;
                } else if (formatting == 21) {
                    this.obfuscated = false;
                    this.bold = false;
                    this.strikethrough = false;
                    this.underline = false;
                    this.italic = false;

                    if (batchActive) {
                        tessellator.draw();
                        batchActive = false;
                        currentPage = kalia$PAGE_NONE;
                    }

                    currentRed = this.red;
                    currentGreen = this.green;
                    currentBlue = this.blue;
                    currentAlpha = this.alpha;
                    GlStateManager.color(currentRed, currentGreen, currentBlue, currentAlpha);
                }
                ++i;
                continue;
            }

            int tableIndex = kalia$CHAR_INDEX[c];
            if (this.obfuscated && tableIndex != -1) {
                int charWidth = this.getCharWidth(c);
                char mapped;
                do {
                    mapped = kalia$TABLE.charAt(this.random.nextInt(kalia$TABLE.length()));
                } while (charWidth != this.getCharWidth(mapped));
                c = mapped;
                tableIndex = kalia$CHAR_INDEX[c];
            }

            float offset = this.unicode ? 0.5F : 1.0F;
            boolean shadowOffset = (c == 0 || tableIndex == -1 || this.unicode) && shadow;
            if (shadowOffset) {
                this.x -= offset;
                this.y -= offset;
            }

            float advance;
            if (c == ' ') {
                advance = 4.0F;
            } else {
                currentPage = kalia$ensureGlyphPage(c, tableIndex, currentPage, tessellator, bufferBuilder);
                batchActive = true;
                advance = currentPage == -1
                        ? kalia$addNormalGlyph(tableIndex, this.italic, bufferBuilder)
                        : kalia$addUnicodeGlyph(c, this.italic, bufferBuilder);
            }

            if (shadowOffset) {
                this.x += offset;
                this.y += offset;
            }

            if (this.bold) {
                this.x += offset;
                if (shadowOffset) {
                    this.x -= offset;
                    this.y -= offset;
                }

                if (c != ' ') {
                    currentPage = kalia$ensureGlyphPage(c, tableIndex, currentPage, tessellator, bufferBuilder);
                    batchActive = true;
                    if (currentPage == -1) {
                        kalia$addNormalGlyph(tableIndex, this.italic, bufferBuilder);
                    } else {
                        kalia$addUnicodeGlyph(c, this.italic, bufferBuilder);
                    }
                }

                this.x -= offset;
                if (shadowOffset) {
                    this.x += offset;
                    this.y += offset;
                }
                ++advance;
            }

            if (this.strikethrough && decorationCount < 256) {
                int dataIndex = decorationCount++ * 8;
                float yCenter = this.y + (float) (this.fontHeight / 2);
                decorations[dataIndex] = this.x;
                decorations[dataIndex + 1] = yCenter - 1.0F;
                decorations[dataIndex + 2] = this.x + advance;
                decorations[dataIndex + 3] = yCenter;
                decorations[dataIndex + 4] = currentRed;
                decorations[dataIndex + 5] = currentGreen;
                decorations[dataIndex + 6] = currentBlue;
                decorations[dataIndex + 7] = currentAlpha;
            }

            if (this.underline && decorationCount < 256) {
                int dataIndex = decorationCount++ * 8;
                float yBottom = this.y + (float) this.fontHeight;
                decorations[dataIndex] = this.x - 1.0F;
                decorations[dataIndex + 1] = yBottom - 1.0F;
                decorations[dataIndex + 2] = this.x + advance;
                decorations[dataIndex + 3] = yBottom;
                decorations[dataIndex + 4] = currentRed;
                decorations[dataIndex + 5] = currentGreen;
                decorations[dataIndex + 6] = currentBlue;
                decorations[dataIndex + 7] = currentAlpha;
            }

            this.x += (float) ((int) advance);
        }

        if (batchActive) {
            tessellator.draw();
        }

        if (decorationCount > 0) {
            GlStateManager.disableTexture();
            bufferBuilder.begin(7, VertexFormats.POSITION);

            float previousRed = currentRed;
            float previousGreen = currentGreen;
            float previousBlue = currentBlue;
            float previousAlpha = currentAlpha;

            for (int i = 0; i < decorationCount; ++i) {
                int dataIndex = i * 8;
                float decoRed = decorations[dataIndex + 4];
                float decoGreen = decorations[dataIndex + 5];
                float decoBlue = decorations[dataIndex + 6];
                float decoAlpha = decorations[dataIndex + 7];

                if (decoRed != previousRed || decoGreen != previousGreen || decoBlue != previousBlue || decoAlpha != previousAlpha) {
                    tessellator.draw();
                    GlStateManager.color(decoRed, decoGreen, decoBlue, decoAlpha);
                    bufferBuilder.begin(7, VertexFormats.POSITION);
                    previousRed = decoRed;
                    previousGreen = decoGreen;
                    previousBlue = decoBlue;
                    previousAlpha = decoAlpha;
                } else if (i == 0) {
                    GlStateManager.color(decoRed, decoGreen, decoBlue, decoAlpha);
                }

                float x1 = decorations[dataIndex];
                float y1 = decorations[dataIndex + 1];
                float x2 = decorations[dataIndex + 2];
                float y2 = decorations[dataIndex + 3];
                bufferBuilder.vertex(x1, y2, 0.0).next();
                bufferBuilder.vertex(x2, y2, 0.0).next();
                bufferBuilder.vertex(x2, y1, 0.0).next();
                bufferBuilder.vertex(x1, y1, 0.0).next();
            }

            tessellator.draw();
            GlStateManager.enableTexture();
        }
    }

    @Unique
    private int kalia$ensureGlyphPage(char c, int tableIndex, int currentPage, Tessellator tessellator, BufferBuilder bufferBuilder) {
        int page = (tableIndex != -1 && !this.unicode) ? -1 : c / 256;
        if (page == currentPage) {
            return currentPage;
        }

        if (currentPage != kalia$PAGE_NONE) {
            tessellator.draw();
        }

        if (page == -1) {
            this.textureManager.bindTexture(this.fontTexture);
        } else {
            this.textureManager.bindTexture(kalia$getFontPage(page));
        }

        bufferBuilder.begin(7, VertexFormats.POSITION_TEXTURE);
        return page;
    }

    @Unique
    private float kalia$addNormalGlyph(int index, boolean italic, BufferBuilder bufferBuilder) {
        int texU = (index % 16) * 8;
        int texV = (index / 16) * 8;
        int slant = italic ? 1 : 0;
        int width = this.characterWidths[index];
        float glyphWidth = (float) width - 0.01F;

        float u0 = (float) texU / 128.0F;
        float v0 = (float) texV / 128.0F;
        float u1 = ((float) texU + glyphWidth - 1.0F) / 128.0F;
        float v1 = ((float) texV + 7.99F) / 128.0F;

        bufferBuilder.vertex(this.x + (float) slant, this.y, 0.0).texture(u0, v0).next();
        bufferBuilder.vertex(this.x - (float) slant, this.y + 7.99, 0.0).texture(u0, v1).next();
        bufferBuilder.vertex(this.x + glyphWidth - 1.0F - (float) slant, this.y + 7.99, 0.0).texture(u1, v1).next();
        bufferBuilder.vertex(this.x + glyphWidth - 1.0F + (float) slant, this.y, 0.0).texture(u1, v0).next();

        return (float) width;
    }

    @Unique
    private float kalia$addUnicodeGlyph(char c, boolean italic, BufferBuilder bufferBuilder) {
        int rawStart = this.glyphWidths[c] >>> 4;
        int rawEnd = this.glyphWidths[c] & 15;
        float glyphStart = (float) rawStart;
        float glyphEnd = (float) (rawEnd + 1);
        float texX = (float) (c % 16 * 16) + glyphStart;
        float texY = (float) ((c & 255) / 16 * 16);
        float glyphWidth = glyphEnd - glyphStart - 0.02F;
        float slant = italic ? 1.0F : 0.0F;

        float u0 = texX / 256.0F;
        float v0 = texY / 256.0F;
        float u1 = (texX + glyphWidth) / 256.0F;
        float v1 = (texY + 15.98F) / 256.0F;

        bufferBuilder.vertex(this.x + slant, this.y, 0.0).texture(u0, v0).next();
        bufferBuilder.vertex(this.x - slant, this.y + 7.99, 0.0).texture(u0, v1).next();
        bufferBuilder.vertex(this.x + glyphWidth / 2.0F - slant, this.y + 7.99, 0.0).texture(u1, v1).next();
        bufferBuilder.vertex(this.x + glyphWidth / 2.0F + slant, this.y, 0.0).texture(u1, v0).next();

        return (glyphEnd - glyphStart) / 2.0F + 1.0F;
    }

    @Unique
    private static Identifier kalia$getFontPage(int page) {
        if (PAGES[page] == null) {
            PAGES[page] = new Identifier(String.format("textures/font/unicode_page_%02x.png", page));
        }
        return PAGES[page];
    }

    @Unique
    private static int kalia$formattingIndex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return switch (c) {
            case 'k', 'K' -> 16;
            case 'l', 'L' -> 17;
            case 'm', 'M' -> 18;
            case 'n', 'N' -> 19;
            case 'o', 'O' -> 20;
            case 'r', 'R' -> 21;
            default -> -1;
        };
    }
}