package com.mowoo.mowoo_backend.attachment.entity;

import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.util.UUID;

@Entity
@Table(name = "attachments", indexes = {@Index(name = "idx_attachments_diary", columnList = "diary_id"),
        @Index(name = "idx_attachments_message", columnList = "message_id")})
@Check(constraints = "(diary_id is not null and message_id is null) or (diary_id is null and message_id is not null)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(36)", foreignKey = @ForeignKey(name = "fk_attachments_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id", columnDefinition = "CHAR(36)", foreignKey = @ForeignKey(name = "fk_attachments_diary"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Diary diary;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", columnDefinition = "CHAR(36)", foreignKey = @ForeignKey(name = "fk_attachments_message"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatMessage message;
    @Column(name = "object_key", nullable = false, unique = true, length = 255)
    private String objectKey;
    @Column(name = "mime_type", nullable = false, length = 32)
    private String mimeType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Attachment(User user, Diary diary, ChatMessage message, String objectKey, String mimeType, long sizeBytes, int sortOrder) {
        if ((diary == null) == (message == null)) throw new IllegalArgumentException("첨부 대상은 한 개여야 합니다.");
        this.user=user; this.diary=diary; this.message=message; this.objectKey=objectKey;
        this.mimeType=mimeType; this.sizeBytes=sizeBytes; this.sortOrder=sortOrder;
    }
}
