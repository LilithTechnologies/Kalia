package re.lilith.kalia.mixins.gl;

import org.lwjgl.util.glu.Project;
import org.lwjgl.util.glu.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import re.lilith.kalia.gl.GlBridge;

import java.nio.FloatBuffer;

@Mixin(Project.class)
public class MixinProject extends Util {
    @Shadow
    @Final
    private static FloatBuffer matrix;

    @Shadow
    private static void __gluMakeIdentityf(FloatBuffer m) {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    @Shadow
    @Final
    private static float[] forward;

    @Shadow
    @Final
    private static float[] side;

    @Shadow
    @Final
    private static float[] up;

    /**
     * @reason Replace OpenGL
     * @author Lunasa
     */
    @Overwrite
    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        float sine, cotangent, deltaZ;
        float radians = (float) (fovy / 2 * Math.PI / 180);

        deltaZ = zFar - zNear;
        sine = (float) Math.sin(radians);

        if ((deltaZ == 0) || (sine == 0) || (aspect == 0)) {
            return;
        }

        cotangent = (float) Math.cos(radians) / sine;

        __gluMakeIdentityf(matrix);

        matrix.put(0 * 4 + 0, cotangent / aspect);
        matrix.put(1 * 4 + 1, cotangent);
        matrix.put(2 * 4 + 2, -zFar / deltaZ);
        matrix.put(2 * 4 + 3, -1);
        matrix.put(3 * 4 + 2, -zNear * zFar / deltaZ);
        matrix.put(3 * 4 + 3, 0);

        GlBridge.multMatrix(matrix);
    }

    /**
     * @reason Replace OpenGL
     * @author Lunasa
     */
    @Overwrite
    public static void gluLookAt(
            float eyex,
            float eyey,
            float eyez,
            float centerx,
            float centery,
            float centerz,
            float upx,
            float upy,
            float upz) {
        float[] forward = MixinProject.forward;
        float[] side = MixinProject.side;
        float[] up = MixinProject.up;

        forward[0] = centerx - eyex;
        forward[1] = centery - eyey;
        forward[2] = centerz - eyez;

        up[0] = upx;
        up[1] = upy;
        up[2] = upz;

        normalize(forward);

        /* Side = forward x up */
        cross(forward, up, side);
        normalize(side);

        /* Recompute up as: up = side x forward */
        cross(side, forward, up);

        __gluMakeIdentityf(matrix);
        matrix.put(0 * 4 + 0, side[0]);
        matrix.put(1 * 4 + 0, side[1]);
        matrix.put(2 * 4 + 0, side[2]);

        matrix.put(0 * 4 + 1, up[0]);
        matrix.put(1 * 4 + 1, up[1]);
        matrix.put(2 * 4 + 1, up[2]);

        matrix.put(0 * 4 + 2, -forward[0]);
        matrix.put(1 * 4 + 2, -forward[1]);
        matrix.put(2 * 4 + 2, -forward[2]);

        GlBridge.multMatrix(matrix);
        GlBridge.translate(-eyex, -eyey, -eyez);
    }

}
