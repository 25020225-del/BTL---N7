package client.service;

import client.network.NetworkService;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side service facade routing user wallet assets mutations and statement balance checks.
 */
public class WalletService {

    private WalletService() {
    }

    public static void fetchWalletHistory() {
        NetworkService.sendMessage("FETCH_WALLET", "");
    }

    public static void requestWithdrawal(long amount, String payoutMethod, String payoutDetails, String totpCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount);
        payload.put("payoutMethod", payoutMethod);
        payload.put("payoutDetails", payoutDetails);
        if (totpCode != null && !totpCode.isBlank()) {
            payload.put("totpCode", totpCode);
        }
        NetworkService.sendMessage("REQUEST_WITHDRAW", payload);
    }
}