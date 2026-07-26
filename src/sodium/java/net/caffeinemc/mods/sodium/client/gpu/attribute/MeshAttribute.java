package net.caffeinemc.mods.sodium.client.gpu.attribute;

import re.lilith.kalia.renderer.format.VertexAttributeFormat;

public class MeshAttribute {
    private final VertexAttributeFormat format;

    private final int pointer;
    private final int stride;
    private final int size;

    public MeshAttribute(ScalarType type, int count, boolean normalized, int pointer, int stride, boolean intType) {
        this(resolve(type, count, normalized, intType), pointer, stride, count * type.bytes);
    }

    public MeshAttribute(VertexAttributeFormat format, int pointer, int stride, int size) {
        this.format = format;
        this.pointer = pointer;
        this.stride = stride;
        this.size = size;
    }

    public MeshAttribute(MeshAttribute attribute) {
        this(attribute.format, attribute.pointer, attribute.stride, attribute.size);
    }

    private static VertexAttributeFormat resolve(ScalarType type, int count, boolean normalized, boolean intType) {
        if (type == ScalarType.FLOAT) {
            return switch (count) {
                case 1 -> VertexAttributeFormat.FLOAT;
                case 2 -> VertexAttributeFormat.FLOAT2;
                case 3 -> VertexAttributeFormat.FLOAT3;
                case 4 -> VertexAttributeFormat.FLOAT4;
                default -> throw unsupported(type, count, normalized, intType);
            };
        }
        if (type == ScalarType.UNSIGNED_BYTE && count == 4) {
            if (normalized) {
                return VertexAttributeFormat.UNORM8X4;
            }
            if (intType) {
                return VertexAttributeFormat.UINT8X4;
            }
        }
        if (type == ScalarType.BYTE && count == 4 && normalized) {
            return VertexAttributeFormat.SNORM8X4;
        }
        if (type == ScalarType.UNSIGNED_SHORT && count == 2) {
            return intType ? VertexAttributeFormat.UINT16X2 : VertexAttributeFormat.UNORM16X2;
        }
        if (type == ScalarType.SHORT && intType) {
            return switch (count) {
                case 2 -> VertexAttributeFormat.SHORT2;
                case 4 -> VertexAttributeFormat.SHORT4;
                default -> throw unsupported(type, count, normalized, intType);
            };
        }
        if (type == ScalarType.UNSIGNED_INT && intType) {
            return switch (count) {
                case 1 -> VertexAttributeFormat.UINT;
                case 2 -> VertexAttributeFormat.UINT2;
                default -> throw unsupported(type, count, normalized, intType);
            };
        }
        throw unsupported(type, count, normalized, intType);
    }

    private static IllegalArgumentException unsupported(ScalarType type, int count, boolean normalized, boolean intType) {
        return new IllegalArgumentException(
                "Unsupported vertex attribute: %s x%d normalized=%s int=%s".formatted(type, count, normalized, intType));
    }

    public VertexAttributeFormat getFormat() {
        return this.format;
    }

    public int getPointer() {
        return pointer;
    }

    public int getStride() {
        return stride;
    }

    public int getSize() {
        return size;
    }
}
