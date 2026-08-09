package re.lilith.kalia.javatest;

import kotlin.Unit;
import re.lilith.kalia.renderer.Kalia;
import re.lilith.kalia.renderer.api.RenderPipeline;
import re.lilith.kalia.renderer.api.ShaderFormat;
import re.lilith.kalia.renderer.format.VertexAttributeFormat;
import re.lilith.kalia.renderer.format.VertexFormat;
import re.lilith.kalia.renderer.format.VertexStepMode;
import re.lilith.kalia.renderer.graph.RenderGraph_dslKt;
import re.lilith.kalia.renderer.shader.BindingKind;
import re.lilith.kalia.renderer.shader.ShaderSource;
import re.lilith.kalia.renderer.shader.ShaderStage;

public class JavaTest {
    static void main() {
        Kalia.INSTANCE.createDevice(
                null,
                null,
                null
        );

        var vertexFormat = new VertexFormat.Builder(VertexStepMode.VERTEX)
                .attribute("Position", 0, VertexAttributeFormat.FLOAT4)
                .attribute("TexCoord", 1, VertexAttributeFormat.SHORT2)
                .build();

        var program = new ShaderFormat.Builder()
                .setLabel("kalia:blur")
                .addStage(ShaderStage.VERTEX, new ShaderSource.Glsl("blur.vert", "..."))
                .addStage(ShaderStage.FRAGMENT, new ShaderSource.Glsl("blur.frag", "..."))
                .bind("SourceFb", 0, BindingKind.TEXTURE, ShaderStage.FRAGMENT)
                .bind("BlurSettings", 0, BindingKind.UNIFORM_BUFFER, ShaderStage.FRAGMENT)
                .pushConstants(104)
                .build();


        var renderPipeline = new RenderPipeline.Builder(program)
                .vertexFormat(vertexFormat)
                .build();

    }
}
