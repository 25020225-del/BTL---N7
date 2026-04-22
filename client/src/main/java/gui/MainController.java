package gui;

import java.io.IOException;

public class MainController
{
    private static void startBidder() throws IOException{
        ClientBidderController clientBidderController = new ClientBidderController();
        clientBidderController.start();
    }
    private static void startSeller() throws IOException{
        ClientSellerController clientSellerController = new ClientSellerController();
        clientSellerController.start();
    }
    public static void start() throws IOException {
        //startBidder();
        startSeller();
    }
}
