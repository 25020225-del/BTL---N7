package model.finance;

/**
 * Represents a user's digital wallet within the system.
 * This class manages the user's financial balance, allowing them to participate
 * in auctions, place bids, and process deposits or refunds.
 */
public class Wallet {
    private String userId;
    private double balance;

    /**
     * Default constructor.
     */
    public Wallet() {}

    /**
     * Constructs a new Wallet with the specified user ID and initial balance.
     *
     * @param userId  The unique identifier of the user who owns this wallet.
     * @param balance The initial monetary balance of the wallet.
     */
    public Wallet(String userId, double balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    /**
     * Safely checks whether the wallet contains sufficient funds to cover a specified amount.
     * This is typically used to validate if a user can afford to place a bid or make a withdrawal.
     *
     * @param amount The target amount to check against the current balance.
     * @return {@code true} if the current balance is greater than or equal to the specified amount; {@code false} otherwise.
     */
    public boolean hasEnoughBalance(double amount) {
        return this.balance >= amount;
    }
}