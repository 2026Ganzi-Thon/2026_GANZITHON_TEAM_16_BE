package com.mowoo.mowoo_backend.chat.entity;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uq_chat_messages_session_sequence", columnNames = {"session_id", "sequence_no"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_chat_messages_session"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatSession session;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false)
    private SenderRole senderRole;

    // 응답한 캐릭터. 사용자 메시지는 null이다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_chat_messages_character"))
    private AiCharacter character;

    @Column(columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "edited_at", columnDefinition = "DATETIME")
    private Instant editedAt;

    private ChatMessage(ChatSession session, int sequenceNo, SenderRole senderRole, AiCharacter character,
                        String content) {
        this.session = session;
        this.sequenceNo = sequenceNo;
        this.senderRole = senderRole;
        this.character = character;
        this.content = content;
    }

    public static ChatMessage ofUser(ChatSession session, int sequenceNo, String content) {
        return new ChatMessage(session, sequenceNo, SenderRole.USER, null, content);
    }

    public static ChatMessage ofAssistant(ChatSession session, int sequenceNo, AiCharacter character, String content) {
        return new ChatMessage(session, sequenceNo, SenderRole.ASSISTANT, character, content);
    }

    public void edit(String content, Instant editedAt) {
        this.content = content;
        this.editedAt = editedAt;
    }

    // 사용자 메시지 수정에 맞춰 AI 응답을 다시 만든다. 명세 예시대로 응답의 editedAt은 남기지 않는다.
    public void replaceReply(String content) {
        this.content = content;
    }
}
