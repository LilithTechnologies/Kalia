package net.caffeinemc.mods.sodium.legacy.compat.mojang.minecraft.render;

import re.lilith.kalia.gl.ShaderUniforms;

public class FogHelper {
    public static float getFogEnd() {
        return ShaderUniforms.INSTANCE.fogEnd();
    }

    public static float getFogStart() {
        return ShaderUniforms.INSTANCE.fogStart();
    }

    public static float[] getFogColor() {
        ShaderUniforms uniforms = ShaderUniforms.INSTANCE;
        return new float[]{uniforms.fogRed(), uniforms.fogGreen(), uniforms.fogBlue(), 1.0f};
    }
}
