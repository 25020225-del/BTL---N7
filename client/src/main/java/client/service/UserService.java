package client.service;

import client.network.NetworkService;

public class UserService {
    public static void LogOut() {
        NetworkService.sendMessage("LOGOUT", "");
    }
}
