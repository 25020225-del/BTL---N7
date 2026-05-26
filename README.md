# 🔨 Hệ Thống Đấu Giá Trực Tuyến Thời Gian Thực (Online Auction System)

> **Môn học:** Lập Trình Nâng Cao (LTNC) - Trường Đại học Công nghệ, ĐHQGHN
> **Nhóm thực hiện:** Team N7
> **Hạn cuối dự án:** 23:59 ngày 31/05/2026

---

## 📝 1. Giới thiệu Bài toán & Phạm vi Hệ thống
Hệ thống Đấu giá Trực tuyến là một nền tảng thời gian thực cho phép người dùng đăng ký, đăng nhập bảo mật bằng 2FA, nạp tiền vào ví điện tử và tham gia đấu giá các sản phẩm đa dạng.

Hệ thống được phát triển theo kiến trúc **Client-Server đa luồng (Multi-threaded Socket)**, tối ưu hóa tương tác thời gian thực bằng mô hình truyền nhận thông điệp tùy biến, đảm bảo đồng bộ hóa trạng thái giữa tất cả các Client khi có sự thay đổi về giá đấu hoặc thời gian còn lại của phiên.

### Các thực thể và nghiệp vụ chính:
*   **Người bán (Seller):** Tạo phiên đấu giá, thiết lập giá khởi điểm, giá mua đứt (nếu có), bước giá tối thiểu và thời gian kết thúc phiên.
*   **Người mua (Bidder):** Nạp/rút tiền, đặt giá thủ công, kích hoạt cơ chế Đấu giá tự động (Auto-bid), theo dõi danh sách phiên đấu giá thời gian thực.
*   **Quản trị viên (Admin):** Phê duyệt phiên đấu giá mới, quản lý tài khoản người dùng và giám sát lịch sử giao dịch dòng tiền.

---

## 💻 2. Yêu cầu Cài đặt Môi trường
Để biên dịch và chạy dự án này, máy tính của bạn cần cài đặt sẵn:
*   **Java Development Kit (JDK):** Version 25 trở lên.
*   **Apache Maven:** Version 3.8+ (dùng để quản lý dependency và build).
*   **Hệ cơ sở dữ liệu:** SQLite (Tệp cơ sở dữ liệu tích hợp sẵn tại `auction_system.db`).
*   **Hệ điều hành tương thích:** Windows, macOS, Linux.

---

## 📁 3. Sơ đồ cấu trúc thư mục Dự án
Dự án được tổ chức theo mô hình Maven đa module rõ ràng:

```text
BTL---N7/
├── pom.xml                  # File POM cấu hình chung và quản lý thư viện cha
├── auction_system.db        # Tệp cơ sở dữ liệu SQLite
├── common/                  # Module chứa tài nguyên dùng chung cho cả Client & Server
│   ├── pom.xml
│   └── src/main/java/
│       ├── model/           # Các đối tượng nghiệp vụ (User, Wallet, Auction, Item, Transaction...)
│       └── network/         # Các lớp thông điệp truyền tải qua Socket (NetworkMessage)
├── server/                  # Module Socket Server xử lý luồng dữ liệu & logic nghiệp vụ
│   ├── pom.xml
│   └── src/main/java/
│       ├── controller/      # Điều phối nghiệp vụ (Đấu giá, Ví tiền, Tài khoản)
│       ├── database/        # Kết nối DB (DatabaseManager) & Quản lý giao dịch (TransactionManager)
│       ├── service/         # Các động cơ cốt lõi (AutoBidEngine, TOTP-2FA, Cloudinary)
│       └── server/          # MultiThreadedServer & bộ các ClientHandler xử lý lệnh Socket
└── client/                  # Module Desktop Client tương tác người dùng
    ├── pom.xml
    └── src/
        ├── java/
        │   ├── client/      # Xử lý kết nối Socket & Event Bus đồng bộ UI (NetworkClient)
        │   └── gui/         # Các Controller JavaFX quản lý giao diện
        └── resources/gui/   # Giao diện FXML & tệp styling CSS tối tân
```
## 🚀 4. Hướng dẫn Khởi chạy Hệ thống qua dòng lệnh
**Bước 1:** Build dự án bằng Maven
Mở terminal tại thư mục gốc BTL---N7 và chạy lệnh:
```text
mvn clean package -DskipTests
```
**Bước 2:** Trong thư mục gốc của dự án (hoặc cùng thư mục với file jar của Server), tạo một file `.env` và cấu hình các thông tin môi trường sau:
```env
LOCALTONET_API_TOKEN=[?]
JSONBIN_API_KEY=$2a$10$aUHAf8TlnNuGwO8/5/I2yeiLKj8Fqh2xaAZ09R/UF/9JTpGhdqRZK
BIN_ID=69d4960b856a6821890813a2
CLOUDINARY_CLOUD_NAME=de1isjzur
CLOUDINARY_API_KEY=748352485873877
CLOUDINARY_API_SECRET=_6OeXCYYcSL05fVZmqyGh3vtgvg
```
> [!NOTE]
> * Hệ thống sử dụng Localtonet để kết nối Client qua mạng WAN/Internet rộng lớn và Cloudinary để làm kho chứa ảnh sản phẩm.
> * **Giải pháp chạy Offline (Localhost)**: Trong trường hợp dịch vụ Internet trung gian `jsonbin.io` gặp sự cố (như lỗi máy chủ 500/504), bạn vẫn có thể kiểm thử cục bộ hoàn hảo! Chỉ cần khởi chạy Server, sau đó mở Client lên. Do không liên lạc được với JsonBin, Client sẽ tự động kích hoạt cơ chế dự phòng và kết nối trực tiếp vào máy của bạn qua cổng địa chỉ nội bộ `ws://localhost:6969`.

**Bước 3:** Khởi chạy Socket Server
```text
java -jar server/target/server-1.0-SNAPSHOT.jar
```
Hệ thống sẽ tự động khởi tạo cơ sở dữ liệu SQLite auction_system.db nếu chưa tồn tại, chuẩn bị các luồng socket nhận kết nối.

**Bước 4:** Khởi chạy Desktop Client (Mở nhiều terminal để giả lập nhiều người chơi)

```text
java -jar client/target/client-1.0-SNAPSHOT.jar
```
## ✨ 5. Các Tính năng Cốt lõi đã Hoàn thiện
### 🔐 A. Xác thực & Bảo mật (Auth & 2FA)
* **Mã hóa mật khẩu:** Sử dụng thuật toán BCrypt băm mật khẩu kèm muối (salt) ngẫu nhiên, chống tấn công bảng băm ngược.
* **Mã xác thực hai yếu tố (2FA):** Tạo mã QR dùng ứng dụng quét mã (Google Authenticator) để cấp mã OTP thời gian thực (TOTPService), bảo mật tuyệt đối tài khoản.
### 💳 B. Ví tài chính & Giao dịch 
* **Quản lý Số dư Kép:** Gồm balance (số dư khả dụng) và locked_balance (số tiền bị tạm khóa khi đang dẫn đầu một phiên đấu giá).
* **Giao dịch an toàn:** Hệ thống tự động khóa tiền của người đấu giá cao nhất hiện tại. Nếu có người khác trả giá cao hơn, hệ thống ngay lập tức hoàn trả tiền bị tạm khóa về ví người cũ và khóa tiền của người mới theo thời gian thực thông qua cơ chế TransactionManager bảo toàn dữ liệu.
### 🔨 C. Động cơ Đấu giá (Bidding & Auto-Bid Engine)
* **Đấu giá thủ công:** Người dùng đặt giá lớn hơn giá hiện tại cộng với bước giá tối thiểu.
* **Động cơ Tự động Đấu giá (Auto-Bid Engine):** Người dùng cài đặt mức giá tối đa sẵn sàng trả. Hệ thống sẽ tự động đặt giá tăng dần một cách thông minh bất cứ khi nào có đối thủ vượt mặt, cho đến khi chạm hạn mức tối đa của họ.
* **Chống tranh chấp ghi dữ liệu đồng thời (Optimistic Locking):** Áp dụng cơ chế so sánh phiên bản (versioning) trong SQLite để phát hiện và ngăn chặn xung đột khi nhiều người dùng cùng bắn phá lệnh thầu ở cùng một mili-giây.