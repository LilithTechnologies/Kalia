package org.embeddedt.embeddium.impl.render.chunk.shader;

import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;

import java.util.ServiceLoader;

/**
 * These shader implementations try to remain compatible with the deprecated fixed function pipeline by manually
 * copying the state into each shader's uniforms. The shader code itself is a straight-forward implementation of the
 * fog functions themselves from the fixed-function pipeline, except that they use the distance from the camera
 * rather than the z-buffer to produce better looking fog that doesn't move with the player's view angle.
 *
 * Minecraft itself will actually try to enable distance-based fog by using the proprietary NV_fog_distance extension,
 * but as the name implies, this only works on graphics cards produced by NVIDIA. The shader implementation however does
 * not depend on any vendor-specific extensions and is written using very simple GLSL code.
 */
public abstract class ChunkShaderFogComponent implements ChunkShaderComponent {
    public static final FogService FOG_SERVICE = ServiceLoader.load(FogService.class).findFirst().orElseThrow();

    protected final ChunkShaderUniforms uniforms;

    protected ChunkShaderFogComponent(ChunkShaderUniforms uniforms) {
        this.uniforms = uniforms;
    }

    public static class None extends ChunkShaderFogComponent {
        public None(ChunkShaderUniforms uniforms) {
            super(uniforms);
        }

        @Override
        public void setup() {
            this.uniforms.setFogShape(0);
        }
    }

    public static class Exp2 extends ChunkShaderFogComponent {
        public Exp2(ChunkShaderUniforms uniforms) {
            super(uniforms);
        }

        @Override
        public void setup() {
            this.uniforms.setFogShape(0);
            this.uniforms.setFogColor(FOG_SERVICE.getFogColor());
            this.uniforms.setFogDensity(FOG_SERVICE.getFogDensity());
        }
    }

    public static class Smooth extends ChunkShaderFogComponent {
        public Smooth(ChunkShaderUniforms uniforms) {
            super(uniforms);
        }

        @Override
        public void setup() {
            this.uniforms.setFogColor(FOG_SERVICE.getFogColor());
            this.uniforms.setFogShape(FOG_SERVICE.getFogShapeIndex());
            this.uniforms.setFogRange(FOG_SERVICE.getFogStart(), FOG_SERVICE.getFogEnd());
        }
    }
}
