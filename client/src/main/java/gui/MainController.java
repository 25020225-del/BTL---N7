package gui;

import model.Admin;
import model.Bidder;
import model.Seller;
import model.User;

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
    private static void startAdmin() throws IOException{
        ClientAdminController clientAdminController = new ClientAdminController();
        clientAdminController.start();
    }
    public static void start(User user) throws IOException {
        if(user instanceof Admin){
            startAdmin();
        }
        else if(user instanceof Bidder){
            startBidder();
        } else if (user instanceof Seller) {
            startSeller();
        }
        //startBidder();
        //startSeller();
        //startAdmin();
    }
}
