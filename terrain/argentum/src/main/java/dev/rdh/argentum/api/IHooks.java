package dev.rdh.argentum.api;

import org.embeddedt.embeddium.impl.gui.framework.TextComponent;

import java.util.ServiceLoader;

public interface IHooks {
    void setVsyncEnabled(boolean enabled);
    TextComponent getFriendlyModName(String id);

    int getFxaaMode();
    void setFxaaMode(int ordinal);

    int getUpscaleMode();
    void setUpscaleMode(int ordinal);

    float getWorldDownscale();
    void setWorldDownscale(float value);

    /**
     * Whether the graphics adapter can build and trace acceleration structures.
     * Every other ray tracing option is meaningless when this is false.
     */
    boolean isRayTracingSupported();

    /**
     * A short description of why ray tracing is unavailable, or an empty string
     * when it is available.
     */
    String getRayTracingStatus();

    boolean isRayTracingEnabled();
    void setRayTracingEnabled(boolean enabled);

    int getRayTracingQuality();
    void setRayTracingQuality(int ordinal);

    int getRayTracingScale();
    void setRayTracingScale(int ordinal);

    /** Indirect light strength, as a percentage. */
    int getRayTracingIndirect();
    void setRayTracingIndirect(int percent);

    /** Ambient occlusion strength, as a percentage. */
    int getRayTracingOcclusion();
    void setRayTracingOcclusion(int percent);

    /** How much light a ray reaching the sky brings back, as a percentage. */
    int getRayTracingSkyLight();
    void setRayTracingSkyLight(int percent);

    /** Extra brightness given to block light, which stands in for emission, as a percentage. */
    int getRayTracingEmissive();
    void setRayTracingEmissive(int percent);

    /** Direct sunlight and moonlight brightness, as a percentage. */
    int getRayTracingSun();
    void setRayTracingSun(int percent);

    /** Ambient brightness of the sky dome, as a percentage. */
    int getRayTracingSkyAmbient();
    void setRayTracingSkyAmbient(int percent);

    /** Brightness of Minecraft's block light, as a percentage. */
    int getRayTracingBlockLight();
    void setRayTracingBlockLight(int percent);

    /** Exposure applied before tone mapping, as a percentage. */
    int getRayTracingExposure();
    void setRayTracingExposure(int percent);

    boolean isRayTracedReflections();
    void setRayTracedReflections(boolean enabled);

    int getRayTracingDenoiser();
    void setRayTracingDenoiser(int ordinal);

    int getRayTracingFilterIterations();
    void setRayTracingFilterIterations(int iterations);

    int getRayTracingAccumulation();
    void setRayTracingAccumulation(int frames);

    /** Sections whose structures may be built per frame. */
    int getRayTracingBuildBudget();
    void setRayTracingBuildBudget(int sections);

    /** Radius around the camera kept traceable, in chunk sections. */
    int getRayTracingSceneRadius();
    void setRayTracingSceneRadius(int sections);

    int getRayTracingDebugView();
    void setRayTracingDebugView(int ordinal);

    IHooks INSTANCE = ServiceLoader.load(IHooks.class)
            .findFirst()
            .orElseThrow(() -> new NullPointerException("Failed to load hook service"));
}
