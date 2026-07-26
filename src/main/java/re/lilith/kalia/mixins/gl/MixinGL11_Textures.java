package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import re.lilith.kalia.texture.TextureTable;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11C.GL_PROXY_TEXTURE_2D;

@Mixin(GL11.class)
public class MixinGL11_Textures {

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static int glGenTextures() {
        return TextureTable.INSTANCE.generate();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDeleteTextures(int texture) {
        TextureTable.INSTANCE.delete(texture);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDeleteTextures(IntBuffer textures) {
        for (int index = textures.position(); index < textures.limit(); index++) {
            TextureTable.INSTANCE.delete(textures.get(index));
        }
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glBindTexture(int target, int texture) {
        re.lilith.kalia.gl.TextureUnits.bind(texture);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexImage2D(
            int target, int level, int internalFormat,
            int width, int height, int border,
            int format, int type, ByteBuffer pixels
    ) {
        if (target == GL_PROXY_TEXTURE_2D) {
            TextureTable.INSTANCE.defineProxyLevel(width, height);
            return;
        }
        TextureTable.INSTANCE.defineLevel(level, width, height, internalFormat);
        if (pixels != null) {
            TextureTable.INSTANCE.upload(level, 0, 0, width, height, format, type, pixels);
        }
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexImage2D(
            int target, int level, int internalFormat,
            int width, int height, int border,
            int format, int type, long pixels
    ) {
        if (target == GL_PROXY_TEXTURE_2D) {
            TextureTable.INSTANCE.defineProxyLevel(width, height);
            return;
        }
        TextureTable.INSTANCE.defineLevel(level, width, height, internalFormat);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexImage2D(
            int target, int level, int internalFormat,
            int width, int height, int border,
            int format, int type, IntBuffer pixels
    ) {
        if (target == GL_PROXY_TEXTURE_2D) {
            TextureTable.INSTANCE.defineProxyLevel(width, height);
            return;
        }
        TextureTable.INSTANCE.defineLevel(level, width, height, internalFormat);
        if (pixels != null) {
            TextureTable.INSTANCE.upload(level, 0, 0, width, height, format, type, asBytes(pixels));
        }
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexSubImage2D(
            int target, int level, int xOffset, int yOffset,
            int width, int height, int format, int type, ByteBuffer pixels
    ) {
        TextureTable.INSTANCE.upload(level, xOffset, yOffset, width, height, format, type, pixels);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexSubImage2D(
            int target, int level, int xOffset, int yOffset,
            int width, int height, int format, int type, IntBuffer pixels
    ) {
        TextureTable.INSTANCE.upload(level, xOffset, yOffset, width, height, format, type, asBytes(pixels));
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexSubImage2D(
            int target, int level, int xOffset, int yOffset,
            int width, int height, int format, int type, long pixels
    ) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexParameteri(int target, int name, int value) {
        TextureTable.INSTANCE.setParameter(name, value);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glTexParameterf(int target, int name, float value) {
        TextureTable.INSTANCE.setParameter(name, value);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static int glGetTexLevelParameteri(int target, int level, int name) {
        if (target == GL_PROXY_TEXTURE_2D) {
            return TextureTable.INSTANCE.proxyParameter(name);
        }
        return TextureTable.INSTANCE.levelParameter(level, name);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static int glGetTexParameteri(int target, int name) {
        return 0;
    }

    @Unique
    private static ByteBuffer asBytes(IntBuffer source) {
        if (source == null) {
            return null;
        }
        return org.lwjgl.system.MemoryUtil.memByteBuffer(
                org.lwjgl.system.MemoryUtil.memAddress(source),
                source.remaining() * Integer.BYTES
        );
    }
}
