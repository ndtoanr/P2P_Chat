package com.p2pchat.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Lớp Message đại diện cho một thông điệp trong giao thức P2P
 */
public class Message {

    private static final Gson gson = new GsonBuilder().create();

    private String type;
    private String from;
    private String to;
    private long timestamp;
    private String messageId;
    private JsonObject payload;

    public Message() {
        this.timestamp = System.currentTimeMillis();
        this.messageId = UUID.randomUUID().toString();
        this.payload = new JsonObject();
    }

    public Message(String type, String from, String to) {
        this();
        this.type = type;
        this.from = from;
        this.to = to;
    }

    // ==================== FACTORY METHODS ====================

    public static Message createRegister(String username, String password, String ip, int port) {
        Message msg = new Message(Constants.TYPE_REGISTER, username, "SERVER");
        msg.payload.addProperty("password", password);
        msg.payload.addProperty("ip", ip);
        msg.payload.addProperty("port", port);
        return msg;
    }

    public static Message createLogin(String username, String password, String ip, int port) {
        Message msg = new Message(Constants.TYPE_LOGIN, username, "SERVER");
        msg.payload.addProperty("password", password);
        msg.payload.addProperty("ip", ip);
        msg.payload.addProperty("port", port);
        return msg;
    }

    public static Message createDirectMessage(String from, String to, String content) {
        Message msg = new Message(Constants.TYPE_DIRECT_MSG, from, to);
        msg.payload.addProperty("content", content);
        return msg;
    }

    public static Message createGroupMessage(String from, String groupId, String content) {
        Message msg = new Message(Constants.TYPE_GROUP_MSG, from, groupId);
        msg.payload.addProperty("content", content);
        msg.payload.addProperty("groupId", groupId);
        return msg;
    }

    public static Message createHeartbeat(String from) {
        return new Message(Constants.TYPE_HEARTBEAT, from, "SERVER");
    }

    public static Message createAck(String from, String to, String originalMessageId) {
        Message msg = new Message(Constants.TYPE_ACK, from, to);
        msg.payload.addProperty("originalMessageId", originalMessageId);
        return msg;
    }

    public static Message createTyping(String from, String to, boolean isTyping) {
        Message msg = new Message(Constants.TYPE_TYPING, from, to);
        msg.payload.addProperty("isTyping", isTyping);
        return msg;
    }

    public static Message createReadReceipt(String from, String to, String originalMessageId) {
        Message msg = new Message(Constants.TYPE_READ_RECEIPT, from, to);
        msg.payload.addProperty("originalMessageId", originalMessageId);
        return msg;
    }

    public static Message createPeerStatus(String username, String status, String ip, int port) {
        Message msg = new Message(Constants.TYPE_PEER_STATUS, username, "BROADCAST");
        msg.payload.addProperty("status", status);
        msg.payload.addProperty("ip", ip);
        msg.payload.addProperty("port", port);
        return msg;
    }

    public static Message createGroupCreate(String from, String groupName) {
        Message msg = new Message(Constants.TYPE_GROUP_CREATE, from, "SERVER");
        msg.payload.addProperty("groupName", groupName);
        return msg;
    }

    public static Message createGroupJoin(String from, String groupId) {
        Message msg = new Message(Constants.TYPE_GROUP_JOIN, from, "SERVER");
        msg.payload.addProperty("groupId", groupId);
        return msg;
    }

    public static Message createGroupLeave(String from, String groupId) {
        Message msg = new Message(Constants.TYPE_GROUP_LEAVE, from, "SERVER");
        msg.payload.addProperty("groupId", groupId);
        return msg;
    }

    public static Message createFileInit(String from, String to, String fileName, long fileSize, String fileHash, String transferId) {
        Message msg = new Message(Constants.TYPE_FILE_INIT, from, to);
        msg.payload.addProperty("fileName", fileName);
        msg.payload.addProperty("fileSize", fileSize);
        msg.payload.addProperty("fileHash", fileHash);
        msg.payload.addProperty("transferId", transferId);
        return msg;
    }

    public static Message createKeyExchange(String from, String to, String publicKey) {
        Message msg = new Message(Constants.TYPE_KEY_EXCHANGE, from, to);
        msg.payload.addProperty("publicKey", publicKey);
        return msg;
    }

    public static Message createDisconnect(String from) {
        return new Message(Constants.TYPE_DISCONNECT, from, "SERVER");
    }

    public static Message createFriendRequest(String from, String to) {
        Message msg = new Message(Constants.TYPE_FRIEND_REQUEST, from, to);
        return msg;
    }

    public static Message createFriendAccept(String from, String to) {
        Message msg = new Message(Constants.TYPE_FRIEND_ACCEPT, from, to);
        return msg;
    }

    public static Message createFriendReject(String from, String to) {
        Message msg = new Message(Constants.TYPE_FRIEND_REJECT, from, to);
        return msg;
    }

    public static Message createSearchUser(String from, String query) {
        Message msg = new Message(Constants.TYPE_SEARCH_USER, from, "SERVER");
        msg.payload.addProperty("query", query);
        return msg;
    }

    public static Message createGroupCreateWithMembers(String from, String groupName, String[] members) {
        Message msg = new Message(Constants.TYPE_GROUP_CREATE_WITH_MEMBERS, from, "SERVER");
        msg.payload.addProperty("groupName", groupName);
        com.google.gson.JsonArray membersArray = new com.google.gson.JsonArray();
        for (String member : members) {
            membersArray.add(member);
        }
        msg.payload.add("members", membersArray);
        return msg;
    }

    public static Message createFriendRemove(String from, String targetUser) {
        Message msg = new Message(Constants.TYPE_FRIEND_REMOVE, from, "SERVER");
        msg.payload.addProperty("targetUser", targetUser);
        return msg;
    }

    public static Message createGroupDelete(String from, String groupId) {
        Message msg = new Message(Constants.TYPE_GROUP_DELETE, from, "SERVER");
        msg.payload.addProperty("groupId", groupId);
        return msg;
    }

    public static Message createGroupRemoveMember(String from, String groupId, String targetUser) {
        Message msg = new Message(Constants.TYPE_GROUP_REMOVE_MEMBER, from, "SERVER");
        msg.payload.addProperty("groupId", groupId);
        msg.payload.addProperty("targetUser", targetUser);
        return msg;
    }

    public static Message createError(String errorMessage) {
        Message msg = new Message(Constants.TYPE_ERROR, "SERVER", "");
        msg.payload.addProperty("error", errorMessage);
        return msg;
    }

    // ==================== SERIALIZATION ====================

    public String toJson() {
        return gson.toJson(this);
    }

    public static Message fromJson(String json) {
        return gson.fromJson(json, Message.class);
    }

    // ==================== GETTERS & SETTERS ====================

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public JsonObject getPayload() { return payload; }
    public void setPayload(JsonObject payload) { this.payload = payload; }

    public String getPayloadString(String key) {
        if (payload != null && payload.has(key)) {
            return payload.get(key).getAsString();
        }
        return null;
    }

    public int getPayloadInt(String key) {
        if (payload != null && payload.has(key)) {
            return payload.get(key).getAsInt();
        }
        return 0;
    }

    public long getPayloadLong(String key) {
        if (payload != null && payload.has(key)) {
            return payload.get(key).getAsLong();
        }
        return 0;
    }

    public boolean getPayloadBoolean(String key) {
        if (payload != null && payload.has(key)) {
            return payload.get(key).getAsBoolean();
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("Message{type='%s', from='%s', to='%s', id='%s'}", type, from, to, messageId);
    }
}
