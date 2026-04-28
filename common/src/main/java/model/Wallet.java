package model;

public class Wallet {
    private String userId;
    private double balance;

    public Wallet() {}

    public Wallet(String userId, double balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    // safety check
    public boolean hasEnoughBalance(double amount) {
        return this.balance >= amount;
    }
}