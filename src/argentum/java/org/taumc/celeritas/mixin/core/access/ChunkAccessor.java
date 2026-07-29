package org.taumc.celeritas.mixin.core.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.chunk.Chunk;

@Mixin(Chunk.class)
public interface ChunkAccessor {
    @Accessor("containsEntities")
    boolean getHasEntities();
}
