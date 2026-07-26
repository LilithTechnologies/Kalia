package net.caffeinemc.mods.sodium.mixin.core;

import net.minecraft.client.render.BaseFrustum;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import re.lilith.kalia.gl.GlEnums;
import re.lilith.kalia.gl.MatrixState;

import java.nio.FloatBuffer;

@Mixin(Frustum.class)
public abstract class MixinFrustum extends BaseFrustum {
    @Shadow
    private FloatBuffer projectionBuffer;

    @Shadow
    private FloatBuffer modelBuffer;

    @Shadow
    private FloatBuffer field_2075;

    @Shadow
    protected abstract void normalize(float[] fs);

    @Overwrite
    public void start() {
        this.projectionBuffer.clear();
        this.modelBuffer.clear();
        this.field_2075.clear();

        Camera.PROJECTION_MATRIX.clear();
        MatrixState.INSTANCE.write(GlEnums.GL_PROJECTION_MATRIX, Camera.PROJECTION_MATRIX);
        Camera.MODEL_MATRIX.clear();
        MatrixState.INSTANCE.write(GlEnums.GL_MODELVIEW_MATRIX, Camera.MODEL_MATRIX);

        this.projectionBuffer = Camera.PROJECTION_MATRIX;
        this.modelBuffer = Camera.MODEL_MATRIX;
        float[] fs = this.projectionMatrix;
        float[] gs = this.modelMatrix;
        this.projectionBuffer.flip().limit(16);
        this.projectionBuffer.get(fs);
        this.modelBuffer.flip().limit(16);
        this.modelBuffer.get(gs);

        Camera.PROJECTION_MATRIX.rewind();
        Camera.MODEL_MATRIX.rewind();
        this.clipMatrix[0] = gs[0] * fs[0] + gs[1] * fs[4] + gs[2] * fs[8] + gs[3] * fs[12];
        this.clipMatrix[1] = gs[0] * fs[1] + gs[1] * fs[5] + gs[2] * fs[9] + gs[3] * fs[13];
        this.clipMatrix[2] = gs[0] * fs[2] + gs[1] * fs[6] + gs[2] * fs[10] + gs[3] * fs[14];
        this.clipMatrix[3] = gs[0] * fs[3] + gs[1] * fs[7] + gs[2] * fs[11] + gs[3] * fs[15];
        this.clipMatrix[4] = gs[4] * fs[0] + gs[5] * fs[4] + gs[6] * fs[8] + gs[7] * fs[12];
        this.clipMatrix[5] = gs[4] * fs[1] + gs[5] * fs[5] + gs[6] * fs[9] + gs[7] * fs[13];
        this.clipMatrix[6] = gs[4] * fs[2] + gs[5] * fs[6] + gs[6] * fs[10] + gs[7] * fs[14];
        this.clipMatrix[7] = gs[4] * fs[3] + gs[5] * fs[7] + gs[6] * fs[11] + gs[7] * fs[15];
        this.clipMatrix[8] = gs[8] * fs[0] + gs[9] * fs[4] + gs[10] * fs[8] + gs[11] * fs[12];
        this.clipMatrix[9] = gs[8] * fs[1] + gs[9] * fs[5] + gs[10] * fs[9] + gs[11] * fs[13];
        this.clipMatrix[10] = gs[8] * fs[2] + gs[9] * fs[6] + gs[10] * fs[10] + gs[11] * fs[14];
        this.clipMatrix[11] = gs[8] * fs[3] + gs[9] * fs[7] + gs[10] * fs[11] + gs[11] * fs[15];
        this.clipMatrix[12] = gs[12] * fs[0] + gs[13] * fs[4] + gs[14] * fs[8] + gs[15] * fs[12];
        this.clipMatrix[13] = gs[12] * fs[1] + gs[13] * fs[5] + gs[14] * fs[9] + gs[15] * fs[13];
        this.clipMatrix[14] = gs[12] * fs[2] + gs[13] * fs[6] + gs[14] * fs[10] + gs[15] * fs[14];
        this.clipMatrix[15] = gs[12] * fs[3] + gs[13] * fs[7] + gs[14] * fs[11] + gs[15] * fs[15];
        float[] hs = this.homogeneousCoordinates[0];
        hs[0] = this.clipMatrix[3] - this.clipMatrix[0];
        hs[1] = this.clipMatrix[7] - this.clipMatrix[4];
        hs[2] = this.clipMatrix[11] - this.clipMatrix[8];
        hs[3] = this.clipMatrix[15] - this.clipMatrix[12];
        this.normalize(hs);
        float[] is = this.homogeneousCoordinates[1];
        is[0] = this.clipMatrix[3] + this.clipMatrix[0];
        is[1] = this.clipMatrix[7] + this.clipMatrix[4];
        is[2] = this.clipMatrix[11] + this.clipMatrix[8];
        is[3] = this.clipMatrix[15] + this.clipMatrix[12];
        this.normalize(is);
        float[] js = this.homogeneousCoordinates[2];
        js[0] = this.clipMatrix[3] + this.clipMatrix[1];
        js[1] = this.clipMatrix[7] + this.clipMatrix[5];
        js[2] = this.clipMatrix[11] + this.clipMatrix[9];
        js[3] = this.clipMatrix[15] + this.clipMatrix[13];
        this.normalize(js);
        float[] ks = this.homogeneousCoordinates[3];
        ks[0] = this.clipMatrix[3] - this.clipMatrix[1];
        ks[1] = this.clipMatrix[7] - this.clipMatrix[5];
        ks[2] = this.clipMatrix[11] - this.clipMatrix[9];
        ks[3] = this.clipMatrix[15] - this.clipMatrix[13];
        this.normalize(ks);
        float[] ls = this.homogeneousCoordinates[4];
        ls[0] = this.clipMatrix[3] - this.clipMatrix[2];
        ls[1] = this.clipMatrix[7] - this.clipMatrix[6];
        ls[2] = this.clipMatrix[11] - this.clipMatrix[10];
        ls[3] = this.clipMatrix[15] - this.clipMatrix[14];
        this.normalize(ls);
        float[] ms = this.homogeneousCoordinates[5];
        ms[0] = this.clipMatrix[3] + this.clipMatrix[2];
        ms[1] = this.clipMatrix[7] + this.clipMatrix[6];
        ms[2] = this.clipMatrix[11] + this.clipMatrix[10];
        ms[3] = this.clipMatrix[15] + this.clipMatrix[14];
        this.normalize(ms);
    }
}
