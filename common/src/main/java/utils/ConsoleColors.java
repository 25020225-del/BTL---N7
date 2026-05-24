package utils;

/**
 * Utility class containing ANSI escape codes for colorizing console outputs.
 * Provides invariant text styling templates to safeguard standard log readability.
 */
public final class ConsoleColors {

    private ConsoleColors() {
    }

    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
}