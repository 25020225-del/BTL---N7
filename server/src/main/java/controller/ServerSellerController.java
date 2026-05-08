package controller;

import database.dao.AuctionDAO;
import model.auction.Auction;
import model.item.Item;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;

import java.sql.SQLException;
import java.time.LocalDateTime;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for handling auction-related actions initiated by a seller.
 * It provides functionality to create new auctions, modify existing ones under specific
 * conditions, and handle the deletion/removal of auction sessions from the active database.
 */
public class ServerSellerController {

    private static final Logger log = LoggerFactory.getLogger(ServerSellerController.class);

    private final AuctionDAO auctionDAO;

    /**
     * Constructs the controller with the necessary Data Access Objects.
     * This implementation follows the Dependency Injection pattern to facilitate 
     * easier testing and decoupling.
     *
     * @param auctionDAO The DAO responsible for auction-related database transactions.
     */
    public ServerSellerController(AuctionDAO auctionDAO) {
        this.auctionDAO = auctionDAO;
    }

    /**
     * Creates and persists a new auction session in the database.
     * This method generates a dynamic auction ID and calculates the end time based
     * on the provided duration.
     *
     * @param currentUser     The authenticated user who is hosting/selling the item.
     * @param item            The item entity to be placed under auction.
     * @param bidIncrement    The minimum amount that each subsequent bid must increase by.
     * @param durationMinutes The total time the auction will remain active.
     * @return A newly created {@link Auction} instance if successful; {@code null} if a database error occurs.
     */
    public Auction addAuction(User currentUser, Item item, double bidIncrement, LocalDateTime startTime, int durationMinutes) {
        // Utilize the factory method to prepare the Auction object in RAM
        Auction newAuction = Auction.createNewAuction(item, currentUser, bidIncrement, startTime, durationMinutes);

        try {
            if (auctionDAO.addAuction(newAuction)) {
                log.info("User {} created auction: {}", currentUser.getName(), item.getItemName());
                return newAuction;
            }
        } catch (SQLException e) {
            log.error("Database error during addAuction", e);
        }
        return null;
    }

    /**
     * Updates the information of an existing auction.
     * Modification is strictly prohibited if the auction is already RUNNING,
     * FINISHED, or DELETED to maintain system integrity.
     *
     * @param currentUser   The user attempting the edit (must be the original seller).
     * @param auction       The auction session to be modified.
     * @param newName       The updated item name.
     * @param newDesc       The updated item description.
     * @param newStartPrice The updated starting/base price.
     * @param newStartTime  The updated scheduled start time.
     * @param newEndTime    The updated scheduled end time.
     * @return {@code true} if the update was successful and permitted; {@code false} otherwise.
     */
    public boolean editAuction(User currentUser, Auction auction, String newName, String newDesc, double newStartPrice, LocalDateTime newStartTime, LocalDateTime newEndTime) {
        // Security check: Only the owner can edit the auction
        if (!auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Edit denied: not owner of auction {}", auction.getId());
            return false;
        }

        if (auction.getStatus().equals(Auction.STATUS_RUNNING)
                || auction.getStatus().equals(Auction.STATUS_PAID)
                || auction.getStatus().equals(Auction.STATUS_FINISHED)
                || auction.getStatus().equals(Auction.STATUS_DELETED)) {
            log.warn("Cannot edit auction {} in status {}", auction.getId(), auction.getStatus());
            return false;
        }

        String newStatus = auction.getStatus().equals(Auction.STATUS_CANCELED)
                ? Auction.STATUS_PENDING
                : auction.getStatus();

        try {
            if (auctionDAO.updateAuction(auction, newName, newDesc, newStartPrice, newStartTime, newEndTime, newStatus)) {
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    auction.getItem().setItemName(newName);
                    auction.getItem().setDescription(newDesc);
                    auction.getItem().setStartingPrice(newStartPrice);
                    auction.setCurrentPrice(newStartPrice);
                    auction.setStartTime(newStartTime);
                    auction.setEndTime(newEndTime);
                    auction.setStatus(newStatus);
                }

                log.info("Auction {} updated successfully", auction.getId());
                return true;
            }
        } catch (SQLException e) {
            log.error("Database error during editAuction for {}", auction.getId(), e);
        }
        return false;
    }

    /**
     * Marks an auction as DELETED in the system.
     * This method verifies ownership before performing the status transition.
     *
     * @param currentUser The user attempting the deletion.
     * @param auction     The auction session to be removed.
     * @return {@code true} if the deletion was successful; {@code false} if unauthorized or a database error occurred.
     */
    public boolean deleteAuction(User currentUser, Auction auction) {
        // Security check: Only the owner can delete the auction
        if (!auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Delete denied: user {} not owner", currentUser.getId());
            return false;
        }

        try {
            if (auctionDAO.updateAuctionStatus(auction.getId(), Auction.STATUS_DELETED)) {
                auction.setStatus(Auction.STATUS_DELETED);
                log.info("Auction {} deleted by {}", auction.getId(), currentUser.getName());
                return true;
            }
        } catch (SQLException e) {
            log.error("Database error during deleteAuction for {}", auction.getId(), e);
        }
        return false;
    }
}
