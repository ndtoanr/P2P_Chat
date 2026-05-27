package com.p2pchat.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.p2pchat.peer.PeerNode;
import com.p2pchat.shared.Constants;
import com.p2pchat.shared.DatabaseManager;
import com.p2pchat.shared.Message;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Timer;
import static com.p2pchat.gui.UIHelper.*;

/**
 * Giao diện chat chính - Premium dark theme
 */
public class ChatGUI extends JFrame {

    private final PeerNode peerNode;

    // UI Colors imported from UIHelper via static import

    // Panels
    private JPanel sidebarPanel;
    private JPanel chatAreaPanel;
    private JPanel contactListPanel;
    private JPanel groupListPanel;
    private JPanel chatListPanel;
    private JScrollPane chatScroll;
    private JTextField messageField;
    private JLabel chatTitleLabel;
    private JLabel chatStatusLabel;
    private JLabel typingLabel;
    private JLabel connectionStatusLabel;
    private JPanel inputPanel;

    // State
    private String currentChatTarget = null;
    private boolean isGroupChat = false;
    private String currentGroupId = null;
    private final Map<String, StyledDocument> chatDocuments = new HashMap<>();
    private final Map<String, List<Message>> chatHistories = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private final Map<String, JLabel> messageStatusLabels = new HashMap<>();
    private final Set<String> readMessageIds = new HashSet<>();
    private String lastRenderedDate = null;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    private javax.swing.Timer typingTimer;
    private JTextField searchField;
    private JPanel searchResultPanel;
    private boolean isSearchMode = false;
    private JDialog manageGroupDialog;

    // Reply state
    private String replyToMessageId = null;
    private String replyToSender = null;
    private String replyToContent = null;
    private JPanel replyPanel = null;

    public ChatGUI(PeerNode peerNode) {
        this.peerNode = peerNode;
        initUI();
        setupCallbacks();
        setTitle("P2P Chat - " + peerNode.getUsername());
        
        // Yêu cầu lại dữ liệu từ server vì callbacks được setup SAU khi login
        // (messages có thể đã đến trước khi callbacks sẵn sàng)
        peerNode.requestFriendList();
        
        // Load unread counts từ DB ngay lập tức
        loadUnreadCountsFromDB();
        
        // Initial render sau khi login
        refreshContactList();
        refreshGroupList();
    }
    
    private void loadUnreadCountsFromDB() {
        try {
            // Lấy danh sách bạn bè từ DB trực tiếp
            List<String> friends = DatabaseManager.getFriends(peerNode.getUsername());
            for (String friend : friends) {
                int dbUnread = DatabaseManager.getUnreadCountFromUser(peerNode.getUsername(), friend);
                if (dbUnread > 0) {
                    unreadCounts.put(friend, dbUnread);
                    System.out.println("[Init] Unread from " + friend + ": " + dbUnread);
                }
            }
        } catch (Exception e) {
            System.err.println("[Init] Failed to load unread counts: " + e.getMessage());
        }
    }

    private void initUI() {
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(ChatGUI.this,
                    "Bạn có muốn thoát?", "Xác nhận", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    peerNode.disconnect();
                    dispose();
                    System.exit(0);
                }
            }
        });

        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_DARK);

        // Left sidebar
        sidebarPanel = createSidebar();
        mainPanel.add(sidebarPanel, BorderLayout.WEST);

        // Right chat area
        chatAreaPanel = createChatArea();
        mainPanel.add(chatAreaPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    // ==================== SIDEBAR ====================

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(290, 0));
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        // Header with user info
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_SIDEBAR);
        header.setBorder(new EmptyBorder(16, 16, 12, 16));

        JPanel userInfoPanel = new JPanel(new BorderLayout());
        userInfoPanel.setOpaque(false);

        JLabel avatarLabel = UIHelper.createAvatar(peerNode.getUsername(), 42);
        JPanel avatarWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        avatarWrapper.setOpaque(false);
        avatarWrapper.add(avatarLabel);
        userInfoPanel.add(avatarWrapper, BorderLayout.WEST);

        JPanel namePanel = new JPanel();
        namePanel.setOpaque(false);
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
        namePanel.setBorder(new EmptyBorder(4, 12, 0, 0));

        JLabel nameLabel = new JLabel(peerNode.getUsername());
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        nameLabel.setForeground(TEXT_PRIMARY);

        connectionStatusLabel = new JLabel("● Online");
        connectionStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        connectionStatusLabel.setForeground(GREEN);

        namePanel.add(nameLabel);
        namePanel.add(connectionStatusLabel);
        userInfoPanel.add(namePanel, BorderLayout.CENTER);
        header.add(userInfoPanel, BorderLayout.CENTER);

        // Search bar
        JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
        searchPanel.setOpaque(false);
        searchPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        searchField = UIHelper.createSearchField("Tìm bạn bè...");

        // Hành động tìm kiếm chung
        Runnable doSearch = () -> {
            String q = searchField.getText().trim();
            if (!q.isEmpty()) {
                peerNode.searchUser(q);
                isSearchMode = true;
            }
        };

        searchField.addActionListener(e -> doSearch.run());
        searchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                if (searchField.getText().trim().isEmpty()) {
                    isSearchMode = false;
                    refreshContactList();
                }
            }
        });

        // Nút tìm kiếm
        JButton searchBtn = new JButton("Tìm") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover() ? ACCENT_HOVER : ACCENT;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        searchBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        searchBtn.setPreferredSize(new Dimension(48, 36));
        searchBtn.setContentAreaFilled(false);
        searchBtn.setBorderPainted(false);
        searchBtn.setFocusPainted(false);
        searchBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        searchBtn.addActionListener(e -> doSearch.run());

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchBtn, BorderLayout.EAST);
        header.add(searchPanel, BorderLayout.SOUTH);
        sidebar.add(header, BorderLayout.NORTH);

        // Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabbedPane.setBackground(BG_SIDEBAR);
        tabbedPane.setForeground(TEXT_SECONDARY);

        // Contacts tab
        JPanel contactsTab = new JPanel(new BorderLayout());
        contactsTab.setBackground(BG_SIDEBAR);
        contactListPanel = new JPanel();
        contactListPanel.setLayout(new BoxLayout(contactListPanel, BoxLayout.Y_AXIS));
        contactListPanel.setBackground(BG_SIDEBAR);
        JScrollPane contactScroll = new JScrollPane(contactListPanel);
        contactScroll.setBorder(null);
        contactScroll.getViewport().setBackground(BG_SIDEBAR);
        contactScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        contactsTab.add(contactScroll, BorderLayout.CENTER);

        // Groups tab
        JPanel groupsTab = new JPanel(new BorderLayout());
        groupsTab.setBackground(BG_SIDEBAR);

        // Create group "+" button
        JPanel groupTopPanel = new JPanel(new BorderLayout());
        groupTopPanel.setBackground(BG_SIDEBAR);
        groupTopPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JButton createGroupBtn = UIHelper.createSmallButton("Tạo nhóm mới", TEAL);
        createGroupBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        createGroupBtn.addActionListener(e -> showCreateGroupDialog());
        groupTopPanel.add(createGroupBtn);
        groupsTab.add(groupTopPanel, BorderLayout.NORTH);

        groupListPanel = new JPanel();
        groupListPanel.setLayout(new BoxLayout(groupListPanel, BoxLayout.Y_AXIS));
        groupListPanel.setBackground(BG_SIDEBAR);
        JScrollPane groupScroll = new JScrollPane(groupListPanel);
        groupScroll.setBorder(null);
        groupScroll.getViewport().setBackground(BG_SIDEBAR);
        groupScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        groupsTab.add(groupScroll, BorderLayout.CENTER);

        tabbedPane.addTab("Liên hệ", contactsTab);
        tabbedPane.addTab("Nhóm", groupsTab);
        sidebar.add(tabbedPane, BorderLayout.CENTER);
        return sidebar;
    }

    // ==================== CHAT AREA ====================

    private JPanel createChatArea() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);

        // Chat header
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(BG_SIDEBAR);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(14, 20, 14, 20)
        ));

        JPanel chatInfoPanel = new JPanel();
        chatInfoPanel.setOpaque(false);
        chatInfoPanel.setLayout(new BoxLayout(chatInfoPanel, BoxLayout.Y_AXIS));

        chatTitleLabel = new JLabel("Chọn một liên hệ để bắt đầu chat");
        chatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        chatTitleLabel.setForeground(TEXT_PRIMARY);

        chatStatusLabel = new JLabel(" ");
        chatStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chatStatusLabel.setForeground(TEXT_SECONDARY);

        chatInfoPanel.add(chatTitleLabel);
        chatInfoPanel.add(chatStatusLabel);
        chatHeader.add(chatInfoPanel, BorderLayout.CENTER);

        panel.add(chatHeader, BorderLayout.NORTH);

        // Chat messages area
        chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        chatListPanel.setBackground(BG_DARK);
        chatListPanel.setBorder(new EmptyBorder(12, 20, 12, 20));

        chatScroll = new JScrollPane(chatListPanel);
        chatScroll.setBorder(null);
        chatScroll.getViewport().setBackground(BG_DARK);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(chatScroll, BorderLayout.CENTER);

        // Bottom input area
        inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(BG_SIDEBAR);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            new EmptyBorder(12, 16, 12, 16)
        ));

        // Typing indicator
        typingLabel = new JLabel(" ");
        typingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        typingLabel.setForeground(TEXT_MUTED);
        typingLabel.setBorder(new EmptyBorder(0, 6, 6, 0));
        inputPanel.add(typingLabel, BorderLayout.NORTH);

        // Message input field
        JPanel inputWrapper = new JPanel(new BorderLayout(10, 0));
        inputWrapper.setOpaque(false);

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageField.setForeground(TEXT_PRIMARY);
        messageField.setBackground(BG_INPUT);
        messageField.setCaretColor(ACCENT);
        messageField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            new EmptyBorder(10, 14, 10, 14)
        ));
        messageField.setEnabled(false);

        messageField.addActionListener(e -> sendMessage());

        // Typing indicator sender
        messageField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (currentChatTarget != null && !isGroupChat) {
                    peerNode.sendTyping(currentChatTarget, true);
                    resetTypingTimer();
                }
            }
        });

        JButton sendBtn = UIHelper.createSendButton();
        sendBtn.addActionListener(e -> sendMessage());

        inputWrapper.add(messageField, BorderLayout.CENTER);
        inputWrapper.add(sendBtn, BorderLayout.EAST);
        inputPanel.add(inputWrapper, BorderLayout.CENTER);

        panel.add(inputPanel, BorderLayout.SOUTH);

        // Welcome screen content
        showWelcomeScreen();

        return panel;
    }

    // ==================== CALLBACKS ====================

    private void setupCallbacks() {
        peerNode.setOnMessageReceived(message -> SwingUtilities.invokeLater(() -> handleReceivedMessage(message)));
        peerNode.setOnPeerStatusChanged(message -> SwingUtilities.invokeLater(() -> {
            refreshContactList();
            String peerName = message.getFrom();
            String status = message.getPayloadString("status");
            boolean isOnline = Constants.STATUS_ONLINE.equals(status);

            // Cập nhật chat header nếu đang mở chat với peer này
            if (currentChatTarget != null && currentChatTarget.equals(peerName) && !isGroupChat) {
                chatStatusLabel.setText(isOnline ? "● Online" : "○ Offline");
                chatStatusLabel.setForeground(isOnline ? GREEN : TEXT_MUTED);
                
                // Nếu họ vừa Online, cập nhật tất cả nhãn trạng thái đang là "Đã gửi"
                if (isOnline) {
                    for (JLabel label : messageStatusLabels.values()) {
                        String text = label.getText();
                        if (text != null && text.contains("Đã gửi")) {
                            label.setText("  Đã nhận");
                            label.setForeground(new Color(156, 163, 175));
                        }
                    }
                }
            }
        }));
        peerNode.setOnGroupListUpdated(message -> SwingUtilities.invokeLater(this::refreshGroupList));
        peerNode.setOnTypingReceived(message -> SwingUtilities.invokeLater(() -> handleTyping(message)));
        peerNode.setOnFriendListUpdated(message -> SwingUtilities.invokeLater(() -> {
            // Load unread counts từ DB cho từng bạn bè
            try {
                for (String friend : peerNode.getFriends().keySet()) {
                    int dbUnread = DatabaseManager.getUnreadCountFromUser(peerNode.getUsername(), friend);
                    int memUnread = unreadCounts.getOrDefault(friend, 0);
                    int maxUnread = Math.max(dbUnread, memUnread);
                    System.out.println("[DEBUG] Friend: " + friend + " dbUnread=" + dbUnread + " memUnread=" + memUnread + " -> " + maxUnread);
                    if (maxUnread > 0) {
                        unreadCounts.put(friend, maxUnread);
                    }
                }
            } catch (Exception e) {
                System.err.println("[DEBUG] DB unread query failed: " + e.getMessage());
            }
            refreshContactList();
        }));
        peerNode.setOnError(error -> SwingUtilities.invokeLater(() -> showNotification(error, RED)));
        peerNode.setOnFriendRequestReceived(message -> SwingUtilities.invokeLater(() -> {
            peerNode.requestFriendList();
            showNotification(message.getFrom() + " muốn kết bạn với bạn!", AMBER);
        }));
        peerNode.setOnFriendRequestListUpdated(message -> SwingUtilities.invokeLater(this::refreshContactList));
        peerNode.setOnFriendAcceptReceived(message -> SwingUtilities.invokeLater(() -> {
            showNotification(message.getFrom() + " đã chấp nhận lời mời kết bạn!", GREEN);
            if (isSearchMode && !searchField.getText().trim().isEmpty()) {
                peerNode.searchUser(searchField.getText().trim());
            }
        }));
        peerNode.setOnFriendRejectReceived(message -> SwingUtilities.invokeLater(() -> {
            showNotification(message.getFrom() + " đã từ chối lời mời kết bạn!", RED);
            if (isSearchMode && !searchField.getText().trim().isEmpty()) {
                peerNode.searchUser(searchField.getText().trim());
            }
        }));
        peerNode.setOnSearchResultReceived(message -> SwingUtilities.invokeLater(() -> showSearchResults(message)));
        peerNode.setOnGroupMembersReceived(message -> SwingUtilities.invokeLater(() -> handleGroupMembersReceived(message)));
        peerNode.setOnReadReceiptReceived(message -> SwingUtilities.invokeLater(() -> handleReadReceipt(message)));
        peerNode.setOnConnectionChanged(connected -> SwingUtilities.invokeLater(() -> {
            connectionStatusLabel.setText("● Online");
            connectionStatusLabel.setForeground(GREEN);
        }));
    }

    private void handleReadReceipt(Message message) {
        String msgId = message.getPayloadString("originalMessageId");
        if (msgId != null) {
            readMessageIds.add(msgId);
            // Lưu vào DB để giữ trạng thái sau khi đăng xuất
            try { DatabaseManager.markMessageRead(msgId); } catch (Exception ignored) {}
            JLabel statusLbl = messageStatusLabels.get(msgId);
            if (statusLbl != null) {
                statusLbl.setText("  Đã đọc");
                statusLbl.setForeground(new Color(52, 211, 153));
            }
        }
    }

    // ==================== MESSAGE HANDLING ====================

    private void handleReceivedMessage(Message message) {
        switch (message.getType()) {
            case Constants.TYPE_DIRECT_MSG -> {
                String from = message.getFrom();
                String content = message.getPayloadString("content");

                addMessageToHistory(from, message);
                try { DatabaseManager.saveMessage(message); } catch (Exception ignored) {}

                if (from.equals(currentChatTarget) && !isGroupChat) {
                    appendChatBubble(from, content, message.getTimestamp(), false, message.getMessageId(),
                        message.getPayloadBoolean("isForwarded"), message.getPayloadBoolean("isReply"),
                        message.getPayloadString("replyToId"), message.getPayloadString("replyToSender"),
                        message.getPayloadString("replyToContent"));
                    chatListPanel.revalidate();
                    chatListPanel.repaint();
                    scrollToBottom();
                    // Gửi read receipt + lưu DB
                    peerNode.sendReadReceipt(from, message.getMessageId());
                    try { DatabaseManager.markMessageRead(message.getMessageId()); } catch (Exception ignored) {}
                } else {
                    // Tăng số tin nhắn chưa đọc và làm mới sidebar ngay lập tức
                    boolean isOfflinePending = message.getPayloadBoolean("isOfflinePending");
                    System.out.println("[DEBUG] handleReceivedMessage DIRECT_MSG: from=" + from + " isOfflinePending=" + isOfflinePending);
                    if (!isOfflinePending) {
                        unreadCounts.merge(from, 1, Integer::sum);
                        showNotification("Tin nhắn mới từ " + from, ACCENT);
                    }
                    refreshContactList();
                }
            }
            case Constants.TYPE_GROUP_MSG -> {
                String groupId = message.getPayloadString("groupId");
                String from = message.getFrom();
                String content = message.getPayloadString("content");

                addMessageToGroupHistory(groupId, message);
                try { DatabaseManager.saveMessage(message); } catch (Exception ignored) {}

                if (isGroupChat && groupId.equals(currentGroupId)) {
                    boolean isSystem = message.getPayloadBoolean("isSystem");
                    if (isSystem) {
                        appendSystemMessage(content);
                    } else {
                        appendChatBubble(from, content, message.getTimestamp(), false, message.getMessageId(),
                            message.getPayloadBoolean("isForwarded"), message.getPayloadBoolean("isReply"),
                            message.getPayloadString("replyToId"), message.getPayloadString("replyToSender"),
                            message.getPayloadString("replyToContent"));
                    }
                    chatListPanel.revalidate();
                    chatListPanel.repaint();
                    scrollToBottom();
                } else {
                    boolean isOfflinePending = message.getPayloadBoolean("isOfflinePending");
                    unreadCounts.merge(groupId, 1, Integer::sum);
                    System.out.println("[DEBUG] handleReceivedMessage GROUP_MSG: groupId=" + groupId + " from=" + from + " isOfflinePending=" + isOfflinePending + " newUnread=" + unreadCounts.get(groupId));
                    refreshGroupList();
                    if (!isOfflinePending) {
                        showNotification("Tin nhắn nhóm mới", TEAL);
                    }
                }
            }
            case Constants.TYPE_GROUP_CREATE_RESPONSE -> {
                if (message.getPayloadBoolean("success")) {
                    showNotification("Tạo nhóm thành công: " + message.getPayloadString("groupName"), GREEN);
                }
            }
        }
    }

    private void sendMessage() {
        String content = messageField.getText().trim();
        if (content.isEmpty() || currentChatTarget == null) return;

        boolean hasReply = replyToMessageId != null;
        String savedReplyId = replyToMessageId;
        String savedReplySender = replyToSender;
        String savedReplyContent = replyToContent;

        if (isGroupChat) {
            Message sentMsg = Message.createGroupMessage(peerNode.getUsername(), currentGroupId, content);
            if (hasReply) {
                sentMsg.getPayload().addProperty("isReply", true);
                sentMsg.getPayload().addProperty("replyToId", savedReplyId);
                sentMsg.getPayload().addProperty("replyToSender", savedReplySender);
                sentMsg.getPayload().addProperty("replyToContent", savedReplyContent);
            }
            if (!peerNode.sendRawMessage(sentMsg)) return;
            addMessageToGroupHistory(currentGroupId, sentMsg);
            try { DatabaseManager.saveMessage(sentMsg); } catch (Exception ignored) {}
            appendChatBubble("Bạn", content, sentMsg.getTimestamp(), true, sentMsg.getMessageId(),
                false, hasReply, savedReplyId, savedReplySender, savedReplyContent);
        } else {
            Message sentMsg = Message.createDirectMessage(peerNode.getUsername(), currentChatTarget, content);
            if (hasReply) {
                sentMsg.getPayload().addProperty("isReply", true);
                sentMsg.getPayload().addProperty("replyToId", savedReplyId);
                sentMsg.getPayload().addProperty("replyToSender", savedReplySender);
                sentMsg.getPayload().addProperty("replyToContent", savedReplyContent);
            }
            if (!peerNode.sendRawMessage(sentMsg)) return;
            addMessageToHistory(currentChatTarget, sentMsg);
            try { DatabaseManager.saveMessage(sentMsg); } catch (Exception ignored) {}
            appendChatBubble("Bạn", content, sentMsg.getTimestamp(), true, sentMsg.getMessageId(),
                false, hasReply, savedReplyId, savedReplySender, savedReplyContent);
        }

        chatListPanel.revalidate();
        chatListPanel.repaint();
        scrollToBottom();

        messageField.setText("");
        clearReply();

        // Stop typing indicator
        if (!isGroupChat) {
            peerNode.sendTyping(currentChatTarget, false);
        }
    }

    private void setReplyTo(String msgId, String sender, String content) {
        this.replyToMessageId = msgId;
        this.replyToSender = sender;
        this.replyToContent = content;

        if (replyPanel != null && inputPanel != null) {
            inputPanel.remove(replyPanel);
        }
        replyPanel = new JPanel(new BorderLayout());
        replyPanel.setBackground(new Color(40, 42, 58));
        replyPanel.setBorder(new EmptyBorder(6, 16, 6, 8));

        JPanel replyInfo = new JPanel();
        replyInfo.setOpaque(false);
        replyInfo.setLayout(new BoxLayout(replyInfo, BoxLayout.Y_AXIS));
        JLabel rSender = new JLabel("↩ Trả lời " + sender);
        rSender.setFont(new Font("Segoe UI", Font.BOLD, 11));
        rSender.setForeground(TEAL);
        String truncated = content.length() > 50 ? content.substring(0, 47) + "..." : content;
        JLabel rContent = new JLabel(truncated);
        rContent.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        rContent.setForeground(TEXT_SECONDARY);
        replyInfo.add(rSender);
        replyInfo.add(rContent);
        replyPanel.add(replyInfo, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Hủy");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        closeBtn.setForeground(new Color(239, 68, 68));
        closeBtn.setPreferredSize(new Dimension(60, 28));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> clearReply());
        replyPanel.add(closeBtn, BorderLayout.EAST);

        if (inputPanel != null) {
            inputPanel.add(replyPanel, BorderLayout.NORTH);
            inputPanel.revalidate();
            inputPanel.repaint();
        }
        messageField.requestFocus();
    }

    private void clearReply() {
        replyToMessageId = null;
        replyToSender = null;
        replyToContent = null;
        if (replyPanel != null && inputPanel != null) {
            inputPanel.remove(replyPanel);
            replyPanel = null;
            inputPanel.revalidate();
            inputPanel.repaint();
        }
    }

    private void showForwardDialog(String content) {
        JDialog dialog = new JDialog(this, "Chuyển tiếp tin nhắn", true);
        dialog.setSize(350, 450);
        dialog.setLocationRelativeTo(this);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel titleLbl = new JLabel("Chọn người nhận:");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setBorder(new EmptyBorder(0, 0, 10, 0));
        mainPanel.add(titleLbl, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        // Friends
        JLabel friendsLabel = new JLabel("  Bạn bè");
        friendsLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        friendsLabel.setForeground(TEAL);
        friendsLabel.setBorder(new EmptyBorder(4, 0, 4, 0));
        friendsLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        listPanel.add(friendsLabel);

        for (String friend : peerNode.getFriends().keySet()) {
            JPanel item = new JPanel(new BorderLayout());
            item.setBorder(new EmptyBorder(4, 8, 4, 8));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JLabel label = new JLabel("👤 " + friend);
            label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            item.add(label, BorderLayout.CENTER);
            item.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    dialog.dispose();
                    forwardMessageTo(friend, false, null, content);
                }
                public void mouseEntered(MouseEvent e) { item.setBackground(new Color(55, 65, 81)); }
                public void mouseExited(MouseEvent e) { item.setBackground(UIManager.getColor("Panel.background")); }
            });
            listPanel.add(item);
        }

        // Groups
        List<JsonObject> joinedGroups = peerNode.getJoinedGroups();
        if (!joinedGroups.isEmpty()) {
            listPanel.add(Box.createVerticalStrut(8));
            JLabel groupsLabel = new JLabel("  Nhóm");
            groupsLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            groupsLabel.setForeground(AMBER);
            groupsLabel.setBorder(new EmptyBorder(4, 0, 4, 0));
            groupsLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
            listPanel.add(groupsLabel);

            for (JsonObject group : joinedGroups) {
                String gId = group.get("groupId").getAsString();
                String gName = group.get("groupName").getAsString();
                JPanel item = new JPanel(new BorderLayout());
                item.setBorder(new EmptyBorder(4, 8, 4, 8));
                item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
                item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                JLabel label = new JLabel("👥 " + gName);
                label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                item.add(label, BorderLayout.CENTER);
                item.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        dialog.dispose();
                        forwardMessageTo(gName, true, gId, content);
                    }
                    public void mouseEntered(MouseEvent e) { item.setBackground(new Color(55, 65, 81)); }
                    public void mouseExited(MouseEvent e) { item.setBackground(UIManager.getColor("Panel.background")); }
                });
                listPanel.add(item);
            }
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        mainPanel.add(scroll, BorderLayout.CENTER);
        dialog.setContentPane(mainPanel);
        dialog.setVisible(true);
    }

    private void forwardMessageTo(String target, boolean isGroup, String groupId, String content) {
        Message sentMsg;
        if (isGroup) {
            sentMsg = Message.createGroupMessage(peerNode.getUsername(), groupId, content);
        } else {
            sentMsg = Message.createDirectMessage(peerNode.getUsername(), target, content);
        }
        sentMsg.getPayload().addProperty("isForwarded", true);

        if (!peerNode.sendRawMessage(sentMsg)) return;

        if (isGroup) {
            addMessageToGroupHistory(groupId, sentMsg);
        } else {
            addMessageToHistory(target, sentMsg);
        }
        try { DatabaseManager.saveMessage(sentMsg); } catch (Exception ignored) {}

        // If currently viewing this chat, render the bubble
        if (isGroup && this.isGroupChat && groupId.equals(currentGroupId)) {
            appendChatBubble("Bạn", content, sentMsg.getTimestamp(), true, sentMsg.getMessageId(),
                true, false, null, null, null);
            chatListPanel.revalidate();
            chatListPanel.repaint();
            scrollToBottom();
        } else if (!isGroup && !this.isGroupChat && target.equals(currentChatTarget)) {
            appendChatBubble("Bạn", content, sentMsg.getTimestamp(), true, sentMsg.getMessageId(),
                true, false, null, null, null);
            chatListPanel.revalidate();
            chatListPanel.repaint();
            scrollToBottom();
        }
    }

    // ==================== CHAT DISPLAY ====================

    private void openChat(String target, boolean isGroup, String groupId) {
        this.currentChatTarget = target;
        this.isGroupChat = isGroup;
        this.currentGroupId = groupId;

        String creator = "";
        if (isGroup) {
            JsonObject groupObj = peerNode.getGroups().get(groupId);
            if (groupObj != null && groupObj.has("createdBy")) {
                creator = groupObj.get("createdBy").getAsString();
            }
        }

        chatTitleLabel.setText(isGroup ? "💬 " + target : "👤 " + target);
        chatStatusLabel.setText(isGroup ? (creator.isEmpty() ? "Nhóm chat" : "Nhóm của " + creator) : (peerNode.getOnlinePeers().containsKey(target) ? "● Online" : "○ Offline"));
        chatStatusLabel.setForeground(
            isGroup ? TEXT_SECONDARY : (peerNode.getOnlinePeers().containsKey(target) ? GREEN : TEXT_MUTED)
        );

        messageField.setEnabled(true);
        if (inputPanel != null) {
            inputPanel.setVisible(true);
        }
        messageField.requestFocus();
        typingLabel.setText(" ");

        // Clear chat panel
        chatListPanel.removeAll();
        messageStatusLabels.clear();
        lastRenderedDate = null;
        unreadCounts.put(target, 0);
        if (isGroup) {
            unreadCounts.put(groupId, 0);
        }
        
        // Load readMessageIds từ DB để khôi phục trạng thái "Đã đọc"
        if (!isGroup) {
            try {
                Set<String> dbRead = DatabaseManager.getReadMessageIds(peerNode.getUsername(), target);
                readMessageIds.addAll(dbRead);
            } catch (Exception ignored) {}
        }
        
        // Refresh contact list to remove badge
        if (isGroup) refreshGroupList();
        else refreshContactList();

        // Load history
        loadChatHistory(target, isGroup, groupId);
    }

    private void loadChatHistory(String target, boolean isGroup, String groupId) {
        String key = isGroup ? "group:" + groupId : target;
        List<Message> history = chatHistories.getOrDefault(key, new ArrayList<>());

        // Also try loading from DB
        try {
            List<Message> dbHistory;
            if (isGroup) {
                dbHistory = DatabaseManager.getGroupMessages(groupId, 100);
            } else {
                dbHistory = DatabaseManager.getMessageHistory(peerNode.getUsername(), target, 100);
            }

            // Merge DB history with in-memory (avoid duplicates)
            Set<String> existingIds = new HashSet<>();
            for (Message m : history) existingIds.add(m.getMessageId());
            for (Message m : dbHistory) {
                if (!existingIds.contains(m.getMessageId())) {
                    history.add(m);
                }
            }
            history.sort(Comparator.comparingLong(Message::getTimestamp));
            chatHistories.put(key, history);
        } catch (Exception e) {
            // DB might not be available
        }

        for (Message msg : history) {
            String content = msg.getPayloadString("content");
            boolean isSent = msg.getFrom().equals(peerNode.getUsername());
            appendChatBubble(isSent ? "Bạn" : msg.getFrom(), content, msg.getTimestamp(), isSent, msg.getMessageId(),
                msg.getPayloadBoolean("isForwarded"), msg.getPayloadBoolean("isReply"),
                msg.getPayloadString("replyToId"), msg.getPayloadString("replyToSender"),
                msg.getPayloadString("replyToContent"));
            
            // Gửi read receipt cho tin nhắn nhận được + lưu DB
            if (!isSent && !isGroup) {
                peerNode.sendReadReceipt(msg.getFrom(), msg.getMessageId());
                try { DatabaseManager.markMessageRead(msg.getMessageId()); } catch (Exception ignored) {}
            }
        }
        
        chatListPanel.revalidate();
        chatListPanel.repaint();
        scrollToBottom();
    }
    
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScroll.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void appendChatBubble(String sender, String content, long timestamp, boolean isSent, String messageId) {
        appendChatBubble(sender, content, timestamp, isSent, messageId, false, false, null, null, null);
    }

    private void appendChatBubble(String sender, String content, long timestamp, boolean isSent, String messageId,
                                  boolean isForwarded, boolean isReply, String replyToId, String replySender, String replyContent) {
        String msgDate = dateFormat.format(new Date(timestamp));
        if (lastRenderedDate == null || !lastRenderedDate.equals(msgDate)) {
            lastRenderedDate = msgDate;
            appendSystemMessage(msgDate);
        }

        JPanel wrapper = new JPanel(new FlowLayout(isSent ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(1, 0, 1, 0));

        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(isSent ? ACCENT : new Color(55, 65, 81));
        bubble.setBorder(new EmptyBorder(6, 12, 6, 12));
        bubble.setOpaque(false);

        // Forwarded label
        if (isForwarded) {
            JLabel fwdLabel = new JLabel("↪ Chuyển tiếp");
            fwdLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            fwdLabel.setForeground(new Color(180, 190, 254));
            fwdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(fwdLabel);
            bubble.add(Box.createVerticalStrut(2));
        }

        // Reply quote block
        if (isReply && replyContent != null) {
            JPanel quotePanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(255, 255, 255, 15));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(TEAL);
                    g2.fillRoundRect(0, 0, 3, getHeight(), 3, 3);
                    g2.dispose();
                }
            };
            quotePanel.setLayout(new BoxLayout(quotePanel, BoxLayout.Y_AXIS));
            quotePanel.setOpaque(false);
            quotePanel.setBorder(new EmptyBorder(4, 10, 4, 8));
            quotePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            String displayName = replySender;
            if (peerNode.getUsername().equals(replySender)) {
                displayName = "Bạn";
            }
            JLabel quoteSender = new JLabel(displayName != null ? displayName : "");
            quoteSender.setFont(new Font("Segoe UI", Font.BOLD, 11));
            quoteSender.setForeground(TEAL);
            quoteSender.setAlignmentX(Component.LEFT_ALIGNMENT);
            quotePanel.add(quoteSender);

            String truncated = replyContent.length() > 60 ? replyContent.substring(0, 57) + "..." : replyContent;
            JLabel quoteText = new JLabel(truncated);
            quoteText.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            quoteText.setForeground(new Color(200, 200, 210));
            quoteText.setAlignmentX(Component.LEFT_ALIGNMENT);
            quotePanel.add(quoteText);

            quotePanel.setMaximumSize(new Dimension(300, 50));
            bubble.add(quotePanel);
            bubble.add(Box.createVerticalStrut(3));
        }

        if (!isSent && isGroupChat) {
            JLabel nameLbl = new JLabel(sender);
            nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            nameLbl.setForeground(AMBER);
            nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(nameLbl);
            bubble.add(Box.createVerticalStrut(1));
        }

        JTextArea textArea = new JTextArea(content);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textArea.setForeground(Color.WHITE);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        int lines = content.split("\r\n|\r|\n").length;
        if (content.length() > 40 || lines > 1) {
            textArea.setColumns(35);
        }
        
        bubble.add(textArea);

        // Footer: time + status
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        footerPanel.setOpaque(false);
        footerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel timeLbl = new JLabel(timeFormat.format(new Date(timestamp)));
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLbl.setForeground(new Color(255, 255, 255, 150));
        footerPanel.add(timeLbl);

        if (isSent && messageId != null && !isGroupChat) {
            String statusText;
            Color statusColor;
            
            if (readMessageIds.contains(messageId)) {
                statusText = "  Đã đọc";
                statusColor = new Color(52, 211, 153);
            } else {
                boolean targetOnline = currentChatTarget != null && peerNode.getOnlinePeers().containsKey(currentChatTarget);
                statusText = targetOnline ? "  Đã nhận" : "  Đã gửi";
                statusColor = targetOnline ? new Color(156, 163, 175) : new Color(107, 114, 128);
            }
            
            JLabel statusLbl = new JLabel(statusText);
            statusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            statusLbl.setForeground(statusColor);
            footerPanel.add(statusLbl);
            messageStatusLabels.put(messageId, statusLbl);
        }

        bubble.add(Box.createVerticalStrut(2));
        bubble.add(footerPanel);

        // Wrap bubble with "..." menu button
        JPanel bubbleRow = new JPanel(new FlowLayout(isSent ? FlowLayout.RIGHT : FlowLayout.LEFT, 2, 0));
        bubbleRow.setOpaque(false);

        JButton menuBtn = new JButton("⋯");
        menuBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        menuBtn.setForeground(TEXT_MUTED);
        menuBtn.setPreferredSize(new Dimension(28, 28));
        menuBtn.setContentAreaFilled(false);
        menuBtn.setBorderPainted(false);
        menuBtn.setFocusPainted(false);
        menuBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        menuBtn.setVisible(false);

        final String msgContent = content;
        final String msgSender = isSent ? peerNode.getUsername() : sender;
        final String msgId = messageId;
        menuBtn.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            JMenuItem replyItem = new JMenuItem("↩ Trả lời");
            replyItem.addActionListener(ev -> setReplyTo(msgId, msgSender, msgContent));
            popup.add(replyItem);

            JMenuItem fwdItem = new JMenuItem("↪ Chuyển tiếp");
            fwdItem.addActionListener(ev -> showForwardDialog(msgContent));
            popup.add(fwdItem);
            popup.show(menuBtn, 0, menuBtn.getHeight());
        });

        // Show/hide menu button on hover
        MouseAdapter hoverAdapter = new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { menuBtn.setVisible(true); }
            public void mouseExited(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), bubbleRow);
                if (!bubbleRow.contains(p)) menuBtn.setVisible(false);
            }
        };
        bubble.addMouseListener(hoverAdapter);
        menuBtn.addMouseListener(hoverAdapter);
        bubbleRow.addMouseListener(hoverAdapter);

        if (isSent) {
            bubbleRow.add(menuBtn);
            bubbleRow.add(bubble);
        } else {
            bubbleRow.add(bubble);
            bubbleRow.add(menuBtn);
        }

        wrapper.add(bubbleRow);
        chatListPanel.add(wrapper);
    }

    private void appendSystemMessage(String content) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(6, 0, 6, 0));

        JLabel sysLbl = new JLabel(content);
        sysLbl.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        sysLbl.setForeground(TEXT_MUTED);
        
        wrapper.add(sysLbl);
        chatListPanel.add(wrapper);
    }

    private void showWelcomeScreen() {
        chatListPanel.removeAll();
        
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);
        
        wrapper.add(Box.createVerticalStrut(100));
        
        JLabel title = new JLabel("💬 Chào mừng đến P2P Chat!");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel sub1 = new JLabel("Chọn một liên hệ hoặc nhóm từ sidebar để bắt đầu trò chuyện.");
        sub1.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sub1.setForeground(TEXT_SECONDARY);
        sub1.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel sub2 = new JLabel("Tin nhắn được mã hóa và gửi trực tiếp peer-to-peer.");
        sub2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sub2.setForeground(TEXT_SECONDARY);
        sub2.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel sub3 = new JLabel("🔒 An toàn  •  ⚡ Nhanh chóng  •  🌐 Ngang hàng");
        sub3.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sub3.setForeground(TEXT_SECONDARY);
        sub3.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        wrapper.add(title);
        wrapper.add(Box.createVerticalStrut(20));
        wrapper.add(sub1);
        wrapper.add(Box.createVerticalStrut(5));
        wrapper.add(sub2);
        wrapper.add(Box.createVerticalStrut(20));
        wrapper.add(sub3);
        
        chatListPanel.add(wrapper);
        chatListPanel.revalidate();
        chatListPanel.repaint();
        if (inputPanel != null) {
            inputPanel.setVisible(false);
        }
    }

    // ==================== CONTACT & GROUP LIST ====================

    private void refreshContactList() {
        if (isSearchMode) {
            // Search mode - hiện search results
            return;
        }

        contactListPanel.removeAll();

        // Hiện pending friend requests
        List<JsonObject> pending = peerNode.getPendingFriendRequests();
        if (!pending.isEmpty()) {
            JLabel reqLabel = new JLabel("  Lời mời kết bạn — " + pending.size());
            reqLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            reqLabel.setForeground(AMBER);
            reqLabel.setBorder(new EmptyBorder(10, 12, 6, 12));
            reqLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            contactListPanel.add(reqLabel);

            for (JsonObject req : pending) {
                String reqUser = req.get("username").getAsString();
                JPanel reqItem = createFriendRequestItem(reqUser);
                contactListPanel.add(reqItem);
            }
        }

        // Hiện friends
        Map<String, JsonObject> friendMap = peerNode.getFriends();
        Map<String, JsonObject> onlinePeers = peerNode.getOnlinePeers();

        if (friendMap.isEmpty() && pending.isEmpty()) {
            JLabel emptyLabel = new JLabel("<html><center>Chưa có bạn bè<br>Dùng thanh tìm kiếm để kết bạn!</center></html>");
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            emptyLabel.setForeground(TEXT_MUTED);
            emptyLabel.setBorder(new EmptyBorder(30, 20, 20, 20));
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            contactListPanel.add(emptyLabel);
        } else if (!friendMap.isEmpty()) {
            // Tách online và offline
            List<String> onlineFriends = new ArrayList<>();
            List<String> offlineFriends = new ArrayList<>();
            for (String f : friendMap.keySet()) {
                if (onlinePeers.containsKey(f)) onlineFriends.add(f);
                else offlineFriends.add(f);
            }

            if (!onlineFriends.isEmpty()) {
                JLabel onLabel = new JLabel("  Online — " + onlineFriends.size());
                onLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
                onLabel.setForeground(GREEN);
                onLabel.setBorder(new EmptyBorder(10, 12, 6, 12));
                onLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                contactListPanel.add(onLabel);
                for (String f : onlineFriends) contactListPanel.add(createContactItem(f, true));
            }

            if (!offlineFriends.isEmpty()) {
                JLabel offLabel = new JLabel("  Offline — " + offlineFriends.size());
                offLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
                offLabel.setForeground(TEXT_MUTED);
                offLabel.setBorder(new EmptyBorder(10, 12, 6, 12));
                offLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                contactListPanel.add(offLabel);
                for (String f : offlineFriends) contactListPanel.add(createContactItem(f, false));
            }
        }

        contactListPanel.revalidate();
        contactListPanel.repaint();
    }

    private JPanel createContactItem(String username, boolean online) {
        JPanel item = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (username.equals(currentChatTarget) && !isGroupChat) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor(new Color(99, 102, 241, 25));
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                    g2d.setColor(ACCENT);
                    g2d.fillRect(0, 0, 3, getHeight());
                }
            }
        };
        item.setBackground(BG_SIDEBAR);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        item.setBorder(new EmptyBorder(8, 16, 8, 16));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Round avatar
        JLabel avatar = UIHelper.createAvatar(username, 38);

        // Name and status
        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(new EmptyBorder(2, 12, 2, 0));

        JLabel nameLabel = new JLabel(username);
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameLabel.setForeground(TEXT_PRIMARY);

        // Online: chấm xanh, Offline: không hiện gì
        if (online) {
            JLabel statusLabel = new JLabel("● Online");
            statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            statusLabel.setForeground(GREEN);
            infoPanel.add(nameLabel);
            infoPanel.add(statusLabel);
        } else {
            infoPanel.add(nameLabel);
        }

        item.add(avatar, BorderLayout.WEST);
        item.add(infoPanel, BorderLayout.CENTER);
        
        int unread = unreadCounts.getOrDefault(username, 0);
        if (unread > 0) {
            JLabel badge = new JLabel(String.valueOf(unread), SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(RED);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            badge.setForeground(Color.WHITE);
            badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
            badge.setPreferredSize(new Dimension(20, 20));
            
            JPanel badgePanel = new JPanel(new GridBagLayout());
            badgePanel.setOpaque(false);
            badgePanel.add(badge);
            item.add(badgePanel, BorderLayout.EAST);
        }

        item.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (!username.equals(currentChatTarget) || isGroupChat) item.setBackground(BG_HOVER);
            }
            public void mouseExited(MouseEvent e) { item.setBackground(BG_SIDEBAR); }
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem unfriendItem = new JMenuItem("Hủy kết bạn");
                    unfriendItem.setForeground(RED);
                    unfriendItem.addActionListener(ev -> {
                        int res = JOptionPane.showConfirmDialog(ChatGUI.this,
                            "Bạn có chắc chắn muốn hủy kết bạn với " + username + "?",
                            "Hủy kết bạn", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (res == JOptionPane.YES_OPTION) {
                            peerNode.unfriend(username);
                        }
                    });
                    popup.add(unfriendItem);
                    popup.show(item, e.getX(), e.getY());
                } else {
                    openChat(username, false, null);
                    refreshContactList();
                }
            }
        });
        return item;
    }

    private void refreshGroupList() {
        groupListPanel.removeAll();
        List<JsonObject> joinedGroups = peerNode.getJoinedGroups();
        List<JsonObject> availableGroups = peerNode.getAvailableGroups();

        // Kiểm tra xem nhóm hiện tại có còn tồn tại trong danh sách đã tham gia không
        if (isGroupChat && currentGroupId != null) {
            boolean currentGroupStillExists = false;
            for (JsonObject g : joinedGroups) {
                if (g.get("groupId").getAsString().equals(currentGroupId)) {
                    currentGroupStillExists = true;
                    break;
                }
            }
            if (!currentGroupStillExists) {
                currentChatTarget = null;
                isGroupChat = false;
                currentGroupId = null;
                showWelcomeScreen();
            }
        }

        if (!joinedGroups.isEmpty()) {
            JLabel joinedLabel = new JLabel("  Nhóm của bạn — " + joinedGroups.size());
            joinedLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            joinedLabel.setForeground(TEXT_MUTED);
            joinedLabel.setBorder(new EmptyBorder(10, 12, 6, 12));
            joinedLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            groupListPanel.add(joinedLabel);
            for (JsonObject g : joinedGroups) groupListPanel.add(createGroupItem(g, true));
        }
        if (!availableGroups.isEmpty()) {
            JLabel availLabel = new JLabel("  Nhóm khác — " + availableGroups.size());
            availLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            availLabel.setForeground(TEXT_MUTED);
            availLabel.setBorder(new EmptyBorder(14, 12, 6, 12));
            availLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            groupListPanel.add(availLabel);
            for (JsonObject g : availableGroups) groupListPanel.add(createGroupItem(g, false));
        }
        if (joinedGroups.isEmpty() && availableGroups.isEmpty()) {
            JLabel emptyLabel = new JLabel("Chưa có nhóm nào");
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            emptyLabel.setForeground(TEXT_MUTED);
            emptyLabel.setBorder(new EmptyBorder(20, 20, 20, 20));
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            groupListPanel.add(emptyLabel);
        }
        groupListPanel.revalidate();
        groupListPanel.repaint();
    }

    private JPanel createGroupItem(JsonObject group, boolean joined) {
        String groupId = group.get("groupId").getAsString();
        String groupName = group.get("groupName").getAsString();
        JPanel item = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (isGroupChat && groupId.equals(currentGroupId)) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor(new Color(45, 212, 191, 25));
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                    g2d.setColor(TEAL);
                    g2d.fillRect(0, 0, 3, getHeight());
                }
            }
        };
        item.setBackground(BG_SIDEBAR);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        item.setBorder(new EmptyBorder(8, 16, 8, 16));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel icon = new JLabel("👥", SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        icon.setPreferredSize(new Dimension(38, 38));

        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(new EmptyBorder(2, 12, 2, 0));
        JLabel nameLabel = new JLabel(groupName);
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameLabel.setForeground(TEXT_PRIMARY);
        
        String creator = group.has("createdBy") ? group.get("createdBy").getAsString() : "";
        JLabel statusLabel = new JLabel((joined ? "Đã tham gia" : "Chưa tham gia") + (creator.isEmpty() ? "" : " • Tạo bởi " + creator));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(joined ? TEAL : TEXT_MUTED);
        infoPanel.add(nameLabel);
        infoPanel.add(statusLabel);

        item.add(icon, BorderLayout.WEST);
        item.add(infoPanel, BorderLayout.CENTER);

        if (!joined) {
            JButton joinBtn = UIHelper.createSmallButton("Tham gia", TEAL);
            joinBtn.addActionListener(e -> peerNode.joinGroup(groupId));
            item.add(joinBtn, BorderLayout.EAST);
        } else {
            int unread = unreadCounts.getOrDefault(groupId, 0);
            if (unread > 0) {
                JLabel badge = new JLabel(String.valueOf(unread), SwingConstants.CENTER) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(RED);
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                        g2.dispose();
                        super.paintComponent(g);
                    }
                };
                badge.setForeground(Color.WHITE);
                badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
                badge.setPreferredSize(new Dimension(20, 20));
                
                JPanel badgePanel = new JPanel(new GridBagLayout());
                badgePanel.setOpaque(false);
                badgePanel.add(badge);
                item.add(badgePanel, BorderLayout.EAST);
            }
        }

        item.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { item.setBackground(BG_HOVER); }
            public void mouseExited(MouseEvent e) { item.setBackground(BG_SIDEBAR); }
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (joined) {
                        JPopupMenu popup = new JPopupMenu();
                        JsonObject groupObj = peerNode.getGroups().get(groupId);
                        boolean isAdmin = groupObj != null && groupObj.get("createdBy").getAsString().equals(peerNode.getUsername());
                        
                        JMenuItem viewItem = new JMenuItem("Xem thành viên");
                        viewItem.addActionListener(ev -> showManageGroupDialog(groupId, groupName));
                        popup.add(viewItem);

                        if (isAdmin) {
                            JMenuItem deleteItem = new JMenuItem("Xóa nhóm");
                            deleteItem.setForeground(RED);
                            deleteItem.addActionListener(ev -> {
                                int res = JOptionPane.showConfirmDialog(ChatGUI.this,
                                    "Bạn có chắc chắn muốn xóa nhóm " + groupName + "? Hành động này không thể hoàn tác.",
                                    "Xóa nhóm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                                if (res == JOptionPane.YES_OPTION) {
                                    peerNode.deleteGroup(groupId);
                                    if (groupId.equals(currentGroupId)) {
                                         currentChatTarget = null;
                                         isGroupChat = false;
                                         currentGroupId = null;
                                         showWelcomeScreen();
                                     }
                                }
                            });
                            popup.add(deleteItem);
                        } else {
                            JMenuItem leaveItem = new JMenuItem("Rời nhóm");
                            leaveItem.setForeground(RED);
                            leaveItem.addActionListener(ev -> {
                                int res = JOptionPane.showConfirmDialog(ChatGUI.this,
                                    "Bạn có chắc chắn muốn rời nhóm " + groupName + "?",
                                    "Rời nhóm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                                if (res == JOptionPane.YES_OPTION) {
                                    peerNode.leaveGroup(groupId);
                                    if (groupId.equals(currentGroupId)) {
                                        currentChatTarget = null;
                                        isGroupChat = false;
                                        currentGroupId = null;
                                        showWelcomeScreen();
                                    }
                                }
                            });
                            popup.add(leaveItem);
                        }
                        popup.show(item, e.getX(), e.getY());
                    }
                } else {
                    if (joined) { openChat(groupName, true, groupId); refreshGroupList(); }
                }
            }
        });
        return item;
    }

    // ==================== TYPING INDICATOR ====================

    private void handleTyping(Message message) {
        String from = message.getFrom();
        boolean isTyping = message.getPayloadBoolean("isTyping");

        if (from.equals(currentChatTarget) && !isGroupChat) {
            typingLabel.setText(isTyping ? from + " đang nhập..." : " ");
        }
    }

    private void resetTypingTimer() {
        if (typingTimer != null) typingTimer.stop();
        typingTimer = new javax.swing.Timer(3000, e -> {
            if (currentChatTarget != null && !isGroupChat) {
                peerNode.sendTyping(currentChatTarget, false);
            }
        });
        typingTimer.setRepeats(false);
        typingTimer.start();
    }

    // ==================== DIALOGS ====================

    private void showCreateGroupDialog() {
        // Lấy danh sách bạn bè
        Map<String, JsonObject> friendMap = peerNode.getFriends();
        if (friendMap.size() < 2) {
            JOptionPane.showMessageDialog(this, "Bạn cần có ít nhất 2 bạn bè để tạo nhóm!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(300, 350));

        JTextField nameField = new JTextField(20);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(new JLabel("Tên nhóm:"));
        panel.add(Box.createVerticalStrut(6));
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("Chọn thành viên (tối thiểu 2 người):"));
        panel.add(Box.createVerticalStrut(6));

        // Checkboxes cho bạn bè
        List<JCheckBox> checkBoxes = new ArrayList<>();
        JPanel membersPanel = new JPanel();
        membersPanel.setLayout(new BoxLayout(membersPanel, BoxLayout.Y_AXIS));
        for (String friend : friendMap.keySet()) {
            JCheckBox cb = new JCheckBox(friend);
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            checkBoxes.add(cb);
            membersPanel.add(cb);
        }
        JScrollPane scroll = new JScrollPane(membersPanel);
        scroll.setPreferredSize(new Dimension(280, 200));
        panel.add(scroll);

        int result = JOptionPane.showConfirmDialog(this, panel, "Tạo nhóm mới",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String groupName = nameField.getText().trim();
            List<String> selectedMembers = new ArrayList<>();
            selectedMembers.add(peerNode.getUsername()); // Tự thêm bản thân
            for (JCheckBox cb : checkBoxes) {
                if (cb.isSelected()) selectedMembers.add(cb.getText());
            }
            if (groupName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên nhóm!");
                return;
            }
            if (selectedMembers.size() < 3) { // bản thân + 2 người
                JOptionPane.showMessageDialog(this, "Vui lòng chọn ít nhất 2 thành viên!");
                return;
            }
            peerNode.createGroupWithMembers(groupName, selectedMembers.toArray(new String[0]));
        }
    }

    private void showManageGroupDialog(String groupId, String groupName) {
        if (manageGroupDialog != null && manageGroupDialog.isVisible()) {
            manageGroupDialog.dispose();
        }

        JsonObject groupObj = peerNode.getGroups().get(groupId);
        boolean isAdmin = groupObj != null && groupObj.get("createdBy").getAsString().equals(peerNode.getUsername());
        String title = (isAdmin ? "Quản lý thành viên - " : "Thành viên nhóm - ") + groupName;

        manageGroupDialog = new JDialog(this, title, true);
        manageGroupDialog.setSize(350, 400);
        manageGroupDialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JLabel loadingLabel = new JLabel("Đang tải danh sách thành viên...", SwingConstants.CENTER);
        panel.add(loadingLabel, BorderLayout.CENTER);
        
        manageGroupDialog.setContentPane(panel);
        
        // Request members from server
        peerNode.requestGroupMembers(groupId);
        
        manageGroupDialog.setVisible(true);
    }

    private void handleGroupMembersReceived(Message message) {
        if (manageGroupDialog == null || !manageGroupDialog.isVisible()) return;

        String groupId = message.getPayloadString("groupId");
        JsonArray membersArray = message.getPayload().getAsJsonArray("members");

        JsonObject groupObj = peerNode.getGroups().get(groupId);
        String createdBy = groupObj != null && groupObj.has("createdBy") ? groupObj.get("createdBy").getAsString() : "";
        boolean isCurrentUserAdmin = createdBy.equals(peerNode.getUsername());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        for (JsonElement el : membersArray) {
            String member = el.getAsString();
            JPanel item = new JPanel(new BorderLayout());
            item.setBorder(new EmptyBorder(5, 5, 5, 5));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

            String displayName = member;
            if (member.equals(createdBy)) {
                displayName += " (Trưởng nhóm)";
            }
            JLabel nameLabel = new JLabel(displayName);
            if (member.equals(createdBy)) {
                nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
                nameLabel.setForeground(TEAL);
            } else {
                nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            }
            item.add(nameLabel, BorderLayout.CENTER);

            if (isCurrentUserAdmin && !member.equals(peerNode.getUsername()) && !member.equals(createdBy)) {
                JButton removeBtn = UIHelper.createSmallButton("Xóa thành viên", RED);
                removeBtn.addActionListener(e -> {
                    int res = JOptionPane.showConfirmDialog(manageGroupDialog,
                        "Bạn có chắc chắn muốn xóa " + member + " khỏi nhóm?",
                        "Xác nhận", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (res == JOptionPane.YES_OPTION) {
                        peerNode.removeGroupMember(groupId, member);
                        manageGroupDialog.dispose();
                    }
                });
                item.add(removeBtn, BorderLayout.EAST);
            } else if (member.equals(createdBy)) {
                JLabel adminLabel = new JLabel("Admin");
                adminLabel.setForeground(TEAL);
                adminLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
                item.add(adminLabel, BorderLayout.EAST);
            }
            panel.add(item);
        }

        JScrollPane scroll = new JScrollPane(panel);
        manageGroupDialog.setContentPane(scroll);
        manageGroupDialog.revalidate();
        manageGroupDialog.repaint();
    }

    // ==================== HISTORY MANAGEMENT ====================

    private void addMessageToHistory(String peerUsername, Message message) {
        chatHistories.computeIfAbsent(peerUsername, k -> new ArrayList<>()).add(message);
    }

    private void addMessageToGroupHistory(String groupId, Message message) {
        chatHistories.computeIfAbsent("group:" + groupId, k -> new ArrayList<>()).add(message);
    }

    // ==================== UI UTILITIES ====================

    private void highlightContact(String target) {
        // Hàm này giờ chủ yếu dùng để thông báo cho tin nhắn mới khi đang Online
        int count = unreadCounts.getOrDefault(target, 0) + 1;
        unreadCounts.put(target, count);
        SwingUtilities.invokeLater(this::refreshContactList);
    }

    private void showNotification(String text, Color color) {
        // Simple notification in title bar area
        chatStatusLabel.setText(text);
        chatStatusLabel.setForeground(color);

        javax.swing.Timer resetTimer = new javax.swing.Timer(3000, e -> {
            if (currentChatTarget != null) {
                chatStatusLabel.setText(isGroupChat ? "Nhóm chat" :
                    (peerNode.getOnlinePeers().containsKey(currentChatTarget) ? "● Online" : "○ Offline"));
                chatStatusLabel.setForeground(
                    isGroupChat ? TEXT_SECONDARY :
                    (peerNode.getOnlinePeers().containsKey(currentChatTarget) ? GREEN : TEXT_MUTED));
            } else {
                chatStatusLabel.setText(" ");
            }
        });
        resetTimer.setRepeats(false);
        resetTimer.start();
    }

    // ==================== SEARCH & FRIEND REQUEST ====================

    private void showSearchResults(Message message) {
        contactListPanel.removeAll();
        JsonArray users = message.getPayload().getAsJsonArray("users");
        if (users == null || users.size() == 0) {
            JLabel noResult = new JLabel("Không tìm thấy người dùng");
            noResult.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            noResult.setForeground(TEXT_MUTED);
            noResult.setBorder(new EmptyBorder(20, 20, 20, 20));
            contactListPanel.add(noResult);
        } else {
            JLabel header = new JLabel("  Kết quả tìm kiếm — " + users.size());
            header.setFont(new Font("Segoe UI", Font.BOLD, 11));
            header.setForeground(ACCENT);
            header.setBorder(new EmptyBorder(10, 12, 6, 12));
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            contactListPanel.add(header);

            for (JsonElement el : users) {
                JsonObject u = el.getAsJsonObject();
                String name = u.get("username").getAsString();
                boolean isFriend = u.get("isFriend").getAsBoolean();
                boolean reqSent = u.get("requestSent").getAsBoolean();
                boolean reqRecv = u.get("requestReceived").getAsBoolean();

                JPanel item = new JPanel(new BorderLayout());
                item.setBackground(BG_SIDEBAR);
                item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
                item.setBorder(new EmptyBorder(6, 16, 6, 16));

                JLabel avatar = UIHelper.createAvatar(name, 34);
                JLabel nameLabel = new JLabel(name);
                nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                nameLabel.setForeground(TEXT_PRIMARY);
                nameLabel.setBorder(new EmptyBorder(0, 10, 0, 0));

                item.add(avatar, BorderLayout.WEST);
                item.add(nameLabel, BorderLayout.CENTER);

                if (isFriend) {
                    JButton chatNowBtn = UIHelper.createSmallButton("Chat ngay", GREEN);
                    chatNowBtn.addActionListener(e -> {
                        openChat(name, false, null);
                        isSearchMode = false;
                        searchField.setText("");
                        refreshContactList();
                    });
                    item.add(chatNowBtn, BorderLayout.EAST);
                } else if (reqSent) {
                    JLabel sentLabel = new JLabel("Đã gửi");
                    sentLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                    sentLabel.setForeground(TEXT_MUTED);
                    item.add(sentLabel, BorderLayout.EAST);
                } else if (reqRecv) {
                    JButton acceptBtn = UIHelper.createSmallButton("Chấp nhận", GREEN);
                    acceptBtn.addActionListener(e -> { peerNode.acceptFriendRequest(name); peerNode.searchUser(searchField.getText().trim()); });
                    item.add(acceptBtn, BorderLayout.EAST);
                } else {
                    JButton addBtn = UIHelper.createSmallButton("Kết bạn", ACCENT);
                    addBtn.addActionListener(e -> { peerNode.sendFriendRequest(name); peerNode.searchUser(searchField.getText().trim()); });
                    item.add(addBtn, BorderLayout.EAST);
                }
                contactListPanel.add(item);
            }
        }
        contactListPanel.revalidate();
        contactListPanel.repaint();
    }

    private void performLocalSearch(String query) {
        isSearchMode = true;
        contactListPanel.removeAll();
        Map<String, JsonObject> friendMap = peerNode.getFriends();
        List<String> matchedFriends = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (String friend : friendMap.keySet()) {
            if (friend.toLowerCase().contains(lowerQuery)) {
                matchedFriends.add(friend);
            }
        }

        if (matchedFriends.isEmpty()) {
            JLabel noResult = new JLabel("Không tìm thấy bạn bè");
            noResult.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            noResult.setForeground(TEXT_MUTED);
            noResult.setBorder(new EmptyBorder(20, 20, 20, 20));
            contactListPanel.add(noResult);
        } else {
            JLabel header = new JLabel("  Kết quả tìm kiếm — " + matchedFriends.size());
            header.setFont(new Font("Segoe UI", Font.BOLD, 11));
            header.setForeground(ACCENT);
            header.setBorder(new EmptyBorder(10, 12, 6, 12));
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            contactListPanel.add(header);

            for (String name : matchedFriends) {
                JPanel item = new JPanel(new BorderLayout());
                item.setBackground(BG_SIDEBAR);
                item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
                item.setBorder(new EmptyBorder(6, 16, 6, 16));

                JLabel avatar = UIHelper.createAvatar(name, 34);
                JLabel nameLabel = new JLabel(name);
                nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                nameLabel.setForeground(TEXT_PRIMARY);
                nameLabel.setBorder(new EmptyBorder(0, 10, 0, 0));

                item.add(avatar, BorderLayout.WEST);
                item.add(nameLabel, BorderLayout.CENTER);

                JButton chatNowBtn = UIHelper.createSmallButton("Chat ngay", GREEN);
                chatNowBtn.addActionListener(e -> {
                    openChat(name, false, null);
                    isSearchMode = false;
                    searchField.setText("");
                    refreshContactList();
                });
                item.add(chatNowBtn, BorderLayout.EAST);
                contactListPanel.add(item);
            }
        }
        contactListPanel.revalidate();
        contactListPanel.repaint();
    }

    private JPanel createFriendRequestItem(String username) {
        JPanel item = new JPanel(new BorderLayout());
        item.setBackground(BG_SIDEBAR);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        item.setBorder(new EmptyBorder(6, 16, 6, 12));

        JLabel avatar = UIHelper.createAvatar(username, 36);
        JLabel nameLabel = new JLabel(username);
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        nameLabel.setForeground(TEXT_PRIMARY);
        nameLabel.setBorder(new EmptyBorder(0, 10, 0, 0));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnPanel.setOpaque(false);
        JButton acceptBtn = UIHelper.createSmallButton("Đồng ý", GREEN);
        acceptBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        acceptBtn.addActionListener(e -> {
            peerNode.acceptFriendRequest(username);
        });
        JButton rejectBtn = UIHelper.createSmallButton("Từ chối", RED);
        rejectBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rejectBtn.addActionListener(e -> {
            peerNode.rejectFriendRequest(username);
        });
        btnPanel.add(acceptBtn);
        btnPanel.add(rejectBtn);

        item.add(avatar, BorderLayout.WEST);
        item.add(nameLabel, BorderLayout.CENTER);
        item.add(btnPanel, BorderLayout.EAST);
        return item;
    }

    private String getAvatarText(String username) {
        return UIHelper.getAvatarText(username);
    }

    private Color getAvatarColor(String username) {
        return UIHelper.getAvatarColor(username);
    }
}
