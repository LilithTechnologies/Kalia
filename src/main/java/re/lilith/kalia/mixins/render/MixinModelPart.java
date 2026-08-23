package re.lilith.kalia.mixins.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.ModelBox;
import net.minecraft.client.render.model.ModelPart;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import re.lilith.kalia.frame.draw.EntityBatchers;
import re.lilith.kalia.frame.graph.entity.EntityStage;
import re.lilith.kalia.frame.graph.entity.cuboid.CuboidBatcher;
import re.lilith.kalia.frame.GameFrame;
import re.lilith.kalia.entity.ModelTraversal;
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

    @Shadow
    private boolean compiledList;
    @Shadow
    private int glList;

    @Shadow
    private void compileList(float scale) {
    }

    @Unique
    private static final float RAD_TO_DEG = 57.295776F;

    @Unique
    private boolean kalia$batching() {
        return GameFrame.INSTANCE.isRecording() && EntityBatchers.INSTANCE.isRenderingEntities();
    }

    @Unique
    private void kalia$emit(float scale, boolean batching) {
        if (batching) {
            kalia$recordCuboids(scale);
        } else {
            GlStateManager.callList(this.glList);
        }
    }

    @Unique
    private void kalia$renderChildren(float scale) {
        if (this.modelList == null) {
            return;
        }
        for (int i = 0; i < this.modelList.size(); i++) {
            this.modelList.get(i).render(scale);
        }
    }

    /**
     * @reason Cuboids are batched as instances rather than replayed from a display list
     * @author Lunasa
     */
    @Overwrite
    public void render(float scale) {
        if (this.hide || !this.visible) {
            return;
        }

        boolean batching = kalia$batching();
        if (batching && EntityStage.INSTANCE.onModelRoot(MatrixState.INSTANCE.modelView())) {
            if (EntityStage.INSTANCE.takeReplay()) {
                CuboidBatcher.INSTANCE.replayStaged();
            }
            return;
        }
        if (batching) {
            CuboidBatcher.INSTANCE.beginPart();
            ModelTraversal.render((ModelPart) (Object) this, scale, MatrixState.INSTANCE.modelView());
            return;
        }

        if (!this.compiledList) {
            this.compileList(scale);
        }

        GlStateManager.translate(this.offsetX, this.offsetY, this.offsetZ);

        if (this.posX != 0.0F || this.posY != 0.0F || this.posZ != 0.0F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.pivotX * scale, this.pivotY * scale, this.pivotZ * scale);
            if (this.posZ != 0.0F) GlStateManager.rotate(this.posZ * RAD_TO_DEG, 0.0F, 0.0F, 1.0F);
            if (this.posY != 0.0F) GlStateManager.rotate(this.posY * RAD_TO_DEG, 0.0F, 1.0F, 0.0F);
            if (this.posX != 0.0F) GlStateManager.rotate(this.posX * RAD_TO_DEG, 1.0F, 0.0F, 0.0F);
            kalia$emit(scale, batching);
            kalia$renderChildren(scale);
            GlStateManager.popMatrix();
        } else if (this.pivotX != 0.0F || this.pivotY != 0.0F || this.pivotZ != 0.0F) {
            GlStateManager.translate(this.pivotX * scale, this.pivotY * scale, this.pivotZ * scale);
            kalia$emit(scale, batching);
            kalia$renderChildren(scale);
            GlStateManager.translate(-this.pivotX * scale, -this.pivotY * scale, -this.pivotZ * scale);
        } else {
            kalia$emit(scale, batching);
            kalia$renderChildren(scale);
        }

        GlStateManager.translate(-this.offsetX, -this.offsetY, -this.offsetZ);
    }

    /**
     * @reason Cuboids are batched as instances rather than replayed from a display list
     * @author Lunasa
     */
    @Overwrite
    public void rotateAndRender(float scale) {
        if (this.hide || !this.visible) {
            return;
        }

        boolean batching = kalia$batching();
        if (batching) {
            CuboidBatcher.INSTANCE.beginPart();
            ModelTraversal.renderRotated((ModelPart) (Object) this, scale, MatrixState.INSTANCE.modelView());
            return;
        }

        if (!this.compiledList) {
            this.compileList(scale);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.pivotX * scale, this.pivotY * scale, this.pivotZ * scale);
        if (this.posY != 0.0F) GlStateManager.rotate(this.posY * RAD_TO_DEG, 0.0F, 1.0F, 0.0F);
        if (this.posX != 0.0F) GlStateManager.rotate(this.posX * RAD_TO_DEG, 1.0F, 0.0F, 0.0F);
        if (this.posZ != 0.0F) GlStateManager.rotate(this.posZ * RAD_TO_DEG, 0.0F, 0.0F, 1.0F);
        kalia$emit(scale, batching);
        GlStateManager.popMatrix();
    }

    @Unique
    private void kalia$recordCuboids(float scale) {
        if (this.cuboids.isEmpty()) {
            return;
        }

        Matrix4f modelView = MatrixState.INSTANCE.modelView();
        CuboidBatcher.INSTANCE.beginPart();

        for (ModelBox box : this.cuboids) {
            if (!(box instanceof ModelBoxCuboidData data)) continue;

            float cx = (box.minX + box.maxX) * 0.5F * scale;
            float cy = (box.minY + box.maxY) * 0.5F * scale;
            float cz = (box.minZ + box.maxZ) * 0.5F * scale;

            CuboidBatcher.INSTANCE.recordBox(
                    modelView, cx, cy, cz,
                    data.kalia$texU(), data.kalia$texV(),
                    data.kalia$sizeX(), data.kalia$sizeY(), data.kalia$sizeZ(),
                    data.kalia$inflate(),
                    data.kalia$textureWidth(), data.kalia$textureHeight(),
                    data.kalia$mirror(),
                    scale
            );
        }
    }
}
