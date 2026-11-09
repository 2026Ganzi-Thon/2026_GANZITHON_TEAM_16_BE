package com.mowoo.mowoo_backend.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "chat_message_attachments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_message_attachment_order", columnNames = {"message_id", "sort_order"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_chat_attachment_message"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatMessage message;

    // 저장소의 파일 키. 응답 URL은 조회할 때 만료 URL로 발급한다.
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public ChatMessageAttachment(ChatMessage message, String storageKey, String mimeType, int sortOrder) {
        this.message = message;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.sortOrder = sortOrder;
    }
}
