package model;

public class Bidder extends User {
    // Thuộc tính riêng của người đấu giá (ví dụ: số dư tài khoản)
    public Bidder(String id, String userName, String userPass, String name) {
        super(id, userName, userPass,name,"Bidder");
    }
    @Override
    public String getInfo() {
        return "Bidder Info - ID: " + this.id + ", Name: " + this.name ;
    }
}