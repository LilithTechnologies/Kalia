package dev.rdh.argentum.impl.render.terrain;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.render.RenderLayer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import re.lilith.kalia.renderer.pipeline.BlendState;
import re.lilith.kalia.renderer.pipeline.DepthState;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PrimitiveRenderPassConfigurationBuilder {
    private static TerrainRenderPass.TerrainRenderPassBuilder builderForRenderType(DepthState depth, BlendState blend,
            ChunkVertexType vertexType, Map<String, String> extraDefines) {
        var builder = TerrainRenderPass.builder();
        builder.depth(depth).blend(blend);
        builder.vertexType(vertexType).primitiveType(QuadPrimitiveType.TRIANGULATED).extraDefines(extraDefines);
        return builder;
    }

    public static RenderPassConfiguration<?> build(ChunkVertexType vertexType, boolean translucencySorting,
            int chunkFadeInDuration) {
        Map<String, String> extraDefines = chunkFadeInDuration > 0
                ? Map.of("CHUNK_FADE_IN_DURATION_MS", Integer.toString(chunkFadeInDuration))
                : Map.of();
        TerrainRenderPass solidPass = builderForRenderType(DepthState.READ_WRITE, BlendState.OPAQUE, vertexType, extraDefines)
                .name("solid")
                .fragmentDiscard(false)
                .useReverseOrder(false)
                .build();
        TerrainRenderPass cutoutMippedPass = builderForRenderType(DepthState.READ_WRITE, BlendState.OPAQUE, vertexType, extraDefines)
                .name("cutout_mipped")
                .fragmentDiscard(true)
                .useReverseOrder(false)
                .build();
        TerrainRenderPass translucentPass = builderForRenderType(DepthState.READ_ONLY, BlendState.ALPHA, vertexType, extraDefines)
                .name("translucent")
                .fragmentDiscard(false)
                .useReverseOrder(true)
                .useTranslucencySorting(translucencySorting)
                .build();
        Material translucentMaterial = new Material(translucentPass, AlphaCutoffParameter.ZERO, true);
        Material solidMaterial = new Material(solidPass, AlphaCutoffParameter.ZERO, true);
        Material cutoutMippedMaterial = new Material(cutoutMippedPass, AlphaCutoffParameter.ONE_TENTH, true);
        Material cutoutMaterial = new Material(cutoutMippedPass, AlphaCutoffParameter.ONE_TENTH, false);

        Map<RenderLayer, Collection<TerrainRenderPass>> vanillaRenderStages = new Reference2ReferenceOpenHashMap<>();
        vanillaRenderStages.put(RenderLayer.SOLID, List.of(solidPass, cutoutMippedPass));
        vanillaRenderStages.put(RenderLayer.TRANSLUCENT, List.of(translucentPass));

        Map<RenderLayer, Material> renderTypeToMaterialMap = new Reference2ReferenceOpenHashMap<>();
        renderTypeToMaterialMap.put(RenderLayer.SOLID, solidMaterial);
        renderTypeToMaterialMap.put(RenderLayer.CUTOUT, cutoutMaterial);
        renderTypeToMaterialMap.put(RenderLayer.CUTOUT_MIPPED, cutoutMippedMaterial);
        renderTypeToMaterialMap.put(RenderLayer.TRANSLUCENT, translucentMaterial);

        return new RenderPassConfiguration<>(renderTypeToMaterialMap,
                vanillaRenderStages,
                cutoutMippedMaterial,
                cutoutMippedMaterial,
                translucentMaterial);
    }
}
