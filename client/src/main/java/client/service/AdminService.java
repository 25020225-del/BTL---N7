package client.service;

import client.network.NetworkService;

/**
 * Service class encapsulating all administrator-level network commands.
 *
 * <p>Provides a clear, strongly-typed API for admin actions instead of exposing
 * raw command strings to the calling code.</p>
 *
 * <p>This is a stateless utility class and must not be instantiated.</p>
 */
public final class AdminService {

    /** Private constructor — utility class, not instantiable. */
    private AdminService() {}

    /**
     * Requests the server to return the list of auctions pending admin approval.
     */
    public static void fetchPendingAuctions() {
        NetworkService.sendMessage("FETCH_PENDING_AUCTIONS", "");
    }

    /**
     * Requests the server to return the full list of registered users.
     */
    public static void fetchUsers() {
        NetworkService.sendMessage("FETCH_USERS", "");
    }

    /**
     * Sends a logout request for the currently authenticated admin session.
     */
    public static void logout() { // FIX: was AdminService.logout() which correctly delegates, but UserService had LogOut() — normalized here
        NetworkService.sendMessage("LOGOUT", "");
    }

    /**
     * Sends a request to approve a specific pending auction.
     *
     * @param auctionId The unique identifier of the auction to approve.
     */
    public static void approveAuction(String auctionId) {
        NetworkService.sendMessage("APPROVE_AUCTION", auctionId);
    }

    /**
     * Sends a request to reject a specific pending auction.
     *
     * @param auctionId The unique identifier of the auction to reject.
     */
    public static void rejectAuction(String auctionId) {
        NetworkService.sendMessage("REJECT_AUCTION", auctionId);
    }

    /**
     * Sends a request to block a user account, preventing them from logging in.
     *
     * <p><b>FIX (SRP / Clean API):</b> The original {@code blockUser(String command, String id)}
     * method was a violation of the SRP and the Command-Query Separation principle.
     * Callers were required to pass a raw command string ("BLOCK_USER" or "UNBLOCK_USER"),
     * which leaked internal protocol details. This is now replaced by two clearly named methods.</p>
     *
     * @param userId The unique identifier of the user to block.
     */
    public static void blockUser(String userId) {
        NetworkService.sendMessage("BLOCK_USER", userId);
    }

    /**
     * Sends a request to unblock a previously blocked user account.
     *
     * @param userId The unique identifier of the user to unblock.
     */
    public static void unblockUser(String userId) {
        NetworkService.sendMessage("UNBLOCK_USER", userId);
    }
}
