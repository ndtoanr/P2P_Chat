# P2P Chat System - Hệ thống Nhắn tin Ngang hàng

Một hệ thống nhắn tin phân tán Peer-to-Peer Chat Application được xây dựng bằng Java Socket, hỗ trợ giao tiếp thời gian thực, quản lý nhóm, tự động đồng bộ tin nhắn ngoại tuyến và giao diện người dùng UI Dark theme hiện đại.

---

## Các Chức Năng Hệ Thống

Dự án hiện tại đã triển khai đầy đủ các yêu cầu chức năng :

- **Tham gia mạng P2P:** Đăng ký và đăng nhập tài khoản thông qua Bootstrap Server trung tâm hoặc Peer có trước.
- **Khám phá máy trạm (Peer Discovery):** Tự động truy vấn và đồng bộ danh sách các máy trạm (Peer) đang trực tuyến trong mạng từ khi đăng nhập.
- **Trò chuyện trực tiếp:** Thiết lập kết nối TCP Socket trực tiếp giữa 2 Peer để trao đổi tin nhắn thời gian thực mà không qua trung gian khi cả hai cùng online.
- **Trò chuyện nhóm:** Tạo nhóm chat mới, thêm/xóa thành viên khỏi nhóm, rời nhóm hoặc xóa nhóm. Tin nhắn được tự động phát tới tất cả các thành viên trong nhóm.
- **Cập nhật trạng thái (Online/Offline):** Theo dõi danh sách Peer và bạn bè đang online. Hệ thống cập nhật trạng thái lập tức khi có Peer mới tham gia hoặc rời mạng.
- **Truyền tin cậy:** Sử dụng giao thức TCP Socket tin cậy. Tích hợp cơ chế **Heartbeat quét định kỳ (15s)** giữa Peer và Server để phát hiện, xử lý các sự cố ngắt kết nối đột ngột hoặc mất mạng của Peer trạm.
- **Lưu và chuyển tiếp tin nhắn ngoại tuyến (Store-and-Forward):** Hỗ trợ gửi tin nhắn cho bạn bè ngay cả khi họ đang offline. Tin nhắn được lưu trữ tạm thời trong CSDL và tự động đồng bộ gửi lại ngay khi đối phương đăng nhập.
- **Hệ thống quản lý bạn bè (Friend System):** Tìm kiếm người dùng, gửi lời mời kết bạn, chấp nhận/từ chối lời mời và hủy kết bạn trực quan.
- **Giao diện người dùng trực quan (GUI):** Thiết kế giao diện đồ họa hiện đại sử dụng Java Swing kết hợp thư viện `FlatLaf` (giao diện tối Dark theme), nâng cao trải nghiệm người dùng với các hiệu ứng động, chỉ báo trạng thái đang gõ chữ ("... đang gõ"), trạng thái tin nhắn (Đã gửi, Đã nhận, Đã đọc).

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
- **Java Development Kit (JDK 17)** trở lên (đã cấu hình biến môi trường `JAVA_HOME`).
- **MySQL Server 8.0+** đã được cài đặt và đang chạy.
- **MySQL Workbench** 

### Bước 1: Khởi tạo Cơ sở dữ liệu
1. Kết nối vào MySQL Server của bạn.
2. Tạo cơ sở dữ liệu mới với tên `p2p_chat` hỗ trợ tốt tiếng Việt có dấu:
   ```sql
   CREATE DATABASE p2p_chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
3. Mở và chạy toàn bộ mã SQL trong thư mục `sql/schema.sql` để khởi tạo cấu trúc các bảng dữ liệu cần thiết (`users`, `messages`, `chat_groups`, `group_members`, `offline_messages`, `peer_registry`, `friends`, `friend_requests`).

### Bước 2: Cấu hình kết nối hệ thống
Mở tệp tin `src/main/java/com/p2pchat/shared/Constants.java` và chỉnh sửa các hằng số cấu hình kết nối cho phù hợp với môi trường của bạn:
```java
// IP máy chủ Bootstrap Server (Ví dụ: "localhost" khi test 1 máy hoặc IP LAN "172.20.10.2")
public static final String BOOTSTRAP_HOST = "172.20.10.2"; 
public static final int BOOTSTRAP_PORT = 9000;

// Cấu hình kết nối MySQL Database
public static final String DB_URL = "jdbc:mysql://172.20.10.2:3306/p2p_chat?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
public static final String DB_USER = "root";
public static final String DB_PASSWORD = "your_mysql_password"; // Thay thế bằng mật khẩu MySQL của bạn
```
> [!NOTE]  
> Nếu bạn chạy cả Server và Client trên cùng một máy tính cá nhân để thử nghiệm, bạn có thể thay thế địa chỉ IP `172.20.10.2` bằng `localhost` hoặc `127.0.0.1`.

### Bước 3: Biên dịch và Đóng gói dự án (Build)
Dự án sử dụng Maven wrapper được đóng gói sẵn để tải thư viện và build dự án:
- Trên **Windows**: Nhấp đúp chuột vào file `build.bat` hoặc mở Terminal chạy lệnh:
  ```cmd
  .\build.bat
  ```
- Quá trình biên dịch sẽ tạo ra file Jar chạy tại: `target/p2p-chat-1.0-SNAPSHOT.jar`.

### Bước 4: Khởi chạy Bootstrap Server
Bootstrap Server cần được chạy trước để mở cổng lắng nghe đăng ký và điều phối kết nối:
- Nhấp đúp chuột vào file `run_server.bat`.
- **Hoặc** khởi chạy thủ công qua dòng lệnh Terminal:
  ```cmd
  java -cp target/p2p-chat-1.0-SNAPSHOT.jar;lib/* com.p2pchat.server.BootstrapServer
  ```

### Bước 5: Khởi chạy Peer (Ứng dụng Chat)
Sau khi Server đã chạy thành công, khởi động các máy trạm Chat Client (bạn có thể mở nhiều Client cùng lúc trên một hoặc nhiều máy để thử nghiệm chat qua lại):
- Nhấp đúp chuột vào file `run_client.bat`.
- **Hoặc** khởi chạy thủ công qua dòng lệnh Terminal:
  ```cmd
  java -cp target/p2p-chat-1.0-SNAPSHOT.jar;lib/* com.p2pchat.gui.MainApp
  ```

---

## Cấu Hình Chạy Qua Mạng LAN (Nhiều Máy Tính Khác Nhau)
Để các máy tính khác trong cùng một mạng LAN (WiFi/Mạng dây) có thể chat trực tiếp với nhau:
1. **Tìm IPv4 của máy chủ:** Trên máy chạy Bootstrap Server và MySQL, mở cmd chạy lệnh `ipconfig` để lấy địa chỉ IPv4 LAN (ví dụ: `172.20.10.2` hoặc `192.168.1.X`).
2. **Cập nhật IP cấu hình:** Thay đổi giá trị của `BOOTSTRAP_HOST` và địa chỉ IP trong `DB_URL` của tệp `Constants.java` thành IPv4 vừa tìm thấy ở trên.
3. **Cấp quyền truy cập CSDL:** Cấp quyền truy cập từ xa cho user MySQL trên máy chủ để máy trạm (Peer) kết nối được CSDL khi ở chế độ dự phòng:
   ```sql
   GRANT ALL PRIVILEGES ON p2p_chat.* TO 'root'@'%' IDENTIFIED BY 'your_mysql_password';
   FLUSH PRIVILEGES;
   ```
4. **Mở cổng Firewall (Tường lửa):** Trên máy chủ chạy Bootstrap Server, đảm bảo đã mở (Allow) cổng **9000** (cổng TCP của server) và cổng **3306** (cổng MySQL) trong Windows Defender Firewall để các máy khác truy cập được.

---
Dự án đồ án: Xây dựng hệ thống nhắn tin mạng ngang hàng P2P Chat bằng ngôn ngữ Java.
