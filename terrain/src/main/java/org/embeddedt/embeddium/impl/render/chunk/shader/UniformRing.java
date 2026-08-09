package org.embeddedt.embeddium.impl.render.chunk.shader;

import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.renderer.resource.BufferDescription;
import re.lilith.kalia.renderer.resource.BufferUsage;
import re.lilith.kalia.renderer.resource.GpuBuffer;

import java.nio.ByteBuffer;

public final class UniformRing implements AutoCloseable {
    private static final long ALIGNMENT = 256L;

    private final RenderDevice device;
    private final String label;
    private final long stride;
    private final int bands;

    private GpuBuffer buffer;
    private int slicesPerBand;
    private int band;
    private int next;
    private int peak;

    public UniformRing(RenderDevice device, String label, int sizeBytes, int initialSlices) {
        this.device = device;
        this.label = label;
        this.stride = (sizeBytes + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT;
        this.bands = Math.max(1, device.getCapabilities().getFramesInFlight());
        this.slicesPerBand = Math.max(1, initialSlices);
        this.buffer = allocate();
    }

    private GpuBuffer allocate() {
        long size = this.stride * (long) this.slicesPerBand * this.bands;
        return this.device.createBuffer(new BufferDescription(this.label, size, BufferUsage.STREAM,
                false, false, true, false, false));
    }

    public GpuBuffer getBuffer() {
        return this.buffer;
    }

    public long getSliceBytes() {
        return this.stride;
    }

    public long push(ByteBuffer data) {
        int slice = this.next;
        if (slice >= this.slicesPerBand) {
            slice = this.slicesPerBand - 1;
        } else {
            this.next++;
            if (this.next > this.peak) {
                this.peak = this.next;
            }
        }
        long offset = (((long) this.band * this.slicesPerBand) + slice) * this.stride;
        this.buffer.write(data, offset);
        return offset;
    }

    public void beginFrame() {
        if (this.peak >= this.slicesPerBand) {
            this.slicesPerBand = this.slicesPerBand * 2;
            this.buffer.close();
            this.buffer = allocate();
            this.band = 0;
            this.peak = 0;
        }
        this.band = (this.band + 1) % this.bands;
        this.next = 0;
    }

    @Override
    public void close() {
        this.buffer.close();
    }
}
