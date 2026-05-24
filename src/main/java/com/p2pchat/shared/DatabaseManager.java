package com.p2pchat.shared;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý kết nối MySQL với HikariCP connection pool
 */
public class DatabaseManager {

    private static HikariDataSource dataSource;

    /**
     * Khởi tạo connection pool
     */
    public static void initialize() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(Constants.DB_URL);
        config.setUsername(Constants.DB_USER);
        config.setPassword(Constants.DB_PASSWORD);
        config.setMaximumPoolSize(Constants.DB_POOL_SIZE);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        System.out.println("[DB] MySQL connection pool initialized");

        // Tự động kiểm tra và nâng cấp cột group_id trong bảng messages từ INT lên VARCHAR(36)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE messages MODIFY COLUMN group_id VARCHAR(36) DEFAULT NULL");
            System.out.println("[DB] Auto-migration: messages.group_id modified to VARCHAR(36) successfully.");
        } catch (SQLException e) {
            System.out.println("[DB] Auto-migration check: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DB] Connection pool closed");
        }
    }

    // ==================== USER OPERATIONS ====================

    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users (username, password_hash, display_name) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, hashPassword(password));
            stmt.setString(3, username);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // Duplicate entry
                System.out.println("[DB] Username already exists: " + username);
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    public static boolean authenticateUser(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("password_hash").equals(hashPassword(password));
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void updateUserStatus(String username, String status) {
        String sql = "UPDATE users SET status = ?, last_seen = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void updatePublicKey(String username, String publicKey) {
        String sql = "UPDATE users SET public_key = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, publicKey);
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String getPublicKey(String username) {
        String sql = "SELECT public_key FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("public_key");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ==================== MESSAGE OPERATIONS ====================

    /**
     * Encode forwarded/reply metadata into content string for DB storage
     */
    private static String formatContentForSave(Message message) {
        String content = message.getPayloadString("content");
        if (message.getPayloadBoolean("isForwarded")) {
            JsonObject json = new JsonObject();
            json.addProperty("isForwarded", true);
            json.addProperty("content", content);
            return json.toString();
        } else if (message.getPayloadBoolean("isReply")) {
            JsonObject json = new JsonObject();
            json.addProperty("isReply", true);
            json.addProperty("replyToId", message.getPayloadString("replyToId"));
            json.addProperty("replyToSender", message.getPayloadString("replyToSender"));
            json.addProperty("replyToContent", message.getPayloadString("replyToContent"));
            json.addProperty("content", content);
            return json.toString();
        }
        return content;
    }

    /**
     * Decode saved content from DB back into Message payload properties
     */
    private static void parseSavedContent(Message msg, String rawContent) {
        if (rawContent != null && rawContent.startsWith("{") && rawContent.endsWith("}")) {
            try {
                JsonObject json = JsonParser.parseString(rawContent).getAsJsonObject();
                if (json.has("isForwarded") && json.get("isForwarded").getAsBoolean()) {
                    msg.getPayload().addProperty("isForwarded", true);
                    msg.getPayload().addProperty("content", json.get("content").getAsString());
                    return;
                } else if (json.has("isReply") && json.get("isReply").getAsBoolean()) {
                    msg.getPayload().addProperty("isReply", true);
                    if (json.has("replyToId")) msg.getPayload().addProperty("replyToId", json.get("replyToId").getAsString());
                    if (json.has("replyToSender")) msg.getPayload().addProperty("replyToSender", json.get("replyToSender").getAsString());
                    if (json.has("replyToContent")) msg.getPayload().addProperty("replyToContent", json.get("replyToContent").getAsString());
                    msg.getPayload().addProperty("content", json.get("content").getAsString());
                    return;
                }
            } catch (Exception ignored) {}
        }
        msg.getPayload().addProperty("content", rawContent != null ? rawContent : "");
    }

    public static void saveMessage(Message message) {
        String sql = "INSERT INTO messages (message_id, from_user, to_user, content, message_type, is_group_message, group_id, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE message_id=message_id";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, message.getMessageId());
            stmt.setString(2, message.getFrom());
            stmt.setString(3, message.getTo());
            stmt.setString(4, formatContentForSave(message));
            stmt.setString(5, message.getType().equals(Constants.TYPE_GROUP_MSG) ? "TEXT" : "TEXT");
            stmt.setBoolean(6, message.getType().equals(Constants.TYPE_GROUP_MSG));
            stmt.setString(7, message.getPayloadString("groupId"));
            stmt.setLong(8, message.getTimestamp());
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() != 1062) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Đánh dấu tin nhắn đã đọc trong DB
     */
    public static void markMessageRead(String messageId) {
        String sql = "UPDATE messages SET is_read = TRUE WHERE message_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, messageId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Ignore - cột có thể chưa tồn tại
        }
    }

    /**
     * Đếm số tin nhắn chưa đọc từ một người gửi cụ thể
     */
    public static int getUnreadCountFromUser(String myUsername, String fromUser) {
        String sql = "SELECT COUNT(*) FROM messages WHERE from_user = ? AND to_user = ? AND is_group_message = FALSE AND (is_read IS NULL OR is_read = FALSE)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fromUser);
            stmt.setString(2, myUsername);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            // Ignore
        }
        return 0;
    }

    /**
     * Lấy danh sách messageId đã được đọc (do mình gửi cho to_user và to_user đã đọc)
     */
    public static java.util.Set<String> getReadMessageIds(String myUsername, String toUser) {
        String sql = "SELECT message_id FROM messages WHERE from_user = ? AND to_user = ? AND is_group_message = FALSE AND is_read = TRUE";
        java.util.Set<String> ids = new java.util.HashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, myUsername);
            stmt.setString(2, toUser);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) ids.add(rs.getString("message_id"));
        } catch (SQLException e) {
            // Ignore
        }
        return ids;
    }

    public static List<Message> getMessageHistory(String user1, String user2, int limit) {
        String sql = "SELECT * FROM messages WHERE ((from_user = ? AND to_user = ?) OR (from_user = ? AND to_user = ?)) AND is_group_message = FALSE ORDER BY timestamp ASC LIMIT ?";
        List<Message> messages = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            stmt.setInt(5, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String rawContent = rs.getString("content");
                Message msg = new Message(Constants.TYPE_DIRECT_MSG, rs.getString("from_user"), rs.getString("to_user"));
                msg.setMessageId(rs.getString("message_id"));
                msg.setTimestamp(rs.getLong("timestamp"));
                parseSavedContent(msg, rawContent);
                messages.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    public static List<Message> getGroupMessages(String groupId, int limit) {
        String sql = "SELECT * FROM messages WHERE group_id = ? AND is_group_message = TRUE ORDER BY timestamp ASC LIMIT ?";
        List<Message> messages = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String rawContent = rs.getString("content");
                String gId = rs.getString("group_id");
                Message msg = new Message(Constants.TYPE_GROUP_MSG, rs.getString("from_user"), gId);
                msg.getPayload().addProperty("groupId", gId);
                msg.setMessageId(rs.getString("message_id"));
                msg.setTimestamp(rs.getLong("timestamp"));
                parseSavedContent(msg, rawContent);
                messages.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    // ==================== OFFLINE MESSAGE OPERATIONS ====================

    public static void saveOfflineMessage(Message message, String toUser) {
        String sql = "INSERT INTO offline_messages (message_id, from_user, to_user, content, message_type, is_group_message, group_id, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, message.getMessageId());
            stmt.setString(2, message.getFrom());
            stmt.setString(3, toUser);
            stmt.setString(4, formatContentForSave(message));
            stmt.setString(5, "TEXT");
            stmt.setBoolean(6, message.getType().equals(Constants.TYPE_GROUP_MSG));
            stmt.setString(7, message.getPayloadString("groupId"));
            stmt.setLong(8, message.getTimestamp());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<Message> getOfflineMessages(String username) {
        String sql = "SELECT * FROM offline_messages WHERE to_user = ? ORDER BY timestamp ASC";
        List<Message> messages = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String rawContent = rs.getString("content");
                Message msg = new Message(Constants.TYPE_DIRECT_MSG, rs.getString("from_user"), rs.getString("to_user"));
                msg.setMessageId(rs.getString("message_id"));
                msg.setTimestamp(rs.getLong("timestamp"));
                if (rs.getBoolean("is_group_message")) {
                    msg.setType(Constants.TYPE_GROUP_MSG);
                    msg.getPayload().addProperty("groupId", rs.getString("group_id"));
                }
                parseSavedContent(msg, rawContent);
                messages.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    public static void deleteOfflineMessages(String username) {
        String sql = "DELETE FROM offline_messages WHERE to_user = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== GROUP OPERATIONS ====================

    public static String createGroup(String groupName, String createdBy) {
        String groupId = java.util.UUID.randomUUID().toString();
        String sql = "INSERT INTO chat_groups (group_id, group_name, created_by) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setString(2, groupName);
            stmt.setString(3, createdBy);
            stmt.executeUpdate();

            // Thêm người tạo vào nhóm với vai trò ADMIN
            addGroupMember(groupId, createdBy, "ADMIN");
            return groupId;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void addGroupMember(String groupId, String username, String role) {
        String sql = "INSERT IGNORE INTO group_members (group_id, username, role) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setString(2, username);
            stmt.setString(3, role);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void removeGroupMember(String groupId, String username) {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean deleteGroup(String groupId) {
        String sql1 = "DELETE FROM group_members WHERE group_id = ?";
        String sql2 = "DELETE FROM offline_messages WHERE group_id = ?";
        String sql3 = "DELETE FROM messages WHERE group_id = ?";
        String sql4 = "DELETE FROM chat_groups WHERE group_id = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt1 = conn.prepareStatement(sql1)) { stmt1.setString(1, groupId); stmt1.executeUpdate(); }
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2)) { stmt2.setString(1, groupId); stmt2.executeUpdate(); }
                try (PreparedStatement stmt3 = conn.prepareStatement(sql3)) { stmt3.setString(1, groupId); stmt3.executeUpdate(); }
                try (PreparedStatement stmt4 = conn.prepareStatement(sql4)) { stmt4.setString(1, groupId); stmt4.executeUpdate(); }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isGroupAdmin(String groupId, String username) {
        String sql = "SELECT created_by FROM chat_groups WHERE group_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("created_by").equals(username);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static List<String> getGroupMembers(String groupId) {
        String sql = "SELECT username FROM group_members WHERE group_id = ?";
        List<String> members = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                members.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return members;
    }

    public static List<String[]> getUserGroups(String username) {
        String sql = "SELECT g.group_id, g.group_name FROM chat_groups g INNER JOIN group_members gm ON g.group_id = gm.group_id WHERE gm.username = ?";
        List<String[]> groups = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                groups.add(new String[]{rs.getString("group_id"), rs.getString("group_name")});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }

    public static List<String[]> getAllGroups() {
        String sql = "SELECT group_id, group_name, created_by FROM chat_groups ORDER BY created_at DESC";
        List<String[]> groups = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                groups.add(new String[]{
                    rs.getString("group_id"),
                    rs.getString("group_name"),
                    rs.getString("created_by")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }

    // ==================== PEER REGISTRY ====================

    public static void registerPeer(String username, String ip, int port) {
        String sql = "INSERT INTO peer_registry (username, ip_address, tcp_port, is_online) VALUES (?, ?, ?, TRUE) ON DUPLICATE KEY UPDATE ip_address = ?, tcp_port = ?, is_online = TRUE, last_heartbeat = CURRENT_TIMESTAMP";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, ip);
            stmt.setInt(3, port);
            stmt.setString(4, ip);
            stmt.setInt(5, port);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void unregisterPeer(String username) {
        String sql = "UPDATE peer_registry SET is_online = FALSE WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void updateHeartbeat(String username) {
        String sql = "UPDATE peer_registry SET last_heartbeat = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== UTILITY ====================

    private static String hashPassword(String password) {
        // Simple hash - in production use BCrypt
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return password; // fallback
        }
    }

    // ==================== FRIEND SYSTEM ====================

    /**
     * Gửi lời mời kết bạn
     */
    public static boolean sendFriendRequest(String fromUser, String toUser) {
        // Kiểm tra đã là bạn chưa
        if (areFriends(fromUser, toUser)) return false;

        String sql = "INSERT INTO friend_requests (from_user, to_user, status) VALUES (?, ?, 'PENDING') " +
                     "ON DUPLICATE KEY UPDATE status = 'PENDING', created_at = CURRENT_TIMESTAMP";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fromUser);
            stmt.setString(2, toUser);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Chấp nhận lời mời kết bạn
     */
    public static boolean acceptFriendRequest(String fromUser, String toUser) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Cập nhật trạng thái request
                String updateSql = "UPDATE friend_requests SET status = 'ACCEPTED' WHERE from_user = ? AND to_user = ? AND status = 'PENDING'";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, fromUser);
                    stmt.setString(2, toUser);
                    int updated = stmt.executeUpdate();
                    if (updated == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // Thêm vào bảng friends (lưu theo thứ tự alphabet để tránh trùng)
                String user1 = fromUser.compareTo(toUser) < 0 ? fromUser : toUser;
                String user2 = fromUser.compareTo(toUser) < 0 ? toUser : fromUser;
                String insertSql = "INSERT IGNORE INTO friends (user1, user2) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, user1);
                    stmt.setString(2, user2);
                    stmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Hủy kết bạn
     */
    public static boolean deleteFriend(String user1, String user2) {
        String u1 = user1.compareTo(user2) < 0 ? user1 : user2;
        String u2 = user1.compareTo(user2) < 0 ? user2 : user1;
        String sql = "DELETE FROM friends WHERE user1 = ? AND user2 = ?";
        String sql2 = "DELETE FROM friend_requests WHERE (from_user = ? AND to_user = ?) OR (from_user = ? AND to_user = ?)";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, u1);
                    stmt.setString(2, u2);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2)) {
                    stmt2.setString(1, user1);
                    stmt2.setString(2, user2);
                    stmt2.setString(3, user2);
                    stmt2.setString(4, user1);
                    stmt2.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Từ chối lời mời kết bạn
     */
    public static boolean rejectFriendRequest(String fromUser, String toUser) {
        String sql = "UPDATE friend_requests SET status = 'REJECTED' WHERE from_user = ? AND to_user = ? AND status = 'PENDING'";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fromUser);
            stmt.setString(2, toUser);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Kiểm tra 2 người có là bạn bè không
     */
    public static boolean areFriends(String user1, String user2) {
        String u1 = user1.compareTo(user2) < 0 ? user1 : user2;
        String u2 = user1.compareTo(user2) < 0 ? user2 : user1;
        String sql = "SELECT COUNT(*) FROM friends WHERE user1 = ? AND user2 = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, u1);
            stmt.setString(2, u2);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Lấy danh sách bạn bè của một user
     */
    public static List<String> getFriends(String username) {
        String sql = "SELECT CASE WHEN user1 = ? THEN user2 ELSE user1 END AS friend FROM friends WHERE user1 = ? OR user2 = ?";
        List<String> friends = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            stmt.setString(3, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                friends.add(rs.getString("friend"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return friends;
    }

    /**
     * Lấy danh sách lời mời kết bạn đang chờ (nhận được)
     */
    public static List<String> getPendingFriendRequests(String username) {
        String sql = "SELECT from_user FROM friend_requests WHERE to_user = ? AND status = 'PENDING'";
        List<String> requests = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                requests.add(rs.getString("from_user"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return requests;
    }

    /**
     * Lấy danh sách lời mời đã gửi đang chờ
     */
    public static List<String> getSentFriendRequests(String username) {
        String sql = "SELECT to_user FROM friend_requests WHERE from_user = ? AND status = 'PENDING'";
        List<String> requests = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                requests.add(rs.getString("to_user"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return requests;
    }

    /**
     * Tìm kiếm user theo tên (LIKE)
     */
    public static List<String> searchUsers(String query, String excludeUser) {
        String sql = "SELECT username FROM users WHERE username LIKE ? AND username != ? LIMIT 20";
        List<String> users = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + query + "%");
            stmt.setString(2, excludeUser);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    /**
     * Lấy tất cả users (trừ bản thân)
     */
    public static List<String> getAllUsers(String excludeUser) {
        String sql = "SELECT username FROM users WHERE username != ? ORDER BY username";
        List<String> users = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, excludeUser);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }
}
