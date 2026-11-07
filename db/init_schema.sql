CREATE TABLE users (
    id CHAR(36) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    email VARCHAR(254) NULL,
    birth_date DATE NULL,
    use_past_diaries BOOLEAN NOT NULL DEFAULT FALSE,
    evening_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_auth_identities (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    provider ENUM('GOOGLE', 'NAVER', 'KAKAO') NOT NULL,
    provider_subject VARBINARY(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_auth_provider_subject (provider, provider_subject),
    CONSTRAINT fk_auth_identity_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_characters (
    id CHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description TEXT NULL,
    image_url VARCHAR(2048) NULL,
    is_premium BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ai_characters_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE character_purchases (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    character_id CHAR(36) NOT NULL,
    payment_provider VARCHAR(50) NOT NULL,
    provider_purchase_id VARBINARY(255) NOT NULL,
    amount_minor_units BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) NOT NULL,
    status ENUM('COMPLETED', 'REFUNDED') NOT NULL DEFAULT 'COMPLETED',
    purchased_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    refunded_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_purchase_provider_transaction (payment_provider, provider_purchase_id),
    KEY idx_purchases_user_date (user_id, purchased_at),
    CONSTRAINT fk_purchase_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_purchase_character
        FOREIGN KEY (character_id) REFERENCES ai_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_characters (
    user_id CHAR(36) NOT NULL,
    character_id CHAR(36) NOT NULL,
    acquired_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, character_id),
    CONSTRAINT fk_user_character_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_character_character
        FOREIGN KEY (character_id) REFERENCES ai_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE emotion_types (
    id CHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    color_hex CHAR(7) NOT NULL,
    icon_url VARCHAR(2048) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_emotion_types_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_sessions (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    character_id CHAR(36) NOT NULL,
    title VARCHAR(150) NULL,
    summary TEXT NULL,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_chat_sessions_user_date (user_id, started_at),
    CONSTRAINT fk_chat_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_chat_sessions_character
        FOREIGN KEY (character_id) REFERENCES ai_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_messages (
    id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    sequence_no INT UNSIGNED NOT NULL,
    sender_role ENUM('USER', 'ASSISTANT') NOT NULL,
    character_id CHAR(36) NULL,
    content TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_chat_messages_session_sequence (session_id, sequence_no),
    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_character
        FOREIGN KEY (character_id) REFERENCES ai_characters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_message_attachments (
    id CHAR(36) NOT NULL,
    message_id CHAR(36) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_message_attachment_order (message_id, sort_order),
    CONSTRAINT fk_chat_attachment_message
        FOREIGN KEY (message_id) REFERENCES chat_messages (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diaries (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    source_session_id CHAR(36) NULL,
    entry_date DATE NOT NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    saved_at DATETIME NULL,
    emotion_confirmed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_diaries_source_session (source_session_id),
    KEY idx_diaries_user_entry_date (user_id, entry_date),
    CONSTRAINT fk_diaries_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_diaries_source_session
        FOREIGN KEY (source_session_id) REFERENCES chat_sessions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diary_attachments (
    id CHAR(36) NOT NULL,
    diary_id CHAR(36) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_diary_attachment_order (diary_id, sort_order),
    CONSTRAINT fk_diary_attachment_diary
        FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diary_emotions (
    diary_id CHAR(36) NOT NULL,
    emotion_type_id CHAR(36) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (diary_id, emotion_type_id),
    CONSTRAINT fk_diary_emotions_diary
        FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE,
    CONSTRAINT fk_diary_emotions_type
        FOREIGN KEY (emotion_type_id) REFERENCES emotion_types (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diary_references (
    message_id CHAR(36) NOT NULL,
    diary_id CHAR(36) NOT NULL,
    accepted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id, diary_id),
    CONSTRAINT fk_diary_references_message
        FOREIGN KEY (message_id) REFERENCES chat_messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_diary_references_diary
        FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
