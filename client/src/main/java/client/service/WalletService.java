package client.service;

import client.network.NetworkService;

import java.util.HashMap;
import java.util.Map;

/**
 * Service layer for wallet-related network commands.
 *
 * <p>Encapsulates all {@code NetworkService.sendMessage} calls related to
 * wallet operations (deposit, withdrawal, history fetch).</p>
 */
public class WalletService {

    /**
     * Requests the user's wallet balance and transaction history from the server.
     */
    public static void fetchWalletHistory() {
        NetworkService.sendMessage("FETCH_WALLET", "");
    }

    /**
     * Sends a deposit request (with optional TOTP code) to the server.
     *
     * @param amount    Amount to deposit in VND (must be &gt; 0).
     * @param totpCode  6-digit TOTP code, or {@code null} if TOTP is not enabled.
     */
    public static void createDeposit(long amount, String totpCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount);
        if (totpCode != null && !totpCode.isBlank()) {
            payload.put("totpCode", totpCode);
        }
        NetworkService.sendMessage("CREATE_DEPOSIT", payload);
    }

    /**
     * Sends a withdrawal request (with optional TOTP code) to the server.
     *
     * @param amount        Amount to withdraw in VND (must be &gt; 0).
     * @param payoutMethod  Payment method (e.g., "BANK_TRANSFER", "MOMO").
     * @param payoutDetails Account information string.
     * @param totpCode      6-digit TOTP code, or {@code null} if TOTP is not enabled.
     */
    public static void requestWithdrawal(long amount,
                                         String payoutMethod,
                                         String payoutDetails,
                                         String totpCode) {
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