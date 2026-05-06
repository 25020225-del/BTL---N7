package gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleLongProperty;

public class Transaction {
    private final SimpleStringProperty date;
    private final SimpleStringProperty type;
    private final SimpleLongProperty amount;
    private final SimpleStringProperty status;
    private final SimpleStringProperty note;

    public Transaction(String date, String type, long amount, String status, String note) {
        this.date = new SimpleStringProperty(date);
        this.type = new SimpleStringProperty(type);
        this.amount = new SimpleLongProperty(amount);
        this.status = new SimpleStringProperty(status);
        this.note = new SimpleStringProperty(note);
    }

    public String getDate() {
        return date.get();
    }
    public String getType() {
        return type.get();
    }
    public long getAmount() {
        return amount.get();
    }
    public String getStatus() {
        return status.get();
    }
    public String getNote() {
        return note.get();
    }
}