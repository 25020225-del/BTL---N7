package gui;

public class Launcher{
    public static void main(String[] args){
        //Keep UI Setting
        System.setProperty("glass.win.uiScale","1.0");
        System.setProperty("glass.gtk.uiScale","2.0");

        //MUST NOT IMPORT JAVAFX HERE
        MainApplication.main(args);
    }
}