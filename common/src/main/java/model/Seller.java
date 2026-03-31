package model;

public class Seller extends User {
    private double rating; // Điểm đánh giá độ uy tín của người bán

    // 1. THÊM HÀM TẠO RỖNG (Bắt buộc cho Jackson/Gson)
    public Seller() {
        super();
    }

    public Seller(String id, String userName, String userPass, String name, String email) {
        super(id, userName, userPass, name, email, "Seller");
        this.rating = 5.0; // Mặc định uy tín tối đa khi mới tham gia
    }

    // 2. THÊM GETTER/SETTER (Để JSON có thể đọc/ghi dữ liệu)
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    @Override
    public String getInfo() {
        // Lấy toàn bộ thông tin gốc từ User, sau đó nối thêm Rating của Seller
        return super.getInfo() + " | Rating: " + this.rating;
    }
}