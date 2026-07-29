package org.embeddedt.embeddium.impl.gpu.shader;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import re.lilith.kalia.renderer.shader.ShaderStage;

/**
 * An enumeration over the supported Kalia shader types.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ShaderType {
    VERTEX("vsh"),
    FRAGMENT("fsh");

    public final String fileExtension;
}
