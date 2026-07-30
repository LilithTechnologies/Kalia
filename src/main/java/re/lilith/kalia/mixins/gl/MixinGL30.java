package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import re.lilith.kalia.gl.tables.FramebufferTable;
import re.lilith.kalia.gl.tables.RenderbufferTable;
import re.lilith.kalia.gl.tables.TextureTable;

@Mixin(GL30.class)
public class MixinGL30 {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glGenerateMipmap(int target) {
        TextureTable.INSTANCE.generateMipmaps();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static int glGenFramebuffers() {
        return FramebufferTable.INSTANCE.generate();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glBindFramebuffer(int target, int framebuffer) {
        FramebufferTable.INSTANCE.bind(framebuffer);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDeleteFramebuffers(int framebuffer) {
        FramebufferTable.INSTANCE.delete(framebuffer);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glFramebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
        FramebufferTable.INSTANCE.attachTexture(attachment, texture);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glFramebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        FramebufferTable.INSTANCE.attachRenderbuffer(attachment, renderbuffer);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static int glCheckFramebufferStatus(int target) {
        return FramebufferTable.INSTANCE.status();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static int glGenRenderbuffers() {
        return RenderbufferTable.INSTANCE.generate();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glBindRenderbuffer(int target, int renderbuffer) {
        RenderbufferTable.INSTANCE.bind(renderbuffer);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDeleteRenderbuffers(int renderbuffer) {
        RenderbufferTable.INSTANCE.delete(renderbuffer);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glRenderbufferStorage(int target, int internalFormat, int width, int height) {
        RenderbufferTable.INSTANCE.allocate(internalFormat, width, height);
    }
}
