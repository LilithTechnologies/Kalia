package re.lilith.kalia.gl.tables

import re.lilith.kalia.gl.emulation.GlTexture

internal class TextureTableData {
    var epoch = -1

    var lastId = 0
    var lastTexture: GlTexture? = null

    var proxyWidth = 0
    var proxyHeight = 0

    fun forget() {
        lastId = 0
        lastTexture = null
    }
}
