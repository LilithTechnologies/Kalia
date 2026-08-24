package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.renderer.utility.MemoryAccess;
import sun.misc.Unsafe;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.BitSet;

@Mixin(BufferBuilder.class)
public abstract class MixinBufferBuilder {
    @Shadow
    public ByteBuffer buffer;
    @Shadow
    private IntBuffer intBuffer;
    @Shadow
    private int vertexCount;
    @Shadow
    private VertexFormatElement currentElement;
    @Shadow
    private int currentElementId;
    @Shadow
    private boolean textured;
    @Shadow
    private double offsetX;
    @Shadow
    private double offsetY;
    @Shadow
    private double offsetZ;
    @Shadow
    private VertexFormat format;

    @Shadow
    private void grow(int size) {
    }

    @Unique
    private VertexFormat sulfide$layoutFormat;
    @Unique
    private int[] sulfide$offsets;
    @Unique
    private VertexFormatElement[] sulfide$elements;
    @Unique
    private int[] sulfide$nextElement;
    @Unique
    private int sulfide$stride;

    @Unique
    private void sulfide$refreshLayout() {
        VertexFormat active = format;
        int count = active.getSize();

        int[] offsets = new int[count];
        VertexFormatElement[] elements = new VertexFormatElement[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = active.getIndex(i);
            elements[i] = active.get(i);
        }

        int[] next = new int[count];
        for (int i = 0; i < count; i++) {
            int candidate = i;
            for (int step = 0; step < count; step++) {
                candidate = (candidate + 1) % count;
                if (elements[candidate].getType() != VertexFormatElement.Type.PADDING) {
                    break;
                }
            }
            next[i] = candidate;
        }

        sulfide$offsets = offsets;
        sulfide$elements = elements;
        sulfide$nextElement = next;
        sulfide$stride = active.getVertexSize();
        sulfide$layoutFormat = active;
    }

    /**
     * @reason Advance through a cached layout instead of re-reading boxed offsets from the format
     * @author Lunasa
     */
    @Overwrite
    private void nextElement() {
        if (format != sulfide$layoutFormat) {
            sulfide$refreshLayout();
        }
        int next = sulfide$nextElement[currentElementId];
        currentElementId = next;
        currentElement = sulfide$elements[next];
    }

    @Unique
    private static final long sulfide$BUF_ADDR_OFF;

    static {
        try {
            sulfide$BUF_ADDR_OFF = MemoryAccess.objectFieldOffset(
                    Buffer.class.getDeclaredField("address")
            );
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Unique
    private long sulfide$addr;

    @Unique
    private float[] sulfide$distances;
    @Unique
    private long[] sulfide$sortKeys;
    @Unique
    private int[] sulfide$sortOrder;
    @Unique
    private int[] sulfide$quadScratch;
    @Unique
    private final BitSet sulfide$sorted = new BitSet();

    @Unique
    private void sulfide$refreshAddr() {
        sulfide$addr = MemoryAccess.getLong(buffer, sulfide$BUF_ADDR_OFF);
    }

    @Inject(method = "begin", at = @At("RETURN"))
    private void sulfide$afterBegin(int drawMode, VertexFormat fmt, CallbackInfo ci) {
        sulfide$refreshAddr();
        sulfide$refreshLayout();
    }

    @Inject(method = "grow", at = @At("RETURN"))
    private void sulfide$afterGrow(int size, CallbackInfo ci) {
        sulfide$refreshAddr();
    }

    @Unique
    private long sulfide$elementPtr() {
        if (format != sulfide$layoutFormat) {
            sulfide$refreshLayout();
        }
        return sulfide$addr
                + (long) vertexCount * sulfide$stride
                + sulfide$offsets[currentElementId];
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public BufferBuilder vertex(double x, double y, double z) {
        long p = sulfide$elementPtr();
        switch (currentElement.getFormat()) {
            case FLOAT:
                MemoryAccess.putFloat(p, (float) (x + offsetX));
                MemoryAccess.putFloat(p + 4, (float) (y + offsetY));
                MemoryAccess.putFloat(p + 8, (float) (z + offsetZ));
                break;
            case UNSIGNED_INT:
            case INT:
                MemoryAccess.putInt(p, Float.floatToRawIntBits((float) (x + offsetX)));
                MemoryAccess.putInt(p + 4, Float.floatToRawIntBits((float) (y + offsetY)));
                MemoryAccess.putInt(p + 8, Float.floatToRawIntBits((float) (z + offsetZ)));
                break;
            case UNSIGNED_SHORT:
            case SHORT:
                MemoryAccess.putShort(p, (short) (int) (x + offsetX));
                MemoryAccess.putShort(p + 2, (short) (int) (y + offsetY));
                MemoryAccess.putShort(p + 4, (short) (int) (z + offsetZ));
                break;
            case UNSIGNED_BYTE:
            case BYTE:
                MemoryAccess.putByte(p, (byte) (int) (x + offsetX));
                MemoryAccess.putByte(p + 1, (byte) (int) (y + offsetY));
                MemoryAccess.putByte(p + 2, (byte) (int) (z + offsetZ));
                break;
        }
        nextElement();
        return (BufferBuilder) (Object) this;
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public BufferBuilder color(int red, int green, int blue, int alpha) {
        if (textured) return (BufferBuilder) (Object) this;
        long p = sulfide$elementPtr();
        switch (currentElement.getFormat()) {
            case FLOAT:
                MemoryAccess.putFloat(p, red / 255.0F);
                MemoryAccess.putFloat(p + 4, green / 255.0F);
                MemoryAccess.putFloat(p + 8, blue / 255.0F);
                MemoryAccess.putFloat(p + 12, alpha / 255.0F);
                break;
            case UNSIGNED_INT:
            case INT:
                MemoryAccess.putFloat(p, (float) red);
                MemoryAccess.putFloat(p + 4, (float) green);
                MemoryAccess.putFloat(p + 8, (float) blue);
                MemoryAccess.putFloat(p + 12, (float) alpha);
                break;
            case UNSIGNED_SHORT:
            case SHORT:
                MemoryAccess.putShort(p, (short) red);
                MemoryAccess.putShort(p + 2, (short) green);
                MemoryAccess.putShort(p + 4, (short) blue);
                MemoryAccess.putShort(p + 6, (short) alpha);
                break;
            case UNSIGNED_BYTE:
            case BYTE:
                if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                    MemoryAccess.putByte(p, (byte) red);
                    MemoryAccess.putByte(p + 1, (byte) green);
                    MemoryAccess.putByte(p + 2, (byte) blue);
                    MemoryAccess.putByte(p + 3, (byte) alpha);
                } else {
                    MemoryAccess.putByte(p, (byte) alpha);
                    MemoryAccess.putByte(p + 1, (byte) blue);
                    MemoryAccess.putByte(p + 2, (byte) green);
                    MemoryAccess.putByte(p + 3, (byte) red);
                }
                break;
        }
        nextElement();
        return (BufferBuilder) (Object) this;
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public BufferBuilder texture(double u, double v) {
        long p = sulfide$elementPtr();
        switch (currentElement.getFormat()) {
            case FLOAT:
                MemoryAccess.putFloat(p, (float) u);
                MemoryAccess.putFloat(p + 4, (float) v);
                break;
            case UNSIGNED_INT:
            case INT:
                MemoryAccess.putInt(p, (int) u);
                MemoryAccess.putInt(p + 4, (int) v);
                break;
            case UNSIGNED_SHORT:
            case SHORT:
                MemoryAccess.putShort(p, (short) (int) v);
                MemoryAccess.putShort(p + 2, (short) (int) u);
                break;
            case UNSIGNED_BYTE:
            case BYTE:
                MemoryAccess.putByte(p, (byte) (int) v);
                MemoryAccess.putByte(p + 1, (byte) (int) u);
                break;
        }
        nextElement();
        return (BufferBuilder) (Object) this;
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public BufferBuilder texture2(int u, int v) {
        long p = sulfide$elementPtr();
        switch (currentElement.getFormat()) {
            case FLOAT:
                MemoryAccess.putFloat(p, (float) u);
                MemoryAccess.putFloat(p + 4, (float) v);
                break;
            case UNSIGNED_INT:
            case INT:
                MemoryAccess.putInt(p, u);
                MemoryAccess.putInt(p + 4, v);
                break;
            case UNSIGNED_SHORT:
            case SHORT:
                MemoryAccess.putShort(p, (short) v);
                MemoryAccess.putShort(p + 2, (short) u);
                break;
            case UNSIGNED_BYTE:
            case BYTE:
                MemoryAccess.putByte(p, (byte) v);
                MemoryAccess.putByte(p + 1, (byte) u);
                break;
        }
        nextElement();
        return (BufferBuilder) (Object) this;
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public BufferBuilder normal(float x, float y, float z) {
        long p = sulfide$elementPtr();
        switch (currentElement.getFormat()) {
            case FLOAT:
                MemoryAccess.putFloat(p, x);
                MemoryAccess.putFloat(p + 4, y);
                MemoryAccess.putFloat(p + 8, z);
                break;
            case UNSIGNED_INT:
            case INT:
                MemoryAccess.putInt(p, (int) x);
                MemoryAccess.putInt(p + 4, (int) y);
                MemoryAccess.putInt(p + 8, (int) z);
                break;
            case UNSIGNED_SHORT:
            case SHORT:
                MemoryAccess.putShort(p, (short) ((int) x * 32767 & 0xFFFF));
                MemoryAccess.putShort(p + 2, (short) ((int) y * 32767 & 0xFFFF));
                MemoryAccess.putShort(p + 4, (short) ((int) z * 32767 & 0xFFFF));
                break;
            case UNSIGNED_BYTE:
            case BYTE:
                MemoryAccess.putByte(p, (byte) ((int) x * 127 & 0xFF));
                MemoryAccess.putByte(p + 1, (byte) ((int) y * 127 & 0xFF));
                MemoryAccess.putByte(p + 2, (byte) ((int) z * 127 & 0xFF));
                break;
        }
        nextElement();
        return (BufferBuilder) (Object) this;
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public void faceTexture2(int i, int j, int k, int l) {
        int m = (vertexCount - 4) * format.getVertexSizeInteger()
                + format.getUvIndex(1) / 4;
        int n = format.getVertexSize() >> 2;
        long base = sulfide$addr + (long) m * 4;
        long stride = (long) n * 4;
        MemoryAccess.putInt(base, i);
        MemoryAccess.putInt(base + stride, j);
        MemoryAccess.putInt(base + stride * 2, k);
        MemoryAccess.putInt(base + stride * 3, l);
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public void postProcessFacePosition(double d, double e, double f) {
        int stride = format.getVertexSizeInteger();
        int base = (vertexCount - 4) * stride;
        for (int v = 0; v < 4; v++) {
            long p = sulfide$addr + ((long) base + (long) v * stride) * 4;
            MemoryAccess.putInt(p, Float.floatToRawIntBits(
                    (float) (d + offsetX) + Float.intBitsToFloat(MemoryAccess.getInt(p))));
            MemoryAccess.putInt(p + 4, Float.floatToRawIntBits(
                    (float) (e + offsetY) + Float.intBitsToFloat(MemoryAccess.getInt(p + 4))));
            MemoryAccess.putInt(p + 8, Float.floatToRawIntBits(
                    (float) (f + offsetZ) + Float.intBitsToFloat(MemoryAccess.getInt(p + 8))));
        }
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public void faceTint(float r, float g, float b, int i) {
        int j = ((vertexCount - i) * format.getVertexSize()
                + format.getColorIndex()) / 4;
        long p = sulfide$addr + (long) j * 4;
        int k = -1;
        if (!textured) {
            k = MemoryAccess.getInt(p);
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                int lr = (int) ((float) (k & 0xFF) * r);
                int lg = (int) ((float) ((k >> 8) & 0xFF) * g);
                int lb = (int) ((float) ((k >> 16) & 0xFF) * b);
                k = (k & 0xFF000000) | (lb << 16) | (lg << 8) | lr;
            } else {
                int lr = (int) ((float) ((k >> 24) & 0xFF) * r);
                int lg = (int) ((float) ((k >> 16) & 0xFF) * g);
                int lb = (int) ((float) ((k >> 8) & 0xFF) * b);
                k = (k & 0xFF) | (lr << 24) | (lg << 16) | (lb << 8);
            }
        }
        MemoryAccess.putInt(p, k);
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    private void putColor(int index, int red, int green, int blue, int alpha) {
        long p = sulfide$addr + (long) index * 4;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            MemoryAccess.putInt(p, (alpha << 24) | (blue << 16) | (green << 8) | red);
        } else {
            MemoryAccess.putInt(p, (red << 24) | (green << 16) | (blue << 8) | alpha);
        }
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public void putNormal(float x, float y, float z) {
        int nx = (byte) ((int) (x * 127.0F)) & 0xFF;
        int ny = (byte) ((int) (y * 127.0F)) & 0xFF;
        int nz = (byte) ((int) (z * 127.0F)) & 0xFF;
        int packed = nx | (ny << 8) | (nz << 16);
        int stride = format.getVertexSize() >> 2;
        int n = (vertexCount - 4) * stride + format.getNormalIndex() / 4;
        long base = sulfide$addr + (long) n * 4;
        long s = (long) stride * 4;
        MemoryAccess.putInt(base, packed);
        MemoryAccess.putInt(base + s, packed);
        MemoryAccess.putInt(base + s * 2, packed);
        MemoryAccess.putInt(base + s * 3, packed);
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public void putArray(int[] data) {
        grow(data.length);
        int pos = vertexCount * format.getVertexSizeInteger();
        MemoryAccess.copyMemory(
                data,
                MemoryAccess.ARRAY_INT_BASE_OFFSET,
                null,
                sulfide$addr + (long) pos * 4,
                (long) data.length * 4
        );
        vertexCount += data.length / format.getVertexSizeInteger();
        // keep intBuffer.position in sync for grow()'s remaining() check
        intBuffer.position(vertexCount * format.getVertexSizeInteger());
    }

    /**
     * @reason DMA
     * @author Lunasa
     */
    @Overwrite
    public void sortQuads(float cameraX, float cameraY, float cameraZ) {
        int quadCount = vertexCount / 4;
        if (quadCount <= 1) {
            return;
        }

        float[] distances = sulfide$distances;
        if (distances == null || distances.length < quadCount) {
            distances = new float[quadCount];
            sulfide$distances = distances;
        }

        int vertInts = format.getVertexSizeInteger();
        int vertBytes = format.getVertexSize();
        long addr = sulfide$addr;
        long vs = (long) vertInts * 4;

        float adjX = (float) ((double) cameraX + offsetX);
        float adjY = (float) ((double) cameraY + offsetY);
        float adjZ = (float) ((double) cameraZ + offsetZ);

        for (int q = 0; q < quadCount; q++) {
            long qb = addr + (long) q * vertBytes * 4L;

            float x0 = MemoryAccess.getFloat(qb);
            float y0 = MemoryAccess.getFloat(qb + 4);
            float z0 = MemoryAccess.getFloat(qb + 8);
            float x1 = MemoryAccess.getFloat(qb + vs);
            float y1 = MemoryAccess.getFloat(qb + vs + 4);
            float z1 = MemoryAccess.getFloat(qb + vs + 8);
            float x2 = MemoryAccess.getFloat(qb + vs * 2);
            float y2 = MemoryAccess.getFloat(qb + vs * 2 + 4);
            float z2 = MemoryAccess.getFloat(qb + vs * 2 + 8);
            float x3 = MemoryAccess.getFloat(qb + vs * 3);
            float y3 = MemoryAccess.getFloat(qb + vs * 3 + 4);
            float z3 = MemoryAccess.getFloat(qb + vs * 3 + 8);

            float dx = (x0 + x1 + x2 + x3) * 0.25F - adjX;
            float dy = (y0 + y1 + y2 + y3) * 0.25F - adjY;
            float dz = (z0 + z1 + z2 + z3) * 0.25F - adjZ;
            distances[q] = dx * dx + dy * dy + dz * dz;
        }

        long[] keys = sulfide$sortKeys;
        if (keys == null || keys.length < quadCount) {
            keys = new long[quadCount];
            sulfide$sortKeys = keys;
        }
        for (int q = 0; q < quadCount; q++) {
            keys[q] = (((long) ~Float.floatToRawIntBits(distances[q])) << 32) | (q & 0xFFFFFFFFL);
        }
        Arrays.sort(keys, 0, quadCount);

        int[] indices = sulfide$sortOrder;
        if (indices == null || indices.length < quadCount) {
            indices = new int[quadCount];
            sulfide$sortOrder = indices;
        }
        for (int i = 0; i < quadCount; i++) {
            indices[i] = (int) keys[i];
        }

        long quadBytes = (long) vertBytes * 4;
        int[] temp = sulfide$quadScratch;
        if (temp == null || temp.length < vertBytes) {
            temp = new int[vertBytes];
            sulfide$quadScratch = temp;
        }
        BitSet done = sulfide$sorted;
        done.clear();

        for (int m = 0; (m = done.nextClearBit(m)) < quadCount; m++) {
            int target = indices[m];
            if (target != m) {
                MemoryAccess.copyMemory(
                        null, addr + (long) target * quadBytes,
                        temp, MemoryAccess.ARRAY_INT_BASE_OFFSET,
                        quadBytes
                );

                int cur = target;
                for (int nxt = indices[target]; cur != m; nxt = indices[nxt]) {
                    MemoryAccess.copyMemory(
                            addr + (long) nxt * quadBytes,
                            addr + (long) cur * quadBytes,
                            quadBytes
                    );
                    done.set(cur);
                    cur = nxt;
                }

                MemoryAccess.copyMemory(
                        temp, MemoryAccess.ARRAY_INT_BASE_OFFSET,
                        null, addr + (long) m * quadBytes,
                        quadBytes
                );
            }
            done.set(m);
        }
    }
}