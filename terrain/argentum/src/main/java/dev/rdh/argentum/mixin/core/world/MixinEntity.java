package dev.rdh.argentum.mixin.core.world;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.world.cloned.BlockPosCache;


@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow
    public World world;

    @Shadow
    public double x;

    @Shadow
    public double y;

    @Shadow
    public double z;

    @Shadow
    public float width;

    @Shadow
    public float height;

    @Shadow
    protected BlockPos lastPortalBlockPos;
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

    @Unique
    private BlockPos.Mutable celeritas$set(
            BlockPos.Mutable pos,
            double x,
            double y,
            double z
    ) {
        pos.setPosition(
                MathHelper.floor(x),
                MathHelper.floor(y),
                MathHelper.floor(z)
        );

        return pos;
    }

    @Redirect(
            method = "tickFire",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectTickFireBlockPos(
            double x,
            double y,
            double z
    ) {
        return celeritas$set(
                celeritas$pos1(),
                x,
                y,
                z
        );
    }

    @Redirect(
            method = "getLightmapCoordinates",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectLightmapBlockPos(
            double x,
            double y,
            double z
    ) {
        return celeritas$set(
                celeritas$pos1(),
                x,
                y,
                z
        );
    }

    @Redirect(
            method = "getBrightnessAtEyes",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectBrightnessBlockPos(
            double x,
            double y,
            double z
    ) {
        return celeritas$set(
                celeritas$pos1(),
                x,
                y,
                z
        );
    }

    @Redirect(
            method = "isSubmergedIn",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectSubmergedBlockPos(
            double x,
            double y,
            double z
    ) {
        return celeritas$set(
                celeritas$pos1(),
                x,
                y,
                z
        );
    }

    @Redirect(
            method = "spawnSprintingParticles",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectSprintBlockPos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(
                celeritas$pos1(),
                x,
                y,
                z
        );
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectMoveBlockPos(
            int x,
            int y,
            int z
    ) {
        return celeritas$set(
                celeritas$pos1(),
                x,
                y,
                z
        );
    }

    @Redirect(
            method = "checkBlockCollision",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectCollisionBlockPos(
            double x,
            double y,
            double z
    ) {
        return celeritas$set(
                celeritas$pos1(),
                x,
                y,
                z
        );
    }

    @Redirect(
            method = "pushOutOfBlocks",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/util/math/BlockPos"
            )
    )
    private BlockPos celeritas$redirectPushOutBlockPos(
            double x,
            double y,
            double z
    ) {
        return celeritas$set(
                celeritas$pos1(),
                x,
                y,
                z
        );
    }

    @Inject(
            method = "setInNetherPortal",
            at = @At("HEAD")
    )
    private void celeritas$canonicalizePortalPosition(
            BlockPos pos,
            CallbackInfo ci
    ) {}

    @Redirect(
            method = "setInNetherPortal",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;lastPortalBlockPos:Lnet/minecraft/util/math/BlockPos;",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void celeritas$canonicalizePortalPositionField(
            Entity entity,
            BlockPos pos
    ) {
        this.lastPortalBlockPos = BlockPosCache.immutable(pos);
    }
}