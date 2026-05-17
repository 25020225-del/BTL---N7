package model.user;

public class Seller extends User {
    public Seller() { super(); }

    public Seller(User user) {
        super(user.getId(), user.getUserName(), user.getPassword(), user.getName(), "SELLER");
        this.setGood(user.isGood());
    }

    @Override
    public String getInfo() {
        return "[Seller] ID: " + this.getId() + " | Name: " + this.getName();
    }
}