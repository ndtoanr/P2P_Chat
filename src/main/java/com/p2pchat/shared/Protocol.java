package com.p2pchat.shared;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Protocol handler - Đọc/ghi message với length-prefix framing
 * Format: [4 bytes length][JSON message bytes]
 */
public class Protocol {

    /**
     * Gửi message qua socket
     */
    public static void sendMessage(Socket socket, Message message) throws IOException {
        sendMessage(socket.getOutputStream(), message);
    }

    public static synchronized void sendMessage(OutputStream out, Message message) throws IOException {
        byte[] jsonBytes = message.toJson().getBytes(StandardCharsets.UTF_8);
        byte[] header = ByteBuffer.allocate(Constants.HEADER_SIZE).putInt(jsonBytes.length).array();

        out.write(header);
        out.write(jsonBytes);
        out.flush();
    }

    /**
     * Đọc message từ socket (blocking)
     */
    public static Message readMessage(Socket socket) throws IOException {
        return readMessage(socket.getInputStream());
    }

    public static Message readMessage(InputStream in) throws IOException {
        // Đọc 4 bytes header
        byte[] header = readExactly(in, Constants.HEADER_SIZE);
        int messageLength = ByteBuffer.wrap(header).getInt();

        if (messageLength <= 0 || messageLength > Constants.MAX_MESSAGE_SIZE) {
            throw new IOException("Invalid message length: " + messageLength);
        }

        // Đọc message body
        byte[] body = readExactly(in, messageLength);
        String json = new String(body, StandardCharsets.UTF_8);

        return Message.fromJson(json);
    }

    /**
     * Đọc chính xác n bytes từ InputStream
     */
    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] data = new byte[n];
        int offset = 0;
        while (offset < n) {
            int bytesRead = in.read(data, offset, n - offset);
            if (bytesRead == -1) {
                throw new IOException("Connection closed unexpectedly");
            }
            offset += bytesRead;
        }
        return data;
    }

    /**
     * Gửi raw bytes (cho file transfer)
     */
    public static void sendRawBytes(OutputStream out, byte[] data) throws IOException {
        byte[] header = ByteBuffer.allocate(Constants.HEADER_SIZE).putInt(data.length).array();
        out.write(header);
        out.write(data);
        out.flush();
    }

    /**
     * Đọc raw bytes
     */
    public static byte[] readRawBytes(InputStream in) throws IOException {
        byte[] header = readExactly(in, Constants.HEADER_SIZE);
        int length = ByteBuffer.wrap(header).getInt();

        if (length <= 0 || length > Constants.MAX_MESSAGE_SIZE) {
            throw new IOException("Invalid data length: " + length);
        }

        return readExactly(in, length);
    }
}
