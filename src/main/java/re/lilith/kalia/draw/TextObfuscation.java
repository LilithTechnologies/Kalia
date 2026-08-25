package re.lilith.kalia.draw;

public final class TextObfuscation {
    private TextObfuscation() {
    }

    public static boolean contains(String text) {
        int index = text.indexOf(167);
        while (index >= 0 && index + 1 < text.length()) {
            char next = text.charAt(index + 1);
            if (next == 'k' || next == 'K') {
                return true;
            }
            index = text.indexOf(167, index + 2);
        }
        return false;
    }
}
