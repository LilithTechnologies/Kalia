package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.buffer.PersistentMesh;
import re.lilith.kalia.frame.draw.EntityBatchers;
import re.lilith.kalia.frame.draw.ItemMeshCache;
import re.lilith.kalia.entity.item.ItemBatcher;
import re.lilith.kalia.gl.MatrixState;

import java.util.List;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    @Shadow
    private void renderBakedItemQuads(BufferBuilder bufferBuilder, List<BakedQuad> quads, int color, ItemStack stack) {
        throw new AssertionError();
    }

    /**
     * @reason Cache mesh
     * @author Lunasa
     */
    @Overwrite
    private void renderBakedItemModel(BakedModel model, int color, ItemStack stack) {
        if (ItemMeshCache.INSTANCE.isStackIndependent(model, color, stack != null)) {
            PersistentMesh mesh = ItemMeshCache.INSTANCE.getOrBuild(model, color, () -> kalia$buildQuadBuffer(model, color, stack));
            if (mesh != null) {
                if (EntityBatchers.INSTANCE.isRenderingEntities()) {
                    MatrixState.INSTANCE.flush();
                    Matrix4f modelView = MatrixState.INSTANCE.modelView();
                    ItemBatcher.INSTANCE.record(mesh, modelView);
                } else {
                    ItemMeshCache.INSTANCE.drawImmediate(mesh, 7);
                }
                return;
            }
        }

        kalia$buildQuadBuffer(model, color, stack);
        Tessellator.getInstance().draw();
    }

    @Unique
    private BufferBuilder kalia$buildQuadBuffer(BakedModel model, int color, ItemStack stack) {
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
        bufferBuilder.begin(7, VertexFormats.BLOCK_NORMALS);

        for (Direction direction : Direction.values()) {
            this.renderBakedItemQuads(bufferBuilder, model.getByDirection(direction), color, stack);
        }
        this.renderBakedItemQuads(bufferBuilder, model.getQuads(), color, stack);
        return bufferBuilder;
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void kalia$onReload(ResourceManager resourceManager, CallbackInfo ci) {
        ItemMeshCache.INSTANCE.clear();
    }
}
