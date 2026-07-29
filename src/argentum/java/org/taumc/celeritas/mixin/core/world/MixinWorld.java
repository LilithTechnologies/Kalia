package org.taumc.celeritas.mixin.core.world;

import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Unique;

@Mixin(World.class)
public class MixinWorld implements ChunkTrackerHolder {
    @Unique
    private final ChunkTracker celeritas$tracker = new ChunkTracker();

    @Override
    public ChunkTracker sodium$getTracker() {
        return celeritas$tracker;
    }
}
