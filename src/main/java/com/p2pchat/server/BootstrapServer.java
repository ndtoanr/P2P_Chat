package com.p2pchat.server;

import com.p2pchat.shared.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Bootstrap Server - Server trung tâm quản lý peer discovery,
 * đăng ký/đăng nhập, và store-and-forward cho offline messages
 */
public class BootstrapServer {

    private ServerSocket serverSocket;
    private final int port;
    private final PeerRegistry peerRegistry;
    private final ExecutorService threadPool;
    private volatile boolean running = false;

    // Heartbeat monitor
    private ScheduledExecutorService heartbeatScheduler;

    public BootstrapServer(int port) {
        this.port = port;
        this.peerRegistry = new PeerRegistry();
        this.threadPool = Executors.newCachedThreadPool();
    }

    /**
     * Khởi động Bootstrap Server
     */
    public void start() {
        try {
            // Khởi tạo database
            DatabaseManager.initialize();

            serverSocket = new ServerSocket(port);
            running = true;

            System.out.println("╔══════════════════════════════════════════╗");
            System.out.println("║     P2P CHAT - BOOTSTRAP SERVER         ║");
            System.out.println("║     Port: " + port + "                           ║");
            System.out.println("║     Status: RUNNING                     ║");
            System.out.println("╚══════════════════════════════════════════╝");

            // Khởi động heartbeat monitor
            startHeartbeatMonitor();

            // Accept connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[Server] New connection from: " + clientSocket.getRemoteSocketAddress());
                    threadPool.execute(new ClientHandler(clientSocket, peerRegistry));
                } catch (SocketException e) {
                    if (running) {
                        System.err.println("[Server] Accept error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[Server] Failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Monitor heartbeat - kiểm tra peer timeout mỗi 15s
     */
    private void startHeartbeatMonitor() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                List<String> timedOut = peerRegistry.checkTimeouts();
                for (String username : timedOut) {
                    System.out.println("[Server] Peer timed out: " + username);
                    DatabaseManager.updateUserStatus(username, Constants.STATUS_OFFLINE);
                    DatabaseManager.unregisterPeer(username);

                    // Thông báo cho các peer khác
                    Message statusMsg = Message.createPeerStatus(username, Constants.STATUS_OFFLINE, "", 0);
                    peerRegistry.broadcastToAll(statusMsg, username);
                }
            } catch (Exception e) {
                System.err.println("[Server] Heartbeat monitor error: " + e.getMessage());
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    /**
     * Dừng server
     */
    public void stop() {
        running = false;
        try {
            if (heartbeatScheduler != null) {
                heartbeatScheduler.shutdown();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
            threadPool.shutdown();
            DatabaseManager.close();
            System.out.println("[Server] Bootstrap Server stopped");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main - Chạy Bootstrap Server
     */
    public static void main(String[] args) {
        int port = Constants.BOOTSTRAP_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port, using default: " + Constants.BOOTSTRAP_PORT);
            }
        }

        BootstrapServer server = new BootstrapServer(port);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }
}
