package re.lilith.kalia.mixins.access;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccess {
    @Invoker
    float invokeGetFov(float tickDelta, boolean changingFov);

    @Invoker("setupCamera")
    void invokeSetupCamera(float tickDelta, int anaglyphFilter);

    @Invoker("updateFog")
    void invokeUpdateFog(float tickDelta);

    @Invoker("renderFog")
    void invokeRenderFog(int stage, float tickDelta);

    @Invoker("updateLightmap")
    void invokeUpdateLightmap(float tickDelta);

    @Invoker("enableLightmap")
    void invokeEnableLightmap();

    @Invoker("disableLightmap")
    void invokeDisableLightmap();

    @Invoker("renderWeather")
    void invokeRenderWeather(float tickDelta);

    @Invoker("renderHand")
    void invokeRenderHand(float tickDelta, int anaglyphFilter);

    @Invoker("renderDebugCrosshair")
    void invokeRenderDebugCrosshair(float tickDelta);

    @Accessor("renderHand")
    boolean isHandEnabled();

    @Accessor("renderingPanorama")
    boolean isRenderingPanorama();

    @Invoker("shouldRenderBlockOutline")
    boolean invokeShouldRenderBlockOutline();

    @Invoker("updateTargetedEntity")
    void invokeUpdateTargetedEntity(float tickDelta);

    @Accessor("viewDistance")
    float getViewDistance();

    @Accessor("cursorDeltaX")
    float getCursorDeltaX();

    @Accessor("cursorDeltaX")
    void setCursorDeltaX(float value);

    @Accessor("cursorDeltaY")
    float getCursorDeltaY();

    @Accessor("cursorDeltaY")
    void setCursorDeltaY(float value);

    @Accessor("lastTickDelta")
    float getLastTickDelta();

    @Accessor("lastTickDelta")
    void setLastTickDelta(float value);

    @Accessor("smoothedCursorDeltaX")
    float getSmoothedCursorDeltaX();

    @Accessor("smoothedCursorDeltaY")
    float getSmoothedCursorDeltaY();

    @Accessor("lastWindowFocusedTime")
    long getLastWindowFocusedTime();

    @Accessor("lastWindowFocusedTime")
    void setLastWindowFocusedTime(long value);
}
