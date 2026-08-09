package org.embeddedt.embeddium.impl.render.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ShaderLoader {
    public static String getShaderSource(String name) {
        String[] splitStr;
        if(name.contains(":")) {
            splitStr = name.split(":", 2);
        } else {
            splitStr = new String[] { "minecraft", name };
        }
        String path = String.format("/assets/%s/shaders/%s", splitStr[0], splitStr[1]);

        try (InputStream in = ShaderLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new RuntimeException("Shader not found: " + path);
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader source for " + path, e);
        }
    }
}
