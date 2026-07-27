package org.embeddedt.embeddium.impl.render.chunk.shader;

import lombok.Getter;

import java.util.List;
import java.util.function.Function;

public enum ChunkFogMode implements ChunkShaderComponent.Factory<ChunkShaderFogComponent> {
    NONE(ChunkShaderFogComponent.None::new, List.of()),
    EXP2(ChunkShaderFogComponent.Exp2::new, List.of("USE_FOG", "USE_FOG_EXP2")),
    SMOOTH(ChunkShaderFogComponent.Smooth::new, List.of("USE_FOG", "USE_FOG_SMOOTH"));

    private final Function<ChunkShaderUniforms, ChunkShaderFogComponent> factory;
    @Getter
    private final List<String> defines;

    ChunkFogMode(Function<ChunkShaderUniforms, ChunkShaderFogComponent> factory, List<String> defines) {
        this.factory = factory;
        this.defines = defines;
    }

    @Override
    public ChunkShaderFogComponent create(ChunkShaderUniforms uniforms) {
        return factory.apply(uniforms);
    }

    public static ChunkFogMode fromGLMode(int mode) {
        return switch (mode) {
            case 0 -> ChunkFogMode.NONE;
            case 0x2601 -> ChunkFogMode.SMOOTH;
            default -> ChunkFogMode.EXP2;
        };
    }
}
