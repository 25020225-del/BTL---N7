package gui;

import java.awt.Toolkit;
import java.awt.Dimension;

public class Launcher{
    public static double getWindowsScale(){
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double height = screenSize.getHeight();
        return (height / 720.0) * 0.8;
    }
    public static void main(String[] args){

        System.setProperty("glass.win.uiScale",getWindowsScale()+"");
        System.setProperty("glass.gtk.uiScale",getWindowsScale()+"");
        //MUST NOT IMPORT JAVAFX HERE
        MainApplication.main(args);
    }
}