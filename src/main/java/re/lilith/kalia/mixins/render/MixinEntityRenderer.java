package re.lilith.kalia.mixins.render;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.block.Block;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.texture.Texture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.entity.shadow.ShadowBatcher;
import re.lilith.kalia.texture.TextureTable;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer<T extends Entity> {
    @Shadow
    protected EntityRenderDispatcher dispatcher;
    @Shadow
    protected float shadowSize;
    @Shadow
    private static Identifier SHADOW_TEXTURE;

    @Shadow
    private World getWorld() {
        throw new AssertionError();
    }

    @Unique
    private static boolean kalia$shadowTextureResolved = false;

    // todo: fix shadow batching
//    @Inject(
//            method = "renderShadow(Lnet/minecraft/entity/Entity;DDDFF)V",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    private void kalia$renderShadow(Entity entity, double x, double y, double z, float f, float tickDelta, CallbackInfo ci) {
//        ci.cancel();
//
//        if (!kalia$shadowTextureResolved) {
//            this.dispatcher.textureManager.bindTexture(SHADOW_TEXTURE);
//            Texture texture = this.dispatcher.textureManager.getTexture(SHADOW_TEXTURE);
//            ShadowBatcher.INSTANCE.texture = TextureTable.INSTANCE.get(texture.getGlId());
//            kalia$shadowTextureResolved = true;
//        }
//
//        World world = this.getWorld();
//        float g = this.shadowSize;
//        if (entity instanceof MobEntity) {
//            MobEntity mobEntity = (MobEntity) entity;
//            g *= mobEntity.method_2638();
//            if (mobEntity.isBaby()) {
//                g *= 0.5F;
//            }
//        }
//
//        double d = entity.prevTickX + (entity.x - entity.prevTickX) * tickDelta;
//        double e = entity.prevTickY + (entity.y - entity.prevTickY) * tickDelta;
//        double h = entity.prevTickZ + (entity.z - entity.prevTickZ) * tickDelta;
//        int i = MathHelper.floor(d - g);
//        int j = MathHelper.floor(d + g);
//        int k = MathHelper.floor(e - g);
//        int l = MathHelper.floor(e);
//        int m = MathHelper.floor(h - g);
//        int n = MathHelper.floor(h + g);
//        double o = x - d;
//        double p = y - e;
//        double q = z - h;
//
//        for (BlockPos blockPos : BlockPos.mutableIterate(new BlockPos(i, k, m), new BlockPos(j, l, n))) {
//            Block block = world.getBlockState(blockPos.down()).getBlock();
//            if (block.getBlockType() != -1 && world.getLightLevelWithNeighbours(blockPos) > 3) {
//                kalia$recordShadowQuad(world, block, x, y, z, blockPos, f, g, o, p, q);
//            }
//        }
//    }

    @Unique
    private void kalia$recordShadowQuad(
            World world, Block block,
            double d, double e, double f,
            BlockPos blockPos,
            float g, float h,
            double i, double j, double k
    ) {
        if (!block.renderAsNormalBlock()) {
            return;
        }
        double l = (g - (e - (blockPos.getY() + j)) / 2.0) * 0.5 * world.getBrightness(blockPos);
        if (l < 0.0) {
            return;
        }
        if (l > 1.0) {
            l = 1.0;
        }

        double m = blockPos.getX() + block.getMinX() + i;
        double n = blockPos.getX() + block.getMaxX() + i;
        double o = blockPos.getY() + block.getMinY() + j + 0.015625;
        double p = blockPos.getZ() + block.getMinZ() + k;
        double q = blockPos.getZ() + block.getMaxZ() + k;
        float r = (float) ((d - m) / 2.0 / h + 0.5);
        float s = (float) ((d - n) / 2.0 / h + 0.5);
        float t = (float) ((f - p) / 2.0 / h + 0.5);
        float u = (float) ((f - q) / 2.0 / h + 0.5);

        ShadowBatcher.INSTANCE.record(
                (float) m, (float) o, (float) p,
                (float) (n - m), (float) (q - p),
                r, s, t, u,
                (float) l
        );
    }

    @ModifyConstant(method = "postRender", constant = @Constant(doubleValue = 256.0D))
    private double graphite$extendEntityShadowDistance(double vanillaDistanceSquared) {
        double distance = SodiumClientMod.options().quality.entityShadowDistance;
        return distance * distance;
    }
}
