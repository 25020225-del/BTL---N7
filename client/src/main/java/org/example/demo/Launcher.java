package org.example.demo;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        System.setProperty("glass.win.uiScale", "2.0");
        System.setProperty("glass.gtk.uiScale", "2.0");
        Application.launch(MainApplication.class, args);
    }
}
