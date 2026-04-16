package controller;

import model.Admin;
import model.Seller;
import model.Auction;

public class ServerAdminController {

    public static final String ANSI_RESET  = "\u001B[0m";
    public static final String ANSI_RED    = "\u001B[31m";
    public static final String ANSI_GREEN  = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    public boolean approveAuction(Admin admin, Auction auction) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
            System.out.println("[Security]: User does not have approval rights");
            return false;
        }

        auction.setStatus(Auction.STATUS_OPEN);
        System.out.println("[System]: Admin \"" + ANSI_YELLOW + admin.getName() + ANSI_RESET + "\" has approved auction \"" + ANSI_YELLOW + auction.getId() + ANSI_RESET + "\"");

        return true;
    }

    public void verifySeller(Admin admin, Seller seller) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            seller.setGood(true);
            System.out.println("[System]: Admin \"" + ANSI_YELLOW + admin.getName() + ANSI_RESET + "\" has verified Seller \"" + ANSI_YELLOW + seller.getName() + ANSI_RESET + "\" as reputable");
        }
    }

    public void rejectAuctionRequest(Admin admin, Auction auction) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            auction.setStatus(Auction.STATUS_CANCELED);
            System.out.println("[System]: Admin \"" + ANSI_YELLOW + admin.getName() + ANSI_RESET + "\" has rejected the auction request for \"" + ANSI_YELLOW + auction.getId() + ANSI_RESET + "\"");
        }
    }

    public void forceDeleteAuction(Admin admin, Auction auction) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            auction.setStatus(Auction.STATUS_DELETED);
            System.out.println("[System]: Admin \"" + ANSI_YELLOW + admin.getName() + ANSI_RESET + "\" has permanently deleted auction \"" + ANSI_YELLOW + auction.getId() + ANSI_RESET + "\"");
        }
    }
}