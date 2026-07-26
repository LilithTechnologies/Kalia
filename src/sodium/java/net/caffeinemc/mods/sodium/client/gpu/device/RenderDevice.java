package net.caffeinemc.mods.sodium.client.gpu.device;

import net.caffeinemc.mods.sodium.client.gpu.KaliaAccess;
import net.caffeinemc.mods.sodium.client.gpu.buffer.DeviceBuffer;

public final class RenderDevice {
    public static final RenderDevice INSTANCE = new RenderDevice();

    private final CommandList commandList = new CommandList();

    private RenderDevice() {
    }

    public CommandList createCommandList() {
        return this.commandList;
    }

    public int getSubTexelPrecisionBits() {
        return KaliaAccess.getSubTexelBits();
    }

    public void destroyObjectWhenSafe(DeviceBuffer buffer) {
        if (buffer != null) {
            buffer.delete();
        }
    }
}
