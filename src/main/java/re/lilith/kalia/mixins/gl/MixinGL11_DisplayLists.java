package re.lilith.kalia.mixins.gl;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import re.lilith.kalia.frame.draw.DisplayLists;

@Mixin(GL11.class)
public class MixinGL11_DisplayLists {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static int glGenLists(int count) {
        return DisplayLists.INSTANCE.generate(count);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glNewList(int list, int mode) {
        DisplayLists.INSTANCE.begin(list);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glEndList() {
        DisplayLists.INSTANCE.end();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite(remap = false)
    public static void glDeleteLists(int list, int count) {
        DisplayLists.INSTANCE.delete(list, count);
    }
}
