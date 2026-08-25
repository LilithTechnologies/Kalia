package dev.rdh.argentum.mixin.core.render;

import net.minecraft.client.render.BaseFrustum;
import net.minecraft.client.render.CullingCameraView;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import dev.rdh.argentum.impl.render.terrain.matrix.PrimitiveChunkMatrixGetter;

@Mixin(CullingCameraView.class)
public class MixinCullingCameraView implements ViewportProvider {
    @Shadow
    private BaseFrustum clipper;

    @Shadow
    private double x, y, z;

    @Override
    public Viewport sodium$createViewport() {
        PrimitiveChunkMatrixGetter.update(this.clipper.projectionMatrix, this.clipper.modelMatrix);

        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.set(clipper.modelMatrix);
        modelMatrix.invert();
        Vector3f offset = new Vector3f();
        modelMatrix.transformPosition(offset);
        Frustum cullTester = this::celeritas$isVisible;
        return new Viewport(cullTester,
                new org.joml.Vector3d(this.x + offset.x, this.y + offset.y, this.z + offset.z));
    }

    @Unique
    private boolean celeritas$isVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (float[] plane : this.clipper.homogeneousCoordinates) {
            float x = plane[0] < 0.0F ? minX : maxX;
            float y = plane[1] < 0.0F ? minY : maxY;
            float z = plane[2] < 0.0F ? minZ : maxZ;
            if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] <= 0.0F) {
                return false;
            }
        }
        return true;
    }
}
