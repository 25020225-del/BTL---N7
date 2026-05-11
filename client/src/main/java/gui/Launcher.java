package gui;

import java.awt.*;

/**
 * A launcher class for the JavaFX application.
 * It serves as the primary entry point to configure dynamic UI scaling based on
 * the user's screen resolution before starting the main JavaFX application loop.
 */
public class Launcher {

    /**
     * Calculates the appropriate UI scaling factor based on the current screen's height.
     * The calculation uses a baseline height of 720 pixels and applies a 0.6 multiplier
     * to determine the final scale ratio.
     *
     * @return The calculated scaling factor as a double.
     */
    public static double getWindowsScale() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double height = screenSize.getHeight();
        return (height / 720.0) * 0.7;
    }

    /**
     * The main entry point of the application.
     * It sets the JavaFX system properties for UI scaling on Windows and GTK environments
     * before delegating the execution to the main application class.
     *
     * @param args Command-line arguments passed to the application.
     */
    public static void main(String[] args) {
        System.setProperty("glass.win.uiScale", getWindowsScale() + "");
        System.setProperty("glass.gtk.uiScale", getWindowsScale() + "");
        MainApplication.main(args);
    }
}