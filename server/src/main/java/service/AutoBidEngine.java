package service;

import controller.ServerBidderController;
import model.auction.Auction;
import model.auction.AutoBid;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static utils.ConsoleColors.*;

/**
 * The core engine responsible for managing and executing automated bidding logic.
 * It processes registered bots asynchronously using a priority-based approach
 * (First-In, First-Out by registration time) to ensure fairness and prevent thread starvation.
 *
 * Part of the Auction System project.
 */
public class AutoBidEngine {

    /** Fixed thread pool for managing concurrent bot execution. */
    private static final int MAX_BOTPOOL_SIZE = 50;
    private static final ExecutorService botPool = Executors.newFixedThreadPool(MAX_BOTPOOL_SIZE);

    /** Controller used to handle the actual bid placement and wallet transactions. */
    private static final ServerBidderController bidderCtrl = new ServerBidderController();

    /**
     * Entry point to trigger an automated scan for a specific auction.
     * This is usually called after a manual bid or a new bot registration.
     *
     * @param auction The auction session to be scanned for potential bot actions.
     */
    public static void triggerBotScan(Auction auction) {
        botPool.submit(() -> processNextBot(auction));
    }

    /**
     * Recursively evaluates the bot queue and executes bids.
     * Uses a deep copy of the bot registry to ensure thread safety and avoid
     * ConcurrentModificationException during asynchronous processing.
     *
     * @param auction The active auction session being processed.
     */
    private static void processNextBot(Auction auction) {
        if (!auction.getStatus().equals(Auction.STATUS_RUNNING)) {
            return;
        }

        PriorityQueue<AutoBid> originalBots = auction.getActiveAutoBids();
        if (originalBots.isEmpty()) return;

        // ENFORCEMENT: Create a deep copy to prevent mutability issues across threads.
        PriorityQueue<AutoBid> bots = new PriorityQueue<>(Comparator.comparing(AutoBid::getTimeRegistered));
        for (AutoBid original : originalBots) {
            AutoBid copy = new AutoBid(original.getBidder(), original.getMaxBid(), original.getIncrement());
            copy.setTimeRegistered(original.getTimeRegistered());
            bots.offer(copy);
        }

        AutoBid capableBot = null;
        double requiredBid = 0;

        while (!bots.isEmpty()) {
            AutoBid bot = bots.poll();

            // Skip if the bot is already leading the auction.
            if (auction.getWinningBidder() != null &&
                    bot.getBidder().getId().equals(auction.getWinningBidder().getId())) {
                continue;
            }

            // Calculate the next required bid based on auction rules and bot preferences.
            double actualIncrement = Math.max(auction.getBidIncrement(), bot.getIncrement());
            requiredBid = (auction.getWinningBidder() == null) ?
                    auction.getItem().getStartingPrice() :
                    auction.getCurrentPrice() + actualIncrement;

            if (requiredBid <= bot.getMaxBid()) {
                capableBot = bot;
                break;
            }
        }

        if (capableBot != null) {
            final AutoBid currentBot = capableBot;
            final double finalRequiredBid = requiredBid;

            System.out.println("[Auto-Bid Engine]: Bot of \""
                    + YELLOW + currentBot.getBidder().getUserName() + RESET + "\" is attempting an automatic bid.");

            // Use an async callback to either recurse or clean up failed bots.
            bidderCtrl.placeBidOnAuction(currentBot.getBidder(), auction, finalRequiredBid, true)
                    .thenAccept(success -> {
                        if (success) {
                            // Price changed, re-trigger scanning to see if other bots respond.
                            processNextBot(auction);
                        } else {
                            // Typically occurs if the user has insufficient wallet funds.
                            System.out.println("[Auto-Bid Engine]: Bot of \""
                                    + YELLOW + currentBot.getBidder().getUserName() + RESET + "\" failed. Removing configuration.");
                            auction.getActiveAutoBids().removeIf(b ->
                                    b.getBidder().getId().equals(currentBot.getBidder().getId())
                            );
                            processNextBot(auction);
                        }
                    }).exceptionally(ex -> {
                        System.out.println("[System]: Bot Engine execution failed: " + RED + ex.getMessage() + RESET);
                        return null;
                    });
        }
    }
}