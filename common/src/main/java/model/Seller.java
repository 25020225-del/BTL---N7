package model;

public class Seller extends User {
    private boolean isGood;

    public Seller() {
        super();
    }

    public Seller(String id, String userName, String userPass, String name) {
        super(id, userName, userPass, name, "Seller");
        this.isGood = false; // Mặc định uy tín tối đa khi mới tham gia
    }


    public boolean isGood() { return isGood; }
    public void setGood(boolean isGood) { this.isGood = isGood; }

    @Override
    public String getInfo() {
        String mallTag = isGood ? "[GOOD] " : "";
        return mallTag + super.getInfo() ;
    }
}