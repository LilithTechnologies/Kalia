package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// shaders are a major todo
@Mixin(GL20.class)
public class MixinGL20 {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static int glCreateShader(int type) {
        return -1;
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static int glCreateProgram() {
        return -1;
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniform2fv(int location, FloatBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniform1iv(int location, IntBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniform1fv(int location, FloatBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniform2iv(int location, IntBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniform3iv(int location, IntBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniform3fv(int location, FloatBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniform4iv(int location, IntBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniform4fv(int location, FloatBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniformMatrix2fv(int location, boolean transpose, FloatBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {

    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void glShaderSource(int shader, CharSequence... strings) {
    }
}