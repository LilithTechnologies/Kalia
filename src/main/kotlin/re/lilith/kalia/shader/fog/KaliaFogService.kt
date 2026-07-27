package re.lilith.kalia.shader.fog

import org.embeddedt.embeddium.impl.render.chunk.fog.FogService
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode
import re.lilith.kalia.gl.ShaderUniforms.fogBlue
import re.lilith.kalia.gl.ShaderUniforms.fogDensity
import re.lilith.kalia.gl.ShaderUniforms.fogEnd
import re.lilith.kalia.gl.ShaderUniforms.fogGreen
import re.lilith.kalia.gl.ShaderUniforms.fogMode
import re.lilith.kalia.gl.ShaderUniforms.fogRed
import re.lilith.kalia.gl.ShaderUniforms.fogStart
import re.lilith.kalia.gl.ShaderUniforms.isFogEnabled

class KaliaFogService : FogService {
    override fun getFogEnd(): Float {
        return fogEnd()
    }

    override fun getFogStart(): Float {
        return fogStart()
    }

    override fun getFogDensity(): Float {
        return fogDensity()
    }

    override fun getFogShapeIndex(): Int {
        return 0
    }

    override fun getFogCutoff(): Float {
        return getFogEnd()
    }

    override fun getFogColor(): FloatArray? {
        return floatArrayOf(fogRed(), fogGreen(), fogBlue(), 1.0f)
    }

    override fun getFogMode(): ChunkFogMode? {
        if (!isFogEnabled()) {
            return ChunkFogMode.NONE
        }
        return ChunkFogMode.fromGLMode(fogMode().glMode)
    }
}
