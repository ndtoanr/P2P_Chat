package com.p2pchat.server;

import com.p2pchat.shared.*;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý registry các peer đang online
 */
public class PeerRegistry {

    /**
     * Thông tin một peer
     */
    public static class PeerInfo {
        public String username;
        public String ip;
        public int port;
        public Socket socket;
        public OutputStream outputStream;
        public long lastHeartbeat;
        public String status;

        public PeerInfo(String username, String ip, int port, Socket socket) throws IOException {
            this.username = username;
            this.ip = ip;
            this.port = port;
            this.socket = socket;
            this.outputStream = socket.getOutputStream();
            this.lastHeartbeat = System.currentTimeMillis();
            this.status = Constants.STATUS_ONLINE;
        }
    }

    // Map username -> PeerInfo
    private final ConcurrentHashMap<String, PeerInfo> peers = new ConcurrentHashMap<>();

    /**
     * Đăng ký peer mới
     */
    public void registerPeer(String username, String ip, int port, Socket socket) throws IOException {
        // Nếu peer đã tồn tại, đóng kết nối cũ
        PeerInfo existing = peers.get(username);
        if (existing != null) {
            try {
                existing.socket.close();
            } catch (IOException ignored) {}
        }

        PeerInfo info = new PeerInfo(username, ip, port, socket);
        peers.put(username, info);
        System.out.println("[Registry] Peer registered: " + username + " (" + ip + ":" + port + ")");
    }

    /**
     * Xóa peer
     */
    public void unregisterPeer(String username) {
        PeerInfo info = peers.remove(username);
        if (info != null) {
            try {
                info.socket.close();
            } catch (IOException ignored) {}
            System.out.println("[Registry] Peer unregistered: " + username);
        }
    }

    /**
     * Kiểm tra peer có online không
     */
    public boolean isPeerOnline(String username) {
        PeerInfo info = peers.get(username);
        return info != null && !info.socket.isClosed();
    }

    /**
     * Lấy thông tin peer
     */
    public PeerInfo getPeer(String username) {
        return peers.get(username);
    }

    /**
     * Cập nhật heartbeat
     */
    public void updateHeartbeat(String username) {
        PeerInfo info = peers.get(username);
        if (info != null) {
            info.lastHeartbeat = System.currentTimeMillis();
        }
    }

    /**
     * Gửi message tới một peer
     */
    public boolean sendToPeer(String username, Message message) {
        PeerInfo info = peers.get(username);
        if (info != null && !info.socket.isClosed()) {
            try {
                Protocol.sendMessage(info.outputStream, message);
                return true;
            } catch (IOException e) {
                System.err.println("[Registry] Failed to send to " + username + ": " + e.getMessage());
                unregisterPeer(username);
            }
        }
        return false;
    }

    /**
     * Broadcast message tới tất cả peer (trừ excludeUser)
     */
    public void broadcastToAll(Message message, String excludeUser) {
        for (Map.Entry<String, PeerInfo> entry : peers.entrySet()) {
            if (!entry.getKey().equals(excludeUser)) {
                sendToPeer(entry.getKey(), message);
            }
        }
    }

    /**
     * Lấy danh sách peer online (dùng cho PEER_LIST response)
     */
    public List<Map<String, Object>> getOnlinePeerList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, PeerInfo> entry : peers.entrySet()) {
            PeerInfo info = entry.getValue();
            if (!info.socket.isClosed()) {
                Map<String, Object> peerMap = new HashMap<>();
                peerMap.put("username", info.username);
                peerMap.put("ip", info.ip);
                peerMap.put("port", info.port);
                peerMap.put("status", info.status);
                list.add(peerMap);
            }
        }
        return list;
    }

    /**
     * Kiểm tra timeout - trả về danh sách username bị timeout
     */
    public List<String> checkTimeouts() {
        List<String> timedOut = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, PeerInfo> entry : peers.entrySet()) {
            if (now - entry.getValue().lastHeartbeat > Constants.HEARTBEAT_TIMEOUT) {
                timedOut.add(entry.getKey());
            }
        }
        // Remove timed out peers
        for (String username : timedOut) {
            unregisterPeer(username);
        }
        return timedOut;
    }

    /**
     * Số lượng peer online
     */
    public int getOnlineCount() {
        return peers.size();
    }
}
