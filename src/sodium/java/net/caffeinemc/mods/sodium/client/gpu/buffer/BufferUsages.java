package net.caffeinemc.mods.sodium.client.gpu.buffer;

import net.caffeinemc.mods.sodium.client.gpu.util.EnumBit;

public enum BufferUsages implements EnumBit {
    STORAGE_BUFFER,
    UNIFORM_BUFFER,
    INDEX_BUFFER,
    VERTEX_BUFFER,
    INDIRECT_BUFFER,
    TRANSFER_SRC,
    TRANSFER_DST;

    @Override
    public int getBits() {
        return 1 << this.ordinal();
    }
}
