package net.caffeinemc.mods.sodium.mixin.sodium.core.render.world;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.SimpleFrustum;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.gl.GlEnums;
import re.lilith.kalia.gl.MatrixState;

import java.util.List;
import java.util.Map;

@Mixin(WorldRenderer.class)
public abstract class LevelRendererMixin implements LevelRendererExtension {
    @Shadow
    @Final
    private Map<Integer, BlockBreakingInfo> blockBreakingInfos;
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    private int renderedEntityCount;
    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;
    @Shadow
    private ClientWorld world;
    @Shadow
    private int totalEntityCount;
    @Unique
    private SodiumWorldRenderer renderer;

    @Unique
    private final BlockPos.Mutable entityBlockPos = new BlockPos.Mutable();

    @Override
    public SodiumWorldRenderer sodium$getWorldRenderer() {
        return this.renderer;
    }

    @Redirect(method = "reload()V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;viewDistance:I", ordinal = 1, opcode = Opcodes.GETFIELD))
    private int nullifyBuiltChunkStorage(GameOptions instance) {
        // Do not allow any resources to be allocated
        return 0;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(MinecraftClient client, CallbackInfo ci) {
        this.renderer = new SodiumWorldRenderer(client);
    }

    @Inject(method = "setWorld", at = @At("RETURN"))
    private void onWorldChanged(ClientWorld level, CallbackInfo ci) {
        this.renderer.setLevel(level);
    }


    @Inject(method = "scheduleTerrainUpdate", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }

    @Unique
    private final Matrix4f graphite$projectionScratch = new Matrix4f();
    @Unique
    private final Matrix4f graphite$modelViewScratch = new Matrix4f();
    @Unique
    private final ChunkRenderMatrices graphite$chunkRenderMatrices =
            new ChunkRenderMatrices(graphite$projectionScratch, graphite$modelViewScratch);

    /**
     * @reason Redirect the chunk layer render passes to our renderer
     * @author JellySquid
     */
    @Overwrite
    public int renderLayer(RenderLayer renderLayer, double tickDelta, int anaglyphFilter, Entity entity) {
        double x = entity.prevTickX + (entity.x - entity.prevTickX) * tickDelta;
        double y = entity.prevTickY + (entity.y - entity.prevTickY) * tickDelta;
        double z = entity.prevTickZ + (entity.z - entity.prevTickZ) * tickDelta;

        MatrixState.INSTANCE.matrixMode(GlEnums.GL_PROJECTION);
        MatrixState.INSTANCE.pushMatrix();
        MatrixState.INSTANCE.matrixMode(GlEnums.GL_MODELVIEW);
        MatrixState.INSTANCE.pushMatrix();

        this.graphite$projectionScratch.set(MatrixState.INSTANCE.projection());
        this.graphite$modelViewScratch.set(MatrixState.INSTANCE.modelView());

        this.client.gameRenderer.enableLightmap();
        this.renderer.drawChunkLayer(renderLayer, this.graphite$chunkRenderMatrices, x, y, z);
        this.client.gameRenderer.disableLightmap();

        MatrixState.INSTANCE.matrixMode(GlEnums.GL_PROJECTION);
        MatrixState.INSTANCE.popMatrix();
        MatrixState.INSTANCE.matrixMode(GlEnums.GL_MODELVIEW);
        MatrixState.INSTANCE.popMatrix();

        return 0;
    }

    /**
     * @reason Redirect the terrain setup phase to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setupTerrain(Entity entity, double tickDelta, CameraView cameraView, int frame, boolean spectator) {
        var frustum = new SimpleFrustum((CullingCameraView) cameraView);
        var transform = entity.getCameraPosVec((float) tickDelta);
        var viewport = new Viewport(frustum, transform);
        var updateChunksImmediately = false;

        this.renderer.setupTerrain(viewport, spectator, updateChunksImmediately);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void updateBlock(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, true);
    }

    /**
     * @reason Redirect the updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void updateChunks(long p) {
    }

    @Inject(method = "reload()V", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        this.renderer.reload();
    }

    /**
     * @author Decencies
     * @reason Redirect entities to our renderer
     */
    @Overwrite
    public void renderEntities(Entity player, CameraView camera, float partialTicks) {
        this.world.profiler.push("prepare");
        Entity renderView = client.getCameraEntity();

        BlockEntityRenderDispatcher.INSTANCE
                .updateCamera(world, client.getTextureManager(), client.textRenderer, renderView, partialTicks);

        entityRenderDispatcher
                .updateCamera(world, client.textRenderer, renderView, client.targetedEntity, client.options, partialTicks);

        double renderX = renderView.prevTickX + (renderView.x - renderView.prevTickX) * partialTicks;
        double renderY = renderView.prevTickY + (renderView.y - renderView.prevTickY) * partialTicks;
        double renderZ = renderView.prevTickZ + (renderView.z - renderView.prevTickZ) * partialTicks;
        BlockEntityRenderDispatcher.CAMERA_X = renderX;
        BlockEntityRenderDispatcher.CAMERA_Y = renderY;
        BlockEntityRenderDispatcher.CAMERA_Z = renderZ;

        entityRenderDispatcher.updateCamera(renderX, renderY, renderZ);
        client.gameRenderer.enableLightmap();

        List<Entity> entities = this.world.getLoadedEntities();
        totalEntityCount = entities.size();
        renderedEntityCount = 0;

        world.profiler.swap("entities");

        boolean isSleeping = renderView instanceof LivingEntity && ((LivingEntity) renderView).isSleeping();
        int perspective = client.options.perspective;
        SodiumWorldRenderer sodiumRenderer = this.renderer;

        for (int i = 0, size = entities.size(); i < size; i++) {
            Entity entity = entities.get(i);

            if (!entity.shouldRender(renderX, renderY, renderZ)) {
                if (!entity.hasVehicle() || entity.rider != null) {
                    continue;
                }
            }

            if (!sodiumRenderer.isEntityVisible(entity)) {
                continue;
            }

            if (entity == renderView && perspective == 0 && !isSleeping) {
                continue;
            }

            if (entity.y < 0.0D || entity.y >= 256.0D) {
                continue;
            }

            entityBlockPos.setPosition((int) entity.x, (int) entity.y, (int) entity.z);
            if (!this.world.blockExists(entityBlockPos)) {
                continue;
            }

            ++this.renderedEntityCount;
            this.entityRenderDispatcher.method_6915(entity, partialTicks, false);
        }

        renderer.renderBlockEntities(blockBreakingInfos, partialTicks);

        client.gameRenderer.disableLightmap();
        client.profiler.pop();
    }

    /**
     * @reason Redirect to our renderer
     * @author Lunasa
     */
    @Overwrite
    public String getChunksDebugString() {
        return this.renderer.getChunksDebugString();
    }
}
