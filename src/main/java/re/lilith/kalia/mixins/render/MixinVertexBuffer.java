package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.VertexBuffer;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import re.lilith.kalia.draw.VertexBufferStore;
import re.lilith.kalia.vertex.VertexFormatBridge;

import java.nio.ByteBuffer;

@Mixin(VertexBuffer.class)
public abstract class MixinVertexBuffer {
    @Shadow
    @Final
    private VertexFormat format;

    @Shadow
    private int size;

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public void data(ByteBuffer buffer) {
        int vertices = buffer == null ? 0 : buffer.remaining() / this.format.getVertexSize();
        this.size = vertices;
        VertexBufferStore.INSTANCE.upload(this, buffer, VertexFormatBridge.INSTANCE.translate(this.format), vertices);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public void draw(int mode) {
        VertexBufferStore.INSTANCE.draw(this, mode);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public void bind() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public void unbind() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public void delete() {
        VertexBufferStore.INSTANCE.delete(this);
        this.size = 0;
    }
}
