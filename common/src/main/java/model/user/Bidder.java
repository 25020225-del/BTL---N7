package model.user;

public class Bidder extends User {
    public Bidder() { super(); }

    public Bidder(User user) {
        super(user.getId(), user.getUserName(), user.getPassword(), user.getName(), "BIDDER");
        this.setGood(user.isGood());
    }

    @Override
    public String getInfo() {
        return "[Bidder] ID: " + this.getId() + " | Name: " + this.getName();
    }
}