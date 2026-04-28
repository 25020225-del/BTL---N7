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
        } else if (user.getRole().equalsIgnoreCase("SELLER")) {
            startSeller(user);
        } else {
            startBidder(user);
        }
    }
    private static void startBidder(User user) throws IOException {
        ClientBidderController controller = new ClientBidderController(user);
        controller.start();
    }

    private static void startSeller(User user) throws IOException {
        ClientSellerController controller = new ClientSellerController(user);
        controller.start();
    }

    private static void startAdmin(User user) throws IOException {
        ClientAdminController controller = new ClientAdminController(user);
        controller.start();
    }
}
