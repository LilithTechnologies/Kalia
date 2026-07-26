package re.lilith.kalia.mixins.gl;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import re.lilith.kalia.draw.DisplayLists;
import re.lilith.kalia.gl.GlBridge;
import re.lilith.kalia.gl.TextureUnits;

import java.nio.FloatBuffer;

@Mixin(GlStateManager.class)
public class MixinGlStateManager {
    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void clear(int mask) {
        GlBridge.clear(mask);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void clearColor(float red, float green, float blue, float alpha) {
        GlBridge.clearColor(red, green, blue, alpha);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void clearDepth(double depth) {
        GlBridge.clearDepth(depth);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableDepthTest() {
        GlBridge.enableDepthTest();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableDepthTest() {
        GlBridge.disableDepthTest();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void depthFunc(int function) {
        GlBridge.depthFunc(function);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void depthMask(boolean enabled) {
        GlBridge.depthMask(enabled);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableBlend() {
        GlBridge.enableBlend();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableBlend() {
        GlBridge.disableBlend();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void blendFunc(int source, int destination) {
        GlBridge.blendFunc(source, destination);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void blendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
        GlBridge.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableCull() {
        GlBridge.enableCull();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableCull() {
        GlBridge.disableCull();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void cullFace(int face) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GlBridge.colorMask(red, green, blue, alpha);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enablePolyOffset() {
        GlBridge.enablePolygonOffset();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disablePolyOffset() {
        GlBridge.disablePolygonOffset();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void polygonOffset(float factor, float units) {
        GlBridge.polygonOffset(factor, units);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableColorLogic() {
        GlBridge.enableColorLogic();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableColorLogic() {
        GlBridge.disableColorLogic();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void logicOp(int op) {
        GlBridge.logicOp(op);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void viewport(int x, int y, int width, int height) {
        GlBridge.viewport(x, y, width, height);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void activeTexture(int unit) {
        TextureUnits.activeTexture(unit);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void bindTexture(int textureId) {
        TextureUnits.bind(textureId);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableTexture() {
        TextureUnits.setEnabled(true);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableTexture() {
        TextureUnits.setEnabled(false);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void color(float red, float green, float blue, float alpha) {
        GlBridge.color(red, green, blue, alpha);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void color(float red, float green, float blue) {
        GlBridge.color(red, green, blue, 1.0F);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void alphaFunc(int function, float reference) {
        GlBridge.alphaFunc(function, reference);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableAlphaTest() {
        GlBridge.enableAlphaTest();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableAlphaTest() {
        GlBridge.disableAlphaTest();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableFog() {
        GlBridge.enableFog();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableFog() {
        GlBridge.disableFog();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void fogStart(float start) {
        GlBridge.fogStart(start);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void fogEnd(float end) {
        GlBridge.fogEnd(end);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void fogDensity(float density) {
        GlBridge.fogDensity(density);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void fogMode(int mode) {
        GlBridge.fogMode(mode);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableLighting() {
        GlBridge.enableLighting();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableLighting() {
        GlBridge.disableLighting();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableLight(int light) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableLight(int light) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableColorMaterial() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableColorMaterial() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void colorMaterial(int face, int mode) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableNormalize() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableNormalize() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableRescaleNormal() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableRescaleNormal() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void shadeModel(int mode) {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void callList(int list) {
        DisplayLists.INSTANCE.call(list);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void pushLightingAttributes() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void popAttributes() {
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void matrixMode(int mode) {
        GlBridge.matrixMode(mode);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void pushMatrix() {
        GlBridge.pushMatrix();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void popMatrix() {
        GlBridge.popMatrix();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void loadIdentity() {
        GlBridge.loadIdentity();
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void translate(float x, float y, float z) {
        GlBridge.translate(x, y, z);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void translate(double x, double y, double z) {
        GlBridge.translate(x, y, z);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void rotate(float degrees, float x, float y, float z) {
        GlBridge.rotate(degrees, x, y, z);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void scale(float x, float y, float z) {
        GlBridge.scale(x, y, z);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void scale(double x, double y, double z) {
        GlBridge.scale(x, y, z);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void ortho(double left, double right, double bottom, double top, double near, double far) {
        GlBridge.ortho(left, right, bottom, top, near, far);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void multiMatrix(FloatBuffer matrix) {
        GlBridge.multMatrix(matrix);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void getFloat(int name, FloatBuffer out) {
        GlBridge.getFloat(name, out);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void enableTexCoord(GlStateManager.TexCoord mode) {
        GlBridge.setTexGenEnabled(mode.ordinal(), true);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void disableTexCoord(GlStateManager.TexCoord mode) {
        GlBridge.setTexGenEnabled(mode.ordinal(), false);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void genTex(GlStateManager.TexCoord mode, int value) {
        GlBridge.texGenMode(mode.ordinal(), value);
    }

    /**
     * @reason Redirect to Kalia
     * @author Lunasa
     */
    @Overwrite
    public static void genTex(GlStateManager.TexCoord mode, int name, FloatBuffer params) {
        GlBridge.texGenPlane(mode.ordinal(), name, params);
    }
}
