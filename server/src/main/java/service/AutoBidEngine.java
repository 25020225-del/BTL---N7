package service;

import controller.ServerBidderController;
import model.Auction;
import model.AutoBid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static utils.ConsoleColors.*;

public class AutoBidEngine {
    // Thread pool to run in background bot's loops (not blocking main)
    private static final ExecutorService botPool = Executors.newCachedThreadPool();
    private static final ServerBidderController bidderCtrl = new ServerBidderController();

    public static void triggerBotScan(Auction auction) {
        botPool.submit(() -> {
            boolean priceChanged;
            do {
                priceChanged = false;
                // Cloning to avoid ConcurrentModificationException
                List<AutoBid> bots = new ArrayList<>(auction.getActiveAutoBids());

                for (AutoBid bot : bots) {
                    // Ignore if the bot is the current winner
                    if (auction.getWinningBidder() != null &&
                            bot.getBidder().getId().equals(auction.getWinningBidder().getId())) {
                        continue;
                    }

                    // Min amount to surpass current winner
                    double requiredBid = (auction.getWinningBidder() == null) ?
                            auction.getItem().getStartingPrice() :
                            auction.getCurrentPrice() + bot.getIncrement();

                    // if max bid can afford
                    if (requiredBid <= bot.getMaxBid()) {
                        System.out.println(BLUE + "[Auto-Bid Engine]: Bot của \"" + bot.getBidder().getUserName() + "\" đang tham chiến!" + RESET);

                        // Call bid function with transaction & refund Database (isBot = true)
                        boolean success = bidderCtrl.placeBidOnAuction(bot.getBidder(), auction, bot.getMaxBid(), true);

                        if (success) {
                            priceChanged = true; // Price change => tell other bots to autobid
                            break;
                        } else {
                            // Remove if failed
                            System.out.println("[Auto-Bid Engine]: Bot of \"" + YELLOW + bot.getBidder().getUserName() + RESET + "\" does not have enough money. Automatically remove bot");
                            auction.getActiveAutoBids().remove(bot);
                        }
                    }
                }
            } while (priceChanged); // Loop autobid until there's no affordable bot
        });
    }
}