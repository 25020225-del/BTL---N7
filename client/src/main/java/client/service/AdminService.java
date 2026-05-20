package client.service;

import client.network.NetworkService;

/**
 * Service layer for admin-related network commands.
 *
 * <p>All methods simply delegate to {@link NetworkService#sendMessage}, keeping
 * the UI layer ignorant of the underlying protocol.</p>
 */
public class AdminService {

    public static final String BLOCK_USER   = "BLOCK_USER";
    public static final String UNBLOCK_USER = "UNBLOCK_USER";

    // ── Auction management ────────────────────────────────────────────────────

    /** Fetches all auctions awaiting admin approval. */
    public static void fetchPendingAuctions() {
        NetworkService.sendMessage("FETCH_PENDING_AUCTIONS", "");
    }

    /** Approves the specified pending auction. */
    public static void approveAuction(String auctionId) {
        NetworkService.sendMessage("APPROVE_AUCTION", auctionId);
    }

    /** Rejects the specified pending auction. */
    public static void rejectAuction(String auctionId) {
        NetworkService.sendMessage("REJECT_AUCTION", auctionId);
    }

    // ── User management ───────────────────────────────────────────────────────

    /** Fetches the full list of registered users. */
    public static void fetchUsers() {
        NetworkService.sendMessage("FETCH_USERS", "");
    }

    /**
     * Blocks or unblocks the specified user.
     *
     * @param command Either {@link #BLOCK_USER} or {@link #UNBLOCK_USER}.
     * @param userId  Target user's ID.
     */
    public static void blockUser(String command, String userId) {
        switch (command) {
            case BLOCK_USER   -> NetworkService.sendMessage(BLOCK_USER,   userId);
            case UNBLOCK_USER -> NetworkService.sendMessage(UNBLOCK_USER, userId);
        }
    }

    /** Logs the admin out of the server session. */
    public static void logout() {
        NetworkService.sendMessage("LOGOUT", "");
    }

    // ── Withdrawal management [NEW] ───────────────────────────────────────────

    /**
     * Requests the list of all PENDING withdrawal requests from the server.
     * Response command: {@code FETCH_WITHDRAW_REQUESTS_SUCCESS}.
     */
    public static void fetchWithdrawRequests() {
        NetworkService.sendMessage("FETCH_WITHDRAW_REQUESTS", "");
    }

    /**
     * Approves a specific withdrawal request.
     * Response commands: {@code WITHDRAW_ACTION_SUCCESS} or {@code ERROR}.
     *
     * @param requestId The ID of the withdrawal request to approve.
     */
    public static void approveWithdraw(String requestId) {
        NetworkService.sendMessage("APPROVE_WITHDRAW", requestId);
    }

    /**
     * Rejects a specific withdrawal request, refunding the user's locked balance.
     * Response commands: {@code WITHDRAW_ACTION_SUCCESS} or {@code ERROR}.
     *
     * @param requestId The ID of the withdrawal request to reject.
     */
    public static void rejectWithdraw(String requestId) {
        NetworkService.sendMessage("REJECT_WITHDRAW", requestId);
    }
}