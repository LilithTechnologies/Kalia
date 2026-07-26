package re.lilith.kalia.gl

import re.lilith.kalia.renderer.pipeline.*

// man... this was a terrible idea
// i originally thought i could maybe eliminate any and all opengl references
// but yeah as you can see it was not a good idea
// i pray well for whoever touches this class!
object GlEnums {
    fun compareFunction(glFunc: Int): CompareFunction = when (glFunc) {
        0x0200 -> CompareFunction.NEVER
        0x0201 -> CompareFunction.LESS
        0x0202 -> CompareFunction.EQUAL
        0x0203 -> CompareFunction.LESS_EQUAL
        0x0204 -> CompareFunction.GREATER
        0x0205 -> CompareFunction.NOT_EQUAL
        0x0206 -> CompareFunction.GREATER_EQUAL
        0x0207 -> CompareFunction.ALWAYS
        else -> CompareFunction.LESS_EQUAL
    }

    fun blendFactor(glFactor: Int): BlendFactor = when (glFactor) {
        0 -> BlendFactor.ZERO
        1 -> BlendFactor.ONE
        0x0300 -> BlendFactor.SRC_COLOR
        0x0301 -> BlendFactor.ONE_MINUS_SRC_COLOR
        0x0302 -> BlendFactor.SRC_ALPHA
        0x0303 -> BlendFactor.ONE_MINUS_SRC_ALPHA
        0x0304 -> BlendFactor.DST_ALPHA
        0x0305 -> BlendFactor.ONE_MINUS_DST_ALPHA
        0x0306 -> BlendFactor.DST_COLOR
        0x0307 -> BlendFactor.ONE_MINUS_DST_COLOR
        0x0308 -> BlendFactor.SRC_ALPHA_SATURATE
        0x8001 -> BlendFactor.CONSTANT_COLOR
        0x8002 -> BlendFactor.ONE_MINUS_CONSTANT_COLOR
        else -> BlendFactor.ONE
    }

    fun blendOp(glOp: Int): BlendOp = when (glOp) {
        0x8006 -> BlendOp.ADD
        0x8007 -> BlendOp.MIN
        0x8008 -> BlendOp.MAX
        0x800A -> BlendOp.SUBTRACT
        0x800B -> BlendOp.REVERSE_SUBTRACT
        else -> BlendOp.ADD
    }

    fun logicOp(glOp: Int): LogicOp = when (glOp) {
        0x1500 -> LogicOp.CLEAR
        0x1501 -> LogicOp.AND
        0x1502 -> LogicOp.AND_REVERSE
        0x1503 -> LogicOp.COPY
        0x1504 -> LogicOp.AND_INVERTED
        0x1505 -> LogicOp.NO_OP
        0x1506 -> LogicOp.XOR
        0x1507 -> LogicOp.OR
        0x1508 -> LogicOp.NOR
        0x1509 -> LogicOp.EQUIVALENT
        0x150A -> LogicOp.INVERT
        0x150B -> LogicOp.OR_REVERSE
        0x150C -> LogicOp.COPY_INVERTED
        0x150D -> LogicOp.OR_INVERTED
        0x150E -> LogicOp.NAND
        0x150F -> LogicOp.SET
        else -> LogicOp.COPY
    }

    fun topology(glMode: Int): PrimitiveTopology = when (glMode) {
        0x0000 -> PrimitiveTopology.POINTS // GL_POINTS
        0x0001, 0x0002 -> PrimitiveTopology.LINES // GL_LINES, GL_LINE_LOOP
        0x0003 -> PrimitiveTopology.LINE_STRIP // GL_LINE_STRIP
        0x0005 -> PrimitiveTopology.TRIANGLE_STRIP // GL_TRIANGLE_STRIP
        else -> PrimitiveTopology.TRIANGLES
    }

    fun indexPattern(glMode: Int): IndexPattern = when (glMode) {
        0x0007 -> IndexPattern.QUADS // GL_QUADS
        0x0006, 0x0009 -> IndexPattern.FAN // GL_TRIANGLE_FAN, GL_POLYGON
        else -> IndexPattern.NONE
    }

    enum class IndexPattern {
        NONE,
        QUADS,
        FAN,
        ;
    }

    fun fogMode(glMode: Int): FogMode = when (glMode) {
        0x0801 -> FogMode.EXP2 // GL_EXP2
        0x2601 -> FogMode.LINEAR // GL_LINEAR
        else -> FogMode.EXP // GL_EXP
    }

    enum class FogMode {
        EXP,
        EXP2,
        LINEAR,
        ;
    }

    fun polygonMode(glMode: Int): PolygonMode = when (glMode) {
        0x1B00 -> PolygonMode.POINT
        0x1B01 -> PolygonMode.LINE
        else -> PolygonMode.FILL
    }

    const val GL_COLOR_BUFFER_BIT: Int = 0x4000
    const val GL_DEPTH_BUFFER_BIT: Int = 0x100

    const val GL_MODELVIEW: Int = 0x1700
    const val GL_PROJECTION: Int = 0x1701
    const val GL_TEXTURE: Int = 0x1702

    const val GL_MODELVIEW_MATRIX: Int = 0x0BA6
    const val GL_PROJECTION_MATRIX: Int = 0x0BA7
    const val GL_TEXTURE_MATRIX: Int = 0x0BA8

    const val GL_ALWAYS: Int = 0x0207

    const val GL_EYE_LINEAR: Int = 0x2400
    const val GL_OBJECT_LINEAR: Int = 0x2401
    const val GL_OBJECT_PLANE: Int = 0x2501
    const val GL_EYE_PLANE: Int = 0x2502
}
