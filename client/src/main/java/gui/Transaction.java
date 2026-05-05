package gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleDoubleProperty;

public class Transaction {
    private final SimpleStringProperty date;
    private final SimpleStringProperty type;
    private final SimpleDoubleProperty amount;
    private final SimpleStringProperty status;
    private final SimpleStringProperty note;

    public Transaction(String date, String type, double amount, String status, String note) {
        this.date = new SimpleStringProperty(date);
        this.type = new SimpleStringProperty(type);
        this.amount = new SimpleDoubleProperty(amount);
        this.status = new SimpleStringProperty(status);
        this.note = new SimpleStringProperty(note);
    }

    public String getDate() {
        return date.get();
    }
    public String getType() {
        return type.get();
    }
    public double getAmount() {
        return amount.get();
    }
    public String getStatus() {
        return status.get();
    }
    public String getNote() {
        return note.get();
    }
}