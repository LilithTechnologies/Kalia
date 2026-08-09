package org.embeddedt.embeddium.impl.render.chunk.shader;

import re.lilith.kalia.renderer.resource.GpuPipeline;

import java.util.List;

public record ChunkShaderVariant(GpuPipeline pipeline, List<? extends ChunkShaderComponent> components) {
    public void setup() {
        for (var component : this.components) {
            component.setup();
        }
    }
}
