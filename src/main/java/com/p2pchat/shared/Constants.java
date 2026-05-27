package com.p2pchat.shared;

/**
 * Hằng số dùng chung cho toàn hệ thống P2P Chat
 */
public final class Constants {

    private Constants() {} // Prevent instantiation

    // ==================== SERVER CONFIG ====================
    public static final String BOOTSTRAP_HOST = " 172.20.10.2";
    public static final int BOOTSTRAP_PORT = 9000;

    // ==================== DATABASE CONFIG ====================
    public static final String DB_URL = "jdbc:mysql:// 172.20.10.2:3306/p2p_chat?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    public static final String DB_USER = "root";
    public static final String DB_PASSWORD = "123456";
    public static final int DB_POOL_SIZE = 10;

    // ==================== NETWORK CONFIG ====================
    public static final int BUFFER_SIZE = 8192;
    public static final int HEADER_SIZE = 4; // 4 bytes for message length prefix
    public static final int MAX_MESSAGE_SIZE = 10 * 1024 * 1024; // 10MB max message

    // ==================== TIMING CONFIG ====================
    public static final int HEARTBEAT_INTERVAL = 10_000; // 10 seconds
    public static final int HEARTBEAT_TIMEOUT = 35_000;  // 35 seconds
    public static final int CONNECTION_TIMEOUT = 5_000;   // 5 seconds
    public static final int ACK_TIMEOUT = 5_000;          // 5 seconds
    public static final int MAX_RETRY = 3;

    // ==================== FILE TRANSFER ====================
    public static final int FILE_CHUNK_SIZE = 64 * 1024; // 64KB per chunk
    public static final String FILE_DOWNLOAD_DIR = "downloads";

    // ==================== ENCRYPTION ====================
    public static final String RSA_ALGORITHM = "RSA";
    public static final int RSA_KEY_SIZE = 2048;
    public static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    public static final int AES_KEY_SIZE = 256;
    public static final int GCM_TAG_LENGTH = 128;
    public static final int GCM_IV_LENGTH = 12;

    // ==================== MESSAGE TYPES ====================
    public static final String TYPE_REGISTER = "REGISTER";
    public static final String TYPE_LOGIN = "LOGIN";
    public static final String TYPE_LOGIN_RESPONSE = "LOGIN_RESPONSE";
    public static final String TYPE_REGISTER_RESPONSE = "REGISTER_RESPONSE";
    public static final String TYPE_PEER_LIST = "PEER_LIST";
    public static final String TYPE_HEARTBEAT = "HEARTBEAT";
    public static final String TYPE_DIRECT_MSG = "DIRECT_MSG";
    public static final String TYPE_GROUP_MSG = "GROUP_MSG";
    public static final String TYPE_GROUP_CREATE = "GROUP_CREATE";
    public static final String TYPE_GROUP_CREATE_RESPONSE = "GROUP_CREATE_RESPONSE";
    public static final String TYPE_GROUP_JOIN = "GROUP_JOIN";
    public static final String TYPE_GROUP_LEAVE = "GROUP_LEAVE";
    public static final String TYPE_GROUP_LIST = "GROUP_LIST";
    public static final String TYPE_GROUP_MEMBERS = "GROUP_MEMBERS";
    public static final String TYPE_FILE_INIT = "FILE_INIT";
    public static final String TYPE_FILE_CHUNK = "FILE_CHUNK";
    public static final String TYPE_FILE_COMPLETE = "FILE_COMPLETE";
    public static final String TYPE_FILE_ACCEPT = "FILE_ACCEPT";
    public static final String TYPE_FILE_REJECT = "FILE_REJECT";
    public static final String TYPE_BROADCAST = "BROADCAST";
    public static final String TYPE_ACK = "ACK";
    public static final String TYPE_PEER_STATUS = "PEER_STATUS";
    public static final String TYPE_STORE_FORWARD = "STORE_FORWARD";
    public static final String TYPE_KEY_EXCHANGE = "KEY_EXCHANGE";
    public static final String TYPE_ENCRYPTED_MSG = "ENCRYPTED_MSG";
    public static final String TYPE_TYPING = "TYPING";
    public static final String TYPE_READ_RECEIPT = "READ_RECEIPT";
    public static final String TYPE_DISCONNECT = "DISCONNECT";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_OFFLINE_MESSAGES = "OFFLINE_MESSAGES";

    // ==================== FRIEND SYSTEM ====================
    public static final String TYPE_FRIEND_REQUEST = "FRIEND_REQUEST";
    public static final String TYPE_FRIEND_ACCEPT = "FRIEND_ACCEPT";
    public static final String TYPE_FRIEND_REJECT = "FRIEND_REJECT";
    public static final String TYPE_FRIEND_LIST = "FRIEND_LIST";
    public static final String TYPE_FRIEND_REQUEST_LIST = "FRIEND_REQUEST_LIST";
    public static final String TYPE_SEARCH_USER = "SEARCH_USER";
    public static final String TYPE_SEARCH_RESULT = "SEARCH_RESULT";
    public static final String TYPE_ALL_USERS = "ALL_USERS";
    public static final String TYPE_FRIEND_REMOVE = "FRIEND_REMOVE";

    public static final String TYPE_GROUP_CREATE_WITH_MEMBERS = "GROUP_CREATE_WITH_MEMBERS";
    public static final String TYPE_GROUP_DELETE = "GROUP_DELETE";
    public static final String TYPE_GROUP_REMOVE_MEMBER = "GROUP_REMOVE_MEMBER";

    // ==================== USER STATUS ====================
    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String STATUS_AWAY = "AWAY";
    public static final String STATUS_BUSY = "BUSY";
}
