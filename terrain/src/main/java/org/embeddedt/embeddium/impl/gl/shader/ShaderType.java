package org.embeddedt.embeddium.impl.gl.shader;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import re.lilith.kalia.renderer.shader.ShaderStage;

/**
 * An enumeration over the supported Kalia shader types.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ShaderType {
    VERTEX(ShaderStage.VERTEX, "vsh"),
    FRAGMENT(ShaderStage.FRAGMENT, "fsh");

    public final ShaderStage stage;
    public final String fileExtension;
}
