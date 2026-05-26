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

/**
 * Controller handling lifecycle modifications managed exclusively by auction hosts.
 * Governs state verification for edits, creation payloads, and logical soft deletes.
 */
public class ServerSellerController {

    private static final Logger log = LoggerFactory.getLogger(ServerSellerController.class);
    private final AuctionDAO auctionDAO;

    public ServerSellerController(AuctionDAO auctionDAO) {
        this.auctionDAO = auctionDAO;
    }

    public Auction addAuction(User currentUser, Item item, long bidIncrement, LocalDateTime startTime, int durationMinutes) {
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

    public boolean editAuction(User currentUser, Auction auction, String newName, String newDesc, long newStartPrice, LocalDateTime newStartTime, LocalDateTime newEndTime, int newDurationMinutes) {
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

        String currentStatus = auction.getStatus();
        String newStatus = (currentStatus.equals(Auction.STATUS_CANCELED) || currentStatus.equals(Auction.STATUS_OPEN))
                ? Auction.STATUS_PENDING : currentStatus;

        try {
            if (auctionDAO.updateAuction(auction, newName, newDesc, newStartPrice, newStartTime, newEndTime, newDurationMinutes, newStatus)) {
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    auction.getItem().setItemName(newName);
                    auction.getItem().setDescription(newDesc);
                    auction.getItem().setStartingPrice(newStartPrice);
                    auction.setCurrentPrice(newStartPrice);
                    auction.setStartTime(newStartTime);
                    auction.setEndTime(newEndTime);
                    auction.setDurationMinutes(newDurationMinutes);
                    auction.setStatus(newStatus);
                }

                if (currentStatus.equals(Auction.STATUS_OPEN) && newStatus.equals(Auction.STATUS_PENDING)) {
                    AuctionManager.getAuctionList().remove(auction);
                    log.info("Auction {} demoted to PENDING and removed from RAM Monitor.", auction.getId());
                }
                return true;
            }
        } catch (SQLException e) {
            log.error("Database error during editAuction for {}", auction.getId(), e);
        }
        return false;
    }

    public boolean deleteAuction(User currentUser, Auction auction) {
        if (!auction.getSeller().getId().equals(currentUser.getId())) {
            return false;
        }

        String status = auction.getStatus();
        if (!status.equals(Auction.STATUS_PENDING) && !status.equals(Auction.STATUS_CANCELED)) {
            return false;
        }

        try {
            if (auctionDAO.updateAuctionStatus(auction.getId(), Auction.STATUS_DELETED)) {
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    auction.setStatus(Auction.STATUS_DELETED);
                }
                AuctionManager.getAuctionList().remove(auction);
                return true;
            }
        } catch (SQLException e) {
            log.error("Database error during deleteAuction for {}", auction.getId(), e);
        }
        return false;
    }
}