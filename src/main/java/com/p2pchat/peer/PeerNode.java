package com.p2pchat.peer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.p2pchat.shared.*;

import java.io.*;
import java.net.*;
import java.sql.*;
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

    // Peer Server (P2P)
    private ServerSocket peerServerSocket;
    private int listeningPort = 0;
    private Thread peerServerThread;

    // Heartbeat & DB Sync
    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledExecutorService directDbSyncScheduler;

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
            startPeerServer();
            connectToBootstrap();

            // Gửi REGISTER
            String localIp = getLocalIp();
            Message registerMsg = Message.createRegister(username, password, localIp, listeningPort);
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
            System.out.println("[Peer] Bootstrap Server connection failed. Attempting Direct Database Registration...");
            try {
                DatabaseManager.initialize();
                if (DatabaseManager.registerUser(username, password)) {
                    connected = false;
                    String localIp = getLocalIp();
                    DatabaseManager.registerPeer(username, localIp, listeningPort);
                    DatabaseManager.updateUserStatus(username, Constants.STATUS_ONLINE);
                    
                    loadFriendsFromDB();
                    loadOnlinePeersFromDB();
                    loadGroupsFromDB();
                    loadPendingFriendRequestsFromDB();
                    startDirectDbPeerSync();

                    if (onConnectionChanged != null) onConnectionChanged.accept(false);
                    return true;
                } else {
                    if (onError != null) onError.accept("Đăng ký thất bại: Tên đăng nhập đã tồn tại.");
                    return false;
                }
            } catch (Exception dbEx) {
                if (onError != null) onError.accept("Không thể kết nối tới Server và Database: " + dbEx.getMessage());
                return false;
            }
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
            startPeerServer();
            connectToBootstrap();

            String localIp = getLocalIp();
            Message loginMsg = Message.createLogin(username, password, localIp, listeningPort);
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
            System.out.println("[Peer] Bootstrap Server connection failed. Attempting Direct Database Authentication...");
            try {
                DatabaseManager.initialize();
                if (DatabaseManager.authenticateUser(username, password)) {
                    connected = false;
                    String localIp = getLocalIp();
                    DatabaseManager.registerPeer(username, localIp, listeningPort);
                    DatabaseManager.updateUserStatus(username, Constants.STATUS_ONLINE);
                    
                    loadFriendsFromDB();
                    loadOnlinePeersFromDB();
                    loadGroupsFromDB();
                    loadPendingFriendRequestsFromDB();
                    startDirectDbPeerSync();

                    if (onConnectionChanged != null) onConnectionChanged.accept(false);
                    return true;
                } else {
                    if (onError != null) onError.accept("Đăng nhập thất bại: Sai tài khoản hoặc mật khẩu.");
                    return false;
                }
            } catch (Exception dbEx) {
                if (onError != null) onError.accept("Không thể kết nối tới Server và Database: " + dbEx.getMessage());
                return false;
            }
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
        if (directDbSyncScheduler != null) {
            directDbSyncScheduler.shutdown();
        }

        // Direct DB mode cleanup
        try {
            DatabaseManager.unregisterPeer(username);
            DatabaseManager.updateUserStatus(username, Constants.STATUS_OFFLINE);
        } catch (Exception ignored) {}

        stopPeerServer();
        if (onConnectionChanged != null) onConnectionChanged.accept(false);
        System.out.println("[Peer] Disconnected");
    }

    // ==================== MESSAGING ====================

    /**
     * Gửi tin nhắn trực tiếp - trả về Message đã gửi để GUI dùng đúng messageId
     */
    public Message sendDirectMessage(String to, String content) {
        Message msg = Message.createDirectMessage(username, to, content);
        if (sendToPeerOrFallback(to, msg)) {
            return msg;
        }
        return null;
    }

    /**
     * Gửi tin nhắn nhóm - trả về Message đã gửi
     */
    public Message sendGroupMessage(String groupId, String content) {
        Message msg = Message.createGroupMessage(username, groupId, content);
        if (connected) {
            try {
                Protocol.sendMessage(bootstrapOut, msg);
                return msg;
            } catch (IOException e) {
                if (onError != null) onError.accept("Gửi tin nhắn nhóm thất bại: " + e.getMessage());
                handleConnectionLost();
                return null;
            }
        } else {
            try {
                // Save directly to database
                DatabaseManager.saveMessage(msg);
                // Try sending P2P to all other online members of the group
                List<String> members = DatabaseManager.getGroupMembers(groupId);
                for (String member : members) {
                    if (!member.equals(username)) {
                        sendToPeerOrFallback(member, msg);
                    }
                }
                return msg;
            } catch (Exception e) {
                if (onError != null) onError.accept("Gửi tin nhắn nhóm thất bại: " + e.getMessage());
                return null;
            }
        }
    }

    /**
     * Gửi một Message đã được tạo sẵn (có metadata reply/forward)
     */
    public boolean sendRawMessage(Message msg) {
        // Thử gửi P2P nếu là tin nhắn trực tiếp
        if (msg.getType().equals(Constants.TYPE_DIRECT_MSG)) {
            return sendToPeerOrFallback(msg.getTo(), msg);
        } else {
            if (connected) {
                try {
                    Protocol.sendMessage(bootstrapOut, msg);
                    return true;
                } catch (IOException e) {
                    if (onError != null) onError.accept("Gửi tin nhắn thất bại: " + e.getMessage());
                    handleConnectionLost();
                    return false;
                }
            } else {
                try {
                    DatabaseManager.saveMessage(msg);
                    String groupId = msg.getTo();
                    List<String> members = DatabaseManager.getGroupMembers(groupId);
                    for (String member : members) {
                        if (!member.equals(username)) {
                            sendToPeerOrFallback(member, msg);
                        }
                    }
                    return true;
                } catch (Exception e) {
                    if (onError != null) onError.accept("Gửi tin nhắn thất bại: " + e.getMessage());
                    return false;
                }
            }
        }
    }

    /**
     * Gửi typing indicator
     */
    public void sendTyping(String to, boolean isTyping) {
        Message msg = Message.createTyping(username, to, isTyping);
        sendToPeerOrFallback(to, msg);
    }

    /**
     * Gửi read receipt
     */
    public void sendReadReceipt(String to, String messageId) {
        Message msg = Message.createReadReceipt(username, to, messageId);
        sendToPeerOrFallback(to, msg);
    }

    // ==================== P2P DIRECT CONNECTION ====================

    /**
     * Khởi tạo ServerSocket để lắng nghe kết nối P2P từ các peer khác
     */
    private void startPeerServer() {
        try {
            if (peerServerSocket == null || peerServerSocket.isClosed()) {
                peerServerSocket = new ServerSocket(0); // Cổng ngẫu nhiên
                listeningPort = peerServerSocket.getLocalPort();
                
                peerServerThread = new Thread(() -> {
                    while (peerServerSocket != null && !peerServerSocket.isClosed()) {
                        try {
                            Socket peerSocket = peerServerSocket.accept();
                            new Thread(() -> handleIncomingPeerConnection(peerSocket)).start();
                        } catch (IOException e) {
                            if (!peerServerSocket.isClosed()) {
                                System.err.println("[Peer] Error accepting peer connection: " + e.getMessage());
                            }
                        }
                    }
                }, "PeerServerThread");
                peerServerThread.setDaemon(true);
                peerServerThread.start();
                System.out.println("[Peer] PeerServer started on port " + listeningPort);
            }
        } catch (IOException e) {
            System.err.println("[Peer] Failed to start PeerServer: " + e.getMessage());
        }
    }

    private void stopPeerServer() {
        try {
            if (peerServerSocket != null && !peerServerSocket.isClosed()) {
                peerServerSocket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    private void handleIncomingPeerConnection(Socket peerSocket) {
        try {
            Message message = Protocol.readMessage(peerSocket);
            System.out.println("[Peer] Received P2P Message: " + message.getType() + " from " + message.getFrom());
            handleBootstrapMessage(message);
        } catch (IOException e) {
            System.err.println("[Peer] Error reading from peer socket: " + e.getMessage());
        } finally {
            try {
                peerSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Gửi Message trực tiếp P2P nếu online, ngược lại Fallback gửi qua Server
     */
    private boolean sendToPeerOrFallback(String toUser, Message msg) {
        JsonObject peerInfo = onlinePeers.get(toUser);
        if (peerInfo != null) {
            String ip = peerInfo.get("ip").getAsString();
            int port = peerInfo.get("port").getAsInt();
            boolean p2pSuccess = false;

            // 1. Thử kết nối tới IP được báo cáo
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 2000); // Timeout 2s
                Protocol.sendMessage(socket, msg);
                System.out.println("[Peer] Sent P2P Message to " + toUser + " (" + ip + ":" + port + ")");
                p2pSuccess = true;
            } catch (IOException e) {
                System.out.println("[Peer] P2P connection to " + toUser + " (" + ip + ":" + port + ") failed: " + e.getMessage());
            }

            // 2. Nếu thất bại và IP không phải 127.0.0.1, thử kết nối tới 127.0.0.1 (hỗ trợ test local trên cùng một máy)
            if (!p2pSuccess && !ip.equals("127.0.0.1")) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", port), 2000); // Timeout 2s
                    Protocol.sendMessage(socket, msg);
                    System.out.println("[Peer] Sent P2P Message to " + toUser + " (127.0.0.1:" + port + ") via Local Loopback");
                    p2pSuccess = true;
                } catch (IOException e) {
                    System.out.println("[Peer] P2P loopback connection to " + toUser + " (127.0.0.1:" + port + ") failed too: " + e.getMessage());
                }
            }

            if (p2pSuccess) {
                return true;
            }
        } else {
            System.out.println("[Peer] " + toUser + " is offline or unknown, sending via Server Relay");
        }

        // Fallback
        if (!connected) {
            try {
                DatabaseManager.saveOfflineMessage(msg, toUser);
                System.out.println("[Peer] Direct DB fallback: Saved offline message for " + toUser);
                return true;
            } catch (Exception e) {
                if (onError != null) onError.accept("Lưu tin nhắn offline thất bại: " + e.getMessage());
                return false;
            }
        }
        
        try {
            Protocol.sendMessage(bootstrapOut, msg);
            return true;
        } catch (IOException e) {
            if (onError != null) onError.accept("Gửi tin nhắn thất bại (mất kết nối server): " + e.getMessage());
            handleConnectionLost();
            return false;
        }
    }

    // ==================== FRIEND OPERATIONS ====================

    public void sendFriendRequest(String toUser) {
        if (connected) {
            Message msg = Message.createFriendRequest(username, toUser);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Gửi lời mời kết bạn thất bại!");
            }
        } else {
            try {
                boolean success = DatabaseManager.sendFriendRequest(username, toUser);
                if (success) {
                    System.out.println("[Peer] Direct DB friend request sent to: " + toUser);
                    loadFriendsFromDB();
                    loadPendingFriendRequestsFromDB();
                    // Send callback triggers to GUI
                    if (onFriendRequestListUpdated != null) {
                        onFriendRequestListUpdated.accept(new Message(Constants.TYPE_FRIEND_REQUEST_LIST, "SERVER", username));
                    }
                    if (onFriendListUpdated != null) {
                        onFriendListUpdated.accept(new Message(Constants.TYPE_FRIEND_LIST, "SERVER", username));
                    }
                } else {
                    if (onError != null) onError.accept("Gửi lời mời kết bạn thất bại!");
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Gửi lời mời kết bạn thất bại: " + e.getMessage());
            }
        }
    }

    public void acceptFriendRequest(String fromUser) {
        if (connected) {
            Message msg = Message.createFriendAccept(username, fromUser);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Chấp nhận lời mời thất bại!");
            }
        } else {
            try {
                boolean success = DatabaseManager.acceptFriendRequest(fromUser, username);
                if (success) {
                    System.out.println("[Peer] Direct DB friend request accepted from: " + fromUser);
                    loadFriendsFromDB();
                    loadPendingFriendRequestsFromDB();
                    if (onFriendRequestListUpdated != null) {
                        onFriendRequestListUpdated.accept(new Message(Constants.TYPE_FRIEND_REQUEST_LIST, "SERVER", username));
                    }
                    if (onFriendListUpdated != null) {
                        onFriendListUpdated.accept(new Message(Constants.TYPE_FRIEND_LIST, "SERVER", username));
                    }
                } else {
                    if (onError != null) onError.accept("Chấp nhận lời mời thất bại!");
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Chấp nhận lời mời thất bại: " + e.getMessage());
            }
        }
    }

    public void rejectFriendRequest(String fromUser) {
        if (connected) {
            Message msg = Message.createFriendReject(username, fromUser);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Từ chối lời mời thất bại!");
            }
        } else {
            try {
                boolean success = DatabaseManager.rejectFriendRequest(fromUser, username);
                if (success) {
                    System.out.println("[Peer] Direct DB friend request rejected from: " + fromUser);
                    loadFriendsFromDB();
                    loadPendingFriendRequestsFromDB();
                    if (onFriendRequestListUpdated != null) {
                        onFriendRequestListUpdated.accept(new Message(Constants.TYPE_FRIEND_REQUEST_LIST, "SERVER", username));
                    }
                } else {
                    if (onError != null) onError.accept("Từ chối lời mời thất bại!");
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Từ chối lời mời thất bại: " + e.getMessage());
            }
        }
    }

    public void searchUser(String query) {
        if (connected) {
            Message msg = Message.createSearchUser(username, query);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Tìm kiếm thất bại!");
            }
        } else {
            try {
                List<String> results = DatabaseManager.searchUsers(query, username);
                List<String> friendsList = DatabaseManager.getFriends(username);
                List<String> sentRequests = DatabaseManager.getSentFriendRequests(username);
                List<String> receivedRequests = DatabaseManager.getPendingFriendRequests(username);

                Message response = new Message(Constants.TYPE_SEARCH_RESULT, "SERVER", username);
                JsonArray usersArray = new JsonArray();
                for (String user : results) {
                    JsonObject userObj = new JsonObject();
                    userObj.addProperty("username", user);
                    userObj.addProperty("isFriend", friendsList.contains(user));
                    userObj.addProperty("requestSent", sentRequests.contains(user));
                    userObj.addProperty("requestReceived", receivedRequests.contains(user));
                    userObj.addProperty("isOnline", isPeerOnlineInDb(user));
                    usersArray.add(userObj);
                }
                response.getPayload().add("users", usersArray);
                if (onSearchResultReceived != null) {
                    onSearchResultReceived.accept(response);
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Tìm kiếm thất bại: " + e.getMessage());
            }
        }
    }

    public void requestFriendList() {
        if (connected) {
            Message msg = new Message(Constants.TYPE_FRIEND_LIST, username, "SERVER");
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                // Ignore
            }
        } else {
            loadFriendsFromDB();
            loadPendingFriendRequestsFromDB();
            if (onFriendListUpdated != null) {
                onFriendListUpdated.accept(new Message(Constants.TYPE_FRIEND_LIST, "SERVER", username));
            }
            if (onFriendRequestListUpdated != null) {
                onFriendRequestListUpdated.accept(new Message(Constants.TYPE_FRIEND_REQUEST_LIST, "SERVER", username));
            }
        }
    }

    public void unfriend(String targetUser) {
        if (connected) {
            Message msg = Message.createFriendRemove(username, targetUser);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Hủy kết bạn thất bại!");
            }
        } else {
            try {
                if (DatabaseManager.deleteFriend(username, targetUser)) {
                    loadFriendsFromDB();
                    if (onFriendListUpdated != null) {
                        onFriendListUpdated.accept(new Message(Constants.TYPE_FRIEND_LIST, "SERVER", username));
                    }
                } else {
                    if (onError != null) onError.accept("Hủy kết bạn thất bại!");
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Hủy kết bạn thất bại: " + e.getMessage());
            }
        }
    }

    // ==================== GROUP OPERATIONS ====================

    public void createGroup(String groupName) {
        if (connected) {
            Message msg = Message.createGroupCreate(username, groupName);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Tạo nhóm thất bại!");
            }
        } else {
            try {
                String groupId = DatabaseManager.createGroup(groupName, username);
                if (groupId != null) {
                    loadGroupsFromDB();
                    if (onGroupListUpdated != null) {
                        onGroupListUpdated.accept(new Message(Constants.TYPE_GROUP_LIST, "SERVER", username));
                    }
                } else {
                    if (onError != null) onError.accept("Tạo nhóm thất bại!");
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Tạo nhóm thất bại: " + e.getMessage());
            }
        }
    }

    public void createGroupWithMembers(String groupName, String[] members) {
        if (connected) {
            Message msg = Message.createGroupCreateWithMembers(username, groupName, members);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Tạo nhóm thất bại!");
            }
        } else {
            try {
                String groupId = DatabaseManager.createGroup(groupName, username);
                if (groupId != null) {
                    for (String member : members) {
                        if (!member.equals(username)) {
                            DatabaseManager.addGroupMember(groupId, member, "MEMBER");
                        }
                    }
                    loadGroupsFromDB();
                    if (onGroupListUpdated != null) {
                        onGroupListUpdated.accept(new Message(Constants.TYPE_GROUP_LIST, "SERVER", username));
                    }
                } else {
                    if (onError != null) onError.accept("Tạo nhóm thất bại!");
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Tạo nhóm thất bại: " + e.getMessage());
            }
        }
    }

    public void deleteGroup(String groupId) {
        if (connected) {
            Message msg = Message.createGroupDelete(username, groupId);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Xóa nhóm thất bại!");
            }
        } else {
            try {
                if (DatabaseManager.isGroupAdmin(groupId, username)) {
                    if (DatabaseManager.deleteGroup(groupId)) {
                        loadGroupsFromDB();
                        if (onGroupListUpdated != null) {
                            onGroupListUpdated.accept(new Message(Constants.TYPE_GROUP_LIST, "SERVER", username));
                        }
                    } else {
                        if (onError != null) onError.accept("Xóa nhóm thất bại!");
                    }
                } else {
                    if (onError != null) onError.accept("Chỉ trưởng nhóm mới có quyền xóa nhóm!");
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Xóa nhóm thất bại: " + e.getMessage());
            }
        }
    }

    public void removeGroupMember(String groupId, String targetUser) {
        if (connected) {
            Message msg = Message.createGroupRemoveMember(username, groupId, targetUser);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Xóa thành viên thất bại!");
            }
        } else {
            try {
                if (DatabaseManager.isGroupAdmin(groupId, username)) {
                    DatabaseManager.removeGroupMember(groupId, targetUser);
                    requestGroupMembers(groupId);
                } else {
                    if (onError != null) onError.accept("Bạn không có quyền xóa thành viên!");
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Xóa thành viên thất bại: " + e.getMessage());
            }
        }
    }

    public void requestGroupMembers(String groupId) {
        if (connected) {
            Message msg = new Message(Constants.TYPE_GROUP_MEMBERS, username, "SERVER");
            msg.getPayload().addProperty("groupId", groupId);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Lấy danh sách thành viên thất bại!");
            }
        } else {
            try {
                List<String> members = DatabaseManager.getGroupMembers(groupId);
                Message response = new Message(Constants.TYPE_GROUP_MEMBERS, "SERVER", username);
                response.getPayload().addProperty("groupId", groupId);
                JsonArray membersArray = new JsonArray();
                for (String member : members) {
                    membersArray.add(member);
                }
                response.getPayload().add("members", membersArray);
                if (onGroupMembersReceived != null) {
                    onGroupMembersReceived.accept(response);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void joinGroup(String groupId) {
        if (connected) {
            Message msg = Message.createGroupJoin(username, groupId);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Tham gia nhóm thất bại!");
            }
        } else {
            try {
                DatabaseManager.addGroupMember(groupId, username, "MEMBER");
                loadGroupsFromDB();
                if (onGroupListUpdated != null) {
                    onGroupListUpdated.accept(new Message(Constants.TYPE_GROUP_LIST, "SERVER", username));
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Tham gia nhóm thất bại: " + e.getMessage());
            }
        }
    }

    public void leaveGroup(String groupId) {
        if (connected) {
            Message msg = Message.createGroupLeave(username, groupId);
            try {
                Protocol.sendMessage(bootstrapOut, msg);
            } catch (IOException e) {
                if (onError != null) onError.accept("Rời nhóm thất bại!");
            }
        } else {
            try {
                DatabaseManager.removeGroupMember(groupId, username);
                loadGroupsFromDB();
                if (onGroupListUpdated != null) {
                    onGroupListUpdated.accept(new Message(Constants.TYPE_GROUP_LIST, "SERVER", username));
                }
            } catch (Exception e) {
                if (onError != null) onError.accept("Rời nhóm thất bại: " + e.getMessage());
            }
        }
    }



    // ==================== FILE TRANSFER ====================

    public void sendFileInit(String to, String fileName, long fileSize, String fileHash, String transferId) {
        Message msg = Message.createFileInit(username, to, fileName, fileSize, fileHash, transferId);
        if (!sendToPeerOrFallback(to, msg)) {
            if (onError != null) onError.accept("Gửi yêu cầu chuyển file thất bại!");
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

    // ==================== DIRECT DATABASE MODE HELPERS ====================

    private void loadFriendsFromDB() {
        friends.clear();
        try {
            List<String> friendUsernames = DatabaseManager.getFriends(username);
            for (String friendName : friendUsernames) {
                JsonObject friendObj = new JsonObject();
                friendObj.addProperty("username", friendName);
                boolean isOnline = isPeerOnlineInDb(friendName);
                friendObj.addProperty("isOnline", isOnline);
                friends.put(friendName, friendObj);
            }
        } catch (Exception e) {
            System.err.println("[Peer] Error loading friends from DB: " + e.getMessage());
        }
    }

    private void loadOnlinePeersFromDB() {
        onlinePeers.clear();
        String sql = "SELECT username, ip_address, tcp_port FROM peer_registry WHERE is_online = TRUE AND last_heartbeat >= CURRENT_TIMESTAMP - INTERVAL 30 SECOND";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String peerName = rs.getString("username");
                if (!peerName.equals(username)) {
                    JsonObject peerObj = new JsonObject();
                    peerObj.addProperty("username", peerName);
                    peerObj.addProperty("ip", rs.getString("ip_address"));
                    peerObj.addProperty("port", rs.getInt("tcp_port"));
                    peerObj.addProperty("status", Constants.STATUS_ONLINE);
                    onlinePeers.put(peerName, peerObj);
                }
            }
        } catch (SQLException e) {
            System.err.println("[Peer] Error loading online peers from DB: " + e.getMessage());
        }
    }

    private boolean isPeerOnlineInDb(String peerUsername) {
        String sql = "SELECT COUNT(*) FROM peer_registry WHERE username = ? AND is_online = TRUE AND last_heartbeat >= CURRENT_TIMESTAMP - INTERVAL 30 SECOND";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, peerUsername);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            // Ignore
        }
        return false;
    }

    private void loadGroupsFromDB() {
        groups.clear();
        try {
            List<String[]> allGroups = DatabaseManager.getAllGroups();
            List<String[]> userGroups = DatabaseManager.getUserGroups(username);
            Set<String> joinedGroupIds = new HashSet<>();
            for (String[] g : userGroups) {
                joinedGroupIds.add(g[0]);
            }
            for (String[] group : allGroups) {
                JsonObject groupObj = new JsonObject();
                groupObj.addProperty("groupId", group[0]);
                groupObj.addProperty("groupName", group[1]);
                groupObj.addProperty("createdBy", group[2]);
                groupObj.addProperty("joined", joinedGroupIds.contains(group[0]));
                groups.put(group[0], groupObj);
            }
        } catch (Exception e) {
            System.err.println("[Peer] Error loading groups from DB: " + e.getMessage());
        }
    }

    private void loadPendingFriendRequestsFromDB() {
        pendingFriendRequests.clear();
        try {
            List<String> pending = DatabaseManager.getPendingFriendRequests(username);
            for (String requester : pending) {
                JsonObject reqObj = new JsonObject();
                reqObj.addProperty("username", requester);
                reqObj.addProperty("isOnline", isPeerOnlineInDb(requester));
                pendingFriendRequests.add(reqObj);
            }
        } catch (Exception e) {
            System.err.println("[Peer] Error loading pending friend requests: " + e.getMessage());
        }
    }

    private void fetchOfflineMessagesFromDb() {
        try {
            List<Message> offlineMsgs = DatabaseManager.getOfflineMessages(username);
            if (offlineMsgs != null && !offlineMsgs.isEmpty()) {
                System.out.println("[Peer] Fetched " + offlineMsgs.size() + " offline messages from DB");
                for (Message msg : offlineMsgs) {
                    msg.getPayload().addProperty("isOfflinePending", true);
                    if (onMessageReceived != null) {
                        onMessageReceived.accept(msg);
                    } else {
                        pendingMessages.offer(msg);
                    }
                }
                DatabaseManager.deleteOfflineMessages(username);
            }
        } catch (Exception e) {
            System.err.println("[Peer] Error fetching offline messages from DB: " + e.getMessage());
        }
    }

    private void startDirectDbPeerSync() {
        // Fetch offline messages immediately on login
        fetchOfflineMessagesFromDb();
        
        directDbSyncScheduler = Executors.newSingleThreadScheduledExecutor();
        directDbSyncScheduler.scheduleAtFixedRate(() -> {
            try {
                // Update heartbeat in DB
                DatabaseManager.updateHeartbeat(username);
                
                // Fetch offline messages
                fetchOfflineMessagesFromDb();
                
                // Reload collections
                loadFriendsFromDB();
                loadOnlinePeersFromDB();
                loadGroupsFromDB();
                loadPendingFriendRequestsFromDB();

                // Notify UI components
                if (onFriendListUpdated != null) {
                    onFriendListUpdated.accept(new Message(Constants.TYPE_FRIEND_LIST, "SERVER", username));
                }
                if (onPeerStatusChanged != null) {
                    onPeerStatusChanged.accept(new Message(Constants.TYPE_PEER_STATUS, "SERVER", username));
                }
                if (onGroupListUpdated != null) {
                    onGroupListUpdated.accept(new Message(Constants.TYPE_GROUP_LIST, "SERVER", username));
                }
                if (onFriendRequestListUpdated != null) {
                    onFriendRequestListUpdated.accept(new Message(Constants.TYPE_FRIEND_REQUEST_LIST, "SERVER", username));
                }
            } catch (Exception e) {
                System.err.println("[Peer] Error in direct DB sync: " + e.getMessage());
            }
        }, 0, 5000, TimeUnit.MILLISECONDS);
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
