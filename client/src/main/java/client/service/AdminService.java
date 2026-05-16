package client.service;

import client.network.NetworkService;

public class AdminService {
    public static void fetchPendingAuctions(){
        NetworkService.sendMessage("FETCH_PENDING_AUCTIONS", "");
    }
    public static void fetchUsers(){
        NetworkService.sendMessage("FETCH_USERS", "");
    }
    public static void logout(){
        NetworkService.sendMessage("LOGOUT", "");
    }
}
