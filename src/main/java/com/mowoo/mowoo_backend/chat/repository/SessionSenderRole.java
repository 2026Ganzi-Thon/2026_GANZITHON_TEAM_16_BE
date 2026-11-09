package com.mowoo.mowoo_backend.chat.repository;

import com.mowoo.mowoo_backend.chat.entity.SenderRole;

import java.util.UUID;

public record SessionSenderRole(UUID sessionId, SenderRole senderRole) {
}
