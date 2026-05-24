
CREATE DATABASE IF NOT EXISTS p2p_chat 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE p2p_chat;

-- Bảng người dùng
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    public_key TEXT,
    status ENUM('ONLINE', 'OFFLINE', 'AWAY', 'BUSY') DEFAULT 'OFFLINE',
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_status (status)
) ENGINE=InnoDB;

-- Bảng tin nhắn (lưu lịch sử chat)
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL UNIQUE,
    from_user VARCHAR(50) NOT NULL,
    to_user VARCHAR(50) NOT NULL,
    content TEXT,
    message_type ENUM('TEXT', 'FILE', 'IMAGE', 'SYSTEM', 'ENCRYPTED') DEFAULT 'TEXT',
    is_group_message BOOLEAN DEFAULT FALSE,
    group_id VARCHAR(36) DEFAULT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    is_delivered BOOLEAN DEFAULT FALSE,
    is_encrypted BOOLEAN DEFAULT FALSE,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_from_user (from_user),
    INDEX idx_to_user (to_user),
    INDEX idx_group_id (group_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB;

-- Bảng nhóm chat
CREATE TABLE IF NOT EXISTS chat_groups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL UNIQUE,
    group_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_by VARCHAR(50) NOT NULL,
    max_members INT DEFAULT 50,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_group_id (group_id),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB;

-- Bảng thành viên nhóm
CREATE TABLE IF NOT EXISTS group_members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    username VARCHAR(50) NOT NULL,
    role ENUM('ADMIN', 'MEMBER') DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_member (group_id, username),
    INDEX idx_group_id (group_id),
    INDEX idx_username (username)
) ENGINE=InnoDB;

-- Bảng tin nhắn offline (store-and-forward)
CREATE TABLE IF NOT EXISTS offline_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL,
    from_user VARCHAR(50) NOT NULL,
    to_user VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(20) DEFAULT 'TEXT',
    is_group_message BOOLEAN DEFAULT FALSE,
    group_id VARCHAR(36) DEFAULT NULL,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_to_user (to_user),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB;

-- Bảng peer registry (lưu thông tin kết nối peer)
CREATE TABLE IF NOT EXISTS peer_registry (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    ip_address VARCHAR(45) NOT NULL,
    tcp_port INT NOT NULL,
    is_online BOOLEAN DEFAULT TRUE,
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_is_online (is_online)
) ENGINE=InnoDB;

-- Bảng bạn bè (quan hệ 2 chiều)
CREATE TABLE IF NOT EXISTS friends (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user1 VARCHAR(50) NOT NULL,
    user2 VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_friendship (user1, user2),
    INDEX idx_user1 (user1),
    INDEX idx_user2 (user2)
) ENGINE=InnoDB;

-- Bảng lời mời kết bạn
CREATE TABLE IF NOT EXISTS friend_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    from_user VARCHAR(50) NOT NULL,
    to_user VARCHAR(50) NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'REJECTED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_request (from_user, to_user),
    INDEX idx_from_user (from_user),
    INDEX idx_to_user (to_user)
) ENGINE=InnoDB;
