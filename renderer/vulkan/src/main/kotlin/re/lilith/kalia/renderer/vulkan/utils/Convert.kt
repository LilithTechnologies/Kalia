package re.lilith.kalia.renderer.vulkan.utils

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.graph.LoadOp
import re.lilith.kalia.renderer.pipeline.*
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.FilterMode
import re.lilith.kalia.renderer.resource.WrapMode
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.vulkan.api.descriptor.DescriptorType
import re.lilith.vulkan.api.descriptor.Filter
import re.lilith.vulkan.api.descriptor.SamplerAddressMode
import re.lilith.vulkan.api.descriptor.SamplerMipmapMode
import re.lilith.vulkan.api.pipeline.BlendOperation
import re.lilith.vulkan.api.pipeline.CompareOperation
import re.lilith.vulkan.api.pipeline.LogicOperation
import re.lilith.vulkan.api.pipeline.ShaderStageFlags
import re.lilith.vulkan.api.types.enum.AttachmentLoadOperation
import re.lilith.vulkan.api.types.enum.Format
import re.lilith.vulkan.api.types.flags.BufferUsage
import re.lilith.vulkan.api.types.flags.ImageAspect

internal object Convert {
    fun format(format: TextureFormat): Format = when (format) {
        TextureFormat.R8 -> Format.R8_UNorm
        TextureFormat.RG8 -> Format.R8G8_UNorm
        TextureFormat.RGBA8 -> Format.R8G8B8A8_UNorm
        TextureFormat.BGRA8 -> Format.B8G8R8A8_UNorm
        TextureFormat.RGBA16F -> Format.R16G16B16A16_SFloat
        TextureFormat.RGBA32F -> Format.R32G32B32A32_SFloat
        TextureFormat.DEPTH32F -> Format.D32_SFloat
        TextureFormat.DEPTH24_STENCIL8 -> Format.D24_UNorm_S8_UInt
        TextureFormat.DEPTH32F_STENCIL8 -> Format.D32_SFloat_S8_UInt
    }

    fun textureFormat(format: Format): TextureFormat? = when (format) {
        Format.R8_UNorm -> TextureFormat.R8
        Format.R8G8_UNorm -> TextureFormat.RG8
        Format.R8G8B8A8_UNorm -> TextureFormat.RGBA8
        Format.B8G8R8A8_UNorm -> TextureFormat.BGRA8
        Format.R16G16B16A16_SFloat -> TextureFormat.RGBA16F
        Format.R32G32B32A32_SFloat -> TextureFormat.RGBA32F
        Format.D32_SFloat -> TextureFormat.DEPTH32F
        Format.D24_UNorm_S8_UInt -> TextureFormat.DEPTH24_STENCIL8
        Format.D32_SFloat_S8_UInt -> TextureFormat.DEPTH32F_STENCIL8
        else -> null
    }

    fun vertexFormat(format: VertexAttributeFormat): Format = when (format) {
        VertexAttributeFormat.FLOAT -> Format.R32_SFloat
        VertexAttributeFormat.FLOAT2 -> Format.R32G32_SFloat
        VertexAttributeFormat.FLOAT3 -> Format.R32G32B32_SFloat
        VertexAttributeFormat.FLOAT4 -> Format.R32G32B32A32_SFloat
        VertexAttributeFormat.UNORM8X4 -> Format.R8G8B8A8_UNorm
        VertexAttributeFormat.SNORM8X4 -> Format.R8G8B8A8_SNorm
        VertexAttributeFormat.UNORM16X2 -> Format.R16G16_UNorm
        VertexAttributeFormat.SHORT2 -> Format.R16G16_SInt
        VertexAttributeFormat.SHORT4 -> Format.R16G16B16A16_SInt
        VertexAttributeFormat.UINT -> Format.R32_UInt
        VertexAttributeFormat.UINT2 -> Format.R32G32_UInt
        VertexAttributeFormat.UINT8X4 -> Format.R8G8B8A8_UInt
        VertexAttributeFormat.UINT16X2 -> Format.R16G16_UInt
        VertexAttributeFormat.USHORT2_FLOAT -> Format.R16G16_UInt
    }

    fun aspect(format: TextureFormat): ImageAspect = when {
        format.isColor -> ImageAspect.Color
        format.hasStencil -> ImageAspect.Depth + ImageAspect.Stencil
        else -> ImageAspect.Depth
    }

    fun topology(topology: PrimitiveTopology): re.lilith.vulkan.api.pipeline.PrimitiveTopology = when (topology) {
        PrimitiveTopology.POINTS -> re.lilith.vulkan.api.pipeline.PrimitiveTopology.PointList
        PrimitiveTopology.LINES -> re.lilith.vulkan.api.pipeline.PrimitiveTopology.LineList
        PrimitiveTopology.LINE_STRIP -> re.lilith.vulkan.api.pipeline.PrimitiveTopology.LineStrip
        PrimitiveTopology.TRIANGLES -> re.lilith.vulkan.api.pipeline.PrimitiveTopology.TriangleList
        PrimitiveTopology.TRIANGLE_STRIP -> re.lilith.vulkan.api.pipeline.PrimitiveTopology.TriangleStrip
    }

    fun cullMode(mode: CullMode): re.lilith.vulkan.api.pipeline.CullMode = when (mode) {
        CullMode.NONE -> re.lilith.vulkan.api.pipeline.CullMode.None
        CullMode.FRONT -> re.lilith.vulkan.api.pipeline.CullMode.Front
        CullMode.BACK -> re.lilith.vulkan.api.pipeline.CullMode.Back
    }

    fun frontFace(face: FrontFace): re.lilith.vulkan.api.pipeline.FrontFace = when (face) {
        FrontFace.CLOCKWISE -> re.lilith.vulkan.api.pipeline.FrontFace.Clockwise
        FrontFace.COUNTER_CLOCKWISE -> re.lilith.vulkan.api.pipeline.FrontFace.CounterClockwise
    }

    fun polygonMode(mode: PolygonMode): re.lilith.vulkan.api.pipeline.PolygonMode = when (mode) {
        PolygonMode.FILL -> re.lilith.vulkan.api.pipeline.PolygonMode.Fill
        PolygonMode.LINE -> re.lilith.vulkan.api.pipeline.PolygonMode.Line
        PolygonMode.POINT -> re.lilith.vulkan.api.pipeline.PolygonMode.Point
    }

    fun compare(function: CompareFunction): CompareOperation = when (function) {
        CompareFunction.NEVER -> CompareOperation.Never
        CompareFunction.LESS -> CompareOperation.Less
        CompareFunction.EQUAL -> CompareOperation.Equal
        CompareFunction.LESS_EQUAL -> CompareOperation.LessOrEqual
        CompareFunction.GREATER -> CompareOperation.Greater
        CompareFunction.NOT_EQUAL -> CompareOperation.NotEqual
        CompareFunction.GREATER_EQUAL -> CompareOperation.GreaterOrEqual
        CompareFunction.ALWAYS -> CompareOperation.Always
    }

    fun blendFactor(factor: BlendFactor): re.lilith.vulkan.api.pipeline.BlendFactor = when (factor) {
        BlendFactor.ZERO -> re.lilith.vulkan.api.pipeline.BlendFactor.Zero
        BlendFactor.ONE -> re.lilith.vulkan.api.pipeline.BlendFactor.One
        BlendFactor.SRC_COLOR -> re.lilith.vulkan.api.pipeline.BlendFactor.SourceColor
        BlendFactor.ONE_MINUS_SRC_COLOR -> re.lilith.vulkan.api.pipeline.BlendFactor.OneMinusSourceColor
        BlendFactor.DST_COLOR -> re.lilith.vulkan.api.pipeline.BlendFactor.DestinationColor
        BlendFactor.ONE_MINUS_DST_COLOR -> re.lilith.vulkan.api.pipeline.BlendFactor.OneMinusDestinationColor
        BlendFactor.SRC_ALPHA -> re.lilith.vulkan.api.pipeline.BlendFactor.SourceAlpha
        BlendFactor.ONE_MINUS_SRC_ALPHA -> re.lilith.vulkan.api.pipeline.BlendFactor.OneMinusSourceAlpha
        BlendFactor.DST_ALPHA -> re.lilith.vulkan.api.pipeline.BlendFactor.DestinationAlpha
        BlendFactor.ONE_MINUS_DST_ALPHA -> re.lilith.vulkan.api.pipeline.BlendFactor.OneMinusDestinationAlpha
        BlendFactor.SRC_ALPHA_SATURATE -> re.lilith.vulkan.api.pipeline.BlendFactor.SourceAlphaSaturate
        BlendFactor.CONSTANT_COLOR -> re.lilith.vulkan.api.pipeline.BlendFactor.ConstantColor
        BlendFactor.ONE_MINUS_CONSTANT_COLOR -> re.lilith.vulkan.api.pipeline.BlendFactor.OneMinusConstantColor
    }

    fun blendOp(op: BlendOp): BlendOperation = when (op) {
        BlendOp.ADD -> BlendOperation.Add
        BlendOp.SUBTRACT -> BlendOperation.Subtract
        BlendOp.REVERSE_SUBTRACT -> BlendOperation.ReverseSubtract
        BlendOp.MIN -> BlendOperation.Minimum
        BlendOp.MAX -> BlendOperation.Maximum
    }

    fun logicOp(op: LogicOp): LogicOperation = when (op) {
        LogicOp.CLEAR -> LogicOperation.Clear
        LogicOp.AND -> LogicOperation.And
        LogicOp.AND_REVERSE -> LogicOperation.AndReverse
        LogicOp.COPY -> LogicOperation.Copy
        LogicOp.AND_INVERTED -> LogicOperation.AndInverted
        LogicOp.NO_OP -> LogicOperation.NoOp
        LogicOp.XOR -> LogicOperation.Xor
        LogicOp.OR -> LogicOperation.Or
        LogicOp.NOR -> LogicOperation.Nor
        LogicOp.EQUIVALENT -> LogicOperation.Equivalent
        LogicOp.INVERT -> LogicOperation.Invert
        LogicOp.OR_REVERSE -> LogicOperation.OrReverse
        LogicOp.COPY_INVERTED -> LogicOperation.CopyInverted
        LogicOp.OR_INVERTED -> LogicOperation.OrInverted
        LogicOp.NAND -> LogicOperation.Nand
        LogicOp.SET -> LogicOperation.Set
    }

    fun loadOp(op: LoadOp): AttachmentLoadOperation = when (op) {
        LoadOp.LOAD -> AttachmentLoadOperation.Load
        LoadOp.CLEAR -> AttachmentLoadOperation.Clear
        LoadOp.DISCARD -> AttachmentLoadOperation.DontCare
    }

    fun filter(mode: FilterMode): Filter = when (mode) {
        FilterMode.NEAREST -> Filter.Nearest
        FilterMode.LINEAR -> Filter.Linear
    }

    fun mipmapMode(mode: FilterMode): SamplerMipmapMode = when (mode) {
        FilterMode.NEAREST -> SamplerMipmapMode.Nearest
        FilterMode.LINEAR -> SamplerMipmapMode.Linear
    }

    fun wrap(mode: WrapMode): SamplerAddressMode = when (mode) {
        WrapMode.REPEAT -> SamplerAddressMode.Repeat
        WrapMode.MIRROR -> SamplerAddressMode.MirroredRepeat
        WrapMode.CLAMP_TO_EDGE -> SamplerAddressMode.ClampToEdge
    }

    fun descriptorType(kind: BindingKind): DescriptorType = when (kind) {
        BindingKind.TEXTURE -> DescriptorType.CombinedImageSampler
        BindingKind.UNIFORM_BUFFER -> DescriptorType.UniformBuffer
        BindingKind.STORAGE_BUFFER -> DescriptorType.StorageBuffer
    }

    fun shaderStage(stage: ShaderStage): re.lilith.vulkan.api.pipeline.ShaderStage = when (stage) {
        ShaderStage.VERTEX -> re.lilith.vulkan.api.pipeline.ShaderStage.Vertex
        ShaderStage.FRAGMENT -> re.lilith.vulkan.api.pipeline.ShaderStage.Fragment
        ShaderStage.COMPUTE -> re.lilith.vulkan.api.pipeline.ShaderStage.Compute
    }

    fun stageFlags(stages: Set<ShaderStage>): ShaderStageFlags =
        stages.fold(ShaderStageFlags.None) { flags, stage ->
            flags + when (stage) {
                ShaderStage.VERTEX -> ShaderStageFlags.Vertex
                ShaderStage.FRAGMENT -> ShaderStageFlags.Fragment
                ShaderStage.COMPUTE -> ShaderStageFlags.Compute
            }
        }

    fun bufferUsage(description: BufferDescription): BufferUsage {
        var usage = BufferUsage.None
        if (description.vertex) usage += BufferUsage.VertexBuffer
        if (description.index) usage += BufferUsage.IndexBuffer
        if (description.uniform) usage += BufferUsage.UniformBuffer
        if (description.indirect) usage += BufferUsage.IndirectBuffer
        if (description.usage == re.lilith.kalia.renderer.resource.BufferUsage.STORAGE) {
            usage += BufferUsage.StorageBuffer
        }
        usage += BufferUsage.TransferSource
        usage += BufferUsage.TransferDestination
        return usage
    }
}