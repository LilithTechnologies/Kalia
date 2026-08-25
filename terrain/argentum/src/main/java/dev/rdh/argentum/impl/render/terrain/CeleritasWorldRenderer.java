package dev.rdh.argentum.impl.render.terrain;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.RenderLayer;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.debug.RenderMetrics;
import dev.rdh.argentum.impl.extensions.WorldRendererExtension;
import dev.rdh.argentum.impl.render.terrain.matrix.PrimitiveChunkMatrixGetter;
import re.lilith.kalia.renderer.device.RenderDevice;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.entity.Entity;
import dev.rdh.argentum.impl.render.entity.EntityCullingHook;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;

/**
 * Provides an extension to vanilla's world renderer.
 */
public class CeleritasWorldRenderer extends SimpleWorldRenderer<World, PrimitiveRenderSectionManager, RenderLayer, BlockEntity, Float> {
    /**
     * @return The CeleritasWorldRenderer based on the current dimension
     */
    public static CeleritasWorldRenderer instance() {
        var instance = instanceNullable();

        if (instance == null) {
            throw new IllegalStateException("No renderer attached to active world");
        }

        return instance;
    }

    /**
     * @return The CeleritasWorldRenderer based on the current dimension, or null if none is attached
     */
    public static CeleritasWorldRenderer instanceNullable() {
        var world = MinecraftClient.getInstance().worldRenderer;

        if (world instanceof WorldRendererExtension extension) {
            return extension.sodium$getWorldRenderer();
        }

        return null;
    }

    @Override
    protected void unloadWorld() {
        super.unloadWorld();
    }

    public boolean isRenderingWorld(World world) {
        return this.world == world;
    }

    public static CameraState captureCameraState(double ticks) {
        Entity viewEntity = MinecraftClient.getInstance().getCameraEntity();

        Objects.requireNonNull(viewEntity, "Client must have view entity");

        double x = viewEntity.prevX + (viewEntity.x - viewEntity.prevX) * ticks;
        double y = viewEntity.prevY + (viewEntity.y - viewEntity.prevY) * ticks + (double) viewEntity.getEyeHeight();
        double z = viewEntity.prevZ + (viewEntity.z - viewEntity.prevZ) * ticks;

        float pitch = viewEntity.pitch;
        float yaw = viewEntity.yaw;
        float fogDistance = ChunkShaderFogComponent.FOG_SERVICE.getFogCutoff();

        return new CameraState(x, y, z, pitch, yaw, fogDistance);
    }

    @Override
    public int getEffectiveRenderDistance() {
        return MinecraftClient.getInstance().options.viewDistance;
    }

    @Override
    public int getMinimumBuildHeight() {
        return 0;
    }

    @Override
    public int getMaximumBuildHeight() {
        return this.world.getMaxBuildHeight();
    }

    @Override
    public String getChunksDebugString() {
        return super.getChunksDebugString() + "S: " + this.renderSectionManager.getSectionsWithSkyLight().size();
    }

    @Override
    protected ChunkRenderMatrices createChunkRenderMatrices() {
        return PrimitiveChunkMatrixGetter.getMatrices();
    }

    @Override
    protected PrimitiveRenderSectionManager createRenderSectionManager(RenderDevice device) {
        ChunkTrackerHolder.get(this.world).setRequiredNeighborRadius(Argentum.CONFIG.safeChunkEdges ? 1 : 0);
        ChunkVertexType vertexType = Argentum.CONFIG.compactVertexFormat ? ChunkMeshFormats.COMPACT : ChunkMeshFormats.VANILLA_LIKE;
        return PrimitiveRenderSectionManager.create(vertexType, this.world, this.renderDistance, device);
    }

    public boolean isEntityVisible(Entity entity) {
        if (!Argentum.CONFIG.entityCulling) {
            return true;
        }

        var box = entity.getBoundingBox();
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) {
            return true;
        }

        return this.isEntitySectionVisible(box) && EntityCullingHook.isVisible(entity);
    }

    public boolean isEntitySectionVisible(net.minecraft.util.math.Box box) {
        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public void prepareEntityCulling(List<Entity> entities, Entity camera,
            double cameraX, double cameraY, double cameraZ) {
        if (Argentum.CONFIG.entityCulling) {
            EntityCullingHook.prepare(entities, cameraX, cameraY, cameraZ);
        }
    }

    public boolean isParticleVisible(Particle particle) {
        if (!Argentum.CONFIG.particleCulling || this.getLastViewport() == null) {
            return true;
        }

        var box = particle.getBoundingBox();
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) {
            return true;
        }

        return this.getLastViewport().isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                && this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Override
    protected void renderBlockEntityList(List<BlockEntity> list, Float partialTicksBoxed) {
        float partialTicks = partialTicksBoxed;
        RenderMetrics.Category previous = RenderMetrics.setCategory(RenderMetrics.Category.BLOCK_ENTITY);
        try {
            for (var blockEntity : list) {
                try {
                    RenderMetrics.recordRenderedBlockEntity();
                    BlockEntityRenderDispatcher.INSTANCE.renderEntity(blockEntity, partialTicks, -1);
                } catch(RuntimeException e) {
                    if(blockEntity.isRemoved()) {
                        System.err.println("Suppressing crash from invalid tile entity");
                    } else {
                        throw e;
                    }
                }
            }
        } finally {
            RenderMetrics.setCategory(previous);
        }
    }
}
