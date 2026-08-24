package re.lilith.kalia.entity;

public final class KaliaTick {
    private static int counter;

    private KaliaTick() {
    }

    public static void advance() {
        counter++;
    }

    public static int current() {
        return counter;
    }
}
