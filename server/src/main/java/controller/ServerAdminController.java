package controller;

import model.Admin;
import model.Seller;
import model.Auction;

import static utils.ConsoleColors.*;

public class ServerAdminController {

    public boolean approveAuction(Admin admin, Auction auction) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
            System.out.println("[Security]: " + RED + "User does not have approval rights" + RESET);
            return false;
        }

        auction.setStatus(Auction.STATUS_OPEN);
        System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has approved auction \"" + YELLOW + auction.getId() + RESET + "\"");

        return true;
    }

    public void verifySeller(Admin admin, Seller seller) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            seller.setGood(true);
            System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has verified Seller \"" + YELLOW + seller.getName() + RESET + "\" as reputable");
        }
    }

    public void rejectAuctionRequest(Admin admin, Auction auction) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            auction.setStatus(Auction.STATUS_CANCELED);
            System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has rejected the auction request for \"" + YELLOW + auction.getId() + RESET + "\"");
        }
    }

    public void forceDeleteAuction(Admin admin, Auction auction) {

        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            auction.setStatus(Auction.STATUS_DELETED);
            System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has permanently deleted auction \"" + YELLOW + auction.getId() + RESET + "\"");
        }
    }

}