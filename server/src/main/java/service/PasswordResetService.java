package service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Short-lived token registry governing temporary password reset authorizations.
 * Tokens are scoped to a single-use, 5-minute expiry window and bound to a verified userId.
 *
 * <p>Security guarantees:</p>
 * <ul>
 *   <li>Tokens are random UUIDs — not predictable</li>
 *   <li>Each token may only be consumed once (single-use via {@link #consumeToken})</li>
 *   <li>Expired tokens are purged every 60 seconds by a background daemon</li>
 * </ul>
 */
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Token validity window: 5 minutes. */
    private static final long TOKEN_TTL_MS = 5 * 60 * 1_000L;

    private static final class ResetTokenEntry {
        final String userId;
        final long expiresAt;

        ResetTokenEntry(String userId) {
            this.userId = userId;
            this.expiresAt = System.currentTimeMillis() + TOKEN_TTL_MS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final ConcurrentHashMap<String, ResetTokenEntry> tokenStore = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "reset-token-cleaner");
        t.setDaemon(true);
        return t;
    });

    public PasswordResetService() {
        cleaner.scheduleAtFixedRate(this::purgeExpiredTokens, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Mints a fresh single-use reset token tied to {@code userId}.
     *
     * @param userId the authenticated user who passed TOTP verification
     * @return an opaque UUID token string to be returned to the client
     */
    public String createResetToken(String userId) {
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, new ResetTokenEntry(userId));
        log.info("[RESET] Token minted for userId={} (expires in 5 min)", userId);
        return token;
    }

    /**
     * Validates and atomically consumes a reset token.
     *
     * <p>A token is consumed on first use regardless of password update success — the caller
     * must not retry with the same token if the downstream DB write fails.</p>
     *
     * @param token the opaque reset token supplied by the client
     * @return the {@code userId} bound to this token, or {@code null} if invalid/expired
     */
    public String consumeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        ResetTokenEntry entry = tokenStore.remove(token);
        if (entry == null) {
            log.warn("[RESET] Token not found (already used or never issued): {}", token);
            return null;
        }
        if (entry.isExpired()) {
            log.warn("[RESET] Token expired for userId={}", entry.userId);
            return null;
        }
        log.info("[RESET] Token consumed for userId={}", entry.userId);
        return entry.userId;
    }

    private void purgeExpiredTokens() {
        tokenStore.entrySet().removeIf(e -> e.getValue().isExpired());
        log.debug("[RESET] Expired token purge complete. Active tokens: {}", tokenStore.size());
    }

    /**
     * Gracefully terminates the background cleaner scheduler executor.
     */
    public void shutdown() {
        cleaner.shutdown();
        try {
            if (!cleaner.awaitTermination(5, TimeUnit.SECONDS)) {
                cleaner.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleaner.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[RESET] PasswordResetService background cleaner executor shutdown complete.");
    }
}