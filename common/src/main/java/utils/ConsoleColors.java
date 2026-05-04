package utils;

/**
 * A utility class containing ANSI escape codes for colorizing console output.
 * These constants can be used to improve the readability of log messages, system alerts,
 * and debugging information within the command-line interface.
 */
public final class ConsoleColors {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ConsoleColors() {
    }

    /**
     * Reset the text color and style to the console default.
     */
    public static final String RESET = "\u001B[0m";

    /**
     * Standard Red color code, typically used for errors or critical failures.
     */
    public static final String RED = "\u001B[31m";

    /**
     * Standard Green color code, typically used for success messages or confirmation.
     */
    public static final String GREEN = "\u001B[32m";

    /**
     * Standard Yellow color code, typically used for warnings or system alerts.
     */
    public static final String YELLOW = "\u001B[33m";

    /**
     * Standard Blue color code, typically used for informational logs or secondary details.
     */
    public static final String BLUE = "\u001B[34m";
}