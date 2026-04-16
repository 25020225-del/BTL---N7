package model;

public class Bidder extends User {

    public Bidder() {super();}

    public Bidder(User baseUser) {
        super(baseUser.getId(), baseUser.getUserName(), baseUser.getUserPass(), baseUser.getName(), "BIDDER");
    }

}