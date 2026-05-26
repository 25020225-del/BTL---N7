package gui;

import java.awt.*;

/**
 * Primary runtime bootstrap entry point for the JavaFX application.
 * Evaluates host hardware display bounds to compute deterministic environment
 * scaling transformations prior to initializing the primary presentation engine loop.
 */
public class Launcher {

    /**
     * Computes the proportional UI scaling vector matching the host display height
     * against a normalized 720p baseline calibration.
     *
     * @return the computed geometric scaling modifier factor
     */
    public static double getWindowsScale() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double height = screenSize.getHeight();
        return (height / 720.0) * 0.7;
    }

    /**
     * Enforces explicit native window system scale attributes and hooks execution into
     * the core application layout manager.
     *
     * @param args execution framework runtime override parameters
     */
    public static void main(String[] args) {
        System.setProperty("glass.win.uiScale", getWindowsScale() + "");
        System.setProperty("glass.gtk.uiScale", getWindowsScale() + "");
        MainApplication.main(args);
    }
}