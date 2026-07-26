package re.lilith.kalia.mixins.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.ModelBox;
import net.minecraft.client.render.model.ModelPart;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.entity.CuboidBatcher;
import re.lilith.kalia.frame.GameFrame;
import re.lilith.kalia.gl.MatrixState;
import re.lilith.kalia.entity.ModelBoxCuboidData;

import java.util.List;

@Mixin(ModelPart.class)
public class MixinModelPart {
    @Shadow
    public float offsetX;
    @Shadow
    public float offsetY;
    @Shadow
    public float offsetZ;

    @Shadow
    public float posX;
    @Shadow
    public float posY;
    @Shadow
    public float posZ;

    @Shadow
    public float pivotX;
    @Shadow
    public float pivotY;
    @Shadow
    public float pivotZ;

    @Shadow
    public boolean hide;
    @Shadow
    public boolean visible;

    @Shadow
    public List<ModelBox> cuboids;
    @Shadow
    public List<ModelPart> modelList;

    @Unique
    private static final float RAD_TO_DEG = 180.0F / (float) Math.PI;

    @Unique
    private final Matrix4f kalia$boxMatrix = new Matrix4f();

    @Inject(method = "render(F)V", at = @At("HEAD"), cancellable = true)
    private void kalia$render(float scale, CallbackInfo ci) {
        if (!GameFrame.INSTANCE.isRecording()) return;

        if (this.hide || !this.visible) {
            ci.cancel();
            return;
        }

        GlStateManager.translate(this.offsetX, this.offsetY, this.offsetZ);

        boolean hasRotation = this.posX != 0.0F || this.posY != 0.0F || this.posZ != 0.0F;
        boolean hasPivot = this.pivotX != 0.0F || this.pivotY != 0.0F || this.pivotZ != 0.0F;

        if (hasRotation) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.pivotX * scale, this.pivotY * scale, this.pivotZ * scale);
            if (this.posZ != 0.0F) GlStateManager.rotate(this.posZ * RAD_TO_DEG, 0.0F, 0.0F, 1.0F);
            if (this.posY != 0.0F) GlStateManager.rotate(this.posY * RAD_TO_DEG, 0.0F, 1.0F, 0.0F);
            if (this.posX != 0.0F) GlStateManager.rotate(this.posX * RAD_TO_DEG, 1.0F, 0.0F, 0.0F);
        } else if (hasPivot) {
            GlStateManager.translate(this.pivotX * scale, this.pivotY * scale, this.pivotZ * scale);
        }

        kalia$recordCuboids(scale);

        if (this.modelList != null) {
            for (ModelPart part : this.modelList) {
                part.render(scale);
            }
        }

        if (hasRotation) {
            GlStateManager.popMatrix();
        } else if (hasPivot) {
            GlStateManager.translate(-this.pivotX * scale, -this.pivotY * scale, -this.pivotZ * scale);
        }

        GlStateManager.translate(-this.offsetX, -this.offsetY, -this.offsetZ);

        ci.cancel();
    }

    @Inject(method = "rotateAndRender(F)V", at = @At("HEAD"), cancellable = true)
    private void kalia$rotateAndRender(float scale, CallbackInfo ci) {
        if (!GameFrame.INSTANCE.isRecording()) return;

        if (this.hide || !this.visible) {
            ci.cancel();
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.pivotX * scale, this.pivotY * scale, this.pivotZ * scale);
        if (this.posZ != 0.0F) GlStateManager.rotate(this.posZ * RAD_TO_DEG, 0.0F, 0.0F, 1.0F);
        if (this.posY != 0.0F) GlStateManager.rotate(this.posY * RAD_TO_DEG, 0.0F, 1.0F, 0.0F);
        if (this.posX != 0.0F) GlStateManager.rotate(this.posX * RAD_TO_DEG, 1.0F, 0.0F, 0.0F);

        kalia$recordCuboids(scale);

        if (this.modelList != null) {
            for (ModelPart part : this.modelList) {
                part.render(scale);
            }
        }

        GlStateManager.popMatrix();

        ci.cancel();
    }

    @Unique
    private void kalia$recordCuboids(float scale) {
        Matrix4f modelView = MatrixState.INSTANCE.modelView();

        for (ModelBox box : this.cuboids) {
            if (!(box instanceof ModelBoxCuboidData)) continue;
            ModelBoxCuboidData data = (ModelBoxCuboidData) box;

            float cx = (box.minX + box.maxX) * 0.5F * scale;
            float cy = (box.minY + box.maxY) * 0.5F * scale;
            float cz = (box.minZ + box.maxZ) * 0.5F * scale;

            kalia$boxMatrix.set(modelView).translate(cx, cy, cz);

            CuboidBatcher.INSTANCE.record(
                    kalia$boxMatrix,
                    data.kalia$texU(), data.kalia$texV(),
                    data.kalia$sizeX(), data.kalia$sizeY(), data.kalia$sizeZ(),
                    data.kalia$inflate(),
                    data.kalia$textureWidth(), data.kalia$textureHeight(),
                    scale
            );
        }
    }
}
