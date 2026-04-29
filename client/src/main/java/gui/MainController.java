package gui;

import model.Admin;
import model.Bidder;
import model.Seller;
import model.User;

import java.io.IOException;

public class MainController {
    public static void start(User user) throws IOException {
        if (user.getRole().equalsIgnoreCase("ADMIN")) {
            startAdmin(user);
        } else {
            // Both Bidder and Seller use the same UI
            startUnifiedUser(user);
        }
    }

    private static void startUnifiedUser(User user) throws IOException {
        ClientUserController controller = new ClientUserController(user);
        controller.start();
    }

    //private static void startBidder(User user) throws IOException {
    //    ClientBidderController controller = new ClientBidderController(user);
    //    controller.start();
    //}

    //private static void startSeller(User user) throws IOException {
    //    ClientSellerController controller = new ClientSellerController(user);
    //    controller.start();
    //}

    private static void startAdmin(User user) throws IOException {
        ClientAdminController controller = new ClientAdminController(user);
        controller.start();
    }
}
