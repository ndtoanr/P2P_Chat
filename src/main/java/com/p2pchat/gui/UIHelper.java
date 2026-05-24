package com.p2pchat.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * UI Helper - Các component và màu sắc dùng chung
 */
public class UIHelper {

    // Colors - Modern dark palette
    public static final Color BG_DARK = new Color(17, 24, 39);
    public static final Color BG_SIDEBAR = new Color(24, 32, 48);
    public static final Color BG_CARD = new Color(31, 41, 55);
    public static final Color BG_INPUT = new Color(55, 65, 81);
    public static final Color BG_HOVER = new Color(45, 55, 72);
    public static final Color TEXT_PRIMARY = new Color(243, 244, 246);
    public static final Color TEXT_SECONDARY = new Color(156, 163, 175);
    public static final Color TEXT_MUTED = new Color(107, 114, 128);
    public static final Color ACCENT = new Color(99, 102, 241);
    public static final Color ACCENT_HOVER = new Color(129, 140, 248);
    public static final Color GREEN = new Color(52, 211, 153);
    public static final Color RED = new Color(248, 113, 113);
    public static final Color TEAL = new Color(45, 212, 191);
    public static final Color AMBER = new Color(251, 191, 36);
    public static final Color PURPLE = new Color(168, 85, 247);
    public static final Color PINK = new Color(244, 114, 182);
    public static final Color BORDER = new Color(55, 65, 81);
    public static final Color MSG_SENT = new Color(99, 102, 241, 25);
    public static final Color MSG_RECV = new Color(31, 41, 55);

    public static String getAvatarText(String name) {
        if (name == null || name.isEmpty()) return "?";
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    public static Color getAvatarColor(String name) {
        Color[] colors = {ACCENT, GREEN, PURPLE, TEAL, AMBER, PINK};
        return colors[Math.abs(name.hashCode()) % colors.length];
    }

    /** Tạo avatar tròn */
    public static JLabel createAvatar(String name, int size) {
        Color color = getAvatarColor(name);
        JLabel label = new JLabel(getAvatarText(name), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setFont(new Font("Segoe UI", Font.BOLD, size / 3));
        label.setForeground(Color.WHITE);
        label.setPreferredSize(new Dimension(size, size));
        label.setMinimumSize(new Dimension(size, size));
        label.setMaximumSize(new Dimension(size, size));
        label.setOpaque(false);
        return label;
    }

    /** Nút gửi tin nhắn */
    public static JButton createSendButton() {
        JButton btn = new JButton("Gửi") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed() ? ACCENT.darker() :
                           getModel().isRollover() ? ACCENT_HOVER : ACCENT;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setPreferredSize(new Dimension(46, 42));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Nút hành động nhỏ */
    public static JButton createSmallButton(String text, Color color) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover() ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 40) :
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), 20);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setForeground(color);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** TextField tìm kiếm */
    public static JTextField createSearchField(String placeholder) {
        JTextField field = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(TEXT_MUTED);
                    g2.setFont(getFont());
                    Insets i = getInsets();
                    g2.drawString(placeholder, i.left + 2, getHeight() / 2 + getFont().getSize() / 2 - 2);
                    g2.dispose();
                }
            }
        };
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setForeground(TEXT_PRIMARY);
        field.setBackground(BG_INPUT);
        field.setCaretColor(ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return field;
    }
}
