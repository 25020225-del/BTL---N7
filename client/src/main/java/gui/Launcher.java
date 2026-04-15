package gui;

public class Launcher {
    public static void main(String[] args) {
        // Giữ nguyên thiết lập UI Scale cực xịn của bạn
        System.setProperty("glass.win.uiScale", "1.0");
        System.setProperty("glass.gtk.uiScale", "2.0");

        // ĐÃ SỬA: Đi đường vòng qua MainApplication
        // Tuyệt đối không import javafx.application.Application ở file này
        MainApplication.main(args);
    }
}