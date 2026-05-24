package client.service;

import client.network.NetworkService;

/**
 * Client-side service facade managing standard user account session workflows.
 */
public final class UserService {

    private UserService() {
    }

    /**
     * Triggers a remote session cancellation process for the currently active user context.
     */
    public static void logout() {
        NetworkService.sendMessage("LOGOUT", "");
    }
}