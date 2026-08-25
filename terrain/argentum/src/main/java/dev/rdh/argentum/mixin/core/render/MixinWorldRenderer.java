package dev.rdh.argentum.mixin.core.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.*;
import net.minecraft.client.render.world.ChunkRenderFactory;
import net.minecraft.entity.LivingEntity;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.objectweb.asm.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.extensions.WorldRendererExtension;
import dev.rdh.argentum.impl.debug.RenderMetrics;
import dev.rdh.argentum.impl.render.entity.EntityGatherer;
import dev.rdh.argentum.impl.render.terrain.CeleritasWorldRenderer;
import dev.rdh.argentum.impl.render.terrain.NoopRenderChunkStorage;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Mixin(value = WorldRenderer.class, priority = 900)
public abstract class MixinWorldRenderer implements WorldRendererExtension {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private int renderDistance;

    @Shadow
    public abstract void reload();

    @Shadow
    private ClientWorld world;
    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    private int renderedEntityCount;

    @Shadow
    private boolean needsTerrainUpdate;

    @Unique
    private CeleritasWorldRenderer renderer;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        this.renderer = new CeleritasWorldRenderer();
    }

    @Override
    public CeleritasWorldRenderer sodium$getWorldRenderer() {
        return this.renderer;
    }

    @Inject(method = "setWorld", at = @At("RETURN"))
    private void onWorldChanged(@Coerce World world, CallbackInfo ci) {
        this.renderer.setWorld(world);
    }

    /**
     * @reason Redirect the chunk layer render passes to our renderer
     * @author JellySquid
     */
    @Overwrite
    public int renderLayer(RenderLayer layer, double ticks, int anaglyphRenderPass, Entity viewEntity) {
        DiffuseLighting.disable();

        double d3 = viewEntity.prevX + (viewEntity.x - viewEntity.prevX) * ticks;
        // Do not apply eye height here or weird offsets will happen
        double d4 = viewEntity.prevY + (viewEntity.y - viewEntity.prevY) * ticks;
        double d5 = viewEntity.prevZ + (viewEntity.z - viewEntity.prevZ) * ticks;

        this.client.gameRenderer.enableLightmap();

        this.renderer.drawChunkLayer(layer, d3, d4, d5);

        this.client.gameRenderer.disableLightmap();

        return 1;
    }

    @Unique
    private int frame = 0;

    /**
     * @reason Redirect the terrain setup phase to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setupTerrain(Entity camera, double tickDelta, CameraView culler, int frame, boolean loadChunks) {
        if (this.client.options.viewDistance != this.renderDistance) {
            this.reload();
        }

        if (this.needsTerrainUpdate) {
            this.renderer.getRenderSectionManager().markGraphDirty();
            this.needsTerrainUpdate = false;
        }

        updateFrustums(culler, (float)tickDelta);
    }

    @Unique
    public void updateFrustums(CameraView camera, float tick) {
        this.renderer.setupTerrain(((ViewportProvider)camera).sodium$createViewport(),
                CeleritasWorldRenderer.captureCameraState(tick),
                this.frame++, this.client.player.noClip, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void updateBlock(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    @Inject(method = "reload()V", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        if (!this.renderer.isRenderingWorld(this.world)) {
            return;
        }

        this.renderer.reload();
    }

    @Redirect(
            method = "reload()V",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/World;ILnet/minecraft/client/render/WorldRenderer;Lnet/minecraft/client/render/world/ChunkRenderFactory;)Lnet/minecraft/client/render/BuiltChunkStorage;")
    )
    private BuiltChunkStorage celeritas$skipVanillaChunkStorage(World world, int viewDistance,
                                                                WorldRenderer renderer, ChunkRenderFactory factory) {
        return new NoopRenderChunkStorage(world, viewDistance, renderer, factory);
    }

    /**
     * @author embeddedt
     * @reason Disable vanilla chunk compilation
     */
    @Overwrite
    public void updateChunks(long time) {

    }

    @Inject(method = "renderEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/WorldRenderer;noCullingBlockEntities:Ljava/util/Set;", ordinal = 0, opcode = Opcodes.GETFIELD))
    public void sodium$renderTileEntities(CallbackInfo ci, @Local(ordinal = 0, argsOnly = true) float partialTicks) {
        this.renderer.renderBlockEntities(partialTicks);
    }

    @Unique
    private final EntityGatherer celeritas$entityGatherer = new EntityGatherer();

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=entities"))
    private void celeritas$renderEntities(Entity camera, CameraView culler, float tickDelta, CallbackInfo ci, @Local(ordinal = 0) double d, @Local(ordinal = 1) double e, @Local(ordinal = 2) double g) {
        celeritas$entityGatherer.clear();
        var entityList = celeritas$entityGatherer.getLoadedEntityList(this.world);
        this.renderer.prepareEntityCulling(entityList, camera, d, e, g);

        BlockPos.Mutable entityBlockPos = new BlockPos.Mutable();

        for (Entity entity : entityList) {
            boolean visible = this.entityRenderDispatcher.shouldRender(entity, culler, d, e, g);
            if (visible && !this.renderer.isEntityVisible(entity)) {
                RenderMetrics.recordCulledEntity();
                visible = false;
            }

            if (!visible && entity.rider != this.client.player) {
                if (entity instanceof WitherSkullEntity) {
                    this.client.getEntityRenderManager().method_10204(entity, tickDelta);
                }
                continue;
            }

            boolean isSelfSleeping = this.client.getCameraEntity() instanceof LivingEntity le && le.isSleeping();
            if (entity == this.client.getCameraEntity() && this.client.options.perspective == 0 && !isSelfSleeping) {
                continue;
            }

            if (entity.y >= 0.0 && entity.y < 256.0) {
                entityBlockPos.setPosition(MathHelper.floor(entity.x), MathHelper.floor(entity.y), MathHelper.floor(entity.z));
                if (!this.world.blockExists(entityBlockPos)) {
                    continue;
                }
            }

            this.renderedEntityCount++;
            RenderMetrics.recordRenderedEntity();
            RenderMetrics.Category previous = RenderMetrics.setCategory(RenderMetrics.Category.ENTITY);
            try {
                this.entityRenderDispatcher.renderEntity(entity, tickDelta);
            } finally {
                RenderMetrics.setCategory(previous);
            }
        }
    }

    @Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0))
    private Iterator<?> celeritas$skipVanillaEntityChunks(List<?> chunks) {
        return Collections.emptyIterator();
    }

    /**
     * @reason Replace the debug string
     * @author JellySquid
     */
    @Overwrite
    public String getChunksDebugString() {
        return this.renderer.getChunksDebugString();
    }
}
