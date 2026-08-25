package dev.rdh.argentum.impl.render.terrain;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.world.ChunkRenderFactory;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class NoopRenderChunkStorage extends BuiltChunkStorage {
    private static final BuiltChunk[] EMPTY_CHUNKS = new BuiltChunk[0];

    public NoopRenderChunkStorage(World world, int viewDistance, WorldRenderer renderer, ChunkRenderFactory factory) {
        super(world, viewDistance, renderer, factory);
        this.chunks = EMPTY_CHUNKS;
    }

    @Override
    protected void createChunks(ChunkRenderFactory factory) {
    }

    @Override
    protected void setViewDistance(int viewDistance) {
    }

    @Override
    public void clear() {
    }

    @Override
    public void updateCameraPosition(double x, double z) {
    }

    @Override
    public void scheduleRebuild(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    @Override
    protected BuiltChunk getRenderedChunk(BlockPos pos) {
        return null;
    }
}
