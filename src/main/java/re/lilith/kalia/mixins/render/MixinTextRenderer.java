package re.lilith.kalia.mixins.render;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.draw.KaliaDraw;
import re.lilith.kalia.draw.TextMeshCache;
import re.lilith.kalia.gl.GlBridge;
import re.lilith.kalia.gl.MatrixState;
import re.lilith.kalia.gl.ShaderUniforms;
import re.lilith.kalia.vertex.TranslatedVertexFormat;
import re.lilith.kalia.vertex.VertexFormatBridge;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    @Unique // for fast lookup
    private static final String sulfide$TABLE =
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
    private static final int[] sulfide$CHAR_INDEX = new int[65536];

    static {
        Arrays.fill(sulfide$CHAR_INDEX, -1);
        for (int i = sulfide$TABLE.length() - 1; i >= 0; i--) {
            sulfide$CHAR_INDEX[sulfide$TABLE.charAt(i)] = i;
        }
    }

    // Sized to match TextMeshCache.MAX_ENTRIES so any string with a cached mesh also
    // keeps a cached width (nametags, chat, scoreboard all measure per frame).
    @Unique
    private static final int sulfide$WIDTH_CACHE_ENTRIES = 2048;

    @Unique
    private final Object2IntLinkedOpenHashMap<String> sulfide$widthCache =
            new Object2IntLinkedOpenHashMap<>(sulfide$WIDTH_CACHE_ENTRIES);

    @Unique
    private static final int sulfide$PAGE_NONE = Integer.MIN_VALUE;

    @Unique
    private static final int sulfide$VERTEX_BYTES = 24; // pos 3f + uv 2f + rgba 4ub

    // Bake scratch: glyph vertices are written here first, then each page run is
    // copied into an exact-size buffer owned by the cache entry.
    @Unique
    private static ByteBuffer sulfide$scratch = MemoryUtil.memAlloc(1 << 20);

    @Unique
    private int sulfide$segPage = sulfide$PAGE_NONE;
    @Unique
    private int sulfide$segStartByte;
    @Unique
    private final List<TextMeshCache.Segment> sulfide$segments = new ArrayList<>();

    @Unique
    private static final int sulfide$DECO_STRIDE = 8;
    @Unique
    private static final int sulfide$MAX_DECOS = 256;
    @Unique
    private final float[] sulfide$decoData = new float[sulfide$MAX_DECOS * sulfide$DECO_STRIDE];

    @Inject(method = "reload", at = @At("HEAD"))
    private void sulfide$onReload(ResourceManager mgr, CallbackInfo ci) {
        sulfide$widthCache.clear();
        TextMeshCache.clear();
//        SignTextCache.clear();
    }

    @Inject(method = "setUnicode", at = @At("HEAD"))
    private void sulfide$onSetUnicode(boolean unicode, CallbackInfo ci) {
        sulfide$widthCache.clear();
        TextMeshCache.clear();
//        SignTextCache.clear();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sulfide$initWidthCache(CallbackInfo ci) {
        sulfide$widthCache.defaultReturnValue(Integer.MIN_VALUE);
    }

    /**
     * @reason Cached width computaton
     * @author Lunasa
     */
    @Overwrite
    public int getCharWidth(char character) {
        if (character == 167) return -1;
        if (character == ' ') return 4;

        int i = sulfide$CHAR_INDEX[character];
        if (character > 0 && i != -1 && !this.unicode) {
            return this.characterWidths[i];
        }
        if (this.glyphWidths[character] != 0) {
            int j = this.glyphWidths[character] >>> 4;
            int k = this.glyphWidths[character] & 15;
            if (k > 7) {
                k = 15;
                j = 0;
            }
            ++k;
            return (k - j) / 2 + 1;
        }
        return 0;
    }

    /**
     * @reason Cached width computaton
     * @author Lunasa
     */
    @Overwrite
    public int getStringWidth(String text) {
        if (text == null) return 0;

        int cached = sulfide$widthCache.getAndMoveToFirst(text);
        if (cached != Integer.MIN_VALUE) return cached;

        int width = 0;
        boolean bl = false;

        for (int j = 0; j < text.length(); ++j) {
            char c = text.charAt(j);
            int k = this.getCharWidth(c);
            if (k < 0 && j < text.length() - 1) {
                ++j;
                c = text.charAt(j);
                if (c == 'l' || c == 'L') {
                    bl = true;
                } else if (c == 'r' || c == 'R') {
                    bl = false;
                }
                k = 0;
            }
            width += k;
            if (bl && k > 0) ++width;
        }

        sulfide$widthCache.putAndMoveToFirst(text, width);
        if (sulfide$widthCache.size() > sulfide$WIDTH_CACHE_ENTRIES) {
            sulfide$widthCache.removeLastInt();
        }
        return width;
    }

    /**
     * @reason Cached-mesh text rendering: each unique string is tessellated once into
     * per-page vertex buffers (colors and formatting codes baked per vertex) and
     * replayed with one arena memcpy + one draw per segment on later frames.
     * @author Lunasa
     */
    @Overwrite
    private void draw(String text, boolean shadow) {
        if (text.isEmpty()) {
            return;
        }

        // Alpha is decoupled from the baked mesh: vertices bake fully opaque and the
        // string's alpha is applied through the shader color at draw time. Meshes and
        // cache entries are therefore shared across alpha variants — nametags draw the
        // same string at alpha 32 (through-wall pass) and 255 every frame.
        int opaqueColor = sulfide$packColor(this.red, this.green, this.blue, 1.0F);
        int styleBits = (this.bold ? 1 : 0)
                | (this.italic ? 2 : 0)
                | (this.underline ? 4 : 0)
                | (this.strikethrough ? 8 : 0)
                | (this.obfuscated ? 16 : 0);
        // Obfuscated (magic) text is re-randomised every frame - never cache it.
        boolean cacheable = !this.obfuscated && !sulfide$containsObfuscationCode(text);

        TextMeshCache.CachedText cached = null;
        if (cacheable) {
            cached = TextMeshCache.find(text, shadow, opaqueColor, this.unicode, styleBits);
        }
        if (cached == null) {
            cached = sulfide$bake(text, shadow, opaqueColor);
            if (cacheable) {
                TextMeshCache.put(text, shadow, opaqueColor, this.unicode, styleBits, cached);
            }
        }

        sulfide$drawCached(cached);
        this.x += cached.advance;

        if (!cacheable) {
            cached.free();
        }
    }

    @Unique
    private static String sulfide$guiPipelineKey;
    @Unique
    private static String sulfide$worldPipelineKey;

    @Unique
    private void sulfide$drawCached(TextMeshCache.CachedText cached) {
        TextMeshCache.Segment[] segments = cached.segments;
        if (segments.length == 0) {
            return;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, this.alpha);

        MatrixState.INSTANCE.flush();
        var uniforms = ShaderUniforms.INSTANCE;

        float baseX = uniforms.modelOffsetX();
        float baseY = uniforms.modelOffsetY();
        float baseZ = uniforms.modelOffsetZ();
        uniforms.setModelOffset(baseX + this.x, baseY + this.y, baseZ);

        for (TextMeshCache.Segment segment : segments) {
            boolean decoration = segment.page == TextMeshCache.PAGE_DECORATION;
            if (decoration) {
                GlStateManager.disableTexture();
            } else if (segment.page == TextMeshCache.PAGE_DEFAULT) {
                this.textureManager.bindTexture(this.fontTexture);
            } else {
                this.textureManager.bindTexture(sulfide$getFontPage(segment.page));
            }

            ByteBuffer vertexData = segment.vertexData;
            vertexData.position(0);
            vertexData.limit(segment.vertexCount * sulfide$VERTEX_BYTES);

            KaliaDraw.INSTANCE.drawTransient(
                    vertexData,
                    VertexFormatBridge.INSTANCE.translate(VertexFormats.POSITION_TEXTURE_COLOR),
                    7,
                    segment.vertexCount
            );

            if (decoration) {
                GlStateManager.enableTexture();
            }
        }

        uniforms.setModelOffset(baseX, baseY, baseZ);
    }

    @Unique
    private TextMeshCache.CachedText sulfide$bake(String text, boolean shadow, int baseColor) {
        // Worst case: every char bold (2 quads) plus decorations.
        int worstBytes = (text.length() * 8 + sulfide$MAX_DECOS * 4) * sulfide$VERTEX_BYTES;
        if (sulfide$scratch.capacity() < worstBytes) {
            sulfide$scratch = MemoryUtil.memRealloc(sulfide$scratch, Integer.highestOneBit(worstBytes) * 2);
        }
        ByteBuffer scratch = sulfide$scratch;
        scratch.clear();

        sulfide$segments.clear();
        sulfide$segPage = sulfide$PAGE_NONE;
        sulfide$segStartByte = 0;
        int decoCount = 0;

        float relX = 0.0F;
        int rgba = baseColor;

        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);

            if (c == 167 && i + 1 < text.length()) {
                int j = sulfide$formattingIndex(text.charAt(i + 1));

                if (j < 16) {
                    this.obfuscated = false;
                    this.bold = false;
                    this.strikethrough = false;
                    this.underline = false;
                    this.italic = false;
                    if (j < 0) j = 15;
                    if (shadow) j += 16;
                    int k = this.colorCodes[j];
                    this.color = k;
                    rgba = sulfide$packColor(
                            (float) (k >> 16 & 255) / 255.0F,
                            (float) (k >> 8 & 255) / 255.0F,
                            (float) (k & 255) / 255.0F,
                            1.0F
                    );
                } else if (j == 16) {
                    this.obfuscated = true;
                } else if (j == 17) {
                    this.bold = true;
                } else if (j == 18) {
                    this.strikethrough = true;
                } else if (j == 19) {
                    this.underline = true;
                } else if (j == 20) {
                    this.italic = true;
                } else if (j == 21) {
                    this.obfuscated = false;
                    this.bold = false;
                    this.strikethrough = false;
                    this.underline = false;
                    this.italic = false;
                    rgba = baseColor;
                }
                ++i;
                continue;
            }

            int j = sulfide$CHAR_INDEX[c];

            if (this.obfuscated && j != -1) {
                int charW = this.getCharWidth(c);
                char d;
                do {
                    d = sulfide$TABLE.charAt(this.random.nextInt(sulfide$TABLE.length()));
                } while (charW != this.getCharWidth(d));
                c = d;
                j = sulfide$CHAR_INDEX[c];
            }

            float f = this.unicode ? 0.5F : 1.0F;
            boolean shifted = (c == 0 || j == -1 || this.unicode) && shadow;
            float shift = shifted ? -f : 0.0F;

            float g = sulfide$bakeGlyph(scratch, c, j, this.italic, relX + shift, shift, rgba);

            // bold: bake a second copy offset by 1
            if (this.bold) {
                sulfide$bakeGlyph(scratch, c, j, this.italic, relX + shift + f, shift, rgba);
                ++g;
            }

            if (this.strikethrough && decoCount < sulfide$MAX_DECOS) {
                int di = decoCount++ * sulfide$DECO_STRIDE;
                float yc = (float) (this.fontHeight / 2);
                sulfide$decoData[di] = relX;
                sulfide$decoData[di + 1] = yc - 1.0F;
                sulfide$decoData[di + 2] = relX + g;
                sulfide$decoData[di + 3] = yc;
                sulfide$decoData[di + 4] = Float.intBitsToFloat(rgba);
            }
            if (this.underline && decoCount < sulfide$MAX_DECOS) {
                int di = decoCount++ * sulfide$DECO_STRIDE;
                float yb = (float) this.fontHeight;
                sulfide$decoData[di] = relX - 1.0F;
                sulfide$decoData[di + 1] = yb - 1.0F;
                sulfide$decoData[di + 2] = relX + g;
                sulfide$decoData[di + 3] = yb;
                sulfide$decoData[di + 4] = Float.intBitsToFloat(rgba);
            }

            relX += (float) ((int) g);
        }

        sulfide$closeSegment(scratch);

        if (decoCount > 0) {
            sulfide$segPage = TextMeshCache.PAGE_DECORATION;
            sulfide$segStartByte = scratch.position();
            for (int d = 0; d < decoCount; d++) {
                int di = d * sulfide$DECO_STRIDE;
                float x1 = sulfide$decoData[di];
                float y1 = sulfide$decoData[di + 1];
                float x2 = sulfide$decoData[di + 2];
                float y2 = sulfide$decoData[di + 3];
                int decoRgba = Float.floatToRawIntBits(sulfide$decoData[di + 4]);
                sulfide$vertex(scratch, x1, y2, 0.0F, 0.0F, decoRgba);
                sulfide$vertex(scratch, x2, y2, 0.0F, 0.0F, decoRgba);
                sulfide$vertex(scratch, x2, y1, 0.0F, 0.0F, decoRgba);
                sulfide$vertex(scratch, x1, y1, 0.0F, 0.0F, decoRgba);
            }
            sulfide$closeSegment(scratch);
        }

        TextMeshCache.Segment[] segments = sulfide$segments.toArray(new TextMeshCache.Segment[0]);
        sulfide$segments.clear();
        return new TextMeshCache.CachedText(segments, relX);
    }

    @Unique
    private float sulfide$bakeGlyph(ByteBuffer scratch, char c, int tableIdx, boolean italic,
                                    float relX, float shiftY, int rgba) {
        if (c == ' ') return 4.0F;

        if (tableIdx != -1 && !this.unicode) {
            sulfide$ensureSegment(scratch, TextMeshCache.PAGE_DEFAULT);

            int texU = (tableIdx % 16) * 8;
            int texV = (tableIdx / 16) * 8;
            int slant = italic ? 1 : 0;
            int w = this.characterWidths[tableIdx];
            float fw = (float) w - 0.01F;

            float u0 = (float) texU / 128.0F;
            float v0 = (float) texV / 128.0F;
            float u1 = ((float) texU + fw - 1.0F) / 128.0F;
            float v1 = ((float) texV + 7.99F) / 128.0F;

            sulfide$vertex(scratch, relX + (float) slant, shiftY, u0, v0, rgba);
            sulfide$vertex(scratch, relX - (float) slant, shiftY + 7.99F, u0, v1, rgba);
            sulfide$vertex(scratch, relX + fw - 1.0F - (float) slant, shiftY + 7.99F, u1, v1, rgba);
            sulfide$vertex(scratch, relX + fw - 1.0F + (float) slant, shiftY, u1, v0, rgba);

            return (float) w;
        } else {
            if (this.glyphWidths[c] == 0) return 0.0F;
            sulfide$ensureSegment(scratch, c / 256);

            int rawStart = this.glyphWidths[c] >>> 4;
            int rawEnd = this.glyphWidths[c] & 15;
            float gStart = (float) rawStart;
            float gEnd = (float) (rawEnd + 1);
            float texX = (float) (c % 16 * 16) + gStart;
            float texY = (float) ((c & 255) / 16 * 16);
            float glyphW = gEnd - gStart - 0.02F;
            float slant = italic ? 1.0F : 0.0F;

            float u0 = texX / 256.0F;
            float v0 = texY / 256.0F;
            float u1 = (texX + glyphW) / 256.0F;
            float v1 = (texY + 15.98F) / 256.0F;

            sulfide$vertex(scratch, relX + slant, shiftY, u0, v0, rgba);
            sulfide$vertex(scratch, relX - slant, shiftY + 7.99F, u0, v1, rgba);
            sulfide$vertex(scratch, relX + glyphW / 2.0F - slant, shiftY + 7.99F, u1, v1, rgba);
            sulfide$vertex(scratch, relX + glyphW / 2.0F + slant, shiftY, u1, v0, rgba);

            return (gEnd - gStart) / 2.0F + 1.0F;
        }
    }

    @Unique
    private void sulfide$ensureSegment(ByteBuffer scratch, int page) {
        if (sulfide$segPage != page) {
            sulfide$closeSegment(scratch);
            sulfide$segPage = page;
            sulfide$segStartByte = scratch.position();
        }
    }

    @Unique
    private void sulfide$closeSegment(ByteBuffer scratch) {
        int endByte = scratch.position();
        int length = endByte - sulfide$segStartByte;
        if (sulfide$segPage != sulfide$PAGE_NONE && length > 0) {
            ByteBuffer vertexData = TextMeshCache.allocSegmentBuffer(length);
            MemoryUtil.memCopy(
                    MemoryUtil.memAddress0(scratch) + sulfide$segStartByte,
                    MemoryUtil.memAddress0(vertexData),
                    length
            );
            sulfide$segments.add(new TextMeshCache.Segment(
                    sulfide$segPage,
                    vertexData,
                    length / sulfide$VERTEX_BYTES
            ));
        }
        sulfide$segPage = sulfide$PAGE_NONE;
        sulfide$segStartByte = endByte;
    }

    @Unique
    private static void sulfide$vertex(ByteBuffer buffer, float x, float y, float u, float v, int rgba) {
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(0.0F);
        buffer.putFloat(u);
        buffer.putFloat(v);
        buffer.put((byte) (rgba >> 24 & 255));
        buffer.put((byte) (rgba >> 16 & 255));
        buffer.put((byte) (rgba >> 8 & 255));
        buffer.put((byte) (rgba & 255));
    }

    @Unique
    private static int sulfide$packColor(float r, float g, float b, float a) {
        return ((int) (r * 255.0F) & 255) << 24
                | ((int) (g * 255.0F) & 255) << 16
                | ((int) (b * 255.0F) & 255) << 8
                | ((int) (a * 255.0F) & 255);
    }

    @Unique
    private static boolean sulfide$containsObfuscationCode(String text) {
        int index = text.indexOf(167);
        while (index >= 0 && index + 1 < text.length()) {
            char next = text.charAt(index + 1);
            if (next == 'k' || next == 'K') {
                return true;
            }
            index = text.indexOf(167, index + 2);
        }
        return false;
    }

    @Unique
    private static Identifier sulfide$getFontPage(int page) {
        if (PAGES[page] == null) {
            PAGES[page] = new Identifier(String.format("textures/font/unicode_page_%02x.png", page));
        }
        return PAGES[page];
    }

    @Unique
    private static int sulfide$formattingIndex(char c) {
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