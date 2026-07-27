package org.taumc.celeritas.impl.render.terrain.fog;

import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import re.lilith.kalia.gl.ShaderUniforms;

public class KaliaFogService implements FogService {
    @Override
    public float getFogEnd() {
        return ShaderUniforms.INSTANCE.fogEnd();
    }

    @Override
    public float getFogStart() {
        return ShaderUniforms.INSTANCE.fogStart();
    }

    @Override
    public float getFogDensity() {
        return ShaderUniforms.INSTANCE.fogDensity();
    }

    @Override
    public int getFogShapeIndex() {
        return 0;
    }

    @Override
    public float getFogCutoff() {
        return getFogEnd();
    }

    @Override
    public float[] getFogColor() {
        return new float[]{ShaderUniforms.INSTANCE.fogRed(), ShaderUniforms.INSTANCE.fogGreen(), ShaderUniforms.INSTANCE.fogBlue(), 1.0F};
    }

    @Override
    public ChunkFogMode getFogMode() {
        if (!ShaderUniforms.INSTANCE.isFogEnabled()) {
            return ChunkFogMode.NONE;
        }
        return ChunkFogMode.fromGLMode(ShaderUniforms.INSTANCE.fogMode().getGlMode());
    }
}
