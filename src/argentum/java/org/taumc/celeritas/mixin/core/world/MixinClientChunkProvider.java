package org.taumc.celeritas.mixin.core.world;

import net.minecraft.world.chunk.ClientChunkProvider;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

@Mixin(ClientChunkProvider.class)
public class MixinClientChunkProvider {
    @Shadow
    private World world;

    @Inject(method = "getOrGenerateChunk", at = @At("RETURN"))
    private void afterLoadChunkFromPacket(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        ChunkTrackerHolder.get(this.world).onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
    }

    @Inject(method = "unloadChunk", at = @At("RETURN"))
    private void afterUnloadChunk(int x, int z, CallbackInfo ci) {
        ChunkTrackerHolder.get(this.world).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
    }
}
