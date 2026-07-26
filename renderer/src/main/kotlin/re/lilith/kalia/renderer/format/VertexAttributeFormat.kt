package re.lilith.kalia.renderer.format

/**
 * The scalar layout of a single vertex attribute
 */
enum class VertexAttributeFormat(val componentCount: Int, val byteSize: Int) {
    FLOAT(1, 4),
    FLOAT2(2, 8),
    FLOAT3(3, 12),
    FLOAT4(4, 16),

    // Four bytes normalised to `[0, 1]`, the usual packing for vertex colors
    UNORM8X4(4, 4),

    // Four bytes normalised to `[-1, 1]`, the usual packing for normals
    SNORM8X4(4, 4),

    // Two shorts normalised to `[0, 1]`, used by Minecraft lightmap coordinates
    UNORM16X2(2, 4),

    SHORT2(2, 4),
    SHORT4(4, 8),
    UINT(1, 4),
    UINT2(2, 8),

    // Unsigned integer packings used by compact terrain vertex formats
    UINT8X4(4, 4),
    UINT16X2(2, 4),
}