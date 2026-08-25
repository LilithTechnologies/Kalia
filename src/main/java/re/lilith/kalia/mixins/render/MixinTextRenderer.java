package re.lilith.kalia.mixins.render;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.frame.draw.KaliaDraw;
import re.lilith.kalia.draw.NametagTextRenderer;
import re.lilith.kalia.draw.TextObfuscation;
import re.lilith.kalia.frame.draw.TextMeshCache;
import re.lilith.kalia.frame.graph.entity.nametag.NametagBatcher;
import re.lilith.kalia.gl.MatrixState;
import re.lilith.kalia.gl.ShaderUniforms;
import re.lilith.kalia.vertex.VertexFormatBridge;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Mixin(TextRenderer.class)
public class MixinTextRenderer implements NametagTextRenderer {
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

    static {
        Arrays.fill(kalia$CHAR_INDEX, -1);
        for (int i = kalia$TABLE.length() - 1; i >= 0; i--) {
            kalia$CHAR_INDEX[kalia$TABLE.charAt(i)] = i;
        }
    }

    @Unique
    private static final int kalia$WIDTH_CACHE_ENTRIES = 2048;

    @Unique
    private final Object2IntLinkedOpenHashMap<String> kalia$widthCache =
            new Object2IntLinkedOpenHashMap<>(kalia$WIDTH_CACHE_ENTRIES);

    @Unique
    private static final int kalia$PAGE_NONE = Integer.MIN_VALUE;

    @Unique
    private static final int kalia$VERTEX_BYTES = 24; // pos 3f + uv 2f + rgba 4ub

    @Unique
    private static ByteBuffer kalia$scratch = MemoryUtil.memAlloc(1 << 20);

    @Unique
    private int kalia$segPage = kalia$PAGE_NONE;
    @Unique
    private int kalia$segStartByte;
    @Unique
    private int kalia$segQuads;

    @Unique
    private int kalia$bakeOutputs = TextMeshCache.OUT_MESH | TextMeshCache.OUT_INSTANCES;
    @Unique
    private final List<TextMeshCache.Segment> kalia$segments = new ArrayList<>();

    @Unique
    private final FloatArrayList kalia$instanceScratch = new FloatArrayList();
    @Unique
    private int kalia$instanceSegStart;

    @Unique
    private static final int kalia$DECO_STRIDE = 8;
    @Unique
    private static final int kalia$MAX_DECOS = 256;
    @Unique
    private final float[] kalia$decoData = new float[kalia$MAX_DECOS * kalia$DECO_STRIDE];

    @Inject(method = "reload", at = @At("HEAD"))
    private void kalia$onReload(ResourceManager mgr, CallbackInfo ci) {
        kalia$widthCache.clear();
        TextMeshCache.clear();
//        SignTextCache.clear();
    }

    @Inject(method = "setUnicode", at = @At("HEAD"))
    private void kalia$onSetUnicode(boolean unicode, CallbackInfo ci) {
        kalia$widthCache.clear();
        TextMeshCache.clear();
//        SignTextCache.clear();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kalia$initWidthCache(CallbackInfo ci) {
        kalia$widthCache.defaultReturnValue(Integer.MIN_VALUE);
    }

    /**
     * @reason Cached width computaton
     * @author Lunasa
     */
    @Overwrite
    public int getCharWidth(char character) {
        if (character == 167) return -1;
        if (character == ' ') return 4;

        int i = kalia$CHAR_INDEX[character];
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

        int cached = kalia$widthCache.getAndMoveToFirst(text);
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

        kalia$widthCache.putAndMoveToFirst(text, width);
        if (kalia$widthCache.size() > kalia$WIDTH_CACHE_ENTRIES) {
            kalia$widthCache.removeLastInt();
        }
        return width;
    }

    /**
     * @reason Cache text meshes
     * @author Lunasa
     */
    @Overwrite
    private void draw(String text, boolean shadow) {
        if (text.isEmpty()) {
            return;
        }

        int opaqueColor = kalia$packColor(this.red, this.green, this.blue, 1.0F);
        int styleBits = (this.bold ? 1 : 0)
                | (this.italic ? 2 : 0)
                | (this.underline ? 4 : 0)
                | (this.strikethrough ? 8 : 0)
                | (this.obfuscated ? 16 : 0);
        TextMeshCache.CachedText cached = this.obfuscated
                ? null
                : TextMeshCache.find(text, shadow, opaqueColor, this.unicode, styleBits, TextMeshCache.OUT_MESH);
        boolean owned = false;
        if (cached == null) {
            boolean cacheable = !this.obfuscated && !TextObfuscation.contains(text);
            cached = kalia$bake(text, shadow, opaqueColor, TextMeshCache.OUT_MESH);
            if (cacheable) {
                TextMeshCache.put(text, shadow, opaqueColor, this.unicode, styleBits, TextMeshCache.OUT_MESH, cached);
            } else {
                owned = true;
            }
        }

        kalia$drawCached(cached);
        this.x += cached.advance;

        if (owned) {
            cached.free();
        }
    }

    @Unique
    private void kalia$drawCached(TextMeshCache.CachedText cached) {
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
                this.textureManager.bindTexture(kalia$getFontPage(segment.page));
            }

            ByteBuffer vertexData = segment.vertexData;

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

    @Override
    @Unique
    public void kalia$drawNametag(String text, float x, float y, int argb) {
        if (text.isEmpty()) {
            return;
        }
        if ((argb & 0xFF000000) == 0) {
            argb |= 0xFF000000;
        }

        float r = (float) (argb >> 16 & 255) / 255.0F;
        float g = (float) (argb >> 8 & 255) / 255.0F;
        float b = (float) (argb & 255) / 255.0F;
        float alpha = (float) (argb >>> 24) / 255.0F;
        int opaqueColor = kalia$packColor(r, g, b, 1.0F);

        TextMeshCache.CachedText cached =
                TextMeshCache.find(text, false, opaqueColor, this.unicode, 0, TextMeshCache.OUT_INSTANCES);
        boolean owned = false;
        if (cached == null) {
            boolean cacheable = !kalia$containsObfuscationCode(text);
            cached = kalia$bake(text, false, opaqueColor, TextMeshCache.OUT_INSTANCES);
            if (cacheable) {
                TextMeshCache.put(text, false, opaqueColor, this.unicode, 0, TextMeshCache.OUT_INSTANCES, cached);
            } else {
                owned = true;
            }
        }

        kalia$drawCachedInstanced(cached, x, y, alpha);

        if (owned) {
            cached.free();
        }
    }

    @Unique
    private void kalia$drawCachedInstanced(TextMeshCache.CachedText cached, float baseX, float baseY, float alpha) {
        TextMeshCache.Segment[] segments = cached.segments;
        if (segments.length == 0) {
            return;
        }

        MatrixState.INSTANCE.flush();
        Matrix4f modelView = MatrixState.INSTANCE.modelView();
        int alphaByte = (int) (255.0F * alpha + 0.5F);

        for (TextMeshCache.Segment segment : segments) {
            boolean decoration = segment.page == TextMeshCache.PAGE_DECORATION;
            if (decoration) {
                GlStateManager.disableTexture();
            } else if (segment.page == TextMeshCache.PAGE_DEFAULT) {
                this.textureManager.bindTexture(this.fontTexture);
            } else {
                this.textureManager.bindTexture(kalia$getFontPage(segment.page));
            }

            NametagBatcher.INSTANCE.beginSegment();
            NametagBatcher.INSTANCE.recordGlyphs(modelView, segment.instanceData, baseX, baseY, alphaByte);

            if (decoration) {
                GlStateManager.enableTexture();
            }
        }
    }

    @Unique
    private TextMeshCache.CachedText kalia$bake(String text, boolean shadow, int baseColor, int outputs) {
        kalia$bakeOutputs = outputs;
        ByteBuffer scratch = kalia$scratch;
        if ((outputs & TextMeshCache.OUT_MESH) != 0) {
            int worstBytes = (text.length() * 8 + kalia$MAX_DECOS * 4) * kalia$VERTEX_BYTES;
            if (scratch.capacity() < worstBytes) {
                scratch = MemoryUtil.memRealloc(scratch, Integer.highestOneBit(worstBytes) * 2);
                kalia$scratch = scratch;
            }
            scratch.clear();
        }

        kalia$segments.clear();
        kalia$segPage = kalia$PAGE_NONE;
        kalia$segStartByte = 0;
        kalia$segQuads = 0;
        kalia$instanceScratch.clear();
        kalia$instanceSegStart = 0;
        int decoCount = 0;

        this.obfuscated = false;
        this.bold = false;
        this.strikethrough = false;
        this.underline = false;
        this.italic = false;

        float relX = 0.0F;
        int rgba = baseColor;

        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);

            if (c == 167 && i + 1 < text.length()) {
                int j = kalia$formattingIndex(text.charAt(i + 1));

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
                    rgba = kalia$packColor(
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

            int j = kalia$CHAR_INDEX[c];

            if (this.obfuscated && j != -1) {
                int charW = this.getCharWidth(c);
                char d;
                do {
                    d = kalia$TABLE.charAt(this.random.nextInt(kalia$TABLE.length()));
                } while (charW != this.getCharWidth(d));
                c = d;
                j = kalia$CHAR_INDEX[c];
            }

            float f = this.unicode ? 0.5F : 1.0F;
            boolean shifted = (c == 0 || j == -1 || this.unicode) && shadow;
            float shift = shifted ? -f : 0.0F;

            float g = kalia$bakeGlyph(scratch, c, j, this.italic, relX + shift, shift, rgba);

            // bold: bake a second copy offset by 1
            if (this.bold) {
                kalia$bakeGlyph(scratch, c, j, this.italic, relX + shift + f, shift, rgba);
                ++g;
            }

            if (this.strikethrough && decoCount < kalia$MAX_DECOS) {
                int di = decoCount++ * kalia$DECO_STRIDE;
                float yc = (float) (this.fontHeight / 2);
                kalia$decoData[di] = relX;
                kalia$decoData[di + 1] = yc - 1.0F;
                kalia$decoData[di + 2] = relX + g;
                kalia$decoData[di + 3] = yc;
                kalia$decoData[di + 4] = Float.intBitsToFloat(rgba);
            }
            if (this.underline && decoCount < kalia$MAX_DECOS) {
                int di = decoCount++ * kalia$DECO_STRIDE;
                float yb = (float) this.fontHeight;
                kalia$decoData[di] = relX - 1.0F;
                kalia$decoData[di + 1] = yb - 1.0F;
                kalia$decoData[di + 2] = relX + g;
                kalia$decoData[di + 3] = yb;
                kalia$decoData[di + 4] = Float.intBitsToFloat(rgba);
            }

            relX += (float) ((int) g);
        }

        kalia$closeSegment(scratch);

        if (decoCount > 0) {
            kalia$segPage = TextMeshCache.PAGE_DECORATION;
            kalia$segStartByte = (outputs & TextMeshCache.OUT_MESH) != 0 ? scratch.position() : 0;
            for (int d = 0; d < decoCount; d++) {
                int di = d * kalia$DECO_STRIDE;
                float x1 = kalia$decoData[di];
                float y1 = kalia$decoData[di + 1];
                float x2 = kalia$decoData[di + 2];
                float y2 = kalia$decoData[di + 3];
                int decoRgba = Float.floatToRawIntBits(kalia$decoData[di + 4]);
                if ((outputs & TextMeshCache.OUT_MESH) != 0) {
                    kalia$vertex(scratch, x1, y2, 0.0F, 0.0F, decoRgba);
                    kalia$vertex(scratch, x2, y2, 0.0F, 0.0F, decoRgba);
                    kalia$vertex(scratch, x2, y1, 0.0F, 0.0F, decoRgba);
                    kalia$vertex(scratch, x1, y1, 0.0F, 0.0F, decoRgba);
                }
                if ((outputs & TextMeshCache.OUT_INSTANCES) != 0) {
                    kalia$pushInstance(x1, y2, x2, y1, 0.0F, 0.0F, 0.0F, 0.0F, decoRgba);
                }
                kalia$segQuads++;
            }
            kalia$closeSegment(scratch);
        }

        TextMeshCache.Segment[] segments = kalia$segments.toArray(new TextMeshCache.Segment[0]);
        kalia$segments.clear();
        return new TextMeshCache.CachedText(segments, relX);
    }

    @Unique
    private float kalia$bakeGlyph(ByteBuffer scratch, char c, int tableIdx, boolean italic,
                                    float relX, float shiftY, int rgba) {
        if (c == ' ') return 4.0F;

        if (tableIdx != -1 && !this.unicode) {
            kalia$ensureSegment(scratch, TextMeshCache.PAGE_DEFAULT);

            int texU = (tableIdx % 16) * 8;
            int texV = (tableIdx / 16) * 8;
            int slant = italic ? 1 : 0;
            int w = this.characterWidths[tableIdx];
            float fw = (float) w - 0.01F;

            float u0 = (float) texU / 128.0F;
            float v0 = (float) texV / 128.0F;
            float u1 = ((float) texU + fw - 1.0F) / 128.0F;
            float v1 = ((float) texV + 7.99F) / 128.0F;

            if ((kalia$bakeOutputs & TextMeshCache.OUT_MESH) != 0) {
                kalia$vertex(scratch, relX + (float) slant, shiftY, u0, v0, rgba);
                kalia$vertex(scratch, relX - (float) slant, shiftY + 7.99F, u0, v1, rgba);
                kalia$vertex(scratch, relX + fw - 1.0F - (float) slant, shiftY + 7.99F, u1, v1, rgba);
                kalia$vertex(scratch, relX + fw - 1.0F + (float) slant, shiftY, u1, v0, rgba);
            }
            if ((kalia$bakeOutputs & TextMeshCache.OUT_INSTANCES) != 0) {
                kalia$pushInstance(relX, shiftY, relX + fw - 1.0F, shiftY + 7.99F, u0, v0, u1, v1, rgba);
            }
            kalia$segQuads++;

            return (float) w;
        } else {
            if (this.glyphWidths[c] == 0) return 0.0F;
            kalia$ensureSegment(scratch, c / 256);

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

            if ((kalia$bakeOutputs & TextMeshCache.OUT_MESH) != 0) {
                kalia$vertex(scratch, relX + slant, shiftY, u0, v0, rgba);
                kalia$vertex(scratch, relX - slant, shiftY + 7.99F, u0, v1, rgba);
                kalia$vertex(scratch, relX + glyphW / 2.0F - slant, shiftY + 7.99F, u1, v1, rgba);
                kalia$vertex(scratch, relX + glyphW / 2.0F + slant, shiftY, u1, v0, rgba);
            }
            if ((kalia$bakeOutputs & TextMeshCache.OUT_INSTANCES) != 0) {
                kalia$pushInstance(relX, shiftY, relX + glyphW / 2.0F, shiftY + 7.99F, u0, v0, u1, v1, rgba);
            }
            kalia$segQuads++;

            return (gEnd - gStart) / 2.0F + 1.0F;
        }
    }

    @Unique
    private void kalia$ensureSegment(ByteBuffer scratch, int page) {
        if (kalia$segPage != page) {
            kalia$closeSegment(scratch);
            kalia$segPage = page;
            kalia$segStartByte = (kalia$bakeOutputs & TextMeshCache.OUT_MESH) != 0 ? scratch.position() : 0;
        }
    }

    @Unique
    private void kalia$closeSegment(ByteBuffer scratch) {
        int endByte = (kalia$bakeOutputs & TextMeshCache.OUT_MESH) != 0 ? scratch.position() : 0;
        if (kalia$segPage != kalia$PAGE_NONE && kalia$segQuads > 0) {
            ByteBuffer cachedBuffer = null;
            int vertexCount = 0;

            if ((kalia$bakeOutputs & TextMeshCache.OUT_MESH) != 0) {
                int length = endByte - kalia$segStartByte;
                ByteBuffer vertexData = TextMeshCache.allocSegmentBuffer(length);

                MemoryUtil.memCopy(
                        MemoryUtil.memAddress0(scratch) + kalia$segStartByte,
                        MemoryUtil.memAddress0(vertexData),
                        length
                );

                vertexData.position(0);
                vertexData.limit(length);

                cachedBuffer = vertexData.asReadOnlyBuffer();
                vertexCount = length / kalia$VERTEX_BYTES;
            }

            float[] instanceData = TextMeshCache.NO_INSTANCES;
            if ((kalia$bakeOutputs & TextMeshCache.OUT_INSTANCES) != 0) {
                instanceData = Arrays.copyOfRange(
                        kalia$instanceScratch.elements(), kalia$instanceSegStart, kalia$instanceScratch.size());
            }

            kalia$segments.add(new TextMeshCache.Segment(
                    kalia$segPage, cachedBuffer, vertexCount, instanceData));
        }
        kalia$segPage = kalia$PAGE_NONE;
        kalia$segStartByte = endByte;
        kalia$segQuads = 0;
        kalia$instanceSegStart = kalia$instanceScratch.size();
    }

    @Unique
    private void kalia$pushInstance(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int rgba) {
        kalia$instanceScratch.add(x0);
        kalia$instanceScratch.add(y0);
        kalia$instanceScratch.add(x1);
        kalia$instanceScratch.add(y1);
        kalia$instanceScratch.add(u0);
        kalia$instanceScratch.add(v0);
        kalia$instanceScratch.add(u1);
        kalia$instanceScratch.add(v1);
        kalia$instanceScratch.add(Float.intBitsToFloat(rgba));
    }

    @Unique
    private static void kalia$vertex(ByteBuffer buffer, float x, float y, float u, float v, int rgba) {
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
    private static int kalia$packColor(float r, float g, float b, float a) {
        return ((int) (r * 255.0F) & 255) << 24
                | ((int) (g * 255.0F) & 255) << 16
                | ((int) (b * 255.0F) & 255) << 8
                | ((int) (a * 255.0F) & 255);
    }

    @Unique
    private static boolean kalia$containsObfuscationCode(String text) {
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