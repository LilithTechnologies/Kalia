package re.lilith.kalia.frame

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.resource.GpuTexture

class GameFrameData {
    var encoder: PassContext? = null
    var viewport: Viewport? = null
    var scissor: Rect? = null
    var colorTarget: GpuTexture? = null
    var depthTarget: GpuTexture? = null
}
