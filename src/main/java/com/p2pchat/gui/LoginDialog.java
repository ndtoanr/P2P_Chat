package com.p2pchat.gui;

import com.p2pchat.peer.PeerNode;
import com.p2pchat.shared.Constants;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Dialog đăng nhập/đăng ký - Giao diện premium dark theme
 * Bỏ trường Server Host/Port, mặc định dùng localhost:9000
 */
public class LoginDialog extends JDialog {

    private final PeerNode peerNode;

    // Layout
    private CardLayout cardLayout;
    private JPanel cardsPanel;

    // Login UI Components
    private JTextField loginUserField;
    private JPasswordField loginPassField;
    private JButton loginBtn;
    private JLabel loginStatusLabel;

    // Register UI Components
    private JTextField regUserField;
    private JPasswordField regPassField;
    private JPasswordField regConfirmPassField;
    private JButton regBtn;
    private JLabel regStatusLabel;

    // Colors - Modern dark palette
    private static final Color BG_DARK = new Color(17, 24, 39);
    private static final Color BG_CARD = new Color(31, 41, 55);
    private static final Color BG_INPUT = new Color(55, 65, 81);
    private static final Color TEXT_PRIMARY = new Color(243, 244, 246);
    private static final Color TEXT_SECONDARY = new Color(156, 163, 175);
    private static final Color TEXT_MUTED = new Color(107, 114, 128);
    private static final Color ACCENT = new Color(99, 102, 241);
    private static final Color ACCENT_HOVER = new Color(129, 140, 248);
    private static final Color ACCENT_GREEN = new Color(52, 211, 153);
    private static final Color ACCENT_RED = new Color(248, 113, 113);
    private static final Color BORDER_COLOR = new Color(75, 85, 99);

    public LoginDialog(Frame parent, PeerNode peerNode) {
        super(parent, "P2P Chat - Đăng nhập", true);
        this.peerNode = peerNode;
        initUI();
    }

    private void initUI() {
        setSize(420, 600);
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(true);
        setShape(new RoundRectangle2D.Double(0, 0, 420, 600, 24, 24));

        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Background gradient
                GradientPaint gradient = new GradientPaint(
                    0, 0, BG_DARK,
                    0, getHeight(), new Color(15, 23, 42)
                );
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);

                // Subtle glow effect at top
                GradientPaint glow = new GradientPaint(
                    getWidth() / 2, 0, new Color(99, 102, 241, 30),
                    getWidth() / 2, 200, new Color(99, 102, 241, 0)
                );
                g2d.setPaint(glow);
                g2d.fillRoundRect(0, 0, getWidth(), 200, 24, 24);

                // Border
                g2d.setColor(BORDER_COLOR);
                g2d.setStroke(new BasicStroke(1f));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
                g2d.dispose();
            }
        };
        mainPanel.setOpaque(false);

        // Drag window support
        final Point[] dragPoint = {null};
        mainPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { dragPoint[0] = e.getPoint(); }
        });
        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                Point current = e.getLocationOnScreen();
                setLocation(current.x - dragPoint[0].x, current.y - dragPoint[0].y);
            }
        });

        // Top bar with close button
        JPanel topBar = createTopBar();
        mainPanel.add(topBar, BorderLayout.NORTH);

        // Center content
        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(new EmptyBorder(0, 48, 20, 48));

        // Logo/Title area
        JPanel logoPanel = createLogoPanel();
        centerPanel.add(logoPanel);
        centerPanel.add(Box.createVerticalStrut(24));

        // Forms with CardLayout
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setOpaque(false);
        cardsPanel.add(createLoginForm(), "login");
        cardsPanel.add(createRegisterForm(), "register");
        
        centerPanel.add(cardsPanel);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JPanel createTopBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        panel.setOpaque(false);

        JButton closeBtn = createIconButton("Thoát", ACCENT_RED);
        closeBtn.addActionListener(e -> {
            dispose();
            System.exit(0);
        });
        panel.add(closeBtn);
        return panel;
    }

    private JPanel createLogoPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // App icon with gradient circle background
        JLabel iconLabel = new JLabel("💬", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Gradient circle
                int size = 64;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                GradientPaint gp = new GradientPaint(x, y, ACCENT, x + size, y + size, new Color(168, 85, 247));
                g2d.setPaint(gp);
                g2d.fillOval(x, y, size, size);
                g2d.dispose();

                super.paintComponent(g);
            }
        };
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        iconLabel.setPreferredSize(new Dimension(80, 80));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Title
        JLabel titleLabel = new JLabel("P2P Chat");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Subtitle
        JLabel subtitleLabel = new JLabel("Hệ thống Chat ngang hàng P2P");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitleLabel.setForeground(TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(iconLabel);
        panel.add(Box.createVerticalStrut(12));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(subtitleLabel);

        return panel;
    }

    private JPanel createLoginForm() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Username
        panel.add(createLabel("Tên đăng nhập"));
        panel.add(Box.createVerticalStrut(8));
        loginUserField = createTextField("Nhập username...");
        panel.add(loginUserField);
        panel.add(Box.createVerticalStrut(18));

        // Password
        panel.add(createLabel("Mật khẩu"));
        panel.add(Box.createVerticalStrut(8));
        loginPassField = createPasswordField("Nhập mật khẩu...");
        panel.add(loginPassField);
        panel.add(Box.createVerticalStrut(28));

        // Login button
        loginBtn = createPrimaryButton("Đăng nhập");
        loginBtn.addActionListener(e -> doLogin());
        panel.add(loginBtn);
        panel.add(Box.createVerticalStrut(12));

        // Switch to Register button
        JButton showRegBtn = createSecondaryButton("Chưa có tài khoản? Đăng ký ngay");
        showRegBtn.addActionListener(e -> {
            cardLayout.show(cardsPanel, "register");
            clearMessages();
        });
        panel.add(showRegBtn);
        panel.add(Box.createVerticalStrut(20));

        // Status label
        loginStatusLabel = new JLabel(" ");
        loginStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        loginStatusLabel.setForeground(TEXT_SECONDARY);
        loginStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(loginStatusLabel);

        // Enter key binding
        KeyListener enterKey = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doLogin();
                }
            }
        };
        loginUserField.addKeyListener(enterKey);
        loginPassField.addKeyListener(enterKey);

        return panel;
    }

    private JPanel createRegisterForm() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Username
        panel.add(createLabel("Tên đăng nhập"));
        panel.add(Box.createVerticalStrut(8));
        regUserField = createTextField("Nhập username...");
        panel.add(regUserField);
        panel.add(Box.createVerticalStrut(18));

        // Password
        panel.add(createLabel("Mật khẩu"));
        panel.add(Box.createVerticalStrut(8));
        regPassField = createPasswordField("Nhập mật khẩu...");
        panel.add(regPassField);
        panel.add(Box.createVerticalStrut(18));

        // Confirm Password
        panel.add(createLabel("Xác nhận mật khẩu"));
        panel.add(Box.createVerticalStrut(8));
        regConfirmPassField = createPasswordField("Nhập lại mật khẩu...");
        panel.add(regConfirmPassField);
        panel.add(Box.createVerticalStrut(28));

        // Register button
        regBtn = createPrimaryButton("Đăng ký");
        regBtn.addActionListener(e -> doRegister());
        panel.add(regBtn);
        panel.add(Box.createVerticalStrut(12));

        // Switch to Login button
        JButton showLoginBtn = createSecondaryButton("Đã có tài khoản? Đăng nhập");
        showLoginBtn.addActionListener(e -> {
            cardLayout.show(cardsPanel, "login");
            clearMessages();
        });
        panel.add(showLoginBtn);
        panel.add(Box.createVerticalStrut(20));

        // Status label
        regStatusLabel = new JLabel(" ");
        regStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        regStatusLabel.setForeground(TEXT_SECONDARY);
        regStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(regStatusLabel);

        // Enter key binding
        KeyListener enterKey = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doRegister();
                }
            }
        };
        regUserField.addKeyListener(enterKey);
        regPassField.addKeyListener(enterKey);
        regConfirmPassField.addKeyListener(enterKey);

        return panel;
    }

    // ==================== ACTIONS ====================

    private void doLogin() {
        String username = loginUserField.getText().trim();
        String password = new String(loginPassField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            showLoginStatus("Vui lòng nhập đầy đủ thông tin!", ACCENT_RED);
            return;
        }

        setLoginFormEnabled(false);
        showLoginStatus("Đang đăng nhập...", ACCENT);

        peerNode.setOnError(error -> SwingUtilities.invokeLater(() -> {
            showLoginStatus(error, ACCENT_RED);
            setLoginFormEnabled(true);
        }));

        new Thread(() -> {
            boolean success = peerNode.login(username, password,
                Constants.BOOTSTRAP_HOST, Constants.BOOTSTRAP_PORT);
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    showLoginStatus("Đăng nhập thành công! ✓", ACCENT_GREEN);
                    Timer timer = new Timer(500, e -> dispose());
                    timer.setRepeats(false);
                    timer.start();
                } else {
                    setLoginFormEnabled(true);
                }
            });
        }).start();
    }

    private void doRegister() {
        String username = regUserField.getText().trim();
        String password = new String(regPassField.getPassword()).trim();
        String confirmPassword = new String(regConfirmPassField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showRegStatus("Vui lòng nhập đầy đủ thông tin!", ACCENT_RED);
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            showRegStatus("Mật khẩu xác nhận không khớp!", ACCENT_RED);
            return;
        }

        setRegFormEnabled(false);
        showRegStatus("Đang đăng ký...", ACCENT);

        peerNode.setOnError(error -> SwingUtilities.invokeLater(() -> {
            showRegStatus(error, ACCENT_RED);
            setRegFormEnabled(true);
        }));

        new Thread(() -> {
            boolean success = peerNode.register(username, password,
                Constants.BOOTSTRAP_HOST, Constants.BOOTSTRAP_PORT);
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    showRegStatus("Đăng ký thành công! ✓", ACCENT_GREEN);
                    Timer timer = new Timer(500, e -> dispose());
                    timer.setRepeats(false);
                    timer.start();
                } else {
                    setRegFormEnabled(true);
                }
            });
        }).start();
    }

    // ==================== UI HELPERS ====================

    private void clearMessages() {
        showLoginStatus(" ", TEXT_SECONDARY);
        showRegStatus(" ", TEXT_SECONDARY);
    }

    private void showLoginStatus(String text, Color color) {
        loginStatusLabel.setText(text);
        loginStatusLabel.setForeground(color);
    }
    
    private void showRegStatus(String text, Color color) {
        regStatusLabel.setText(text);
        regStatusLabel.setForeground(color);
    }

    private void setLoginFormEnabled(boolean enabled) {
        loginUserField.setEnabled(enabled);
        loginPassField.setEnabled(enabled);
        loginBtn.setEnabled(enabled);
    }
    
    private void setRegFormEnabled(boolean enabled) {
        regUserField.setEnabled(enabled);
        regPassField.setEnabled(enabled);
        regConfirmPassField.setEnabled(enabled);
        regBtn.setEnabled(enabled);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setForeground(TEXT_SECONDARY);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    private JTextField createTextField(String placeholder) {
        JTextField field = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(TEXT_MUTED);
                    g2.setFont(getFont());
                    Insets insets = getInsets();
                    g2.drawString(placeholder, insets.left + 2, getHeight() / 2 + getFont().getSize() / 2 - 2);
                    g2.dispose();
                }
            }
        };
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setForeground(TEXT_PRIMARY);
        field.setBackground(BG_INPUT);
        field.setCaretColor(ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        field.setAlignmentX(Component.CENTER_ALIGNMENT);

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT, 1, true),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)
                ));
                field.repaint();
            }
            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)
                ));
                field.repaint();
            }
        });

        return field;
    }

    private JPasswordField createPasswordField(String placeholder) {
        JPasswordField field = new JPasswordField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getPassword().length == 0 && !hasFocus()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(TEXT_MUTED);
                    g2.setFont(getFont().deriveFont(Font.PLAIN));
                    Insets insets = getInsets();
                    g2.drawString(placeholder, insets.left + 2, getHeight() / 2 + getFont().getSize() / 2 - 2);
                    g2.dispose();
                }
            }
        };
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setForeground(TEXT_PRIMARY);
        field.setBackground(BG_INPUT);
        field.setCaretColor(ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        field.setAlignmentX(Component.CENTER_ALIGNMENT);

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT, 1, true),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)
                ));
            }
            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)
                ));
            }
        });

        return field;
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bg;
                if (!isEnabled()) {
                    bg = new Color(75, 85, 99);
                } else if (getModel().isPressed()) {
                    bg = ACCENT.darker();
                } else if (getModel().isRollover()) {
                    bg = ACCENT_HOVER;
                } else {
                    bg = ACCENT;
                }

                // Subtle shadow
                g2d.setColor(new Color(0, 0, 0, 40));
                g2d.fillRoundRect(1, 2, getWidth() - 2, getHeight() - 2, 12, 12);

                g2d.setColor(bg);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight() - 1, 12, 12);

                g2d.setColor(Color.WHITE);
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 1;
                g2d.drawString(getText(), x, y);
                g2d.dispose();
            }
        };
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setPreferredSize(new Dimension(0, 46));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setForeground(TEXT_SECONDARY);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(ACCENT_HOVER);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(TEXT_SECONDARY);
            }
        });
        return button;
    }

    private JButton createIconButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(color);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(color.brighter());
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(color);
            }
        });
        return button;
    }
}
