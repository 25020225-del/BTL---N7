module common {
    // Khai báo mở khóa các gói này để client và server có thể gọi được
    exports network;
    exports model;   // Sẵn tiện mở luôn gói model (chứa User, Auction...)

    // Nếu NetworkMessage có dùng Jackson, bạn cần open nó cho Jackson đọc
    // opens network to com.fasterxml.jackson.databind;
}