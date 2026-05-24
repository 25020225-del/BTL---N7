package client.service;

import client.network.NetworkService;

/**
 * Client-side service facade wrapping administrative command delivery channels over the network layer.
 */
public final class AdminService {

    private AdminService() {
    }

    public static void fetchPendingAuctions() {
        NetworkService.sendMessage("FETCH_PENDING_AUCTIONS", "");
    }

    public static void fetchRunningAuctions() {
        NetworkService.sendMessage("FETCH_AUCTIONS", "");
    }

    public static void fetchUsers() {
        NetworkService.sendMessage("FETCH_USERS", "");
    }

    public static void logout() {
        NetworkService.sendMessage("LOGOUT", "");
    }

    public static void blockUser(String userId) {
        NetworkService.sendMessage("BLOCK_USER", userId);
    }

    public static void unblockUser(String userId) {
        NetworkService.sendMessage("UNBLOCK_USER", userId);
    }

    public static void fetchWithdrawRequests() {
        NetworkService.sendMessage("FETCH_WITHDRAW_REQUESTS", "");
    }

    public static void approveWithdraw(String requestId) {
        NetworkService.sendMessage("APPROVE_WITHDRAW", requestId);
    }

    public static void rejectWithdraw(String requestId) {
        NetworkService.sendMessage("REJECT_WITHDRAW", requestId);
    }

    public static void toggleGoodStatus(String userId) {
        NetworkService.sendMessage("TOGGLE_GOOD_STATUS", userId);
    }

    public static void cancelAuction(String auctionId) {
        NetworkService.sendMessage("CANCEL_AUCTION", auctionId);
    }
    public static void approveAuction(String auctionId) {
        NetworkService.sendMessage("APPROVE_AUCTION", auctionId);
    }

    public static void rejectAuction(String auctionId) {
        NetworkService.sendMessage("REJECT_AUCTION", auctionId);
    }
}