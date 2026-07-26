package net.caffeinemc.mods.sodium.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import re.lilith.kalia.frame.GameFrame;

import java.util.ArrayDeque;
import java.util.Deque;

public class ScissorUtil {
    private static final Deque<Rect> SCISSOR_STACK = new ArrayDeque<>();

    public static void scissor(int x, int y, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = new Window(client);
        int scale = window.getScaleFactor();

        setScissor(
                x * scale,
                (client.height - (y + height) * scale),
                width * scale,
                height * scale
        );
    }

    public static void withScissor(int x, int y, int width, int height, Runnable action) {
        MinecraftClient client = MinecraftClient.getInstance();
        int scale = new Window(client).getScaleFactor();
        pushScissor(
                x * scale,
                client.height - (y + height) * scale,
                width * scale,
                height * scale
        );

        try {
            action.run();
        } finally {
            popScissor();
        }
    }

    public static void pushScissor(int x, int y, int width, int height) {
        Rect requested = new Rect(x, y, width, height);
        Rect parent = SCISSOR_STACK.peek();
        Rect effective = parent == null ? requested : parent.intersection(requested);

        SCISSOR_STACK.push(effective);
        apply(effective);
    }

    public static void setScissor(int x, int y, int width, int height) {
        if (SCISSOR_STACK.isEmpty()) {
            GameFrame.INSTANCE.setScissor(x, y, width, height);
            return;
        }

        SCISSOR_STACK.pop();
        pushScissor(x, y, width, height);
    }

    public static void popScissor() {
        if (SCISSOR_STACK.isEmpty()) {
            return;
        }

        SCISSOR_STACK.pop();
        Rect previous = SCISSOR_STACK.peek();
        if (previous == null) {
            GameFrame.INSTANCE.resetScissor();
        } else {
            apply(previous);
        }
    }

    private static void apply(Rect rect) {
        GameFrame.INSTANCE.setScissor(rect.x, rect.y, rect.width, rect.height);
    }

    private record Rect(int x, int y, int width, int height) {
        private Rect intersection(Rect other) {
            int left = Math.max(this.x, other.x);
            int top = Math.max(this.y, other.y);
            int right = Math.min(this.x + this.width, other.x + other.width);
            int bottom = Math.min(this.y + this.height, other.y + other.height);

            return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }
}
