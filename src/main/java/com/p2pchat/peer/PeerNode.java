package com.p2pchat.peer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.p2pchat.shared.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Peer Node - Node ngang hàng vừa là client vừa là server
 * Kết nối tới Bootstrap Server để discovery
 * Nhận/gửi tin nhắn qua server relay
 */
public class PeerNode {

    private String username;
    private String bootstrapHost;
    private int bootstrapPort;

    // Kết nối tới Bootstrap Server
    private Socket bootstrapSocket;
    private OutputStream bootstrapOut;
    private Thread bootstrapListenerThread;

    // Heartbeat
    private ScheduledExecutorService heartbeatScheduler;

    // Peer list
    private final ConcurrentHashMap<String, JsonObject> onlinePeers = new ConcurrentHashMap<>();

    // Groups
    private final ConcurrentHashMap<String, JsonObject> groups = new ConcurrentHashMap<>();

    // Friends
    private final ConcurrentHashMap<String, JsonObject> friends = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<JsonObject> pendingFriendRequests = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Message> pendingMessages = new ConcurrentLinkedQueue<>();

    // Message callbacks
    private Consumer<Message> onMessageReceived;
    private Consumer<Message> onPeerStatusChanged;
    private Consumer<Message> onGroupListUpdated;
    private Consumer<Message> onGroupMembersReceived;
    private Consumer<Message> onTypingReceived;
    private Consumer<Message> onReadReceiptReceived;
    private Consumer<Message> onFileInitReceived;
    private Consumer<Message> onFriendListUpdated;
    private Consumer<Message> onFriendRequestReceived;
    private Consumer<Message> onFriendRequestListUpdated;
    private Consumer<Message> onFriendAcceptReceived;
    private Consumer<Message> onFriendRejectReceived;
    private Consumer<Message> onSearchResultReceived;
    private Consumer<String> onError;
    private Consumer<Boolean> onConnectionChanged;

    private volatile boolean connected = false;

    // ==================== CONSTRUCTOR ====================

    public PeerNode() {
        this.bootstrapHost = Constants.BOOTSTRAP_HOST;
        this.bootstrapPort = Constants.BOOTSTRAP_PORT;
    }

    // ==================== CONNECTION ====================

    /**
     * Đăng ký tài khoản mới và kết nối
     */
    public boolean register(String username, String password, String serverHost, int serverPort) {
        this.username = username;
        this.bootstrapHost = serverHost;
        this.bootstrapPort = serverPort;

        try {
            connectToBootstrap();

            // Gửi REGISTER
            String localIp = getLocalIp();
            Message registerMsg = Message.createRegister(username, password, localIp, 0);
            Protocol.sendMessage(bootstrapSocket, registerMsg);

            // Đọc response
            Message response = Protocol.readMessage(bootstrapSocket);
            if (response.getPayloadBoolean("success")) {
                connected = true;
                startHeartbeat();
                startListeningBootstrap();
                if (onConnectionChanged != null) onConnectionChanged.accept(true);
                return true;
            } else {
                String error = response.getPayloadString("message");
                if (onError != null) onError.accept(error);
                bootstrapSocket.close();
                return false;
            }
        } catch (IOException e) {
            if (onError != null) onError.accept("Không thể kết nối tới server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đăng nhập và kết nối
     */
    public boolean login(String username, String password, String serverHost, int serverPort) {
        this.username = username;
        this.bootstrapHost = serverHost;
        this.bootstrapPort = serverPort;

        try {
            connectToBootstrap();

            String localIp = getLocalIp();
            Message loginMsg = Message.createLogin(username, password, localIp, 0);
            Protocol.sendMessage(bootstrapSocket, loginMsg);

            Message response = Protocol.readMessage(bootstrapSocket);
            if (response.getPayloadBoolean("success")) {
                connected = true;
                startHeartbeat();
                startListeningBootstrap();
                if (onConnectionChanged != null) onConnectionChanged.accept(true);
                return true;
            } else {
                String error = response.getPayloadString("message");
                if (onError != null) onError.accept(error);
                bootstrapSocket.close();
                return false;
            }
        } catch (IOException e) {
            if (onError != null) onError.accept("Không thể kết nối tới server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kết nối TCP tới Bootstrap Server
     */
    private void connectToBootstrap() throws IOException {
        bootstrapSocket = new Socket();
        bootstrapSocket.connect(new InetSocketAddress(bootstrapHost, bootstrapPort), Constants.CONNECTION_TIMEOUT);
        bootstrapOut = bootstrapSocket.getOutputStream();
        System.out.println("[Peer] Connected to Bootstrap Server: " + bootstrapHost + ":" + bootstrapPort);
    }

    /**
     * Ngắt kết nối
     */
    public void disconnect() {
        connected = false;
        try {
            if (bootstrapSocket != null && !bootstrapSocket.isClosed()) {
                Message disconnectMsg = Message.createDisconnect(username);
                Protocol.sendMessage(bootstrapSocket, disconnectMsg);
                bootstrapSocket.close();
            }
        } catch (IOException ignored) {}

        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        if (onConnectionChanged != null) onConnectionChanged.accept(false);
        System.out.println("[Peer] Disconnected");
    }

    // ==================== MESSAGING ====================

    /**
     * Gửi tin nhắn trực tiếp - trả về Message đã gửi để GUI dùng đúng messageId
     */
    public Message sendDirectMessage(String to, String content) {
        if (!connected) {
            if (onError != null) onError.accept("Chưa kết nối tới server!");
            return null;
        }

        Message msg = Message.createDirectMessage(username, to, content);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
            return msg;
        } catch (IOException e) {
            if (onError != null) onError.accept("Gửi tin nhắn thất bại: " + e.getMessage());
            handleConnectionLost();
            return null;
        }
    }

    /**
     * Gửi tin nhắn nhóm - trả về Message đã gửi
     */
    public Message sendGroupMessage(String groupId, String content) {
        if (!connected) {
            if (onError != null) onError.accept("Chưa kết nối tới server!");
            return null;
        }

        Message msg = Message.createGroupMessage(username, groupId, content);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
            return msg;
        } catch (IOException e) {
            if (onError != null) onError.accept("Gửi tin nhắn nhóm thất bại: " + e.getMessage());
            handleConnectionLost();
            return null;
        }
    }

    /**
     * Gửi một Message đã được tạo sẵn (có metadata reply/forward)
     */
    public boolean sendRawMessage(Message msg) {
        if (!connected) {
            if (onError != null) onError.accept("Chưa kết nối tới server!");
            return false;
        }
        try {
            Protocol.sendMessage(bootstrapOut, msg);
            return true;
        } catch (IOException e) {
            if (onError != null) onError.accept("Gửi tin nhắn thất bại: " + e.getMessage());
            handleConnectionLost();
            return false;
        }
    }

    /**
     * Gửi typing indicator
     */
    public void sendTyping(String to, boolean isTyping) {
        if (!connected) return;
        Message msg = Message.createTyping(username, to, isTyping);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            // Ignore typing errors
        }
    }

    /**
     * Gửi read receipt
     */
    public void sendReadReceipt(String to, String messageId) {
        if (!connected) return;
        Message msg = Message.createReadReceipt(username, to, messageId);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            // Ignore
        }
    }

    // ==================== FRIEND OPERATIONS ====================

    public void sendFriendRequest(String toUser) {
        if (!connected) return;
        Message msg = Message.createFriendRequest(username, toUser);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Gửi lời mời kết bạn thất bại!");
        }
    }

    public void acceptFriendRequest(String fromUser) {
        if (!connected) return;
        Message msg = Message.createFriendAccept(username, fromUser);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Chấp nhận lời mời thất bại!");
        }
    }

    public void rejectFriendRequest(String fromUser) {
        if (!connected) return;
        Message msg = Message.createFriendReject(username, fromUser);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Từ chối lời mời thất bại!");
        }
    }

    public void searchUser(String query) {
        if (!connected) return;
        Message msg = Message.createSearchUser(username, query);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Tìm kiếm thất bại!");
        }
    }

    public void requestFriendList() {
        if (!connected) return;
        Message msg = new Message(Constants.TYPE_FRIEND_LIST, username, "SERVER");
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            // Ignore
        }
    }

    public void unfriend(String targetUser) {
        if (!connected) return;
        Message msg = Message.createFriendRemove(username, targetUser);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Hủy kết bạn thất bại!");
        }
    }

    // ==================== GROUP OPERATIONS ====================

    public void createGroup(String groupName) {
        if (!connected) return;
        Message msg = Message.createGroupCreate(username, groupName);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Tạo nhóm thất bại!");
        }
    }

    public void createGroupWithMembers(String groupName, String[] members) {
        if (!connected) return;
        Message msg = Message.createGroupCreateWithMembers(username, groupName, members);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Tạo nhóm thất bại!");
        }
    }

    public void deleteGroup(String groupId) {
        if (!connected) return;
        Message msg = Message.createGroupDelete(username, groupId);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Xóa nhóm thất bại!");
        }
    }

    public void removeGroupMember(String groupId, String targetUser) {
        if (!connected) return;
        Message msg = Message.createGroupRemoveMember(username, groupId, targetUser);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Xóa thành viên thất bại!");
        }
    }

    public void requestGroupMembers(String groupId) {
        if (!connected) return;
        Message msg = new Message(Constants.TYPE_GROUP_MEMBERS, username, "SERVER");
        msg.getPayload().addProperty("groupId", groupId);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Lấy danh sách thành viên thất bại!");
        }
    }

    public void joinGroup(String groupId) {
        if (!connected) return;
        Message msg = Message.createGroupJoin(username, groupId);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Tham gia nhóm thất bại!");
        }
    }

    public void leaveGroup(String groupId) {
        if (!connected) return;
        Message msg = Message.createGroupLeave(username, groupId);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Rời nhóm thất bại!");
        }
    }



    // ==================== FILE TRANSFER ====================

    public void sendFileInit(String to, String fileName, long fileSize, String fileHash, String transferId) {
        if (!connected) return;
        Message msg = Message.createFileInit(username, to, fileName, fileSize, fileHash, transferId);
        try {
            Protocol.sendMessage(bootstrapOut, msg);
        } catch (IOException e) {
            if (onError != null) onError.accept("Gửi file thất bại!");
        }
    }

    // ==================== BOOTSTRAP LISTENER ====================

    /**
     * Lắng nghe messages từ Bootstrap Server
     */
    private void startListeningBootstrap() {
        bootstrapListenerThread = new Thread(() -> {
            try {
                while (connected && !bootstrapSocket.isClosed()) {
                    Message message = Protocol.readMessage(bootstrapSocket);
                    handleBootstrapMessage(message);
                }
            } catch (IOException e) {
                if (connected) {
                    System.out.println("[Peer] Lost connection to Bootstrap Server");
                    handleConnectionLost();
                }
            }
        }, "BootstrapListener");
        bootstrapListenerThread.setDaemon(true);
        bootstrapListenerThread.start();
    }

    /**
     * Xử lý message từ Bootstrap Server
     */
    private void handleBootstrapMessage(Message message) {
        switch (message.getType()) {
            case Constants.TYPE_PEER_LIST -> handlePeerList(message);
            case Constants.TYPE_PEER_STATUS -> handlePeerStatus(message);
            case Constants.TYPE_DIRECT_MSG -> {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(message);
                } else {
                    pendingMessages.add(message);
                }
            }
            case Constants.TYPE_GROUP_MSG -> {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(message);
                } else {
                    pendingMessages.add(message);
                }
            }
            case Constants.TYPE_GROUP_LIST -> handleGroupList(message);
            case Constants.TYPE_GROUP_CREATE_RESPONSE -> {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(message);
                } else {
                    pendingMessages.add(message);
                }
            }
            case Constants.TYPE_GROUP_MEMBERS -> {
                if (onGroupMembersReceived != null) onGroupMembersReceived.accept(message);
            }
            case Constants.TYPE_TYPING -> {
                if (onTypingReceived != null) onTypingReceived.accept(message);
            }
            case Constants.TYPE_READ_RECEIPT -> {
                if (onReadReceiptReceived != null) onReadReceiptReceived.accept(message);
            }
            case Constants.TYPE_FILE_INIT -> {
                if (onFileInitReceived != null) onFileInitReceived.accept(message);
            }
            case Constants.TYPE_KEY_EXCHANGE -> {
                // Store received public key
                if (onMessageReceived != null) onMessageReceived.accept(message);
            }
            case Constants.TYPE_FRIEND_LIST -> handleFriendList(message);
            case Constants.TYPE_FRIEND_REQUEST -> {
                if (onFriendRequestReceived != null) onFriendRequestReceived.accept(message);
            }
            case Constants.TYPE_FRIEND_ACCEPT -> {
                // Refresh friend list
                requestFriendList();
                if (onFriendListUpdated != null) onFriendListUpdated.accept(message);
                if (onFriendAcceptReceived != null) onFriendAcceptReceived.accept(message);
            }
            case Constants.TYPE_FRIEND_REJECT -> {
                if (onFriendRejectReceived != null) onFriendRejectReceived.accept(message);
            }
            case Constants.TYPE_FRIEND_REQUEST_LIST -> handleFriendRequestList(message);
            case Constants.TYPE_SEARCH_RESULT -> {
                if (onSearchResultReceived != null) onSearchResultReceived.accept(message);
            }
            case Constants.TYPE_ERROR -> {
                String error = message.getPayloadString("error");
                if (onError != null) onError.accept(error);
            }
            default -> System.out.println("[Peer] Unhandled message type: " + message.getType());
        }
    }

    private void handlePeerList(Message message) {
        onlinePeers.clear();
        JsonArray peers = message.getPayload().getAsJsonArray("peers");
        if (peers != null) {
            for (JsonElement element : peers) {
                JsonObject peer = element.getAsJsonObject();
                String peerUsername = peer.get("username").getAsString();
                if (!peerUsername.equals(username)) {
                    onlinePeers.put(peerUsername, peer);
                }
            }
        }
        if (onPeerStatusChanged != null) onPeerStatusChanged.accept(message);
    }

    private void handlePeerStatus(Message message) {
        String peerUsername = message.getFrom();
        String status = message.getPayloadString("status");

        if (Constants.STATUS_ONLINE.equals(status)) {
            JsonObject peer = new JsonObject();
            peer.addProperty("username", peerUsername);
            peer.addProperty("ip", message.getPayloadString("ip"));
            peer.addProperty("port", message.getPayloadInt("port"));
            peer.addProperty("status", status);
            onlinePeers.put(peerUsername, peer);
        } else {
            onlinePeers.remove(peerUsername);
        }

        if (onPeerStatusChanged != null) onPeerStatusChanged.accept(message);
    }

    private void handleGroupList(Message message) {
        groups.clear();
        JsonArray groupsArray = message.getPayload().getAsJsonArray("groups");
        if (groupsArray != null) {
            for (JsonElement element : groupsArray) {
                JsonObject group = element.getAsJsonObject();
                groups.put(group.get("groupId").getAsString(), group);
            }
        }
        if (onGroupListUpdated != null) onGroupListUpdated.accept(message);
    }

    private void handleFriendList(Message message) {
        friends.clear();
        JsonArray friendsArray = message.getPayload().getAsJsonArray("friends");
        if (friendsArray != null) {
            for (JsonElement element : friendsArray) {
                JsonObject friend = element.getAsJsonObject();
                friends.put(friend.get("username").getAsString(), friend);
            }
        }
        if (onFriendListUpdated != null) onFriendListUpdated.accept(message);
    }

    private void handleFriendRequestList(Message message) {
        pendingFriendRequests.clear();
        JsonArray requestsArray = message.getPayload().getAsJsonArray("requests");
        if (requestsArray != null) {
            for (JsonElement element : requestsArray) {
                pendingFriendRequests.add(element.getAsJsonObject());
            }
        }
        if (onFriendRequestListUpdated != null) onFriendRequestListUpdated.accept(message);
    }

    // ==================== HEARTBEAT ====================

    private void startHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (connected) {
                try {
                    Message heartbeat = Message.createHeartbeat(username);
                    Protocol.sendMessage(bootstrapOut, heartbeat);
                } catch (IOException e) {
                    System.err.println("[Peer] Heartbeat failed");
                    handleConnectionLost();
                }
            }
        }, Constants.HEARTBEAT_INTERVAL, Constants.HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void handleConnectionLost() {
        connected = false;
        if (onConnectionChanged != null) onConnectionChanged.accept(false);
        if (onError != null) onError.accept("Mất kết nối tới server!");
    }

    // ==================== GETTERS ====================

    public String getUsername() { return username; }
    public boolean isConnected() { return connected; }
    public Map<String, JsonObject> getOnlinePeers() { return Collections.unmodifiableMap(onlinePeers); }
    public Map<String, JsonObject> getGroups() { return Collections.unmodifiableMap(groups); }
    public Map<String, JsonObject> getFriends() { return Collections.unmodifiableMap(friends); }
    public List<JsonObject> getPendingFriendRequests() { return Collections.unmodifiableList(pendingFriendRequests); }

    public List<JsonObject> getJoinedGroups() {
        List<JsonObject> joined = new ArrayList<>();
        for (JsonObject group : groups.values()) {
            if (group.has("joined") && group.get("joined").getAsBoolean()) {
                joined.add(group);
            }
        }
        return joined;
    }

    public List<JsonObject> getAvailableGroups() {
        List<JsonObject> available = new ArrayList<>();
        for (JsonObject group : groups.values()) {
            if (!group.has("joined") || !group.get("joined").getAsBoolean()) {
                available.add(group);
            }
        }
        return available;
    }

    // ==================== CALLBACKS ====================

    public void setOnMessageReceived(Consumer<Message> callback) {
        this.onMessageReceived = callback;
        if (callback != null) {
            Message msg;
            while ((msg = pendingMessages.poll()) != null) {
                callback.accept(msg);
            }
        }
    }
    public void setOnPeerStatusChanged(Consumer<Message> callback) { this.onPeerStatusChanged = callback; }
    public void setOnGroupListUpdated(Consumer<Message> callback) { this.onGroupListUpdated = callback; }
    public void setOnGroupMembersReceived(Consumer<Message> callback) { this.onGroupMembersReceived = callback; }
    public void setOnTypingReceived(Consumer<Message> callback) { this.onTypingReceived = callback; }
    public void setOnReadReceiptReceived(Consumer<Message> callback) { this.onReadReceiptReceived = callback; }
    public void setOnFileInitReceived(Consumer<Message> callback) { this.onFileInitReceived = callback; }
    public void setOnFriendListUpdated(Consumer<Message> callback) { this.onFriendListUpdated = callback; }
    public void setOnFriendRequestReceived(Consumer<Message> callback) { this.onFriendRequestReceived = callback; }
    public void setOnFriendRequestListUpdated(Consumer<Message> callback) { this.onFriendRequestListUpdated = callback; }
    public void setOnFriendAcceptReceived(Consumer<Message> callback) { this.onFriendAcceptReceived = callback; }
    public void setOnFriendRejectReceived(Consumer<Message> callback) { this.onFriendRejectReceived = callback; }
    public void setOnSearchResultReceived(Consumer<Message> callback) { this.onSearchResultReceived = callback; }
    public void setOnError(Consumer<String> callback) { this.onError = callback; }
    public void setOnConnectionChanged(Consumer<Boolean> callback) { this.onConnectionChanged = callback; }

    // ==================== UTILITY ====================

    private String getLocalIp() {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("8.8.8.8", 53), 1000);
            String ip = s.getLocalAddress().getHostAddress();
            s.close();
            return ip;
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) {
                return "127.0.0.1";
            }
        }
    }

    /**
     * Lấy lịch sử chat từ DB
     */
    public List<Message> getChatHistory(String otherUser, int limit) {
        return DatabaseManager.getMessageHistory(username, otherUser, limit);
    }

    public List<Message> getGroupChatHistory(String groupId, int limit) {
        return DatabaseManager.getGroupMessages(groupId, limit);
    }
}
