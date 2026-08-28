package org.embeddedt.embeddium.impl.render.chunk.shader;

import org.embeddedt.embeddium.impl.gpu.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

import java.util.List;

public record ChunkShaderOptions(List<ChunkShaderComponent.Factory<?>> components, TerrainRenderPass pass) {

    /**
     * {@return whether this pass writes a geometry buffer rather than a lit image}
     * <p>
     * Only solid geometry can: blended passes have to composite over an image that
     * has already been lit, so they stay forward-rendered with a single attachment.
     * The shader variant and the pipeline's attachment layout both follow from
     * this, and they have to agree or the pipeline cannot be bound.
     */
    public boolean usesGeometryBuffer() {
        return !this.pass.isBlended() && re.lilith.kalia.sodium.KaliaAccess.INSTANCE.gbufferEnabled();
    }

    public ShaderConstants constants() {
        ShaderConstants.Builder constants = ShaderConstants.builder();
        for (var component : components) {
            constants.addAll(component.getDefines());
        }

        if (this.pass.supportsFragmentDiscard()) {
            constants.add("USE_FRAGMENT_DISCARD");
        }

        if (this.pass.hasNoLightmap()) {
            constants.add("CELERITAS_NO_LIGHTMAP");
        }

        // Terrain becomes a geometry buffer rather than a finished image: the
        // shaders emit albedo, normal and light coordinates and leave the lighting
        // to whatever consumes them.
        if (this.usesGeometryBuffer()) {
            constants.add("KALIA_GBUFFER");
        }

        constants.addAll(pass.extraDefines());

        var vertexType = pass.vertexType();
        var primitiveType = pass.primitiveType();

        vertexType.getDefines().forEach(constants::add);
        constants.addAll(primitiveType.getDefines());

        return constants.build();
    }
}
