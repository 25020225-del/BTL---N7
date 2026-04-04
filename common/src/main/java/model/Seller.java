package model;

public class Seller extends User {

    public Seller() { super(); }

    // Hàm hóa thân từ User thành Seller
    public Seller(User baseUser) {
        // Truyền thông tin cơ bản lên lớp cha
        super(baseUser.getId(), baseUser.getUserName(), baseUser.getUserPass(), baseUser.getName(), "SELLER");

        // QUAN TRỌNG: Kế thừa luôn độ uy tín từ User gốc (lấy từ Database lên)
        this.setGood(baseUser.isGood());
    }

    @Override
    public String getInfo() {
        // Gọi hàm isGood() của lớp cha
        String tag = this.isGood() ? "[UY TÍN] " : "";
        return tag + super.getInfo();
    }
}