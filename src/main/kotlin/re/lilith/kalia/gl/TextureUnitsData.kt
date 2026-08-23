package re.lilith.kalia.gl

class TextureUnitsData {
    val enabled = BooleanArray(TextureUnits.COUNT) { it == 0 }
    val bound = IntArray(TextureUnits.COUNT)
    var activeUnit: Int = 0
}
