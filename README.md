# P2P Chat System - Hệ thống Nhắn tin Ngang hàng

Một hệ thống nhắn tin phân tán (Peer-to-Peer Chat Application) được xây dựng bằng Java Socket, hỗ trợ giao tiếp thời gian thực, quản lý nhóm, tự động đồng bộ tin nhắn ngoại tuyến và giao diện người dùng (UI) Dark theme hiện đại.

---

## Các Tính Năng Nổi Bật

- **Kiến trúc P2P:** Kết hợp Bootstrap Server để định danh (Peer Discovery) và truyền tải tin nhắn ngang hàng tốc độ cao.
- **Tin nhắn Ngoại tuyến (Store-and-Forward):** Gửi tin nhắn ngay cả khi đối phương không trực tuyến. Hệ thống sẽ tự động đồng bộ ngay khi họ đăng nhập lại.
- **Trò chuyện Nhóm:** Tạo nhóm, thêm/xóa thành viên và chat nhóm mượt mà.
- **Quản lý Kết nối Thông minh:** Cơ chế Heartbeat quét định kỳ (15s) tự động phát hiện và dọn dẹp các máy trạm mất mạng hoặc ngắt kết nối đột ngột.
- **Trải nghiệm Giao tiếp Tương tác UX/UI:**
  - Đồng bộ trạng thái: "... đang gõ chữ".
  - Trạng thái tin nhắn: "Đã gửi", "Đã nhận", "Đã đọc".
  - Hỗ trợ **Trả lời trích dẫn** và **Chuyển tiếp** tin nhắn.
  - Giao diện tối Dark Mode chuyên nghiệp sử dụng thư viện `FlatLaf`.
- **Cơ sở dữ liệu an toàn:** Lưu trữ lịch sử tin nhắn và quản lý tài khoản với MySQL. Hỗ trợ Rollback Database an toàn khi có lỗi xảy ra.

---

## Công Nghệ Sử Dụng

- **Ngôn ngữ lập trình:** Java 17
- **Giao thức mạng:** TCP/IP (Java Sockets & ServerSocket)
- **Cơ sở dữ liệu:** MySQL 8.0+
- **Thư viện UI:** FlatLaf (FlatDarkLaf)
- **Xử lý dữ liệu:** Google Gson (JSON Serialization/Deserialization)
- **Quản lý kết nối DB:** HikariCP (Connection Pooling)
- **Đóng gói dự án:** Apache Maven (Maven Shade Plugin)

---

## Cấu Trúc Thư Mục Dự Án

```text
P2P_Chat/
├── sql/
│   └── schema.sql              # Các câu lệnh SQL khởi tạo cấu trúc cơ sở dữ liệu
├── src/main/java/com/p2pchat/
│   ├── gui/                    # Giao diện người dùng Java Swing (ChatGUI, LoginDialog)
│   ├── peer/                   # Logic nghiệp vụ mạng ngang hàng của máy trạm (PeerNode)
│   ├── server/                 # Mã nguồn máy chủ trung gian (BootstrapServer, ClientHandler)
│   └── shared/                 # Các tiện ích dùng chung (DatabaseManager, Constants, Message)
├── pom.xml                     # Tệp cấu hình thư viện và quy trình Build của Maven
├── build.bat                   # Script tự động tải thư viện, dọn dẹp và đóng gói Fat JAR
├── run_server.bat              # Script khởi động nhanh máy chủ (Bootstrap Server)
└── run_client.bat              # Script khởi động nhanh ứng dụng nhắn tin (Client)
```

---

## Hướng Dẫn Cài Đặt và Chạy Ứng Dụng

### Yêu cầu hệ thống:
- Cài đặt **Java Development Kit (JDK 17)** trở lên.
- Cài đặt **MySQL Workbench 8.0 CE**.

### Bước 1: Khởi tạo Cơ sở dữ liệu
1. Mở MySQL (thông qua MySQL Workbench).
2. Tạo cơ sở dữ liệu mới với tên `p2p_chat`:
   ```sql
   CREATE DATABASE p2p_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
3. Chạy toàn bộ mã SQL trong thư mục `sql/schema.sql` để tạo cấu trúc các bảng (`users`, `messages`, `chat_groups`, `group_members`, `offline_messages`, `peer_registry`, `friends`, `friend_requests`).

### Bước 2: Cấu hình kết nối
Mở file `src/main/java/com/p2pchat/shared/Constants.java` và chỉnh sửa các thông số sau cho phù hợp với máy của bạn (nếu cần thiết):
```java
public static final String BOOTSTRAP_HOST = "localhost"; // Đổi thành IP LAN (VD: 192.168.1.10) nếu muốn chạy nhiều máy
public static final String DB_URL = "jdbc:mysql://localhost:3306/p2p_chat";
public static final String DB_USER = "root";
public static final String DB_PASSWORD = "your_mysql_password"; // Mật khẩu MySQL của bạn
```

### Bước 3: Biên dịch và Đóng gói (Build)
Dự án sử dụng Maven để tự động tải thư viện và đóng gói ứng dụng thành file Fat JAR (bao gồm tất cả thư viện cần thiết).
- Trên **Windows**: Click đúp vào file `build.bat` hoặc mở Terminal chạy lệnh:
  ```cmd
  .\build.bat
  ```
Sau khi Build thành công, file thực thi sẽ được tạo ra tại: `target/p2p-chat-1.0-SNAPSHOT.jar`.

### Bước 4: Chạy Server (Bắt buộc phải chạy trước)
Bootstrap Server đóng vai trò là thư mục chỉ dẫn và điều phối kết nối.
- Click đúp vào file `run_server.bat`
- **Hoặc** chạy bằng lệnh:
  ```cmd
  java -cp target/p2p-chat-1.0-SNAPSHOT.jar com.p2pchat.server.BootstrapServer
  ```

### Bước 5: Chạy Client (Ứng dụng chat)
Khởi chạy giao diện nhắn tin cho người dùng. Bạn có thể mở nhiều Client trên cùng một máy để tự test.
- Click đúp vào file `run_client.bat`
- **Hoặc** chạy bằng lệnh:
  ```cmd
  java -jar target/p2p-chat-1.0-SNAPSHOT.jar
  ```

---

## Lưu Ý Khi Chạy Qua Mạng LAN
Nếu bạn muốn các máy tính khác trong cùng mạng WiFi/LAN có thể truy cập vào hệ thống:
1. Đổi `BOOTSTRAP_HOST` và `DB_URL` sang địa chỉ IP IPv4 của máy chủ (Ví dụ: `192.168.x.x`).
2. Cấp quyền truy cập từ xa cho user MySQL (`GRANT ALL PRIVILEGES ON p2p_chat.* TO 'root'@'%'`).
3. Mở cổng Tường lửa (Windows Defender Firewall) cho Port **9000** (Server Socket) và Port **3306** (MySQL).

---
Dự án đồ án: Xây dựng hệ thống nhắn tin mạng ngang hàng P2P Chat bằng ngôn ngữ Java.
