package net.caffeinemc.mods.sodium.client.gpu.buffer;

import org.jetbrains.annotations.Nullable;
import re.lilith.kalia.renderer.resource.GpuBuffer;

import java.nio.ByteBuffer;

public class DeviceBuffer {
    private final GpuBuffer buffer;
    private final @Nullable BufferMapping mapping;

    public DeviceBuffer(GpuBuffer buffer) {
        this.buffer = buffer;
        ByteBuffer mapped = buffer.mapped();
        this.mapping = mapped != null ? new BufferMapping(this, mapped) : null;
    }

    public GpuBuffer gpu() {
        return this.buffer;
    }

    public long getSize() {
        return this.buffer.getSizeBytes();
    }

    public @Nullable BufferMapping getMapping() {
        return this.mapping;
    }

    public void delete() {
        this.buffer.close();
    }
}
