package org.taumc.celeritas.impl.world.cloned;

import net.minecraft.util.math.BlockPos;

public final class BlockPosPool {
    private static final int SIZE = 16 * 16 * 16;

    private final BlockPos[] positions = new BlockPos[SIZE];

    public BlockPosPool(int baseX, int baseY, int baseZ) {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = pack(x, y, z);

                    this.positions[index] = new BlockPos(
                            baseX + x,
                            baseY + y,
                            baseZ + z
                    );
                }
            }
        }
    }

    public BlockPos get(int x, int y, int z) {
        return this.positions[pack(x, y, z)];
    }

    private static int pack(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }
}