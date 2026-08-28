package dev.rdh.argentum.impl.render.terrain.compile.task;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.crash.CrashReportSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.joml.Vector3d;
import dev.rdh.argentum.impl.debug.RenderMetrics;
import dev.rdh.argentum.impl.render.terrain.VoxelizationHook;
import dev.rdh.argentum.impl.render.terrain.compile.PrimitiveBuiltRenderSectionData;
import dev.rdh.argentum.impl.render.terrain.compile.PrimitiveChunkBuildContext;
import dev.rdh.argentum.impl.render.terrain.occlusion.ChunkOcclusionDataBuilder;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;

public class ChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
    private final RenderSection render;
    private final int buildTime;
    private final Vector3d camera;
    private final ChunkRenderContext renderContext;

    public ChunkBuilderMeshingTask(RenderSection render, ChunkRenderContext context, int time, Vector3d camera) {
        this.render = render;
        this.buildTime = time;
        this.camera = camera;
        this.renderContext = context;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        long started = System.nanoTime();
        try {
            return this.executeTimed(context, cancellationToken);
        } finally {
            RenderMetrics.recordChunkBuild(System.nanoTime() - started);
        }
    }

    private ChunkBuildOutput executeTimed(ChunkBuildContext context, CancellationToken cancellationToken) {
        PrimitiveChunkBuildContext buildContext = (PrimitiveChunkBuildContext)context;
        var renderData = new PrimitiveBuiltRenderSectionData();

        ChunkBuildBuffers buffers = buildContext.buffers;
        buffers.init(renderData, this.render.getSectionIndex());

        int minX = this.render.getOriginX();
        int minY = this.render.getOriginY();
        int minZ = this.render.getOriginZ();

        int maxX = minX + 16;
        int maxY = minY + 16;
        int maxZ = minZ + 16;

        // Initialise with minX/minY/minZ so initial getBlockState crash context is correct

        var blockPos = new BlockPos.Mutable(minX, minY, minZ);
        var renderBlocks = MinecraftClient.getInstance().getBlockRenderManager();

        buildContext.beginSection(this.renderContext, minX, minY, minZ);
        ChunkOcclusionDataBuilder occluder = buildContext.getOcclusionBuilder();

        VoxelizationHook.SectionWriter voxels = VoxelizationHook.begin(minX, minY, minZ);
        boolean voxelsCommitted = false;
        // The section still has to be walked for voxels, occlusion and block entities, but the
        // geometry it would have produced is never drawn, so none of it is built.
        boolean buildGeometry = !VoxelizationHook.meshingDisabled();

        try {
            for (int y = minY; y < maxY; y++) {
                if (cancellationToken.isCancelled()) {
                    if (voxels != null) {
                        voxels.abort();
                        voxelsCommitted = true;
                    }
                    return null;
                }

                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        blockPos.setPosition(x, y, z);

                        var blockState = this.renderContext.getBlockState(blockPos);
                        var block = blockState.getBlock();

                        if (block == Blocks.AIR) {
                            continue;
                        }

                        if (voxels != null) {
                            voxels.voxel(x - minX, y - minY, z - minZ, blockState, this.faceLight(x, y, z));
                        }

						if (block.hasBlockEntity()) {
                            BlockEntity blockEntity = this.renderContext.getBlockEntity(blockPos);
                            if (blockEntity != null) {
                                var renderer = BlockEntityRenderDispatcher.INSTANCE.getRenderer(blockEntity);
                                if (renderer != null) {
                                    (renderer.rendersOutsideBoundingBox() ? renderData.globalBlockEntities : renderData.culledBlockEntities).add(blockEntity);
                                }
                            }
                        }

                        if (buildGeometry) {
                            var pass = block.getRenderLayerType();

                            if (block.getBlockType() == 3) {
                                buildContext.getBlockRenderer().render(blockState, blockPos, this.renderContext, pass,
                                        buffers, renderData);
                            } else {
                                renderBlocks.renderBlock(blockState, blockPos, this.renderContext, buildContext.getBuffer(pass));
                            }
                        }

						if (block.isFullBlock()) {
                            occluder.markClosed(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                        }
                    }
                }
            }
            voxels = commitVoxels(voxels);
            voxelsCommitted = true;
        } catch (Throwable exception) {
            throw this.addCrashContext(CrashReport.create(exception, "Encountered exception while building chunk meshes"), blockPos);
        } finally {
            if (!voxelsCommitted && voxels != null) {
                voxels.abort();
            }
        }

        buildContext.finishSection(buffers);

        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes = BuiltSectionMeshParts.groupFromBuildBuffers(buffers,(float)camera.x - minX, (float)camera.y - minY, (float)camera.z - minZ);

        if (!meshes.isEmpty()) {
            renderData.hasBlockGeometry = true;
        }

        renderData.visibilityData = occluder.computeVisibilityEncoding();

        return new ChunkBuildOutput(this.render, renderData, meshes, this.buildTime);
    }

    private static final int[] NEIGHBOUR_X = { -1, 1, 0, 0, 0, 0 };
    private static final int[] NEIGHBOUR_Y = { 0, 0, -1, 1, 0, 0 };
    private static final int[] NEIGHBOUR_Z = { 0, 0, 0, 0, -1, 1 };

    /**
     * The brightest light reaching any face of a block, as {@code sky << 4 | block}.
     *
     * A solid block's own cell is unlit, so sampling it would leave every surface black. Vanilla
     * lights each face from the cell in front of it; taking the maximum over all six is one value
     * per voxel instead of six and is indistinguishable on anything but a lone floating block.
     */
    private int faceLight(int x, int y, int z) {
        int sky = 0;
        int block = 0;
        for (int side = 0; side < 6; side++) {
            int packed = this.renderContext.getLightColor(
                    x + NEIGHBOUR_X[side], y + NEIGHBOUR_Y[side], z + NEIGHBOUR_Z[side], 0);
            int neighbourSky = (packed >> 20) & 15;
            int neighbourBlock = (packed >> 4) & 15;
            if (neighbourSky > sky) {
                sky = neighbourSky;
            }
            if (neighbourBlock > block) {
                block = neighbourBlock;
            }
        }
        return (sky << 4) | block;
    }

    private static VoxelizationHook.SectionWriter commitVoxels(VoxelizationHook.SectionWriter writer) {
        if (writer != null) {
            writer.commit();
        }
        return null;
    }

    private CrashException addCrashContext(CrashReport report, BlockPos pos) {
        CrashReportSection category = report.addElement("Block being rendered");
        try {
            CrashReportSection.addBlockInfo(category, pos, this.renderContext.getBlockState(pos));
        } catch (Throwable ignored) {
            category.add("Block location", CrashReportSection.addBlockData(pos));
        }
        category.add("Chunk section", this.render);
        return new CrashException(report);
    }

}
