package service;

import controller.ServerBidderController;
import model.Auction;
import model.AutoBid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static utils.ConsoleColors.*;

/**
 * Manages automated bidding operations asynchronously.
 * Prevents thread starvation by using recursive callbacks instead of blocking loops.
 */
public class AutoBidEngine {

    // Reduced thread pool size since tasks are now non-blocking
    private static final int MAX_BOTPOOL_SIZE = 50;
    private static final ExecutorService botPool = Executors.newFixedThreadPool(MAX_BOTPOOL_SIZE);

    private static final ServerBidderController bidderCtrl = new ServerBidderController();

    /**
     * Initiates the auto-bid scanning process for a specific auction.
     * @param auction The auction to scan for potential auto-bids.
     */
    public static void triggerBotScan(Auction auction) {
        botPool.submit(() -> processNextBot(auction));
    }

    /**
     * Recursively and asynchronously processes bots one by one.
     * Evaluates constraints and submits bids without blocking the active thread.
     *
     * @param auction The auction being processed.
     */
    private static void processNextBot(Auction auction) {
        // Double-check if the auction is still running before processing bots
        if (!auction.getStatus().equals(Auction.STATUS_RUNNING)) {
            return;
        }

        List<AutoBid> originalBots = auction.getActiveAutoBids();
        if (originalBots.isEmpty()) return;

        // Perform Deep Copy to prevent ConcurrentModificationException during asynchronous execution
        List<AutoBid> bots = new ArrayList<>();
        for (AutoBid original : originalBots) {
            AutoBid copy = new AutoBid(original.getBidder(), original.getMaxBid(), original.getIncrement());
            copy.setTimeRegistered(original.getTimeRegistered());
            bots.add(copy);
        }

        AutoBid capableBot = null;
        double requiredBid = 0;

        // Identify the first bot capable of outbidding the current winner
        for (AutoBid bot : bots) {
            if (auction.getWinningBidder() != null &&
                    bot.getBidder().getId().equals(auction.getWinningBidder().getId())) {
                continue;
            }

            requiredBid = (auction.getWinningBidder() == null) ?
                    auction.getItem().getStartingPrice() :
                    auction.getCurrentPrice() + bot.getIncrement();

            if (requiredBid <= bot.getMaxBid()) {
                capableBot = bot;
                break; // Found the next bot to bid
            }
        }

        // If a capable bot is found, submit the bid asynchronously
        if (capableBot != null) {
            final AutoBid currentBot = capableBot;
            final double finalRequiredBid = requiredBid; // Must be final for lambda

            System.out.println("[Auto-Bid Engine]: Bot of \""
                    + YELLOW + currentBot.getBidder().getUserName() + RESET + "\" is trying to auto-bid");

            // Execute bid and handle the result via an async callback (.thenAccept)
            bidderCtrl.placeBidOnAuction(currentBot.getBidder(), auction, finalRequiredBid, true)
                    .thenAccept(success -> {
                        if (success) {
                            // If successful, the price changed. Recursively check for the next bot.
                            processNextBot(auction);
                        } else {
                            // If failed (e.g., insufficient funds), remove the bot and proceed.
                            System.out.println("[Auto-Bid Engine]: Bot of \""
                                    + YELLOW + currentBot.getBidder().getUserName() + RESET + "\" failed (insufficient funds). Removing configuration.");
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