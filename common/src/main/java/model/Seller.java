package model;

public class Seller extends User {
    private double rating; // Điểm đánh giá độ uy tín của người bán

    public Seller(String id, String userName, String userPass, String name, String email) {
        super(id, userName, userPass, name, email, "Seller");
        this.rating = 5.0; // Mặc định uy tín tối đa khi mới tham gia
    }

    @Override
    public String getInfo() {
        return "[Seller] ID: " + this.id + " | Name: " + this.name + " | Rating: " + this.rating;
    }
}