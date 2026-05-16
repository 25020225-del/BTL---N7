package client.service;

import client.network.NetworkService;

public class WalletService {
    public static void fetchWalletHistory() {
        NetworkService.sendMessage("FETCH_WALLET","");
    }
    public static void createDeposit(double amount) {
        NetworkService.sendMessage("CREATE_DEPOSIT", amount);
    }
}
