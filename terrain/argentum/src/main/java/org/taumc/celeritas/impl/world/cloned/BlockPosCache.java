package org.taumc.celeritas.impl.world.cloned;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;

public final class BlockPosCache {
    private final ArrayDeque<BlockPos.Mutable> pool = new ArrayDeque<>();

    public BlockPos.Mutable acquire(int x, int y, int z) {
        BlockPos.Mutable pos = pool.pollFirst();

        if (pos == null) {
            pos = new BlockPos.Mutable(x, y, z);
        } else {
            pos.setPosition(x, y, z);
        }

        return pos;
    }

    public void release(BlockPos.Mutable pos) {
        if (pos != null) {
            pool.offerFirst(pos);
        }
    }

    public static BlockPos immutable(BlockPos pos) {
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }
}