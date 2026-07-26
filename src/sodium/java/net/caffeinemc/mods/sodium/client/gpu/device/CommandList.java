package net.caffeinemc.mods.sodium.client.gpu.device;

import net.caffeinemc.mods.sodium.client.gpu.KaliaAccess;
import net.caffeinemc.mods.sodium.client.gpu.buffer.BufferUsages;
import net.caffeinemc.mods.sodium.client.gpu.buffer.DeviceBuffer;
import net.caffeinemc.mods.sodium.client.gpu.buffer.MappingType;
import net.caffeinemc.mods.sodium.client.gpu.util.EnumBitField;
import re.lilith.kalia.renderer.resource.BufferDescription;
import re.lilith.kalia.renderer.resource.BufferUsage;

public final class CommandList implements AutoCloseable {
    public DeviceBuffer createBuffer(long bufferSize, MappingType mappingType, EnumBitField<BufferUsages> flags) {
        var description = new BufferDescription(
                "sodium/buffer",
                bufferSize,
                mappingType == MappingType.CPU_ONLY ? BufferUsage.STREAM : BufferUsage.STATIC,
                flags.contains(BufferUsages.VERTEX_BUFFER),
                flags.contains(BufferUsages.INDEX_BUFFER),
                flags.contains(BufferUsages.UNIFORM_BUFFER),
                flags.contains(BufferUsages.INDIRECT_BUFFER),
                flags.contains(BufferUsages.TRANSFER_SRC) || flags.contains(BufferUsages.TRANSFER_DST)
        );

        return new DeviceBuffer(KaliaAccess.device().createBuffer(description));
    }

    public void copyBufferToBuffer(DeviceBuffer src, DeviceBuffer dst, long readOffset, long writeOffset, long bytes) {
        KaliaAccess.device().copyBuffer(src.gpu(), dst.gpu(), readOffset, writeOffset, bytes);
    }

    public void deleteBuffer(DeviceBuffer buffer) {
        buffer.delete();
    }

    @Override
    public void close() {
    }
}
