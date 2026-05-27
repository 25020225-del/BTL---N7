# 🔨 Hệ Thống Đấu Giá Trực Tuyến Thời Gian Thực (Online Auction System)

> **Môn học:** Lập Trình Nâng Cao (LTNC) - Trường Đại học Công nghệ, ĐHQGHN
> **Nhóm thực hiện:** Nhóm 7
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

Hệ thống được thiết kế theo phương pháp **Runtime-Configuration** – mọi cấu hình được đọc động từ file `.env` nằm cùng thư mục chạy tại thời điểm khởi động chương trình. Bạn chỉ cần sửa các giá trị khóa trong file `.env` bằng trình soạn thảo văn bản (như Notepad) .

**Bước 1:** Thiết lập file .env
### 🔗 A. Hướng dẫn lấy khóa Localtonet (Để kết nối mạng diện rộng WAN/Internet)
Nếu muốn chạy Server và cho phép các máy tính khác ngoài mạng LAN kết nối vào thông qua Internet:
1. Truy cập trang web chính thức của **Localtonet**: [https://localtonet.com](https://localtonet.com)
2. Đăng ký một tài khoản miễn phí.
3. Sau khi đăng nhập, đi tới trang **Dashboard** (Bảng điều khiển).
4. Bạn sẽ nhìn thấy mục **API Token**. Hãy copy đoạn mã này.
5. Mở file `.env` bằng Notepad và dán vào dòng:
   ```env
   LOCALTONET_API_TOKEN=đoạn_mã_token_của_bạn
   ```
*(Lưu ý: Nếu chỉ chạy cục bộ và kiểm thử trên cùng một máy qua Localhost, bạn có thể bỏ qua phần này).*

6. Tải và cài đặt **Localtonet** theo hướng dẫn: https://localtonet.com/documents/windows
7. Tạo và khởi động server theo hướng dẫn: https://localtonet.com/documents/udp
### ☁️ B. Hướng dẫn lấy khóa Cloudinary (Dùng làm kho lưu trữ ảnh sản phẩm)
Hệ thống tải ảnh sản phẩm lên đám mây Cloudinary để tối ưu tốc độ truyền tải và lưu trữ:
1. Truy cập trang web chính thức của **Cloudinary**: [https://cloudinary.com](https://cloudinary.com)
2. Đăng ký một tài khoản miễn phí (Free Account).
3. Đăng nhập vào và đi tới giao diện **Console / Dashboard**.
4. Ngay tại trang chủ Dashboard, bạn sẽ thấy thông tin về:
    * **Cloud name**
    * **API Key**
    * **API Secret**
5. Mở file `.env` bằng Notepad và điền chính xác 3 giá trị này vào các dòng tương ứng:
   ```env
   CLOUDINARY_CLOUD_NAME=tên_cloud_của_bạn
   CLOUDINARY_API_KEY=mã_api_key_của_bạn
   CLOUDINARY_API_SECRET=mã_api_secret_của_bạn
   ```

### 💳 C. Hướng dẫn lấy khóa PayPal Sandbox (Dùng cho tính năng nạp tiền thử nghiệm)
Hệ thống sử dụng cổng thanh toán PayPal REST API (Sandbox) để mô phỏng quy trình nạp tiền ảo vào ví người dùng:
1. Truy cập trang web chính thức của **PayPal Developer Portal**: [https://developer.paypal.com](https://developer.paypal.com)
2. Đăng nhập bằng tài khoản PayPal của bạn (hoặc tạo một tài khoản mới).
3. Tại giao diện Developer, chọn mục **Apps & Credentials** (Ứng dụng & Chứng chỉ) trong menu bên trái.
4. Nhấn nút **Create App** (Tạo ứng dụng), điền tên app (ví dụ: `AuctionApp`) và chọn loại tài khoản là **Merchant / Sandbox**.
5. Sau khi tạo ứng dụng thành công, bạn sẽ thấy thông tin về:
   * **Client ID** (Khóa công khai)
   * **Secret** (Khóa bảo mật - hãy nhấn *Show* để xem)
6. Mở file `.env` bằng Notepad và điền 2 giá trị trên vào:
   ```env
   PAYPAL_CLIENT_ID=mã_client_id_của_bạn
   PAYPAL_SECRET=mã_secret_của_bạn
   PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
   ```

***.env mẫu hoàn thiện sẽ trông như sau:***
```env
LOCALTONET_API_TOKEN=[?]
JSONBIN_API_KEY=$2a$10$aUHAf8TlnNuGwO8/5/I2yeiLKj8Fqh2xaAZ09R/UF/9JTpGhdqRZK
BIN_ID=69d4960b856a6821890813a2

CLOUDINARY_CLOUD_NAME=[?]
CLOUDINARY_API_KEY=[?]
CLOUDINARY_API_SECRET=[?]

PAYPAL_CLIENT_ID=[?]
PAYPAL_SECRET=[?]
PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
```
> [!NOTE]
> * Hệ thống sử dụng Localtonet để kết nối Client qua mạng WAN/Internet rộng lớn, Cloudinary để làm kho chứa ảnh sản phẩm, và PayPal Sandbox để nạp tiền ảo.
> * **Giải pháp chạy Offline (Localhost)**: Trong trường hợp dịch vụ Internet trung gian `jsonbin.io` gặp sự cố! Chỉ cần khởi chạy Server, sau đó mở Client lên. Do không liên lạc được với JsonBin, Client sẽ tự động kích hoạt cơ chế dự phòng và kết nối trực tiếp vào máy qua cổng địa chỉ nội bộ `ws://localhost:6969`.

**Bước 2:** Khởi chạy Socket Server
```text
java -jar server/target/server-1.0-SNAPSHOT.jar
```
Hệ thống sẽ tự động khởi tạo cơ sở dữ liệu SQLite auction_system.db nếu chưa tồn tại, chuẩn bị các luồng socket nhận kết nối.

**Bước 3:** Khởi chạy Desktop Client 

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

---

## 🎥 6. Tài liệu & Video Minh họa (Documentation & Video Demo)

Nhằm cung cấp cái nhìn toàn diện và trực quan nhất về Hệ thống Đấu giá Trực tuyến, chúng tôi đã chuẩn bị đầy đủ tài liệu đặc tả kiến trúc chi tiết cùng video hoạt động thực tế dưới đây:

### 📄 Tài liệu Dự án (PDF)
*   **Báo cáo kỹ thuật chi tiết:** [Xem & Tải xuống Báo cáo PDF](asset/PDF.pdf)


### 🎬 Video Demo hoạt động Hệ thống (Video MP4)
*   **Xem trực tiếp hoặc tải xuống:** [Tải xuống Video Demo](asset/video.mp4)



https://github.com/user-attachments/assets/fa412a3d-f597-437f-9ef0-90e66a705409

