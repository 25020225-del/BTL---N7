package client.service;

import client.network.NetworkService;
import gui.widget.item.MinimalUser;

public class AdminService {
    public static final String BLOCK_USER = "BLOCK_USER";
    public static final String UNBLOCK_USER = "UNBLOCK_USER";
    public static void fetchPendingAuctions(){
        NetworkService.sendMessage("FETCH_PENDING_AUCTIONS", "");
    }
    public static void fetchUsers(){
        NetworkService.sendMessage("FETCH_USERS", "");
    }
    public static void logout(){
        NetworkService.sendMessage("LOGOUT", "");
    }
    public static void approveAuction(String id){
        NetworkService.sendMessage("APPROVE_AUCTION", id);
    }
    public static void rejectAuction(String id){
        NetworkService.sendMessage("REJECT_AUCTION", id);
    }
    public static void blockUser(String command, String id){
        switch(command){
            case BLOCK_USER -> {NetworkService.sendMessage(BLOCK_USER, id);}
            case UNBLOCK_USER -> {NetworkService.sendMessage(UNBLOCK_USER, id);}
        }
    }
}
