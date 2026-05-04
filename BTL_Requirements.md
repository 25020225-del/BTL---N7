# Yêu cầu Bài tập lớn: Phát triển hệ thống đấu giá trực tuyến

## 1. Giới thiệu
Xây dựng hệ thống đấu giá trực tuyến (Bidding System) cho phép nhiều người dùng cạnh tranh giá mua sản phẩm trong thời gian xác định.

## 2. Các yêu cầu chức năng (Chức năng bắt buộc)

### 2.1 Quản lý người dùng
- Đăng ký/Đăng nhập.
- Vai trò (Roles):
    - **Bidder**: Tham gia đấu giá.
    - **Seller**: Đăng sản phẩm.
    - **Admin**: Quản lý hệ thống.

### 2.2 Quản lý sản phẩm đấu giá
- Seller có thể Thêm/Sửa/Xóa sản phẩm.
- Thông tin: Tên, mô tả, giá khởi điểm, giá hiện tại cao nhất, thời gian bắt đầu & kết thúc.

### 2.3 Tham gia đấu giá & Kết thúc
- Đặt giá (phải cao hơn giá hiện tại).
- Kiểm tra tính hợp lệ của giá đấu.
- Tự động đóng phiên khi hết giờ.
- Xác định người thắng cuộc.
- Trạng thái phiên: `OPEN` -> `RUNNING` -> `FINISHED` -> `PAID/CANCELED`.

### 2.4 Kỹ thuật & Giao diện
- **GUI**: JavaFX hoặc Swing.
- **Xử lý lỗi**: Giá thấp hơn hiện tại, đấu giá khi đã đóng, lỗi kết nối/dữ liệu.

## 3. Chức năng nâng cao (Ưu tiên thực hiện)
1. **Auto-Bidding**: Tự động trả giá dựa trên `maxBid` và `increment`.
2. **Concurrent Bidding**: Xử lý đấu giá đồng thời, tránh Lost Update/Race Condition.
3. **Anti-sniping**: Gia hạn phiên nếu có bid trong X giây cuối.
4. **Realtime Update**: Dùng Observer Pattern/Socket để cập nhật giá không cần Refresh.
5. **Visualization**: Biểu đồ đường (Line Chart) lịch sử giá theo thời gian thực.

## 4. Thiết kế hệ thống & Kiến trúc

### 4.1 Thiết kế hướng đối tượng (OOP)
- **Entities**:
    - `User` (Abstract) -> `Bidder`, `Seller`, `Admin`.
    - `Item` (Abstract) -> `Electronics`, `Art`, `Vehicle`.
    - `Auction`, `BidTransaction`.
- Áp dụng đủ: Encapsulation, Inheritance, Polymorphism, Abstraction.

### 4.2 Kiến trúc (Networking & MVC)
- Mô hình: **Client-Server**.
- Giao tiếp: Socket hoặc REST API (Dữ liệu JSON).
- Pattern: **MVC** (Model-View-Controller) cho cả Client và Server.
- Chỉ Server mới có quyền truy cập Database.

## 5. Quy chuẩn kỹ thuật
- **Build tool**: Maven/Gradle.
- **Unit Test**: JUnit.
- **Git**: Commit thường xuyên (Conventional Commits).
- **Design Patterns**: Singleton, Factory Method, Observer, Strategy/Command.