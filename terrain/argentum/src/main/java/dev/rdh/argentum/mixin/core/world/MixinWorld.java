package dev.rdh.argentum.mixin.core.world;

import net.minecraft.util.math.BlockPos;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import dev.rdh.argentum.impl.world.cloned.BlockPosCache;

@Mixin(World.class)
public class MixinWorld implements ChunkTrackerHolder {
    @Unique
    private final ChunkTracker celeritas$tracker = new ChunkTracker();

    @Unique
    private final BlockPosCache celeritas$blockPosCache =
            new BlockPosCache();

    @Unique
    private BlockPos.Mutable celeritas$pos1;

    @Unique
    private BlockPos.Mutable celeritas$pos1() {
        if (this.celeritas$pos1 == null) {
            this.celeritas$pos1 =
                    this.celeritas$blockPosCache.acquire(0, 0, 0);
        }

        return this.celeritas$pos1;
    }

    @Unique
    private BlockPos.Mutable celeritas$set(
            BlockPos.Mutable pos,
            int x,
            int y,
            int z
    ) {
        pos.setPosition(x, y, z);
        return pos;
    }

    @Redirect(
            method = "getBlockAt",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectGetBlockAtPos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(celeritas$pos1(), x, y, z);
    }

    @Redirect(
            method = "receivesSunlight",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectReceivesSunlightPos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(celeritas$pos1(), x, y, z);
    }

    @Redirect(
            method = "getLightLevel(Lnet/minecraft/util/math/BlockPos;)I",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectGetLightLevelPos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(celeritas$pos1(), x, y, z);
    }

    @Redirect(
            method = "getLightLevel(Lnet/minecraft/util/math/BlockPos;Z)I",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectGetLightLevelCheckedPos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(celeritas$pos1(), x, y, z);
    }

    @Redirect(
            method = "getLuminance",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectGetLuminancePos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(celeritas$pos1(), x, y, z);
    }

    @Redirect(
            method = "getLightAtPos(Lnet/minecraft/world/LightType;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectGetLightAtPosPos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(celeritas$pos1(), x, y, z);
    }

    @Redirect(
            method = "updateLighting",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectUpdateLightingPos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(celeritas$pos1(), x, y, z);
    }

    @Redirect(
            method = "m_50884879",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectMoodSoundPos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(celeritas$pos1(), x, y, z);
    }

    @Override
    public ChunkTracker sodium$getTracker() {
        return celeritas$tracker;
    }
}
