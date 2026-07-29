package org.embeddedt.embeddium.impl.render.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gpu.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gpu.shader.ShaderParser;
import org.embeddedt.embeddium.impl.gpu.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderUniforms;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderVariant;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.jetbrains.annotations.Nullable;
import re.lilith.kalia.renderer.command.PassContext;
import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.renderer.pipeline.AttachmentLayout;
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription;
import re.lilith.kalia.renderer.resource.GpuPipeline;
import re.lilith.kalia.renderer.shader.BindingKind;
import re.lilith.kalia.renderer.shader.ShaderBinding;
import re.lilith.kalia.renderer.shader.ShaderProgram;
import re.lilith.kalia.renderer.shader.ShaderSource;
import re.lilith.kalia.renderer.shader.ShaderStage;
import re.lilith.kalia.sodium.KaliaAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private static final Logger LOGGER = LogManager.getLogger(ShaderChunkRenderer.class);

    private static final int PUSH_CONSTANT_BYTES = 16;

    private final Map<ChunkShaderOptions, @Nullable ChunkShaderVariant> variants = new Object2ObjectOpenHashMap<>();

    protected final RenderPassConfiguration<?> renderPassConfiguration;
    protected final RenderDevice device;
    protected final ChunkShaderUniforms uniforms;

    protected ChunkShaderVariant activeVariant;

    public ShaderChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
        this.device = device;
        this.renderPassConfiguration = renderPassConfiguration;
        this.uniforms = new ChunkShaderUniforms(device);
    }

    protected @Nullable ChunkShaderVariant compileVariant(ChunkShaderOptions options) {
        ChunkShaderVariant variant = this.variants.get(options);

        if (variant == null && !this.variants.containsKey(options)) {
            try {
                variant = this.createVariant(options);
            } catch (Exception e) {
                LOGGER.error("There was an error creating a chunk program. Terrain will not render until this is fixed.", e);
            }
            this.variants.put(options, variant);
        }

        return variant;
    }

    private static String loadShaderSource(ShaderType type, ShaderConstants constants) {
        String path = "sodium:blocks/block_layer_opaque." + type.fileExtension;
        return ShaderParser.parseShader(ShaderLoader.getShaderSource(path), ShaderLoader::getShaderSource, constants);
    }

    protected ChunkShaderVariant createVariant(ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();
        TerrainRenderPass pass = options.pass();

        String vertexSource = loadShaderSource(ShaderType.VERTEX, constants);
        String fragmentSource = loadShaderSource(ShaderType.FRAGMENT, constants);

        List<ShaderBinding> bindings = new ArrayList<>();
        bindings.add(new ShaderBinding("u_BlockTex", ChunkShaderUniforms.BLOCK_TEXTURE_BINDING, BindingKind.TEXTURE, Set.of(ShaderStage.FRAGMENT)));
        if (!pass.hasNoLightmap()) {
            bindings.add(new ShaderBinding("u_LightTex", ChunkShaderUniforms.LIGHT_TEXTURE_BINDING, BindingKind.TEXTURE, Set.of(ShaderStage.VERTEX)));
        }
        bindings.add(new ShaderBinding("ChunkSceneUniforms", ChunkShaderUniforms.SCENE_UNIFORMS_BINDING, BindingKind.UNIFORM_BUFFER, Set.of(ShaderStage.VERTEX, ShaderStage.FRAGMENT)));
        bindings.add(new ShaderBinding("ChunkRegionAges", ChunkShaderUniforms.REGION_AGES_BINDING, BindingKind.UNIFORM_BUFFER, Set.of(ShaderStage.VERTEX)));

        ShaderProgram program = new ShaderProgram(
                "sodium:chunk_shader",
                Map.of(
                        ShaderStage.VERTEX, new ShaderSource.Glsl("sodium:blocks/block_layer_opaque.vsh", vertexSource),
                        ShaderStage.FRAGMENT, new ShaderSource.Glsl("sodium:blocks/block_layer_opaque.fsh", fragmentSource)
                ),
                bindings,
                PUSH_CONSTANT_BYTES
        );

        AttachmentLayout attachments = new AttachmentLayout(
                List.of(KaliaAccess.INSTANCE.sceneColorFormat()),
                KaliaAccess.INSTANCE.sceneDepthFormat());

        GpuPipeline pipeline = this.device.createPipeline(new GraphicsPipelineDescription(
                program,
                pass.vertexType().getVertexFormat(),
                attachments,
                pass.raster(),
                pass.depth(),
                pass.blend()
        ));

        List<? extends ChunkShaderComponent> components = options.components().stream()
                .map(c -> c.create(this.uniforms))
                .toList();

        return new ChunkShaderVariant(pipeline, components);
    }

    protected List<ChunkShaderComponent.Factory<?>> getShaderComponents() {
        var componentFactories = new ArrayList<ChunkShaderComponent.Factory<?>>(4);
        componentFactories.add(ChunkShaderFogComponent.FOG_SERVICE.getFogMode());
        return componentFactories;
    }

    protected void begin(PassContext passContext, TerrainRenderPass pass) {
        ChunkShaderOptions options = new ChunkShaderOptions(getShaderComponents(), pass);

        this.activeVariant = this.compileVariant(options);

        if (this.activeVariant != null) {
            passContext.bindPipeline(this.activeVariant.pipeline());
            this.activeVariant.setup();
        }
    }

    protected void end(TerrainRenderPass pass) {
        this.activeVariant = null;
    }

    @Override
    public void delete() {
        this.variants.values().stream().filter(Objects::nonNull)
                .forEach(variant -> variant.pipeline().close());
        this.variants.clear();
        this.uniforms.delete();
    }

    @Override
    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderPassConfiguration;
    }
}
