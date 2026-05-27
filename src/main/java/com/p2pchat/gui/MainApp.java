package com.p2pchat.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.p2pchat.peer.PeerNode;
import com.p2pchat.shared.DatabaseManager;

import javax.swing.*;
import java.awt.*;

/**
 * Main Application Entry Point
 * Khởi chạy ứng dụng P2P Chat với FlatLaf Dark theme
 */
public class MainApp {

    public static void main(String[] args) {
        // Setup Look and Feel
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());

            // Custom UI defaults cho dark premium theme
            UIManager.put("Component.arc", 12);
            UIManager.put("Button.arc", 10);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

            // Colors
            UIManager.put("Panel.background", new Color(30, 30, 46));
            UIManager.put("TextField.background", new Color(49, 50, 68));
            UIManager.put("TextArea.background", new Color(49, 50, 68));
            UIManager.put("List.background", new Color(36, 36, 54));
            UIManager.put("List.selectionBackground", new Color(137, 180, 250, 60));
            UIManager.put("Table.background", new Color(36, 36, 54));
            UIManager.put("Button.background", new Color(137, 180, 250));
            UIManager.put("Button.foreground", new Color(30, 30, 46));

        } catch (Exception e) {
            System.err.println("Failed to set Look and Feel");
        }

        // Khởi tạo DB
        try {
            DatabaseManager.initialize();
        } catch (Exception e) {
            System.err.println("[App] Warning: Database not available. Chat history won't be saved locally.");
        }

        // Launch GUI
        SwingUtilities.invokeLater(() -> {
            PeerNode peerNode = new PeerNode();
            LoginDialog loginDialog = new LoginDialog(null, peerNode);
            loginDialog.setVisible(true);

            if (peerNode.getUsername() != null) {
                ChatGUI chatGUI = new ChatGUI(peerNode);
                chatGUI.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
}
