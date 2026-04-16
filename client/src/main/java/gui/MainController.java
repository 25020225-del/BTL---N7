package gui;

import java.io.IOException;

public class MainController
{
    public static void start() throws IOException {
        MainApplication.setNewScene(MainApplication.rootMainView);
        ClientBidderController.start();
    }
}
