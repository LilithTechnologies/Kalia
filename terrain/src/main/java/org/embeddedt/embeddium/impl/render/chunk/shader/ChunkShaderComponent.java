package org.embeddedt.embeddium.impl.render.chunk.shader;

import java.util.Collection;
import java.util.List;

public interface ChunkShaderComponent {
    void setup();

    interface Factory<T extends ChunkShaderComponent> {
        T create(ChunkShaderUniforms uniforms);

        default Collection<String> getDefines() {
            return List.of();
        }
    }
}
