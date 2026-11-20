package com.mowoo.mowoo_backend.attachment.service;

import com.mowoo.mowoo_backend.attachment.entity.Attachment;
import com.mowoo.mowoo_backend.attachment.exception.AttachmentErrorCode;
import com.mowoo.mowoo_backend.attachment.repository.AttachmentRepository;
import com.mowoo.mowoo_backend.attachment.storage.ObjectStorage;
import com.mowoo.mowoo_backend.attachment.validation.ImageValidator;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AttachmentService {
    private final AttachmentRepository attachments;
    private final ObjectStorage storage;
    private final ImageValidator validator;
    private final FileDeletionService deletion;
    @Value("${cloud.aws.s3.path.location:uploads}")
    private String prefix;

    public record View(UUID id, String mimeType, String url, int sortOrder) {}

    // 호출자가 소유권을 검증하고 부모 행을 잠근 트랜잭션에서 호출한다.
    public void addToDiary(Diary diary, List<MultipartFile> files) {
        upload(diary.getUser(), diary, null,
                attachments.findByUserIdAndDiaryIdInOrderBySortOrderAsc(diary.getUser().getId(), List.of(diary.getId())), files);
    }

    public void addToMessage(ChatMessage message, List<MultipartFile> files) {
        upload(message.getSession().getUser(), null, message, List.of(), files);
    }

    private void upload(User user, Diary diary, ChatMessage message, List<Attachment> existing, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;
        if (existing.size() + files.size() > 3) throw new BusinessException(AttachmentErrorCode.TOO_MANY_ATTACHMENTS);
        var images = validator.validate(files);
        int order = existing.stream().mapToInt(Attachment::getSortOrder).max().orElse(-1) + 1;
        // 키에는 사용자 파일 이름·클라이언트 경로를 사용하지 않는다.
        String path = prefix == null ? "uploads" : prefix.replaceAll("^/+|/+$", "");
        if (!path.matches("[a-zA-Z0-9/_-]{1,100}")) throw unavailable();
        for (var image : images) {
            String key = path + "/" + user.getId() + "/" + UUID.randomUUID() + "." + image.extension();
            deletion.trackUpload(key);
            try { storage.put(key, image.bytes(), image.mimeType()); }
            catch (RuntimeException e) { throw unavailable(); }
            attachments.save(new Attachment(user, diary, message, key, image.mimeType(), image.bytes().length, order++));
        }
        attachments.flush();
    }

    public void removeFromDiary(UUID userId, UUID diaryId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return;
        var requested = new HashSet<>(ids);
        var owned = attachments.findByUserIdAndDiaryIdInOrderBySortOrderAsc(userId, List.of(diaryId)).stream()
                .filter(a -> requested.contains(a.getId())).toList();
        if (owned.size() != requested.size()) throw new BusinessException(AttachmentErrorCode.ATTACHMENT_NOT_FOUND);
        remove(owned);
    }

    public void deleteDiaries(UUID userId, Collection<UUID> ids) {
        if (!ids.isEmpty()) remove(attachments.findByUserIdAndDiaryIdInOrderBySortOrderAsc(userId, ids));
    }

    public void deleteSession(UUID userId, UUID sessionId) { remove(attachments.findByUserIdAndMessageSessionId(userId, sessionId)); }
    public void deleteSessions(UUID userId) { remove(attachments.findByUserIdAndMessageIsNotNull(userId)); }
    public void deleteAll(UUID userId) { remove(attachments.findByUserId(userId)); }

    private void remove(List<Attachment> rows) {
        if (rows.isEmpty()) return;
        deletion.afterCommit(rows.stream().map(Attachment::getObjectKey).toList());
        attachments.deleteAll(rows);
        attachments.flush();
    }

    public Map<UUID, List<View>> diaries(UUID userId, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return views(attachments.findByUserIdAndDiaryIdInOrderBySortOrderAsc(userId, ids), a -> a.getDiary().getId());
    }

    public Map<UUID, List<View>> messages(UUID userId, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return views(attachments.findByUserIdAndMessageIdInOrderBySortOrderAsc(userId, ids), a -> a.getMessage().getId());
    }

    private Map<UUID, List<View>> views(List<Attachment> rows, Function<Attachment, UUID> parent) {
        Map<UUID, List<View>> result = new HashMap<>();
        for (Attachment row : rows) {
            String url;
            try { url = storage.signedUrl(row.getObjectKey(), row.getMimeType()); }
            catch (RuntimeException e) { throw unavailable(); }
            result.computeIfAbsent(parent.apply(row), ignored -> new ArrayList<>())
                    .add(new View(row.getId(), row.getMimeType(), url, row.getSortOrder()));
        }
        return result;
    }

    private BusinessException unavailable() { return new BusinessException(AttachmentErrorCode.STORAGE_UNAVAILABLE); }
}
