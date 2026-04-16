package model;

public class Seller extends User {

    public Seller() {
        super();
    }

    public Seller(User baseUser) {
        super(baseUser.getId(), baseUser.getUserName(), baseUser.getUserPass(), baseUser.getName(), "SELLER");
        this.setGood(baseUser.isGood());
    }

    @Override
    public String getInfo() {
        String tag = this.isGood() ? "[TRUSTED] " : "";
        return tag + super.getInfo();
    }

}