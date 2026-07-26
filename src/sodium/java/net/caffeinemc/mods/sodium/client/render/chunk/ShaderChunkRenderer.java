package net.caffeinemc.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.caffeinemc.mods.sodium.client.gpu.KaliaAccess;
import net.caffeinemc.mods.sodium.client.gpu.attribute.MeshVertexFormat;
import net.caffeinemc.mods.sodium.client.gpu.device.CommandList;
import net.caffeinemc.mods.sodium.client.gpu.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import re.lilith.kalia.renderer.command.PassEncoder;
import re.lilith.kalia.renderer.pipeline.*;
import re.lilith.kalia.renderer.resource.GpuPipeline;
import re.lilith.kalia.renderer.shader.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private static ShaderProgram opaqueProgram;
    private static ShaderProgram cutoutProgram;

    private final Map<TerrainRenderPass, GpuPipeline> pipelines = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final MeshVertexFormat vertexFormat;

    protected final RenderDevice device;
    protected final ChunkShaderInterface shaderInterface = new DefaultShaderInterface();

    protected GpuPipeline activePipeline;

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getVertexFormat();

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            this.compilePipeline(pass);
        }
    }

    protected GpuPipeline compilePipeline(TerrainRenderPass pass) {
        return this.pipelines.computeIfAbsent(pass, this::createPipeline);
    }

    private GpuPipeline createPipeline(TerrainRenderPass pass) {
        var description = new GraphicsPipelineDescription(
                program(pass.supportsFragmentDiscard()),
                this.vertexFormat.kalia(),
                new AttachmentLayout(List.of(KaliaAccess.sceneColorFormat()), KaliaAccess.sceneDepthFormat()),
                new RasterState(PrimitiveTopology.TRIANGLES, CullMode.BACK, FrontFace.COUNTER_CLOCKWISE, PolygonMode.FILL, false),
                new DepthState(true, true, CompareFunction.LESS_EQUAL),
                pass.isTranslucent() ? BlendState.Companion.getALPHA() : BlendState.Companion.getOPAQUE(),
                ColorMask.Companion.getALL()
        );

        return KaliaAccess.device().createPipeline(description);
    }

    private static ShaderProgram program(boolean fragmentDiscard) {
        if (fragmentDiscard) {
            if (cutoutProgram == null) {
                cutoutProgram = createProgram(true);
            }
            return cutoutProgram;
        }
        if (opaqueProgram == null) {
            opaqueProgram = createProgram(false);
        }
        return opaqueProgram;
    }

    private static ShaderProgram createProgram(boolean fragmentDiscard) {
        String vertex = loadShader("terrain.vert");
        String fragment = loadShader("terrain.frag");
        String fragmentName = "terrain.frag";

        if (fragmentDiscard) {
            fragment = fragment.replace("#version 450", "#version 450\n#define IS_CUTOUT 1");
            fragmentName = "terrain.cutout.frag";
        }

        return new ShaderProgram(
                fragmentDiscard ? "sodium/terrain/cutout" : "sodium/terrain",
                Map.of(
                        ShaderStage.VERTEX, new ShaderSource.Glsl("terrain.vert", vertex),
                        ShaderStage.FRAGMENT, new ShaderSource.Glsl(fragmentName, fragment)
                ),
                List.of(
                        new ShaderBinding("blockAtlas", 0, BindingKind.TEXTURE, Set.of(ShaderStage.FRAGMENT)),
                        new ShaderBinding("lightTex", 1, BindingKind.TEXTURE, Set.of(ShaderStage.VERTEX))
                ),
                DefaultShaderInterface.PUSH_CONSTANT_SIZE
        );
    }

    private static String loadShader(String name) {
        String path = "/assets/kalia/shaders/" + name;
        try (InputStream source = ShaderChunkRenderer.class.getResourceAsStream(path)) {
            if (source == null) {
                throw new IllegalStateException("Missing terrain shader " + path);
            }
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read terrain shader " + path, failure);
        }
    }

    protected void bindTextures(PassEncoder pass, KaliaAccess.TextureBinding atlas, KaliaAccess.TextureBinding lightmap) {
        pass.bindTexture(0, atlas.texture, atlas.sampler);
        pass.bindTexture(1, lightmap.texture, lightmap.sampler);
    }

    protected ChunkShaderInterface begin(PassEncoder encoder, TerrainRenderPass pass) {
        this.activePipeline = this.compilePipeline(pass);
        encoder.bindPipeline(this.activePipeline);
        this.shaderInterface.setupState(pass);
        return this.shaderInterface;
    }

    protected void end(TerrainRenderPass pass) {
        this.shaderInterface.resetState();
        this.activePipeline = null;
    }

    @Override
    public void delete(CommandList commandList) {
        // Pipelines belong to the device's cache and are shared, so nothing is freed here.
        this.pipelines.clear();
    }
}
