package com.p2pchat.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.p2pchat.shared.*;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * Xử lý kết nối từ mỗi peer client
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final PeerRegistry peerRegistry;
    private String username;
    private final Gson gson = new Gson();

    public ClientHandler(Socket socket, PeerRegistry peerRegistry) {
        this.socket = socket;
        this.peerRegistry = peerRegistry;
    }

    @Override
    public void run() {
        try {
            while (!socket.isClosed()) {
                Message message = Protocol.readMessage(socket);
                handleMessage(message);
            }
        } catch (IOException e) {
            System.out.println("[Handler] Connection lost: " + (username != null ? username : "unknown") + " - " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Xử lý message dựa trên type
     */
    private void handleMessage(Message message) throws IOException {
        System.out.println("[Handler] Received: " + message);

        switch (message.getType()) {
            case Constants.TYPE_REGISTER -> handleRegister(message);
            case Constants.TYPE_LOGIN -> handleLogin(message);
            case Constants.TYPE_HEARTBEAT -> handleHeartbeat(message);
            case Constants.TYPE_DIRECT_MSG -> handleDirectMessage(message);
            case Constants.TYPE_GROUP_MSG -> handleGroupMessage(message);
            case Constants.TYPE_GROUP_CREATE -> handleGroupCreate(message);
            case Constants.TYPE_GROUP_CREATE_WITH_MEMBERS -> handleGroupCreateWithMembers(message);
            case Constants.TYPE_GROUP_JOIN -> handleGroupJoin(message);
            case Constants.TYPE_GROUP_LEAVE -> handleGroupLeave(message);
            case Constants.TYPE_GROUP_DELETE -> handleGroupDelete(message);
            case Constants.TYPE_GROUP_REMOVE_MEMBER -> handleGroupRemoveMember(message);
            case Constants.TYPE_GROUP_LIST -> handleGroupList(message);
            case Constants.TYPE_GROUP_MEMBERS -> handleGroupMembers(message);
            case Constants.TYPE_TYPING -> handleTyping(message);
            case Constants.TYPE_READ_RECEIPT -> handleReadReceipt(message);
            case Constants.TYPE_KEY_EXCHANGE -> handleKeyExchange(message);
            case Constants.TYPE_BROADCAST -> handleBroadcast(message);
            case Constants.TYPE_FILE_INIT -> handleFileInit(message);
            case Constants.TYPE_FILE_ACCEPT, Constants.TYPE_FILE_REJECT -> forwardToPeer(message);
            case Constants.TYPE_FRIEND_REQUEST -> handleFriendRequest(message);
            case Constants.TYPE_FRIEND_ACCEPT -> handleFriendAccept(message);
            case Constants.TYPE_FRIEND_REJECT -> handleFriendReject(message);
            case Constants.TYPE_SEARCH_USER -> handleSearchUser(message);
            case Constants.TYPE_FRIEND_LIST -> handleFriendListRequest(message);
            case Constants.TYPE_FRIEND_REMOVE -> handleFriendRemove(message);
            case Constants.TYPE_DISCONNECT -> handleDisconnect(message);
            default -> System.out.println("[Handler] Unknown message type: " + message.getType());
        }
    }

    // ==================== AUTH HANDLERS ====================

    private void handleRegister(Message message) throws IOException {
        String user = message.getFrom();
        String password = message.getPayloadString("password");
        String ip = message.getPayloadString("ip");
        int port = message.getPayloadInt("port");

        Message response = new Message(Constants.TYPE_REGISTER_RESPONSE, "SERVER", user);

        if (DatabaseManager.registerUser(user, password)) {
            this.username = user;
            peerRegistry.registerPeer(user, ip, port, socket);
            DatabaseManager.registerPeer(user, ip, port);
            DatabaseManager.updateUserStatus(user, Constants.STATUS_ONLINE);

            response.getPayload().addProperty("success", true);
            response.getPayload().addProperty("message", "Đăng ký thành công!");

            // Gửi response
            Protocol.sendMessage(socket, response);

            // Gửi danh sách peer
            sendPeerList();

            // Gửi danh sách group
            sendGroupList();

            // Gửi danh sách bạn bè và lời mời
            sendFriendList();
            sendPendingFriendRequests();

            // Thông báo peer mới cho tất cả
            Message statusMsg = Message.createPeerStatus(user, Constants.STATUS_ONLINE, ip, port);
            peerRegistry.broadcastToAll(statusMsg, user);

            // Gửi offline messages nếu có
            sendOfflineMessages(user);

            System.out.println("[Handler] User registered: " + user);
        } else {
            response.getPayload().addProperty("success", false);
            response.getPayload().addProperty("message", "Tên đăng nhập đã tồn tại!");
            Protocol.sendMessage(socket, response);
        }
    }

    private void handleLogin(Message message) throws IOException {
        String user = message.getFrom();
        String password = message.getPayloadString("password");
        String ip = message.getPayloadString("ip");
        int port = message.getPayloadInt("port");

        Message response = new Message(Constants.TYPE_LOGIN_RESPONSE, "SERVER", user);

        if (DatabaseManager.authenticateUser(user, password)) {
            this.username = user;
            peerRegistry.registerPeer(user, ip, port, socket);
            DatabaseManager.registerPeer(user, ip, port);
            DatabaseManager.updateUserStatus(user, Constants.STATUS_ONLINE);

            response.getPayload().addProperty("success", true);
            response.getPayload().addProperty("message", "Đăng nhập thành công!");

            Protocol.sendMessage(socket, response);

            // Gửi peer list & group list
            sendPeerList();
            sendGroupList();

            // Gửi danh sách bạn bè và lời mời
            sendFriendList();
            sendPendingFriendRequests();

            // Thông báo peer online
            Message statusMsg = Message.createPeerStatus(user, Constants.STATUS_ONLINE, ip, port);
            peerRegistry.broadcastToAll(statusMsg, user);

            // Gửi offline messages
            sendOfflineMessages(user);

            System.out.println("[Handler] User logged in: " + user);
        } else {
            response.getPayload().addProperty("success", false);
            response.getPayload().addProperty("message", "Sai tên đăng nhập hoặc mật khẩu!");
            Protocol.sendMessage(socket, response);
        }
    }

    // ==================== MESSAGE HANDLERS ====================

    private void handleDirectMessage(Message message) {
        String targetUser = message.getTo();

        // Lưu tin nhắn vào DB
        DatabaseManager.saveMessage(message);

        // Thử gửi trực tiếp qua server relay
        if (!peerRegistry.sendToPeer(targetUser, message)) {
            // Peer offline -> lưu offline message
            DatabaseManager.saveOfflineMessage(message, targetUser);
            System.out.println("[Handler] User offline, message stored: " + targetUser);
        }
    }

    private void handleGroupMessage(Message message) {
        String groupId = message.getPayloadString("groupId");

        // Lưu tin nhắn nhóm
        DatabaseManager.saveMessage(message);

        // Gửi tới tất cả thành viên nhóm
        List<String> members = DatabaseManager.getGroupMembers(groupId);
        for (String member : members) {
            if (!member.equals(message.getFrom())) {
                if (!peerRegistry.sendToPeer(member, message)) {
                    // Thành viên offline -> lưu
                    DatabaseManager.saveOfflineMessage(message, member);
                }
            }
        }
    }

    private void handleTyping(Message message) {
        // Forward typing indicator trực tiếp
        peerRegistry.sendToPeer(message.getTo(), message);
    }

    private void handleReadReceipt(Message message) {
        peerRegistry.sendToPeer(message.getTo(), message);
    }

    private void handleBroadcast(Message message) {
        peerRegistry.broadcastToAll(message, message.getFrom());
    }

    // ==================== GROUP HANDLERS ====================

    private void handleGroupCreate(Message message) throws IOException {
        String groupName = message.getPayloadString("groupName");
        String groupId = DatabaseManager.createGroup(groupName, message.getFrom());

        Message response = new Message(Constants.TYPE_GROUP_CREATE_RESPONSE, "SERVER", message.getFrom());
        if (groupId != null) {
            response.getPayload().addProperty("success", true);
            response.getPayload().addProperty("groupId", groupId);
            response.getPayload().addProperty("groupName", groupName);
            response.getPayload().addProperty("message", "Tạo nhóm thành công!");
        } else {
            response.getPayload().addProperty("success", false);
            response.getPayload().addProperty("message", "Tạo nhóm thất bại!");
        }
        Protocol.sendMessage(socket, response);

        // Broadcast group list update cho tất cả
        broadcastGroupList();
    }

    private void handleGroupJoin(Message message) throws IOException {
        String groupId = message.getPayloadString("groupId");
        DatabaseManager.addGroupMember(groupId, message.getFrom(), "MEMBER");

        // Thông báo cho các thành viên nhóm
        Message notification = new Message(Constants.TYPE_GROUP_MSG, "SYSTEM", groupId);
        notification.getPayload().addProperty("content", message.getFrom() + " đã tham gia nhóm");
        notification.getPayload().addProperty("groupId", groupId);
        notification.getPayload().addProperty("isSystem", true);
        handleGroupMessage(notification);

        // Gửi lại group list
        sendGroupList();
        broadcastGroupList();
    }

    private void handleGroupLeave(Message message) throws IOException {
        String groupId = message.getPayloadString("groupId");
        DatabaseManager.removeGroupMember(groupId, message.getFrom());

        Message notification = new Message(Constants.TYPE_GROUP_MSG, "SYSTEM", groupId);
        notification.getPayload().addProperty("content", message.getFrom() + " đã rời nhóm");
        notification.getPayload().addProperty("groupId", groupId);
        notification.getPayload().addProperty("isSystem", true);
        handleGroupMessage(notification);

        sendGroupList();
    }

    private void handleGroupList(Message message) throws IOException {
        sendGroupList();
    }

    private void handleGroupMembers(Message message) throws IOException {
        String groupId = message.getPayloadString("groupId");
        List<String> members = DatabaseManager.getGroupMembers(groupId);

        Message response = new Message(Constants.TYPE_GROUP_MEMBERS, "SERVER", message.getFrom());
        response.getPayload().addProperty("groupId", groupId);
        JsonArray membersArray = new JsonArray();
        for (String member : members) {
            membersArray.add(member);
        }
        response.getPayload().add("members", membersArray);
        Protocol.sendMessage(socket, response);
    }

    // ==================== FILE HANDLERS ====================

    private void handleFileInit(Message message) {
        // Forward file init request to target peer
        if (!peerRegistry.sendToPeer(message.getTo(), message)) {
            // Target offline
            Message error = Message.createError("Người nhận không online, không thể gửi file.");
            error.setTo(message.getFrom());
            peerRegistry.sendToPeer(message.getFrom(), error);
        }
    }

    private void forwardToPeer(Message message) {
        peerRegistry.sendToPeer(message.getTo(), message);
    }

    // ==================== KEY EXCHANGE ====================

    private void handleKeyExchange(Message message) {
        // Lưu public key
        DatabaseManager.updatePublicKey(message.getFrom(), message.getPayloadString("publicKey"));
        // Forward tới peer đích
        peerRegistry.sendToPeer(message.getTo(), message);
    }

    // ==================== HEARTBEAT ====================

    private void handleHeartbeat(Message message) {
        peerRegistry.updateHeartbeat(message.getFrom());
        DatabaseManager.updateHeartbeat(message.getFrom());
    }

    // ==================== FRIEND HANDLERS ====================

    private void handleFriendRequest(Message message) throws IOException {
        String targetUser = message.getTo();
        boolean success = DatabaseManager.sendFriendRequest(message.getFrom(), targetUser);

        if (success) {
            // Thông báo cho người nhận nếu online
            Message notification = new Message(Constants.TYPE_FRIEND_REQUEST, message.getFrom(), targetUser);
            peerRegistry.sendToPeer(targetUser, notification);
            
            // Gửi lại danh sách pending requests cho người nhận
            sendPendingFriendRequestsToPeer(targetUser);

            // Gửi lại friend list cho người gửi
            sendFriendList();
        }
    }

    private void handleFriendAccept(Message message) throws IOException {
        String fromUser = message.getTo(); // Người đã gửi lời mời ban đầu
        String toUser = message.getFrom(); // Người chấp nhận

        boolean success = DatabaseManager.acceptFriendRequest(fromUser, toUser);
        if (success) {
            // Gửi lại friend list cho cả hai
            sendFriendList();
            
            // Gửi lại danh sách lời mời kết bạn (để mất đi lời mời vừa đồng ý)
            sendPendingFriendRequests();

            // Thông báo cho người gửi lời mời ban đầu
            Message notification = new Message(Constants.TYPE_FRIEND_ACCEPT, toUser, fromUser);
            peerRegistry.sendToPeer(fromUser, notification);

            // Gửi friend list cho người gửi lời mời nếu online
            sendFriendListToPeer(fromUser);
        }
    }

    private void handleFriendReject(Message message) throws IOException {
        String fromUser = message.getTo(); // Người đã gửi lời mời ban đầu
        String toUser = message.getFrom(); // Người từ chối
        DatabaseManager.rejectFriendRequest(fromUser, toUser);
        sendPendingFriendRequests();
        
        // Thông báo cho người gửi lời mời ban đầu
        Message notification = new Message(Constants.TYPE_FRIEND_REJECT, toUser, fromUser);
        peerRegistry.sendToPeer(fromUser, notification);
    }

    private void handleSearchUser(Message message) throws IOException {
        String query = message.getPayloadString("query");
        List<String> results = DatabaseManager.searchUsers(query, message.getFrom());
        List<String> friends = DatabaseManager.getFriends(message.getFrom());
        List<String> sentRequests = DatabaseManager.getSentFriendRequests(message.getFrom());
        List<String> receivedRequests = DatabaseManager.getPendingFriendRequests(message.getFrom());

        Message response = new Message(Constants.TYPE_SEARCH_RESULT, "SERVER", message.getFrom());
        JsonArray usersArray = new JsonArray();
        for (String user : results) {
            JsonObject userObj = new JsonObject();
            userObj.addProperty("username", user);
            userObj.addProperty("isFriend", friends.contains(user));
            userObj.addProperty("requestSent", sentRequests.contains(user));
            userObj.addProperty("requestReceived", receivedRequests.contains(user));
            userObj.addProperty("isOnline", peerRegistry.isPeerOnline(user));
            usersArray.add(userObj);
        }
        response.getPayload().add("users", usersArray);
        Protocol.sendMessage(socket, response);
    }

    private void handleFriendListRequest(Message message) throws IOException {
        sendFriendList();
        sendPendingFriendRequests();
    }

    private void handleFriendRemove(Message message) throws IOException {
        String targetUser = message.getPayloadString("targetUser");
        if (DatabaseManager.deleteFriend(message.getFrom(), targetUser)) {
            // Gửi friend list mới cho cả 2
            sendFriendList();
            sendFriendListToPeer(targetUser);
        }
    }

    // ==================== GROUP WITH MEMBERS ====================

    private void handleGroupCreateWithMembers(Message message) throws IOException {
        String groupName = message.getPayloadString("groupName");
        JsonArray membersArray = message.getPayload().getAsJsonArray("members");

        String groupId = DatabaseManager.createGroup(groupName, message.getFrom());
        Message response = new Message(Constants.TYPE_GROUP_CREATE_RESPONSE, "SERVER", message.getFrom());

        if (groupId != null) {
            // Thêm các thành viên
            for (int i = 0; i < membersArray.size(); i++) {
                String member = membersArray.get(i).getAsString();
                if (!member.equals(message.getFrom())) {
                    DatabaseManager.addGroupMember(groupId, member, "MEMBER");
                }
            }

            response.getPayload().addProperty("success", true);
            response.getPayload().addProperty("groupId", groupId);
            response.getPayload().addProperty("groupName", groupName);
            response.getPayload().addProperty("message", "Tạo nhóm thành công!");

            // Thông báo cho các thành viên
            for (int i = 0; i < membersArray.size(); i++) {
                String member = membersArray.get(i).getAsString();
                if (!member.equals(message.getFrom())) {
                    Message notification = new Message(Constants.TYPE_GROUP_MSG, "SYSTEM", groupId);
                    notification.getPayload().addProperty("content", message.getFrom() + " đã thêm bạn vào nhóm " + groupName);
                    notification.getPayload().addProperty("groupId", groupId);
                    notification.getPayload().addProperty("isSystem", true);
                    peerRegistry.sendToPeer(member, notification);
                }
            }
        } else {
            response.getPayload().addProperty("success", false);
            response.getPayload().addProperty("message", "Tạo nhóm thất bại!");
        }
        Protocol.sendMessage(socket, response);
        broadcastGroupList();
    }

    private void handleGroupDelete(Message message) throws IOException {
        String groupId = message.getPayloadString("groupId");
        if (DatabaseManager.isGroupAdmin(groupId, message.getFrom())) {
            DatabaseManager.deleteGroup(groupId);
            broadcastGroupList();
        } else {
            Message response = Message.createError("Chỉ người tạo nhóm mới có quyền xóa nhóm!");
            Protocol.sendMessage(socket, response);
        }
    }

    private void handleGroupRemoveMember(Message message) throws IOException {
        String groupId = message.getPayloadString("groupId");
        String targetUser = message.getPayloadString("targetUser");

        if (DatabaseManager.isGroupAdmin(groupId, message.getFrom())) {
            DatabaseManager.removeGroupMember(groupId, targetUser);
            
            // Gửi thông báo cho user bị xóa
            Message notification = new Message(Constants.TYPE_GROUP_MSG, "SYSTEM", groupId);
            notification.getPayload().addProperty("content", "Bạn đã bị xóa khỏi nhóm bởi Admin.");
            notification.getPayload().addProperty("groupId", groupId);
            notification.getPayload().addProperty("isSystem", true);
            peerRegistry.sendToPeer(targetUser, notification);
            
            // Gửi thông báo cho group
            Message groupNotif = new Message(Constants.TYPE_GROUP_MSG, "SYSTEM", groupId);
            groupNotif.getPayload().addProperty("content", targetUser + " đã bị xóa khỏi nhóm.");
            groupNotif.getPayload().addProperty("groupId", groupId);
            groupNotif.getPayload().addProperty("isSystem", true);
            handleGroupMessage(groupNotif);

            sendGroupList();
            broadcastGroupList();
        } else {
            Message response = Message.createError("Bạn không có quyền xóa thành viên!");
            Protocol.sendMessage(socket, response);
        }
    }

    // ==================== DISCONNECT ====================

    private void handleDisconnect(Message message) {
        cleanup();
    }

    // ==================== HELPER METHODS ====================

    private void sendPeerList() throws IOException {
        List<Map<String, Object>> peerList = peerRegistry.getOnlinePeerList();
        Message peerListMsg = new Message(Constants.TYPE_PEER_LIST, "SERVER", username);
        JsonArray peersArray = new JsonArray();
        for (Map<String, Object> peer : peerList) {
            JsonObject peerObj = new JsonObject();
            peerObj.addProperty("username", (String) peer.get("username"));
            peerObj.addProperty("ip", (String) peer.get("ip"));
            peerObj.addProperty("port", (Integer) peer.get("port"));
            peerObj.addProperty("status", (String) peer.get("status"));
            peersArray.add(peerObj);
        }
        peerListMsg.getPayload().add("peers", peersArray);
        Protocol.sendMessage(socket, peerListMsg);
    }

    private void sendGroupList() throws IOException {
        List<String[]> allGroups = DatabaseManager.getAllGroups();
        List<String[]> userGroups = DatabaseManager.getUserGroups(username);
        Set<String> joinedGroupIds = new HashSet<>();
        for (String[] g : userGroups) {
            joinedGroupIds.add(g[0]);
        }

        Message groupListMsg = new Message(Constants.TYPE_GROUP_LIST, "SERVER", username);
        JsonArray groupsArray = new JsonArray();
        for (String[] group : allGroups) {
            JsonObject groupObj = new JsonObject();
            groupObj.addProperty("groupId", group[0]);
            groupObj.addProperty("groupName", group[1]);
            groupObj.addProperty("createdBy", group[2]);
            groupObj.addProperty("joined", joinedGroupIds.contains(group[0]));
            groupsArray.add(groupObj);
        }
        groupListMsg.getPayload().add("groups", groupsArray);
        Protocol.sendMessage(socket, groupListMsg);
    }

    private void broadcastGroupList() {
        try {
            List<String[]> allGroups = DatabaseManager.getAllGroups();
            for (Map<String, Object> peer : peerRegistry.getOnlinePeerList()) {
                String peerUsername = (String) peer.get("username");
                List<String[]> userGroups = DatabaseManager.getUserGroups(peerUsername);
                Set<String> joinedGroupIds = new HashSet<>();
                for (String[] g : userGroups) {
                    joinedGroupIds.add(g[0]);
                }

                Message groupListMsg = new Message(Constants.TYPE_GROUP_LIST, "SERVER", peerUsername);
                JsonArray groupsArray = new JsonArray();
                for (String[] group : allGroups) {
                    JsonObject groupObj = new JsonObject();
                    groupObj.addProperty("groupId", group[0]);
                    groupObj.addProperty("groupName", group[1]);
                    groupObj.addProperty("createdBy", group[2]);
                    groupObj.addProperty("joined", joinedGroupIds.contains(group[0]));
                    groupsArray.add(groupObj);
                }
                groupListMsg.getPayload().add("groups", groupsArray);
                peerRegistry.sendToPeer(peerUsername, groupListMsg);
            }
        } catch (Exception e) {
            System.err.println("[Handler] Error broadcasting group list: " + e.getMessage());
        }
    }

    private void sendOfflineMessages(String user) {
        List<Message> offlineMessages = DatabaseManager.getOfflineMessages(user);
        if (!offlineMessages.isEmpty()) {
            System.out.println("[Handler] Sending " + offlineMessages.size() + " offline messages to " + user);
            for (Message msg : offlineMessages) {
                peerRegistry.sendToPeer(user, msg);
            }
            DatabaseManager.deleteOfflineMessages(user);
        }
    }

    private void sendFriendList() throws IOException {
        List<String> friends = DatabaseManager.getFriends(username);
        Message friendListMsg = new Message(Constants.TYPE_FRIEND_LIST, "SERVER", username);
        JsonArray friendsArray = new JsonArray();
        for (String friend : friends) {
            JsonObject friendObj = new JsonObject();
            friendObj.addProperty("username", friend);
            friendObj.addProperty("isOnline", peerRegistry.isPeerOnline(friend));
            friendsArray.add(friendObj);
        }
        friendListMsg.getPayload().add("friends", friendsArray);
        Protocol.sendMessage(socket, friendListMsg);
    }

    private void sendFriendListToPeer(String peerUsername) {
        try {
            List<String> friends = DatabaseManager.getFriends(peerUsername);
            Message friendListMsg = new Message(Constants.TYPE_FRIEND_LIST, "SERVER", peerUsername);
            JsonArray friendsArray = new JsonArray();
            for (String friend : friends) {
                JsonObject friendObj = new JsonObject();
                friendObj.addProperty("username", friend);
                friendObj.addProperty("isOnline", peerRegistry.isPeerOnline(friend));
                friendsArray.add(friendObj);
            }
            friendListMsg.getPayload().add("friends", friendsArray);
            peerRegistry.sendToPeer(peerUsername, friendListMsg);
        } catch (Exception e) {
            System.err.println("[Handler] Error sending friend list to " + peerUsername);
        }
    }

    private void sendPendingFriendRequests() throws IOException {
        List<String> pending = DatabaseManager.getPendingFriendRequests(username);
        Message requestListMsg = new Message(Constants.TYPE_FRIEND_REQUEST_LIST, "SERVER", username);
        JsonArray requestsArray = new JsonArray();
        for (String requester : pending) {
            JsonObject reqObj = new JsonObject();
            reqObj.addProperty("username", requester);
            reqObj.addProperty("isOnline", peerRegistry.isPeerOnline(requester));
            requestsArray.add(reqObj);
        }
        requestListMsg.getPayload().add("requests", requestsArray);
        Protocol.sendMessage(socket, requestListMsg);
    }

    private void sendPendingFriendRequestsToPeer(String peerUsername) {
        try {
            List<String> pending = DatabaseManager.getPendingFriendRequests(peerUsername);
            Message requestListMsg = new Message(Constants.TYPE_FRIEND_REQUEST_LIST, "SERVER", peerUsername);
            JsonArray requestsArray = new JsonArray();
            for (String requester : pending) {
                JsonObject reqObj = new JsonObject();
                reqObj.addProperty("username", requester);
                reqObj.addProperty("isOnline", peerRegistry.isPeerOnline(requester));
                requestsArray.add(reqObj);
            }
            requestListMsg.getPayload().add("requests", requestsArray);
            peerRegistry.sendToPeer(peerUsername, requestListMsg);
        } catch (Exception e) {
            System.err.println("[Handler] Error sending pending requests to " + peerUsername);
        }
    }

    private void cleanup() {
        if (username != null) {
            peerRegistry.unregisterPeer(username);
            DatabaseManager.updateUserStatus(username, Constants.STATUS_OFFLINE);
            DatabaseManager.unregisterPeer(username);

            // Thông báo offline
            Message statusMsg = Message.createPeerStatus(username, Constants.STATUS_OFFLINE, "", 0);
            peerRegistry.broadcastToAll(statusMsg, username);

            System.out.println("[Handler] User disconnected: " + username);
        }
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
