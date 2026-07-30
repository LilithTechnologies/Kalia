package re.lilith.kalia.mixins.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.Block;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.texture.Texture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.draw.NametagTextRenderer;
import re.lilith.kalia.frame.graph.entity.nametag.NametagBatcher;
import re.lilith.kalia.frame.graph.entity.shadow.ShadowBatcher;
import re.lilith.kalia.gl.MatrixState;
import re.lilith.kalia.gl.tables.TextureTable;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer<T extends Entity> {
    @Shadow
    protected float shadowSize;
    @Shadow
    private static Identifier SHADOW_TEXTURE;

    @Shadow
    private World getWorld() {
        throw new AssertionError();
    }

    @Shadow
    public TextRenderer getFontRenderer() {
        throw new AssertionError();
    }

    @Shadow
    @Final
    protected EntityRenderDispatcher dispatcher;

    /**
     * @reason Instance nametag rendering
     * @author Lunasa
     */
    @Overwrite
    public void renderLabelIfPresent(T entity, String text, double x, double y, double z, int maxDistance) {
        if (entity.squaredDistanceTo(this.dispatcher.field_11098) >= (double) (maxDistance * maxDistance)) {
            return;
        }

        NametagTextRenderer textRenderer = (NametagTextRenderer) this.getFontRenderer();
        int yOffset = "deadmau5".equals(text) ? -10 : 0;
        int halfWidth = this.getFontRenderer().getStringWidth(text) / 2;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + entity.height + 0.5F, (float) z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-this.dispatcher.yaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(this.dispatcher.pitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.02666667F, -0.02666667F, 0.02666667F);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepthTest();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);

        MatrixState.INSTANCE.flush();
        Matrix4f modelView = MatrixState.INSTANCE.modelView();

        NametagBatcher.INSTANCE.beginLabel();
        NametagBatcher.INSTANCE.recordBackground(
                modelView,
                -halfWidth - 1, -1 + yOffset, halfWidth + 1, 8 + yOffset,
                0x40
        );
        textRenderer.kalia$drawNametag(text, -halfWidth, yOffset, 0x20FFFFFF);

        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        textRenderer.kalia$drawNametag(text, -halfWidth, yOffset, -1);

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    /**
     * @reason Instance shadow rendering
     * @author Lunasa
     */
    @Inject(
            method = "renderShadow(Lnet/minecraft/entity/Entity;DDDFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kalia$renderShadow(Entity entity, double x, double y, double z, float f, float tickDelta, CallbackInfo ci) {
        ci.cancel();

        this.dispatcher.textureManager.bindTexture(SHADOW_TEXTURE);
        Texture texture = this.dispatcher.textureManager.getTexture(SHADOW_TEXTURE);
        ShadowBatcher.texture = TextureTable.INSTANCE.get(texture.getGlId());

        World world = this.getWorld();
        float g = this.shadowSize;
        if (entity instanceof MobEntity mobEntity) {
            g *= mobEntity.method_2638();
            if (mobEntity.isBaby()) {
                g *= 0.5F;
            }
        }

        double d = entity.prevTickX + (entity.x - entity.prevTickX) * tickDelta;
        double e = entity.prevTickY + (entity.y - entity.prevTickY) * tickDelta;
        double h = entity.prevTickZ + (entity.z - entity.prevTickZ) * tickDelta;
        int i = MathHelper.floor(d - g);
        int j = MathHelper.floor(d + g);
        int k = MathHelper.floor(e - g);
        int l = MathHelper.floor(e);
        int m = MathHelper.floor(h - g);
        int n = MathHelper.floor(h + g);
        double o = x - d;
        double p = y - e;
        double q = z - h;

        for (BlockPos blockPos : BlockPos.mutableIterate(new BlockPos(i, k, m), new BlockPos(j, l, n))) {
            Block block = world.getBlockState(blockPos.down()).getBlock();
            if (block.getBlockType() != -1 && world.getLightLevelWithNeighbours(blockPos) > 3) {
                kalia$recordShadowQuad(world, block, x, y, z, blockPos, f, g, o, p, q);
            }
        }
    }

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
}
