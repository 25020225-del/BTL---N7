package model.finance;

/**
 * Domain model representing an accounting account balancing layer assigned to a specific user.
 */
public class Wallet {

    private String userId;
    private long balance;

    public Wallet() {
    }

    public Wallet(String userId, long balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }

    /**
     * Assures the current liquid capital asset bounds can safely back a pending transaction volume.
     *
     * @param amount target value threshold verification challenge
     * @return true if asset reserves match or exceed the requested volume challenge
     */
    public boolean hasSufficientFunds(long amount) {
        return this.balance >= amount;
    }
}