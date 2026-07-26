package re.lilith.kalia.javatest;

import kotlin.Unit;
import re.lilith.kalia.renderer.Kalia;
import re.lilith.kalia.renderer.graph.RenderGraph_dslKt;

public class JavaTest {
    static void main() {
        Kalia.INSTANCE.createDevice(
                null,
                null,
                null
        );

        RenderGraph_dslKt.renderGraph("Graph", context -> {
            context.pass("meow", pass -> {
                pass.draw(graph -> {
                    graph.drawIndexed(0, 0, 0, 0, 0);
                    return Unit.INSTANCE;
                });
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }
}
